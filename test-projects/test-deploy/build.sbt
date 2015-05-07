// Tester project for basic services.
val service = project.in(file("service")).enablePlugins(DeployPlugin)

// Tester project for services with a node build.
val nodeapp = project.in(file("nodeapp")).enablePlugins(WebappPlugin)
