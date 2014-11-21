val test = project.in(file(".")).enablePlugins(ReleasePlugin)

publishTo := Some("foo" at "bar")
