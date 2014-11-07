lazy val tester = project.in(file("."))
  .enablePlugins(WebappPlugin)
  .settings(NodeKeys.nodeProjectDir in Npm := file("client"))

conflictManager := ConflictManager.strict

dependencyOverrides ++= Set(
  "org.scala-sbt" % "sbt" % "0.13.7-RC3"
)
