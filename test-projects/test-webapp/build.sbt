lazy val tester = project.in(file("."))
  .enablePlugins(WebappPlugin)
  .settings(NodeKeys.nodeProjectDir in Npm := file("client"))

fixNullCurrentBranch
