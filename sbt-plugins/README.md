SBT Plugins
===========

Current release [VERSION](#version): 0.2.2

[Usage](#usage)
-----

Within a `plugins.sbt` file in your project's 'project' directory, put the following:

    credentials += Credentials("Sonatype Nexus Repository Manager", "utility.allenai.org", [username], [password])

    resolvers += "allenai nexus repository" at "http://utility.allenai.org:8081/nexus/content/repositories/releases"

    addSbtPlugin("org.allenai.plugins" % [plugin-id] % VERSION)

Release Notes
-------------

### 0.2.2 ###

- Fixed sbt-travis-publisher plugin's detection of pull requests so it no longer publishes pull request artifacts.

### 0.2.1 ###

- Added commit date to sbt-version-injector plugin

### 0.2.0 ###

- Added sbt-travis-publisher plugin
