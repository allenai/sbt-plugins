SBT Plugins
===========

Current release [VERSION](#VERSION): 0.2

sbt-version-injector
--------------------

SBT plugin that generates resource files for extracting version information.

- `artifact.conf`
- `git.conf`

### Installation ###

In your SBT project's `project' directory, add a file named 'version-injector.sbt'
with the following contents:

    credentials += Credentials("Sonatype Nexus Repository Manager", "utility.allenai.org", [username], [password])

    resolvers += "allenai nexus repository" at "http://utility.allenai.org:8081/nexus/content/repositories/releases"

    addSbtPlugin("org.allenai.plugins" % "sbt-version-injector" % "0.2")

### Usage ###

You can access the generated resource values within your Scala application like so:

    // Replace [org] with the SBT 'organization' key's value
    val artifactConf = ConfigFactory.parseURL("/[org]/artifact.conf")
	val gitConf = ConfigFactory.parseURL("/[org]/git.conf")

    val artifactVersion = artifactConf.getString("version")
    val gitVersion = gitConf.getString("describe")
