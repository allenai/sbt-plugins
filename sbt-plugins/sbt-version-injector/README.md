sbt-version-injector
====================

SBT plugin that generates resource files for extracting version information.

- `artifact.conf`
- `git.conf`

Installation
------------

In your SBT project's `project' directory, add a file named 'version-injector.sbt'
with the following contents:

    // TODO: add the resolver required to access allenai Nexus repository
    // resolvers += ...

    addSbtPlugin("org.allenai.plugins" % "sbt-version-injector" % "0.1.0")

Usage
-----

You can access the generated resource values within your Scala application like so:

    // Replace [org] with the SBT 'organization' key's value
    val artifactConf = ConfigFactory.parseURL("/[org]/artifact.conf")
	val gitConf = ConfigFactory.parseURL("/[org]/git.conf")

    val artifactVersion = artifactConf.getString("version")
    val gitVersion = gitConf.getString("describe")
