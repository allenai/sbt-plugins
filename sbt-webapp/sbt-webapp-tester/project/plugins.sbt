lazy val root = Project("plugins", file(".")).dependsOn(plugin)

lazy val plugin = ProjectRef(file("../../").getCanonicalFile.toURI, "sbtWebapp")

addSbtPlugin("io.spray" % "sbt-revolver" % "0.7.2")
