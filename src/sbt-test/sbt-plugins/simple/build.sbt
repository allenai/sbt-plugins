name := "simple"

// Core project with shared code for testing things.
lazy val core = project.in(file("core"))

lazy val docker = project
  .in(file("docker"))
  .dependsOn(core)
