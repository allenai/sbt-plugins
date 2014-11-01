import sbt._
import Keys._

import bintray.{ Keys => BintrayKeys }
import bintray.{ Plugin => BintrayPlugin }
import sbtrelease.ReleasePlugin._

object BuildSettings {

  lazy val basicSettings = Seq(
    organization := "org.allenai.plugins",
    scalacOptions := Seq(
      "-encoding", "utf8",
      "-feature",
      "-unchecked",
      "-deprecation",
      //"-target:jvm-1.7",
      "-language:_",
      "-Xlog-reflective-calls"),
    scalaVersion := "2.10.4")

  lazy val sbtPluginSettings = basicSettings ++ publishToBintraySettings ++ Seq(
    sbtPlugin := true,
    conflictManager := ConflictManager.strict,
    // This is important because we wrap plugins that use a pre 0.13.5 SBT version
    // (sbt-release, sbt-scalariform, sbt-scalastyle) which will cause loss of 0.13.5 sbt interface
    // during SBT project load (this seems to be an SBT bug).
    // This manifests as an SBT error complaining that `enablePlugins` is not a member
    // of sbt.Project. To prevent this build error, we force those plugins to use 0.13.6
    dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.6"
  )

  lazy val publishToBintraySettings = BintrayPlugin.bintrayPublishSettings ++ Seq(
    publishMavenStyle := false,
    BintrayKeys.repository in BintrayKeys.bintray := "sbt-plugins",
    BintrayKeys.bintrayOrganization in BintrayKeys.bintray := Some("allenai"),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html"))
  )

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := (),
    publishTo := None
  )
}
