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
    sbtPlugin := true
  )

  lazy val publishToBintraySettings = BintrayPlugin.bintrayPublishSettings ++ Seq(
    publishMavenStyle := false,
    BintrayKeys.repository in BintrayKeys.bintray := "sbt-plugins",
    BintrayKeys.bintrayOrganization in BintrayKeys.bintray := Some("allenai"),
    licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    // release plugin checks to make sure the publishTo setting is
    // set. However, we're using bintray sbt plugin for publishing and are not
    // using the publishTo setting. To make the release plugin happy,
    // we just set it to a fake resolver value.
    publishTo := Some("fake" at "for sbt-release plugin happiness")
  )

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := (),
    // required until these tickets are closed https://github.com/sbt/sbt-pgp/issues/42,
    // https://github.com/sbt/sbt-pgp/issues/36
    publishTo := Some("fake" at "for sbt-release plugin happiness")
  )
}
