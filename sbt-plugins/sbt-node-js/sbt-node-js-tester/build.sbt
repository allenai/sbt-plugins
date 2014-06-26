name := "tester"

scalaVersion := "2.10.4"

resolvers ++= Seq(
  "spray repo" at "http://repo.spray.io"
)

libraryDependencies ++= Seq(
  "io.spray" % "spray-routing" % "1.3.1",
  "io.spray" % "spray-can" % "1.3.1",
  "io.spray" %%  "spray-json" % "1.2.6",
  "com.typesafe.akka" %% "akka-actor" % "2.3.3"
)

// Install the NodeJsPlugin settings, providing the relative client directory path

val tester = project.in(file(".")).enablePlugins(NodeJsPlugin)
  .settings(
  NodeKeys.npmRoot in Npm := file("node-app"),
    products in Compile <<= (products in Compile).dependsOn(NodeKeys.build in Npm))
  .settings(Revolver.settings: _*)
