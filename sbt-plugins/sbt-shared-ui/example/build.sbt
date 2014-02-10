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

// The `shared` sub-project that will generate web asset resources
// that can be used by dependent projects.
// You must add the SharedUiPlugin.sharedProjectSettings to
// a project to enable it as a shared UI project upon which
// other subprojects can depend.
lazy val shared = project.in(file("shared"))
  .settings(SharedUiPlugin.sharedProjectSettings: _*)

// A UI project that depends on the `shared` project.
// To hook into the shared UI project, you must add the
// SharedUiPlugin.dependentProjectSettings parameterized
// by the shared project. These settings force building of
// shared web assets as a dependency for the dependent project's
// build.
lazy val ui1 = project.in(file("ui1"))
  .dependsOn(shared) // required to enable watching assets in `shared`
  .settings(SharedUiPlugin.dependentProjectSettings(shared): _*)
  .settings(serverSettings: _*)

// A second UI project that depends on the `shared` project
lazy val ui2 = project.in(file("ui2"))
  .dependsOn(shared) // required to enable watching assets in `shared`
  .settings(SharedUiPlugin.dependentProjectSettings(shared): _*)
  .settings(serverSettings: _*)

lazy val root = project.in(file(".")).aggregate(ui1, ui2).settings(
  name := "sbt-shared-ui-tester",
  description := "Proof of technology project for the sbt-shared-ui plugin"
)
