# allenai-sbt-release

AutoPlugin that provides a custom versioning scheme.

**Required by**: `LibraryPlugin`

**Wraps**: [sbt-release](https://github.com/sbt/sbt-release)

The release plugin's default configuration is for releases that are made by the
continuous build system to BinTray.  Specifically, the plugin configures the
`sbt release` command to make commits and tags only--it does not actually
publish.  It's expected that the CI system is configured to recognize the tag
and kick off a publish.

For an example, please look at [common](https://github.com/allenai/common).

## Installation

Enable the plugin for your **root** project in `build.sbt`:

```
val myProject = project.in(file(".")).enablePlugins(ReleasePlugin)
```

## Multi-project builds
If your project consists of subprojects, you must do the following:

- enable the `ReleasePlugin` for the root project
- stub out publishing settings for the root project
- enable the `ReleasePlugin` for all subprojects that you will release
- make sure the root project aggregates at least all subprojects that are to be released via the plugin

Here is an example multi-build project `build.sbt`:

```scala
lazy val root = project.in(file(".")).settings(
    publish := { },
    publishTo := Some("bogus" at "http://nowhere.com"),
    publishLocal := { })
   .enablePlugins(ReleasePlugin)
   .aggregate(core, service)

// The core subproject will be released when you issue the release SBT command
lazy val core = project.in(file("core")).enablePlugins(ReleasePlugin)

// The service subproject will not be released because the ReleasePlugin is not enabled
lazy val service = project.in(file("service")).enablePlugins(WebServicePlugin)
```
