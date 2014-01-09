SBT Plugins
===========

Current release [VERSION](#version): 0.2.0

[Usage](#usage)
-----

Within a `plugins.sbt` file in your project's 'project' directory, put the following:

    credentials += Credentials("Sonatype Nexus Repository Manager", "utility.allenai.org", [username], [password])

    resolvers += "allenai nexus repository" at "http://utility.allenai.org:8081/nexus/content/repositories/releases"

    addSbtPlugin("org.allenai.plugins" % [plugin-id] % VERSION)


