lazy val root = Project("plugins", file(".")).dependsOn(plugin)

lazy val plugin = ProjectRef(file("../../").getCanonicalFile.toURI, "sbtWebService")

conflictManager := ConflictManager.strict

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.6"
