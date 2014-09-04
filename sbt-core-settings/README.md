# allenai-sbt-core-settings

This plugin provides core settings to projects including:

- Setting `conflictManager := ConflictManager.strict`
- Adds slf4j dependencies and resolves common slf4j conflicts
- Provides `CoreSettings.Dependencies` object with many common (and versioned) dependencies
- Enables the `FormatPlugin` and `VersionInjectorPlugin`

To use, add the following to your projects `project/plugins.sbt` file:

```scala
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-core-settings" % VERSION)
```

where `VERSION` is the current version available on BinTray.

See `sbt-core-settings-tester/build.sbt` and `sbt-core-settings-tester/project/plugins.sbt` for usage.
