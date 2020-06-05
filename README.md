# AI2 SBT Plugins

[![Build Status](https://semaphoreci.com/api/v1/projects/8954a1aa-855b-4ada-807e-f81e30ff88dd/653444/badge.svg)](https://semaphoreci.com/allenai/sbt-plugins)

**This repository is no longer supported, and should not be used for new projects.  Internally at AI2, teams (Euclid, S2, and Aristo) are moving away from SBT Plugins.  Please assign any PRs to Michael Schmitz.**

All plugins also enable the `CoreSettings` plugin ([docs](docs/core-settings.md)), which contains AI2-wide common settings. In particular, this enables the `StylePlugin` ([docs](docs/style.md)) to help with code formatting & style.

More documentation for individual plugins can be found in the [docs](docs/) directory.

## Usage

Add the following to your project's `project/plugins.sbt`:

```scala
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-plugins" % VERSION)
```

where `VERSION` is the current release version (see [our bintray repo](https://bintray.com/allenai/sbt-plugins/allenai-sbt-plugins/view) to find available versions).

## Install Git pre-commit hook to autoformat sources

Once you've added the plugins to a project, you can install a git pre-commit hook that will autoformat your code before allowing a commit via:

```shell
sbt generateAutoformatGitHook
```

## Developing AI2 Plugins

Currently, all plugins are defined in the same SBT project. New plugins should be created in:

- `src/main/scala/org/allenai/plugins` if they are to be part of core settings or if they are a mixin plugin.

## Testing

We use [sbt-scripted](http://eed3si9n.com/testing-sbt-plugins) for testing our SBT plugins. To run the tests:

```shell
$ sbt
> scripted
```

You could also just run `sbt test` which will trigger the scripted tests as well.

To keep test execution time down, we prefer to minimize the number of sbt-scripted test projects. If possible, try to write tests in the primary test project in [src/sbt-test/sbt-plugins/simple](src/sbt-test/sbt-plugins/simple).

If you write an isolated test, you can execute only that test via:

```shell
scripted sbt-plugins/my-test
```

This assumes you've written your test in `src/sbt-test/sbt-plugins/my-test`. This is useful to speed up code/test iterating.

## Publishing Releases

Following the Typesafe lead, we publish our plugins to a [bintray](https://bintray.com/allenai/sbt-plugins).

Bintray does not like snapshot versions, so all of our published versions are releases.

### Bintray Credentials

To publish new versions you will need a `~/.bintray/.credentials` file with the following contents. Be sure to `chmod 600` it!
You can also use a personal Bintray login that has access to the `allenai` organization. To setup bintray credentials, either

```bash
sbt bintrayChangeCredentials
```

or:

```bash
realm = Bintray API Realm
host = api.bintray.com
user = ai2-dev [or your bintray username]
password = [API Key for ai2-dev user ]
```

Where `[API Key]` is the API key for the ai2-dev account (or your account if using personal account) on Bintray. You can find it by asking someone! There is a username & password combo that might work to log in to the account as well.

1. Log into bintray as `ai2-dev`
2. Click on the `ai2-dev` account name in top-right corner.
3. Click on `Edit` under `ai2-dev`
4. Click on `API Key` in navigation list

### Releasing

1. Checkout the `master` branch of the repository
2. Make sure the upstream-tracking branch is `master` @ allenai/sbt-plugins
  
```shell
git branch --set-upstream-to=upstream/master
```

3. Cut the release:
  
```shell
sbt release
```

The plugin will set the appropriate defaults so just hit `<ENTER>` through the prompts. Also, some errors will be logged when the plugin creates a tag and pushes it to the upstream repository. This is not really an error but git outputting some text to stderr.

To verify your release, look for the new version in our [bintray repo](https://bintray.com/allenai/sbt-plugins) and also for the tag in the [github repository](https://github.com/allenai/sbt-plugins)
