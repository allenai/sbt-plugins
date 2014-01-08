import BuildSettings._
import sbtrelease.ReleasePlugin._

// Do NOT aggregate the various subprojects to prevent accidental
// publishing of all plugins at the same time.
// By keeping the subprojects isolated, we force plugins to be
// published manually and individually.

lazy val root =
  project.in(file ("."))
    .settings(publishSettings: _*)
    .settings(releaseSettings: _*)
    .settings(
      name := "sbt-plugins")
    .aggregate(sbtVersionInjector)

lazy val sbtVersionInjector =
  project.in(file("sbt-version-injector"))
    .settings(sbtPluginSettings: _*)
    .settings(publishSettings: _*)
    .settings(
      name := "sbt-version-injector"
    )


