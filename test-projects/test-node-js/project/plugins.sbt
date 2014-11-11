lazy val root = Project("plugins", file(".")).dependsOn(plugin)

lazy val plugin = ProjectRef(file("../..").getCanonicalFile.toURI, "ai2Plugins")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.7-RC3"
