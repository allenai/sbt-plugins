# sbt-webapp

This plugin aggregates the `sbt-deploy` and `sbt-node-js` plugins.

To use, add the following to your projects `project/plugins.sbt` file:

```scala
addSbtPlugin("org.allenai.plugins" % "sbt-webapp" % "2014.06.26-1-SNAPSHOT")
```

The `sbt-webapp` is an [AutoPlugin](http://www.scala-sbt.org/release/tutorial/Using-Plugins.html#Creating+an+auto+plugin), which provides default settings (more on defaults later). You have to enable the plugin for your project. In `build.sbt`:

```scala
val myProject = project.in(file(".")).enablePlugins(WebappPlugin)
```

Note: you do not need to import anything as `WebappPlugin` is automatically imported when you add the plugin.

## Default Settings

By default, the `sbt-webapp` plugin sets the `sbt-node-js` settings for the webapp root directory and build directory. They are:

    npmRoot -> [project]/webapp
    buildDir -> [project]/public

Note: these files are relative to your project, so they will be at the same level as `src` and `target` in your project directory.

Additionally, the `sbt-webapp` plugin adds the `buildDir` to the `mappings in Universal` setting to ensure the `buildDir` (default is `public`) is deployed along with the application.

## Overriding Default Settings

You can change any of the settings for `sbt-node-js` and `sbt-deploy` plugins in the following way:

```scala
val myProject = project.in(file("."))
  .enablePlugins(WebappPlugin)
  .settings(
    nodeProjectDir in Npm := file("clientapp"),
    nodeProjectTarget in Npm := file("client-build"),
    mappings in Universal += ...
  )
```

Note: if you change the `buildDir`, the `mappings in Universal` will automatically use the overridden value and package it up during deploy.
