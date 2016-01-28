sbt-version-injector
====================

**Required by**: `CoreSettings`

**Note**: This plugin is auto-enabled as part of `CoreSettings`

SBT plugin that generates resource files for extracting version information.

- `artifact.conf`
- `git.conf`

The recommended way to access is with the [Version class](https://github.com/allenai/common/blob/master/core/src/main/scala/org/allenai/common/Version.scala), but you can also access the generated resources directly:

    // Replace [org] with the SBT 'organization' key's value
    val artifactConf = ConfigFactory.parseURL("/[org]/[artifact name]/artifact.conf")
	val gitConf = ConfigFactory.parseURL("/[org]/[artifact name]/git.conf")

    val artifactVersion = artifactConf.getString("version")
    val gitVersion = gitConf.getString("describe")
    
