lazy val root = Project("plugins", file(".")).dependsOn(plugin)

lazy val plugin = ProjectRef(file("../..").getCanonicalFile.toURI, "ai2Plugins")

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.7-RC3"
