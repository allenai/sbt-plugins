sbt-deploy
====================

**Boss**: Jesse

**Required by**: `WebServicePlugin` && `WebappPlugin`

**Wraps**: [`sbt-native-packager`](https://github.com/sbt/sbt-native-packager)

A plugin for deploying applications to a machine using [Typesafe Config](https://github.com/typesafehub/config) for
configuration.

This handles basic configuration of the [sbt native packager](https://github.com/sbt/sbt-native-packager),
pushing to EC2 hosts, and restarting your service.

deploy
------

### configuration
Enable the deploy plugin, which will provide default settings:
```scala
// In your project's build.sbt
val myProject = project.in(file(".")).enablePlugins(DeployPlugin).settings(
  // Add any additonal directories you need synced from the universal staging dir
  deployDirs += "some_other_dir")
```

By default, we disable the underlying JavaAppPackaging plugin's generation of bash
start scripts. This is to avoid confusion with the `run-class.sh` start script
we generate - which should be used for starting an application as a service. You can opt-in
to having the JavaAppPackaging also generate a non-daemon start script if that is something
you need. To opt-in, add the following line to your build.sbt:

```scala
makeDefaultBashScript := true
```

### running
In order to push to a remote host, you need to have the `AWS_PEM_FILE` environment variables set to
the path of your AWS PEM key file.

Once you have an keyfile configured, you can run by executing, from your project root:

    sbt deploy <target>

"<target>" is an arbitrary object in your configuration file. This should be a config file located
at `conf/deploy.conf` within your project's (or subproject's) root.

This target key must point to an object that has the format documented in
[../src/main/resources/global_deploy.conf](https://github.com/allenai/sbt-plugins/blob/master/src/main/resources/global_deploy.conf).

You can provide key/value overrides on the commandline through Java-style property definitions:

    sbt deploy <target> -Dproperty.path=some_string_value

The override key is within the <target> namespace.  E.g. to set the project version, use `-Dproject.version=`. To set the host use `-Ddeploy.host=`, etc.

### configuration
Documentation for all of the configuration values is in `conf/global_deploy.conf`, which can serve as a base
configuration file for the deploy configurations.

A simple configuration using `global_deploy.conf` could look like:

    staging = {
      include "global_deploy.conf"

      project = {
        name = "servicename"
      }
      deploy = {
        host = "ari-staging.allenai.org"
      }
    }
    prod {
      include "global_deploy.conf"

      project = {
        name = "servicename"
      }
      deploy = {
        host = "ari-staging.allenai.org"
      }
    }

The `ari-core` project has a more complicated example using more features of the [HOCON language](https://github.com/typesafehub/config/blob/master/HOCON.md).

### caching
By Default, the deployTask generates a cacheKey for the subproject that changes on dependency changes or new git commits to the subproject src directory.
run-class.sh injects it into the VM in the application variable `application.cacheKey`.

If you want the cacheKey to be generated locally, running the `stageAndCacheKey` task from Sbt will spit a cacheKey into stage/conf/cacheKey.Sha1

run-class.sh
------------

run-class.sh is a script that will run a single Java classfile out of a
universal packager installation.  The deploy plugin injects this script
into the managed resources of the client project on compile.  It is put in
`bin/` when staging a universal package.

To use this, pick a unique service name (e.g. "controller" or "tuple-solver"),
and create a script with the following template in either `src/main/bin` or `src/universal/bin`:

In `servicename.sh`:

    #!/bin/bash
    CLASS_NAME=org.allenai.mypackage.MyServiceMainClass

    SCRIPT_DIR=`dirname $0`
    SHORT_NAME=`basename $0 .sh`
    . "${SCRIPT_DIR}/run-class.sh" "$CLASS_NAME" "$SHORT_NAME" "$@"

Note that this code (specifically the line assigning SHORT_NAME) will use the
name of your script as your service name, so choose wisely!

The deploy plugin will now stage this file to the `bin` directory with run-class.sh, and it can be used to start your service:

    $ ./servicename.sh start
    $ ./servicename.sh stop
    $ ./servicename.sh restart # Same as 'stop' followed by 'start'.

This will also use `servicename` for your logback appname.
