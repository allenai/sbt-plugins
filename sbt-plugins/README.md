SBT Plugins
===========

Current release [VERSION](#version): 0.2

[Usage](#usage)
-----

Best practice: for each plugin from this project you want to use in your project, add a file named '[plugin-id].sbt'
in your project's 'project' directory with the following contents:

    credentials += Credentials("Sonatype Nexus Repository Manager", "utility.allenai.org", [username], [password])

    resolvers += "allenai nexus repository" at "http://utility.allenai.org:8081/nexus/content/repositories/releases"

    addSbtPlugin("org.allenai.plugins" % [plugin-id] % VERSION)


