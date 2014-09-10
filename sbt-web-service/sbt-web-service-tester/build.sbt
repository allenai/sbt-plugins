name := "core-settings-tester"

// Enabling the WebServicePlugin will add all basic library dependencies
// and other plugins (such as DeployPlugin)
lazy val root = project.in(file(".")).enablePlugins(WebServicePlugin)
