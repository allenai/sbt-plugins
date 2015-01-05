# WebappPlugin

**Requires**: `WebServicePlugin` && `NodeJsPlugin` ([node plugin doc](node-js.md))

The `sbt-webapp` is a superset of the `WebServicePlugin` adding support for managing a Node.js build from SBT. You enable the plugin for your project. In `build.sbt`:

```scala
val myProject = project.in(file(".")).enablePlugins(WebappPlugin)
```

Note: you do not need to import anything as `WebappPlugin` is automatically imported when you add the plugin.

## Default Settings

By default, the `sbt-webapp` plugin sets the `sbt-node-js` settings for the webapp root directory and build directory. They are:

    nodeProjectDirectory -> [project]/webapp
    nodeProjectTarget -> [project]/public

Note: these files are relative to your project, so they will be at the same level as `src` and `target` in your project directory.

Additionally, the `sbt-webapp` plugin adds the `buildDir` to the `mappings in Universal` setting to ensure the `buildDir` (default is `public`) is deployed along with the application.

## Overriding Default Settings

You can change any of the settings for `sbt-node-js` and `sbt-deploy` plugins in the following way:

```scala
// in build.sbt
enablePlugins(WebappPlugin)
nodeProjectDir in Npm := file("clientapp")
nodeProjectTarget in Npm := file("client-build")
mappings in Universal += ...
```

Note: if you change `nodeProjectDirectory`, the `mappings in Universal` will automatically use the overridden value and package it up during deploy.

## Developing with sbt-revolver

The required `WebServicePlugin` includes [`sbt-revolver`](https://github.com/spray/sbt-revolver). For development, you can run the service layer of the web application via:

```shell
sbt
> reStart
```

Running `reStart` will _not_ build the NPM frontend application. However, the `WebappPlugin` provides an additional task that will build the NPM frontend application:

```shell
sbt
> reStartWebapp
```
