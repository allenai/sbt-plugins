[![Build Status](https://magnum.travis-ci.com/allenai/sbt-plugins.svg?token=bTo69ep8z4cnh7oxWjjY)](https://magnum.travis-ci.com/allenai/sbt-plugins)

SBT Plugins
===========

## Usage

Add plugins in your project's `project/plugins.sbt`. For example:

```scala
lazy val ai2PluginsVersion = "2014.11.04-1"

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-library" % ai2PluginsVersion)

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-web-service" % ai2PluginsVersion)

addSbtPlugin("org.allenai.plugins" % "allenai-sbt-webapp" % ai2PluginsVersion)

// Due to an SBT bug, the following settings are necessary when adding the allenai-sbt-web-service
// plugin:
conflictManager := ConflictManager.strict

dependencyOverrides += "org.scala-sbt" % "sbt" % "0.13.6"
```

**Boss**: Mark S

## Publishing

Following the Typesafe lead, we publish our plugins to a [bintray](https://bintray.com/allenai/sbt-plugins).

Bintray does not like snapshot versions, so all of our published versions are releases.

# Configuration
To publish new versions you will need a `~/.bintray/.credentials` file with the following contents. Be sure to `chmod 600` it!
You can also use a personal Bintray login that has access to the `allenai` organization.

```
realm = Bintray API Realm
host = api.bintray.com
user = ai2-dev
password = [API Key for ai2-dev user]
```

Where `[API Key]` is the API key for the ai2-dev account on Bintray. You can find it by:

1. Log into bintray (password is in passwords.txt on ari prod server)
2. Click on the `ai2-dev` account name in top-right corner 
3. Click on `Edit` under `ai2-dev`
4. Click on `API Key` in navigation list

# Publishing

Manually update the `version.sbt` file.

Run `sbt publish`.

## Developing Plugins

Each plugin subproject also has a `[subproj]-tester` directory, which is a separate SBT project using the plugin under test. These projects are meant for rapid feedback while making changes to the plugins, and not necessarily as a template for how to use the plugins.
