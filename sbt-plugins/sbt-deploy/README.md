sbt-deploy
====================

A plugin for deploying applications to a machine using [Typesafe Config](https://github.com/typesafehub/config) for
configuration.

This handles basic configuration of the [sbt native packager](https://github.com/sbt/sbt-native-packager),
pushing to EC2 hosts, and restarting your service.

deploy
------

### installation

Add the plugin to your project:
```
// In project/plugins.sbt
addSbtPlugin("org.allenai.plugins" % "sbt-deploy" % "2014.4.14-1-SNAPSHOT")
```
Add the deploy settings to your project:
```
// In your project's build.sbt
Deploy.settings
```

### running
In order to push to a remote host, you need to have the `deploy.user.ssh_keyfile` config key present. The easiest way to
get this configured is to add it to a `~/.deployrc` file. A sample `.deployrc` file is in [`conf/example_rcfile.conf`](https://github.com/allenai/tools/blob/master/sbt-plugins/sbt-deploy/conf/example_rcfile.conf).

Once you have an keyfile configured, you can run by executing, from your project root:

    sbt deploy deploy.target

"deploy.target" is an arbitrary object in your configuration file. This should be a config file located
at `conf/deploy.conf` within your project's (or subproject's) root.

This target key must point to an object that has the format documented in
[`conf/global_deploy.conf`](https://github.com/allenai/tools/blob/master/sbt-plugins/sbt-deploy/conf/global_deploy.conf).

You can provide key/value overrides on the commandline through Java-style property definitions:

    sbt deploy deploy.target -Dproperty.path=some_string_value

This is mostly useful for setting a custom `project.version`.

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
