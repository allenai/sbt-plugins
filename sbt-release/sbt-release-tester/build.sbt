val test = project.in(file(".")).enablePlugins(AllenaiReleasePlugin)

publishTo := Some("foo" at "bar")
