sbt-style
==========

A plugin for checking Scala source code style. Minimal overlap with the format plugin; this (mostly) checks style that isn't automatically corrected by scalariform.

Installing this plugin will make a project's `compile` task depend on the style checker.

To disable scalastyle warnings for a block of code, bound it in these special comments:
```
// scalastyle:off

bad_code()

// scalastyle:on
```

Note that *no spaces* can happen after the `:` in the comment - this is different than with scalariform!

You can also disable checks on a single line with a postfix comment:
```
bad_code() // scalastyle:ignore
```

## Installation

The plugin is an AutoPlugin which requires SBT version 0.13.5 or later.

To install, add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-style" % VERSION)
```
Substitute `VERSION` with the latest version for the plugin on [bintray](https://bintray.com/allenai/sbt-plugins).

The plugin should be auto-enabled for every project, so you don't have to explicitly enable it in `build.sbt`.

### Tasks

#### styleCheck

Check the source code's style. This will print out any warnings or errors, as
well as a cryptic success message:
```
> styleCheck
[warn] sbt-plugins/sbt-style/sbt-style-tester/src/main/scala/Main.scala:1: Line is more than 100 characters long
[warn] sbt-plugins/sbt-style/sbt-style-tester/src/main/scala/Main.scala:3:12: var initialized with integer literal; should be initialized from named constant or made val
[success] created: sbt.SettingKey$$anon$4@25d7a745
```
