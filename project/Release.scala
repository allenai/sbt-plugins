import sbt._
import Keys._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

object Release {

  def releaseSettings = Seq(
    organization := "org.allenai",
    organizationHomepage := Some(url("https://www.allenai.org")),
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url(s"https://github.com/allenai/sbt-plugins")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/allenai/sbt-plugins"),
        "git@github.com:allenai/sbt-plugins.git"
      )
    ),
    publishArtifact in Test := false,
    // Release process settings.
    releaseProcess := releaseProcessSteps,
    releaseUseGlobalVersion := false
  )

  def releaseProcessSteps = Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    releaseStepCommandAndRemaining("^test"),
    releaseStepCommandAndRemaining("^scripted"),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("^codeArtifactPublish"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
}
