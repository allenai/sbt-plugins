# allenai-sbt-core-settings

**Requires**: `StylePlugin` && `VersionInjectorPlugin`

**Note**: `CoreSettingsPlugin` is implicitly applied to all projects via its `trigger` setting. This means you do not need to explicitly enable this plugin.

The `CoreSettingsPlugin` provides core settings to projects including:

- Setting `conflictManager := ConflictManager.strict`
- Adds slf4j dependencies and resolves common slf4j conflicts
- Provides `CoreSettings.Dependencies` object with many common (and versioned) dependencies
- Enables the `StylePlugin` and `VersionInjectorPlugin`

See `sbt-core-settings-tester/build.sbt` and `sbt-core-settings-tester/project/plugins.sbt` for usage.

## Tip: define a `project/Dependencies.scala` file

The `CoreSettingsPlugin` provides your build classpath with `org.allenai.plugins.CoreDependencies`. If your project will have additional depdencies (most likely), you should define your own `Dependencies` build object that extends the `CoreDependencies` trait:

```scala
// in project/Dependencies.scala
import org.allenai.plugins.CoreDependencies

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
