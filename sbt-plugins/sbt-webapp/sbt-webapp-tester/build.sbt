val tester = project.in(file(".")).enablePlugins(WebappPlugin)

NodeKeys.nodeProjectDir in Npm := file("client")
