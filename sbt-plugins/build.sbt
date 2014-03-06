import BuildSettings._
import sbtrelease.ReleasePlugin._

// Subprojects are aggregated to enforce publishing all to the same version.

lazy val root =
  project.in(file ("."))
    .settings(publishSettings: _*)
    .settings(releaseSettings: _*)
    .settings(
      name := "sbt-plugins")
    .aggregate(sbtVersionInjector, sbtTravisPublisher, sbtSharedUi)

lazy val sbtVersionInjector =
  project.in(file("sbt-version-injector"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "sbt-version-injector"
    )

lazy val sbtTravisPublisher =
  project.in(file("sbt-travis-publisher"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "sbt-travis-publisher"
    )

lazy val sbtSharedUi =
  project.in(file("sbt-shared-ui"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "sbt-shared-ui"
    )

lazy val sbtDeploy =
  project.in(file("sbt-deploy"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "sbt-deploy"
    )
