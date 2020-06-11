import sbt._
import Keys._
import bintray.BintrayKeys._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.ReleaseStateTransformations._

object Release {

  def releaseSettings = Seq(
    crossSbtVersions := Vector("1.3.10", "0.13.16"),
    organization := "org.allenai.plugins",
    organizationHomepage := Some(url("https://www.allenai.org")),
    licenses := Seq("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
    homepage := Some(url(s"https://github.com/allenai/sbt-plugins")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/allenai/sbt-plugins"),
        "git@github.com:allenai/sbt-plugins.git"
      )
    ),
    // Publish settings.
    // http://www.scala-sbt.org/0.12.2/docs/Detailed-Topics/Artifacts.html
    publishArtifact := true, // Enable publish
    publishArtifact in Test := false,
    publishMavenStyle := false,
    // Bintray settings.
    bintrayRepository := "sbt-plugins",
    bintrayPackage := name.value,
    bintrayOrganization := Some("allenai"),
    bintrayVcsUrl := Some("https://github.com/allenai/sbt-plugins.git"),
    //  bintrayReleaseOnPublish := false, To enable staging
    //  bintrayRelease := false

    // Release process settings.
    releaseProcess := releaseProcessSteps,
    releaseUseGlobalVersion := false
  )

  def releaseProcessSteps = Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    releaseStepCommandAndRemaining("^test"),
    releaseStepCommandAndRemaining("^ scripted"),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    releaseStepCommandAndRemaining("^publish"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
}