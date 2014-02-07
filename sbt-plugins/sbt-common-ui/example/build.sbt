import com.typesafe.jse.sbt.JsEnginePlugin.JsEngineKeys
import spray.revolver.RevolverPlugin._

val serverSettings = Revolver.settings ++ Seq(
  resolvers += "spray repo" at "http://repo.spray.io",
  resolvers += "Typesafe Repository" at "http://repo.typesafe.com/typesafe/releases/",
  libraryDependencies += "io.spray" % "spray-routing" % "1.2.0",
  libraryDependencies += "io.spray" % "spray-can" % "1.2.0",
  libraryDependencies += "com.typesafe.akka" %% s"akka-actor" % "2.2.3"
)

val jsEngineSettings = Seq(
  // This override causes local node.js install to be used for
  // javascript execution environment instead of rhino.
  JsEngineKeys.engineType := JsEngineKeys.EngineType.Node
)

lazy val root = project.in(file(".")).aggregate(ui1, ui2).settings(
  name := "sbt-web-pot"
)

lazy val common = project.in(file("common"))
  .settings(CommonUiPlugin.commonProjectSettings: _*)
  .settings(jsEngineSettings: _*)

lazy val ui1 = project.in(file("ui1"))
  .dependsOn(common)
  .settings(CommonUiPlugin.dependentProjectSettings(common): _*)
  .settings(serverSettings: _*)
  .settings(jsEngineSettings: _*)

lazy val ui2 = project.in(file("ui2"))
  .dependsOn(common)
  .settings(CommonUiPlugin.dependentProjectSettings(common): _*)
  .settings(serverSettings: _*)
  .settings(jsEngineSettings: _*)
