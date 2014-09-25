# allenai-sbt-release

AutoPlugin that wraps [sbt-release](https://github.com/sbt/sbt-release) providing a custom versioning scheme.

## Installation

Add the plugin to your project:
```
// In project/plugins.sbt
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-release" % VERSION
```
Substitute `VERSION` with the latest version for the plugin on [bintray](https://bintray.com/allenai/sbt-plugins).

Enable the plugin for your **root** project in `build.sbt`:

```
val myProject = project.in(file(".")).enablePlugins(AllenaiReleasePlugin)
```

## Multi-project builds
If your project consists of subprojects, you must do the following:

- enable the `AllenaiReleasePlugin` for the root project
- stub out publishing settings for the root project
- enable the `AllenaiReleasePlugin` for all subprojects that you will release
- make sure the root project aggregates at least all subprojects that are to be released via the plugin

Here is an example multi-build project `build.sbt`:

```scala
lazy val root = project.in(file(".")).settings(
    publish := { },
    publishTo := Some("bogus" at "http://nowhere.com"),
    publishLocal := { })
   .enablePlugins(AllenaiReleasePlugin)
   .aggregate(core, service)
   
// The core subproject will be released when you issue the release SBT command   
lazy val core = project.in(file("core")).enablePlugins(AllenaiReleasePlugin)

// The service subproject will not be released because the AllenaiReleasePlugin is not enabled
lazy val service = project.in(file("service")).enablePlugins(WebServicePlugin)
```
