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

## Tip: define a `project/Dependencies.scala` file

Enabling the `allenai-sbt-core-settings` provides your build classpath with `org.allenai.sbt.core.CoreDependencies`. If your project will have additional depdencies (most likely), you should define your own `Dependencies` build object that extends the `CoreDependencies` trait:

```scala
// in project/Dependencies.scala
import org.allenai.sbt.CoreDependencies

import sbt._
import sbt.Keys._

object Dependencies extends CoreDependencies {
  val foo = "org.foo" % "foo" % "1.0.0"
  ...
}
```

Then you can access your dependencies (core and project-specific) within your `.sbt` files like so:

```scala
// in build.sbt
import Dependencies._

libraryDependencies ++= Seq(
  scopt, // from CoreDependencies
  foo // from Dependencies
)
```
