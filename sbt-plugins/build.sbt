import BuildSettings._

// Do NOT aggregate the various subprojects to prevent accidental
// publishing of all plugins at the same time.
// By keeping the subprojects isolated, we force plugins to be
// published manually and individually.

lazy val root =
  project.in(file ("."))
    .settings(noPublishing: _*)

lazy val sbtVersionInjector =
  project.in(file("sbt-version-injector"))
    .settings(sbtPluginSettings: _*)
    .settings(publishSettings: _*)
    .settings(
      name := "sbt-version-injector",
      version := "0.1.0-SNAPSHOT"
    )


