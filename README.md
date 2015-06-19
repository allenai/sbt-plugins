AI2 SBT Plugins
===========

The AI2 SBT plugins are intended to minimize build boilerplate accross projects. It is recommended to only enable "Archetype" plugins, which currently include:

- `CliPlugin` - for command line applications using the [scopt](https://github.com/scopt/scopt) library.
- `LibraryPlugin` - for libraries to be released / published using our `ReleasePlugin`
- `WebServicePlugin` - for web service applications built on spray, akka, and spray-json
- `WebappPlugin` ([docs](docs/webapp.md)) - for web applications that have a service layer and a Node.js built web client.

It is fine to enable more than one plugin, including more than one archetype plugin. However, if we find the need to do so it could be an indicator that we have a new archetype to define.

All plugins also enable the `CoreSettings` plugin ([docs](docs/core-settings.md)), which contains AI2-wide common settings. In particular, this enables the `StylePlugin` ([docs](docs/style.md)) to help with code formatting & style.

More documentation for individual plugins can be found in the [docs](docs/) directory.

# Usage

Add the following to your project's `project/plugins.sbt`:

```scala
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-plugins" % VERSION)
```

where `VERSION` is the current release version (see [our bintray repo](https://bintray.com/allenai/sbt-plugins/allenai-sbt-plugins/view) to find available versions).

Our archetype plugins are [`AutoPlugin`](http://www.scala-sbt.org/0.13.6/api/index.html#sbt.AutoPlugin)s. To enable an archetype plugin for a project, do the following:

*If you have a root build.sbt*

```scala
// in build.sbt
val service = project.in(file("service")).enablePlugins(WebServicePlugin)
```

*If you have a project/Build.scala instead of a root build.sbt*

```scala
import org.allenai.plugins.archetypes._

import sbt.Build

object ProjBuild extends Build {
  lazy val service = project.in(file("service")).enablePlugins(WebServicePlugin)
}
```

# Developing AI2 Plugins

Currently, all plugins are defined in the same SBT project. New plugins should be created in:

- `src/main/scala/org/allenai/plugins` if they are to be part of core settings or if they are a mixin plugin (in other words not an Archetype plugin).

- `src/main/scala/org/allenai/plugins/archetypes` if they are to be a project Archetype plugin

## Test Projects

Each plugin should have its own `test-[plugin]` project in the `test-projects/` directory, which are separate SBT projects using the plugin under test. These projects are meant for rapid feedback while making changes to the plugins, and not necessarily as a template for how to use the plugins.

## Publishing Releases

Following the Typesafe lead, we publish our plugins to a [bintray](https://bintray.com/allenai/sbt-plugins).

Bintray does not like snapshot versions, so all of our published versions are releases.

### Bintray Credentials

To publish new versions you will need a `~/.bintray/.credentials` file with the following contents. Be sure to `chmod 600` it!
You can also use a personal Bintray login that has access to the `allenai` organization.

```
realm = Bintray API Realm
host = api.bintray.com
user = ai2-dev [or your bintray username]
password = [API Key for ai2-dev user ]
```

Where `[API Key]` is the API key for the ai2-dev account (or your account if using personal account) on Bintray. You can find it by asking someone! There is a username & password combo that might work to log in to the account as well.

1. Log into bintray as `ai2-dev`
2. Click on the `ai2-dev` account name in top-right corner 
3. Click on `Edit` under `ai2-dev`
4. Click on `API Key` in navigation list

### Releasing

We dogfood our own `ReleasePlugin` for releasing new plugin versions. To issue a release, do the following:

1. Checkout the `master` branch of the repository
2. Make sure the upstream-tracking branch is `master` @ allenai/sbt-plugins
  
  ```shell
  $ git branch --set-upstream-to=upstream/master
  ```
3. Cut the release:
  
  ```shell
  $ sbt release
  ```

The plugin will set the appropriate defaults so just hit `<ENTER>` through the prompts. Also, some errors will be logged when the plugin creates a tag and pushes it to the upstream repository. This is not really an error but git outputting some text to stderr.

To verify your release, look for the new version in our [bintray repo](https://bintray.com/allenai/sbt-plugins) and also for the tag in the [github repository](https://github.com/allenai/sbt-plugins)
