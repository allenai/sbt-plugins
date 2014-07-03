# allenai-sbt-release

AutoPlugin that wraps [sbt-release](https://github.com/sbt/sbt-release) providing a custom versioning scheme.

### installation

Add the plugin to your project:
```
// In project/plugins.sbt
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-release" % VERSION
```
Substitute `VERSION` with the latest version for the plugin on [bintray](https://bintray.com/allenai/sbt-plugins).

Enable the plugin for your project in `build.sbt`:

```
val myProject = project.in(file(".")).enablePlugins(AllenaiReleasePlugin)
```

