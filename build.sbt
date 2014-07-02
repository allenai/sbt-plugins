import BuildSettings._
import sbtrelease.ReleasePlugin._

// Subprojects are aggregated to enforce publishing all to the same version.

lazy val root =
  project.in(file ("."))
    .settings(noPublishing: _*)
    .settings(releaseSettings: _*)
    .settings(
      scalaVersion := "2.10.4",
      name := "sbt-plugins")
    .aggregate(sbtFormat, sbtVersionInjector, sbtTravisPublisher, sbtDeploy, sbtNodeJs, sbtWebapp)

lazy val sbtFormat =
  project.in(file("sbt-format"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-format"
    )

lazy val sbtVersionInjector =
  project.in(file("sbt-version-injector"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-version-injector"
    )

lazy val sbtTravisPublisher =
  project.in(file("sbt-travis-publisher"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-travis-publisher"
    )

lazy val sbtDeploy =
  project.in(file("sbt-deploy"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-deploy"
    )

lazy val sbtNodeJs =
  project.in(file("sbt-node-js"))
    .settings(sbtPluginSettings: _*)
    .settings(publishToBintraySettings: _*)
    .settings(
      name := "allenai-sbt-node-js"
    )

lazy val sbtWebapp =
  project.in(file("sbt-webapp"))
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-webapp"
    ).dependsOn(sbtDeploy, sbtNodeJs)
