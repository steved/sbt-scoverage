name := "sbt-scoverage_domino"

organization := "org.scoverage"

resolvers += "Domino Open-source Artifactory" at "https://domino.jfrog.io/domino/domino-open-source/"
sbtPlugin := true

scalacOptions := Seq("-unchecked", "-deprecation", "-feature", "-encoding", "utf8")

resolvers ++= {
  if (isSnapshot.value) Seq(Resolver.sonatypeRepo("snapshots")) else Nil
}

libraryDependencies += "org.scoverage" %% "scalac-scoverage-plugin_domino" % "1.4.0"
libraryDependencies += "org.scoverage" %% "scalac-scoverage-runtime_domino" % "1.4.0"

publishMavenStyle := true

publishArtifact in Test := false

scriptedLaunchOpts ++= Seq(
  "-Xmx1024M",
  "-Dplugin.version=" + version.value
)

import ReleaseTransformations._
releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  releaseStepCommandAndRemaining("^ test"),
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("^ publishSigned"),
  setNextVersion,
  commitNextVersion,
  pushChanges
)

releaseCrossBuild := false

publishTo := Some("Artifactory Realm" at "https://domino.jfrog.io/domino/domino-open-source")
credentials += Credentials("Artifactory Realm", "domino.jfrog.io", "username", "password")

pomExtra := {
  <url>https://github.com/scoverage/sbt-scoverage</url>
    <licenses>
      <license>
        <name>Apache 2</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0</url>
        <distribution>repo</distribution>
      </license>
    </licenses>
    <scm>
      <url>git@github.com:scoverage/sbt-scoverage.git</url>
      <connection>scm:git@github.com:scoverage/sbt-scoverage.git</connection>
    </scm>
    <developers>
      <developer>
        <id>sksamuel</id>
        <name>sksamuel</name>
        <url>http://github.com/sksamuel</url>
      </developer>
    </developers>
}

crossSbtVersions := Vector("0.13.17", "1.1.1")

scalariformAutoformat := false
