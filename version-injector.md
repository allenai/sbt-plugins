sbt-version-injector
====================

SBT plugin that generates resource files for extracting version information.

- `artifact.conf`
- `git.conf`

You can access the generated resource values within your Scala application like so:

    // Replace [org] with the SBT 'organization' key's value
    val artifactConf = ConfigFactory.parseURL("/[org]/artifact.conf")
	val gitConf = ConfigFactory.parseURL("/[org]/git.conf")

    val artifactVersion = artifactConf.getString("version")
    val gitVersion = gitConf.getString("describe")

## Installation

The plugin is an AutoPlugin which requires SBT version 0.13.5 or later.

To install, add the following to your `project/plugins.sbt`:

```scala
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-version-injector" % VERSION)
```
Substitute `VERSION` with the latest version for the plugin on [bintray](https://bintray.com/allenai/sbt-plugins).

The plugin is set to be auto-enabled for every project, so you don't have to explicitly enable it in `build.sbt`.
