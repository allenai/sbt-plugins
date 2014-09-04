import BuildSettings._

// Subprojects are aggregated to enforce publishing all to the same version.

lazy val root =
  project.in(file ("."))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(noPublishing: _*)
    .settings(
      scalaVersion := "2.10.4",
      name := "sbt-plugins")
    .aggregate(sbtFormat, sbtVersionInjector, coreSettings, sbtTravisPublisher, sbtDeploy, sbtRelease, sbtNodeJs, sbtWebapp)

lazy val sbtFormat =
  project.in(file("sbt-format"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-format"
    )

lazy val sbtVersionInjector =
  project.in(file("sbt-version-injector"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-version-injector"
    )

lazy val coreSettings =
  project.in(file("sbt-core-settings"))
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-core-settings")
    .dependsOn(sbtFormat, sbtVersionInjector)

lazy val sbtTravisPublisher =
  project.in(file("sbt-travis-publisher"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-travis-publisher"
    )

lazy val sbtDeploy =
  project.in(file("sbt-deploy"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-deploy"
    )

lazy val sbtRelease =
  project.in(file("sbt-release"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-release"
    )

lazy val sbtNodeJs =
  project.in(file("sbt-node-js"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-node-js"
    )

lazy val sbtWebapp =
  project.in(file("sbt-webapp"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(
      name := "allenai-sbt-webapp"
    ).dependsOn(sbtDeploy, sbtNodeJs)
