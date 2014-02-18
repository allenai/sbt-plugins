SBT Plugins
===========

Current release [VERSION](#version): 2014.02.18-1

[Usage](#usage)
-----

Within a `plugins.sbt` file in your project's 'project' directory, put the following:

    credentials += Credentials("Sonatype Nexus Repository Manager", "utility.allenai.org", [username], [password])

    resolvers += "allenai nexus repository" at "http://utility.allenai.org:8081/nexus/content/repositories/releases"

    addSbtPlugin("org.allenai.plugins" % [plugin-id] % VERSION)

Release Notes
-------------

### 2014.02.18-1 ###

- Fixed build-breaking bug where a target directory was not available to LESS compiler
- Changed versioning strategy to yyyy.MM.dd-[i]

### 0.2.5 ###

- Fixed sbt-shared-ui to enable dependency on multiple shared projects.
- Changed the sbt-shared-ui settings method names:
  - `SharedUiPlugin.sharedProjectSettings` is replaced by `SharedUiPlugin.uiSettings`
  - `SharedUiPlugin.dependentProjectSettings(project, namespace)` is now `SharedUiPlugin.uses(project, namespace)`
- It is now required for all ui projects (shared or not) to add the `SharedUiPlugin.uiSettings`

### 0.2.4 ###

- Fixed sbt-shared-ui to compile LESS sources prior to copying resources.

### 0.2.3 ###

- Added sbt-shared-ui plugin

### 0.2.2 ###

- Fixed sbt-travis-publisher plugin's detection of pull requests so it no longer publishes pull request artifacts.

### 0.2.1 ###

- Added commit date to sbt-version-injector plugin

### 0.2.0 ###

- Added sbt-travis-publisher plugin
