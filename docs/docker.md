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

The override key is within the <target> namespace.  E.g. to set the host use `-Ddeploy.host=`, etc.

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

Running the deploy task with this config would result in a single instance of `servicename`
being deployed to the host `ari-staging.allenai.org`, with code placed in the remote directory
`/local/deploy/servicename`.

A more complex configuration would involve deploying multiple instances of a host in parallel. An
example configuration achieving this would be:

    staging = {
      include "global_deploy.conf"

      project = {
        name = "servicename"
      }
      deploy = {
        replicas = [
          {
            host = "ari-staging.allenai.org"
          }
          {
            host = "ari-staging.allenai.org"
          }
        ]
      }
    }

Deploying with this config would set up two instances of `servicename` on `ari-staging.allenai.org`,
with code placed in the remote directories `/local/deploy/servicename-1` and
`/local/deploy/servicename-2`.

The naming scheme of remote directories the plugin deploys to is determined by the number of
replicas being deployed to a given host. If only one replica is being deployed to a host, then
only one directory will be created on the host, and it will be named according to the value of
the `deploy.directory` key in config (which in turn can be derived from the `project.name` key).
On the other hand, if multiple replicas are being deployed to a host then multiple directories
will be created on the host; the names of these directories will take the form `deploy.directory-$i`,
where `i` is the index of the replica contained in the directory.

The above example is contrived in that it deploys two identical services on the same host. More
likely, we'd want to deploy two instances of a service with slightly different configuration (to
prevent conflict over port bindings, for example). The deploy task supports this usage by
allowing per-replica config overrides to be specified in `deploy.conf`, for example:

    staging = {
      include "global_deploy.conf"

      project = {
        name = "servicename"
      }
      deploy = {
        replicas = [
          {
            host = "ari-staging.allenai.org",
            config_overrides = { port = 2000 }
          }
          {
            host = "ari-staging.allenai.org"
            config_overrides = { port = 3000 }
          }
        ]
      }
    }

Note that overrides are merged directly with the top-level object defined by the service's
environment configuration (for example, `dev.conf`), so keys must match for overrides to take hold.
It's also possible to specify a `config_overrides` key in the top-level `deploy` object. Any
overrides listed in that location will be applied to all replicas, or to the single top-level
host if only one service instance is being deployed.

The deploy task will handle stopping and cleaning up any replicas that become stale as deploy
configuration changes over time. For example, if a project with config:

    staging = {
      include "global_deploy.conf"

      project = {
        name = "servicename"
      }
      deploy = {
        replicas = [
          {
            host = "ari-staging.allenai.org",
            config_overrides = { port = 2000 }
          }
          {
            host = "ari-staging.allenai.org"
            config_overrides = { port = 3000 }
          }
        ]
      }
    }

Was modified to have config:

    staging = {
      include "global_deploy.conf"

      project = {
        name = "servicename"
      }
      deploy = {
        host = "ari-staging.allenai.org"
      }
    }

On the next deploy, the deploy task will stop the old replicas running in
`/local/deploy/servicename-1` and `/local/deploy/servicename-2` and delete those directories
before deploying to `/local/deploy/servicename`. The same would be true in reverse. The task
would not, however, delete the two directories if a third replica was added instead of the second
being removed, since the directories would still be valid for use according to the directory
naming scheme outlined above.

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

If you need to pass arguments to the service's main class on startup, you can do so by appending
the args to the call to `servicename.sh`, separated from the `start|restart` command by `--`:

    $ ./servicename.sh start -- arg1 arg2 --flag
    $ ./servicename.sh restart -- other1 --otherflag

This is not recommended for normal deploys, however, since it makes it impossible to manually
restart the service with the same start state without looking into deploy configuration.

