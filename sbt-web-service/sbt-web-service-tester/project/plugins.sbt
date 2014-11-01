lazy val root = Project("plugins", file(".")).dependsOn(plugin)

lazy val plugin = ProjectRef(file("../../").getCanonicalFile.toURI, "sbtWebService")

// Not sure why this is necessary, but without it we get the error:
// 'error: value enablePlugins is not a member of sbt.Project'
// What is really confusing (and frustrating) is that we don't have to do this for the
// sbt-webapp-tester project, which adds another layer of abstraction. Why would we
// get this error at this level only (and not the level before - when enabling CoreSettingsPlugin,
// and not the layer after when enabling WebappPlugin)?
conflictManager := ConflictManager.strict

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.6"
