import BuildSettings._

// Subprojects are aggregated to enforce publishing all to the same version.

lazy val root =
  project.in(file ("."))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(noPublishing: _*)
    .settings(
      scalaVersion := "2.10.4",
      name := "sbt-plugins")
    .aggregate(
      sbtStyle,
      sbtVersionInjector,
      sbtCoreSettings,
      sbtTravisPublisher,
      sbtDeploy,
      sbtRelease,
      sbtNodeJs,
      sbtWebService,
      sbtWebapp)

lazy val sbtStyle =
  project.in(file("sbt-style"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-style")

lazy val sbtVersionInjector =
  project.in(file("sbt-version-injector"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-version-injector")

lazy val sbtCoreSettings =
  project.in(file("sbt-core-settings"))
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-core-settings")
    .dependsOn(sbtStyle, sbtVersionInjector)

lazy val sbtTravisPublisher =
  project.in(file("sbt-travis-publisher"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-travis-publisher")

lazy val sbtDeploy =
  project.in(file("sbt-deploy"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-deploy")

lazy val sbtRelease =
  project.in(file("sbt-release"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-release")

lazy val sbtNodeJs =
  project.in(file("sbt-node-js"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-node-js")

// Archetype Plugins

lazy val sbtLibrary =
  project.in(file("sbt-library"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-library")
    .dependsOn(sbtCoreSettings, sbtRelease)

lazy val sbtWebService =
  project.in(file("sbt-web-service"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-web-service")
    .dependsOn(sbtCoreSettings, sbtDeploy)

lazy val sbtWebapp =
  project.in(file("sbt-webapp"))
    .enablePlugins(AllenaiReleasePlugin)
    .settings(sbtPluginSettings: _*)
    .settings(name := "allenai-sbt-webapp")
    .dependsOn(sbtWebService, sbtNodeJs)

// TODO(markschaake): sbtCliApp and other archetype plugins
