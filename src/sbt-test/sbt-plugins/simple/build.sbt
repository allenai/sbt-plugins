name := "simple"

// Core project with shared code for testing things.
lazy val core = project.in(file("core"))

lazy val docker = project
  .in(file("docker"))
  .dependsOn(core)
  .enablePlugins(DockerBuildPlugin)
  .settings(libraryDependencies += "joda-time" % "joda-time" % "2.4")
