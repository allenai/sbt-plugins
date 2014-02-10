import com.typesafe.jse.sbt.JsEnginePlugin.JsEngineKeys
import spray.revolver.RevolverPlugin._

// Spray server settings
val serverSettings = Revolver.settings ++ Seq(
  resolvers += "spray repo" at "http://repo.spray.io",
  resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies += "io.spray" % "spray-routing" % "1.2.0",
  libraryDependencies += "io.spray" % "spray-can" % "1.2.0",
  libraryDependencies += "com.typesafe.akka" %% s"akka-actor" % "2.2.3"
)

// The `common` sub-project that will generate web asset resources
// that can be used by dependent projects.
// You must add the CommonUiPlugin.commonProjectSettings to
// a project to enable it as a common UI project upon which
// other subprojects can depend.
lazy val common = project.in(file("common"))
  .settings(CommonUiPlugin.commonProjectSettings: _*)

// A UI project that depends on the `common` project.
// To hook into the common UI project, you must add the
// CommonUiPlugin.dependentProjectSettings parameterized
// by the common project. These settings force building of
// common web assets as a dependency for the dependent project's
// build.
lazy val ui1 = project.in(file("ui1"))
  .dependsOn(common) // required to enable watching assets in `common`
  .settings(CommonUiPlugin.dependentProjectSettings(common): _*)
  .settings(serverSettings: _*)

// A second UI project that depends on the `common` project
lazy val ui2 = project.in(file("ui2"))
  .dependsOn(common) // required to enable watching assets in `common`
  .settings(CommonUiPlugin.dependentProjectSettings(common): _*)
  .settings(serverSettings: _*)

lazy val root = project.in(file(".")).aggregate(ui1, ui2).settings(
  name := "sbt-common-ui-tester",
  description := "Proof of technology project for the sbt-common-ui plugin"
)
