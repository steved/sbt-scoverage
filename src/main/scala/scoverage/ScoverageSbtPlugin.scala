package scoverage

import sbt.Keys._
import sbt._
import scoverage.report.{ScoverageHtmlWriter, ScoverageXmlWriter, CoberturaXmlWriter}

object ScoverageSbtPlugin extends ScoverageSbtPlugin

class ScoverageSbtPlugin extends sbt.AutoPlugin {

  val OrgScoverage = "org.scoverage"
  val ScalacRuntimeArtifact = "scalac-scoverage-runtime"
  val ScalacPluginArtifact = "scalac-scoverage-plugin"
  val ScoverageVersion = "1.0.0.BETA1"

  object autoImport {
    val coverageExcludedPackages = settingKey[String]("regex for excluded packages")
    val coverageExcludedFiles = settingKey[String]("regex for excluded file paths")
    val coverageMinimumCoverage = settingKey[Double]("scoverage-minimum-coverage")
    val coverageFailOnMinimumCoverage = settingKey[Boolean]("if coverage is less than this value then fail build")
    val coverageHighlighting = settingKey[Boolean]("enables range positioning for highlighting")
    val coverageOutputCobertua = settingKey[Boolean]("enables cobertura XML report generation")
    val coverageOutputXML = settingKey[Boolean]("enables xml report generation")
    val coverageOutputHTML = settingKey[Boolean]("enables html report generation")
    val coverageAggregateReport = settingKey[Boolean]("if true will generate aggregate parent report")
  }

  import autoImport._

  override def trigger = allRequirements
  override def projectSettings = Seq(

    libraryDependencies ++= Seq(
      OrgScoverage % (ScalacRuntimeArtifact + "_" + scalaBinaryVersion.value) % ScoverageVersion % "provided",
      OrgScoverage % (ScalacPluginArtifact + "_" + scalaBinaryVersion.value) % ScoverageVersion % "provided"
    ),

    coverageExcludedPackages := "",
    coverageExcludedFiles := "",
    coverageMinimumCoverage := 0, // default is no minimum
    coverageFailOnMinimumCoverage := false,
    coverageHighlighting := true,
    coverageOutputXML := true,
    coverageOutputHTML := true,
    coverageOutputCobertua := true,
    coverageAggregateReport := true,

    scalacOptions in(Compile, compile) ++= {
      val scoverageDeps: Seq[File] = update.value matching configurationFilter("provided")
      scoverageDeps.find(_.getAbsolutePath.contains(ScalacPluginArtifact)) match {
        case None => throw new Exception(s"Fatal: $ScalacPluginArtifact not in libraryDependencies")
        case Some(classpath) =>
          println("Classpath=" + classpath)
          Seq(
            Some(s"-Xplugin:${classpath.getAbsolutePath}"),
            Some(s"-P:scoverage:dataDir:${crossTarget.value.getAbsolutePath}/scoverage-data"),
            Option(coverageExcludedPackages.value.trim)
              .filter(_.nonEmpty)
              .map(v => s"-P:scoverage:excludedPackages:$v"),
            Option(coverageExcludedFiles.value.trim).filter(_.nonEmpty).map(v => s"-P:scoverage:excludedFiles:$v")
          ).flatten
      }
    },

    // rangepos is broken in some releases of scala
    scalacOptions in(Compile, compile) ++= (if (coverageHighlighting.value) List("-Yrangepos") else Nil),

    // disable parallel execution to work around "classes.bak" bug in SBT
    parallelExecution in Test := false,

    testOptions in Test <+= testsCleanup
  )

  /** Generate hook that is invoked after the tests have executed. */
  def testsCleanup = {
    (crossTarget in Test,
      baseDirectory in Compile,
      scalaSource in Compile,
      definedTests in Test,
      coverageMinimumCoverage in Test,
      coverageFailOnMinimumCoverage in Test,
      streams in Global) map {
      (crossTarget,
       baseDirectory,
       compileSourceDirectory,
       definedTests,
       min,
       failOnMin,
       s) =>
        Tests.Cleanup {
          () =>

            s.log.info(s"[scoverage] Waiting for measurement data to sync...")
            Thread.sleep(2000) // have noticed some delay in writing, hacky but works

            val dataDir = crossTarget / "/scoverage-data"
            val reportDir = crossTarget / "scoverage-report"
            val coberturaDir = crossTarget / "coverage-report"
            coberturaDir.mkdirs()
            reportDir.mkdirs()

            val coverageFile = IOUtils.coverageFile(dataDir)
            val measurementFiles = IOUtils.findMeasurementFiles(dataDir)

            s.log.info(s"[scoverage] Reading scoverage instrumentation [$coverageFile]")

            if (coverageFile.exists) {

              s.log.info(s"[scoverage] Reading scoverage measurements...")
              val coverage = IOUtils.deserialize(coverageFile)
              val measurements = IOUtils.invoked(measurementFiles)
              coverage.apply(measurements)

              s.log.info(s"[scoverage] Generating Cobertura report [${coberturaDir.getAbsolutePath}/cobertura.xml]")
              new CoberturaXmlWriter(baseDirectory, coberturaDir).write(coverage)

              s.log.info(s"[scoverage] Generating XML report [${reportDir.getAbsolutePath}/scoverage.xml]")
              new ScoverageXmlWriter(compileSourceDirectory, reportDir, false).write(coverage)
              new ScoverageXmlWriter(compileSourceDirectory, reportDir, true).write(coverage)

              s.log.info(s"[scoverage] Generating HTML report [${reportDir.getAbsolutePath}/index.html]")
              new ScoverageHtmlWriter(compileSourceDirectory, reportDir).write(coverage)

              s.log.info("[scoverage] Reports completed")

              // check for default minimum
              if (min > 0) {
                def is100(d: Double) = Math.abs(100 - d) <= 0.00001

                if (is100(min) && is100(coverage.statementCoveragePercent)) {
                  s.log.info(s"[scoverage] 100% Coverage !")
                } else if (min > coverage.statementCoveragePercent) {
                  s.log.error(s"[scoverage] Coverage is below minimum [$coverage.statementCoverageFormatted}% < $min%]")
                  if (failOnMin)
                    throw new RuntimeException("Coverage minimum was not reached")
                } else {
                  s.log.info(s"[scoverage] Coverage is above minimum [${coverage.statementCoverageFormatted}% > $min%]")
                }
              }

              s.log.info(s"[scoverage] All done. Coverage was [${coverage.statementCoverageFormatted}%]")
              ()

            } else {
              s.log.info(s"[scoverage] Scoverage data file does not exist. Skipping report generation")
              ()
            }
        }
    }
  }
}