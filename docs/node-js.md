# SBT NodeJS plugin

This plugin provides the ability to manage building a Node.js application from SBT. The primary use case is to allow client-side development to occur in a complete isolation from SBT using Node.js build tools (such as Gulp.js). The only requirement is to have `npm` (Node Package Manager) on your PATH. `npm` is included with every Node.js installation.

## Installation

Add the following to your `project/plugins.sbt`:

```
addSbtPlugin("org.allenai.plugins" % "allenai-sbt-node-js" % VERSION)
```
Substitute `VERSION` with the latest version for the plugin on [bintray](https://bintray.com/allenai/sbt-plugins).

Enable the plugin for your project in `build.sbt`:

```
val myProject = project.in(file(".")).enablePlugins(NodeJsPlugin)
```

## Usage

Start an SBT session scoped to your Spray application:

```
$ sbt
> project my-spray-server
```

### Provided Tasks

The plugin provides several tasks, all scoped to the `npm` config. This means that to execute them, you have to prefix the command with `npm:`.

- `npm:install` download and install all the Node.js dependencies for the client app

- `npm:build` generate a production build of the client app (depends on a `build` task being defined in the package.json of the client app)
   Note: running `npm:build` will always run `npm:install`, so it is not necessary to run both commands separately.

- `npm:test` run tests in the client app (depends on a `test` task being defined in the package.json of the client app)

- `npm:clean` clean the client app (depends on a `clean` task being defined in the package.json of the client app)

- `npm:environment` generates environment variables to set for calls to `npm`. Default values are:

```
NODE_ENV="prod"
NODE_API_HOST="/api"
NODE_BUILD_DIR="public"
```

These environment variables are useful to a Node.js build file (such as a gulpfile.js). You can modify the defaults and add your own environment variables.
TODO: point to the template project which shows how to use the environment variables.

### Arbitrary `npm` Commands

You can run arbitrary `npm` commands from the SBT prompt:

```
> npm <arbitrary commands here>
```
