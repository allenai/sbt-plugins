import BuildSettings._

// Subprojects are aggregated to enforce publishing all to the same version.

lazy val root =
  project.in(file ("."))
    .settings(noPublishing: _*)
    .settings(ReleaseSettings.settings: _*)
    .settings(
      scalaVersion := "2.10.4",
      name := "sbt-plugins")
    .aggregate(sbtFormat, sbtVersionInjector, sbtTravisPublisher, sbtDeploy, sbtNodeJs, sbtWebapp)

lazy val sbtFormat =
  project.in(file("sbt-format"))
    .enablePlugins(FormatPlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-format"
    )

lazy val sbtVersionInjector =
  project.in(file("sbt-version-injector"))
    .enablePlugins(FormatPlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-version-injector"
    )

lazy val sbtTravisPublisher =
  project.in(file("sbt-travis-publisher"))
    .enablePlugins(FormatPlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-travis-publisher"
    )

lazy val sbtDeploy =
  project.in(file("sbt-deploy"))
    .enablePlugins(FormatPlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-deploy"
    )

lazy val sbtRelease =
  project.in(file("sbt-release"))
    .enablePlugins(FormatPlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-release"
    )

lazy val sbtNodeJs =
  project.in(file("sbt-node-js"))
    .enablePlugins(FormatPlugin)
    .settings(sbtPluginSettings: _*)
    .settings(publishToBintraySettings: _*)
    .settings(
      name := "allenai-sbt-node-js"
    )

lazy val sbtWebapp =
  project.in(file("sbt-webapp"))
    .enablePlugins(FormatPlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-webapp"
    ).dependsOn(sbtDeploy, sbtNodeJs)
