# sbt-javascript-tester

A plugin for testing javascript files.

## Installation

Add the following to your `project/plugins.sbt` file:

    val nexus = "http://utility.allenai.org:8081/nexus/content/repositories/releases"

    credentials += Credentials(
      "Sonatype Nexus Repository Manager",
      "utility.allenai.org",
      <username>,
      <password>)

    resolvers += "AllenAI" at nexus

    addSbtPlugin("org.allenai.plugins" % "sbt-javascript-tester" % <VERSION>)

You do not need to import the settings, as they are automatically imported via the new AutoPlugin interface (introduced in SBT 0.13.2-M3).

## Writing Javscript Tests

Test files are expected to be in the `/src/test/assets` directory and have a file suffix of `Spec.js` or `Test.js`. Javascript files without a `Spec.js` or `Test.js` suffix will not be executed by the test runner.

The `sbt-javascript-tester` uses the [Mocha](http://visionmedia.github.io/mocha/) unit testing framework under the hood. The `sbt-javascript-tester` plugins assumes the [BDD interface](http://visionmedia.github.io/mocha/#interfaces) is being used.

## Executing Javascript Tests

Javascript tests will be executed by default during the normal `test` task. You can specifically run javascript tests via:

    > web-assets-test:jstest

## RequireJS Support

To test a file that defines a RequireJS module (via a `define` function call), you will need to add `amdefine` support to the module. Per the [RequireJS docs](http://requirejs.org/docs/node.html#nodeModules), the RequireJS optimizer is smart enough to strip out this extra code at build time.

See the [sbt-javascript-tester-tester](sbt-javascript-tester-tester) project to see how it works. Basically, you will need to add a dependency:

```scala
libraryDependencies += "org.webjars" % "amdefine" % "0.1.0-1"
```

and then in your modules, add a check to the existence of the `define` function and fall back to the node.js require with `amd` syntax:

```javascript
if (typeof define !== 'function') {
  var define = require('amdefine')(module);
}

define([], function() {
  "use strict";
  ...
}
```
