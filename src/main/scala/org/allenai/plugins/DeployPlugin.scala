package org.allenai.plugins

import org.allenai.plugins.NodeJsPlugin.autoImport.{ NodeKeys, Npm }
import com.typesafe.config._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.universal.UniversalPlugin
import sbt._
import sbt.Keys._

import java.io.File
import scala.collection.JavaConverters._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process.{ Process, ProcessLogger }
import scala.util.{ Failure, Success, Try }

/** Plugin to deploy a project to an ec2 instance. Handles copying binaries and config files,
  * generating a basic startup script, copying over an environment-specific config file if it
  * exists, and restarting the service.
  *
  * This looks for a file called `conf/deploy.conf` in your project's root directory for
  * configuration. This should be a typesafe config file with any number of deploy environments
  * configured.
  *
  * This builds from the currently active git branch unless a version is specified, in which case it
  * will check out that tag/branch/commit before building.
  *
  * This will replace _completely_ the contents of the remote {bin,conf,lib,public} directories with
  * the contents from the build. All other directories and files (such as pid and log files) will be
  * left intact.  After the new binaries are pushed, "${deploy.startup_script} restart" will be
  * issued to restart the server.
  *
  * There is a special ~/.deployrc config file that is merged into any deploy target configs as an
  * override.
  *
  * Overrides of the deploy target's settings can also be specified as arguments to the `deploy`
  * task via Java property overrides (-Dprop.path=propvalue). This is the easiest way to specify a
  * version (project.version) or any other variable info. These key/value pairs are imported into
  * the deploy target's scope.  For example, "project.version" will override the current target's
  * version; specifying "target.path.project.version" will not work.
  *
  * Argument overrides have precedence over .deployrc overrides.
  */
object DeployPlugin extends AutoPlugin {

  // JavaAppPackaging gives us the `stage` command, and NodeJsPlugin lets us deploy webapps.
  override def requires: Plugins = plugins.JvmPlugin && JavaAppPackaging && NodeJsPlugin

  /** Static usage string. */
  val Usage = "Usage: deploy [overrides] [deploy target]"

  object autoImport {

    val deploy = inputKey[Unit](Usage)

    val nodeEnv = settingKey[String]("The value to use for NODE_ENV during deploy builds.")

    val makeDefaultBashScript = settingKey[Boolean](
      "If true, will create executable bash script per JavaAppPackaging defaults. " +
        "Defaults to false"
    )

    val filterNotCacheKeyGenFileNames = settingKey[Seq[String]](
      "Starts of jars in stage you don't want hashed for cachekey"
    )

    val cleanStage = taskKey[Unit](
      "Cleans the staging directory. This is not done by default by the universal packager."
    )

    val stageAndCacheKey = taskKey[File]("stages and calculates cacheKey of this project")

    /** The reason this is a Setting instead of just including * is that including * in the rsync
      * command causes files created on the server side (like log files and .pid files) to be
      * deleted when the rsync runs, which we don't want to happen.
      */
    val deployDirs = settingKey[Seq[String]](
      "subdirectories from the stage task to copy during deploy. " +
        "defaults to bin/, lib/, and public/"
    )

    val gitRepoClean = taskKey[Unit]("Succeeds if the git repository is clean")

    val gitRepoPresent = taskKey[Unit]("Succeeds if a git repository is present in the cwd")
  }

  import autoImport._

  /** sbt.Logger wrapper that prepends [deploy] to log messages */
  case class DeployLogger(sbtLogger: Logger) {
    private def logMsg(msg: String) = s"[deploy] $msg"
    def info(msg: String): Unit = sbtLogger.info(logMsg(msg))
    def error(msg: String): Unit = sbtLogger.error(logMsg(msg))
  }

  /** Returns a filter for the local project dependencies. */
  lazy val dependencyFilter: Def.Initialize[Task[ScopeFilter]] = Def.task {
    val localDependencies = buildDependencies.value.classpathTransitiveRefs(thisProjectRef.value)
    ScopeFilter(inProjects(localDependencies: _*))
  }

  /** Returns all of the local dependencies' most recent git commits. */
  lazy val dependentGitCommits: Def.Initialize[Task[Seq[String]]] = Def.taskDyn {
    VersionInjectorPlugin.autoImport.gitLocalSha1.all(dependencyFilter.value)
  }

  /** Returns the filename used by the native packager in the staging directory. */
  lazy val stagingArtifactFilename: Def.Initialize[Task[String]] = Def.task {
    // Adapted from http://git.io/vGoFH .
    val organization = Keys.organization.value
    val moduleId = projectID.value
    val artifact = Keys.artifact.value
    val classifier = artifact.classifier.fold("")("-" + _)

    s"$organization.${artifact.name}-${moduleId.revision}$classifier.${artifact.extension}"
  }

  /** Returns all of the local dependencies' staging artifact filenames. */
  lazy val dependentStagingArtifactFilenames: Def.Initialize[Task[Seq[String]]] = Def.taskDyn {
    stagingArtifactFilename.all(dependencyFilter.value)
  }

  val gitRepoCleanTask = gitRepoClean := {
    // Dependencies
    gitRepoPresent.value

    val log = DeployLogger(streams.value.log)

    // Validate that the git repository is clean.
    if (Process(Seq("git", "diff", "--shortstat")).!! != "") {
      throw new IllegalStateException("Git repository is dirty, exiting.")
    }

    log.info("Git repository is clean.")

    // Validate that the git repository has no untracked files.
    if (Process(Seq("git", "clean", "-n")).!! != "") {
      throw new IllegalStateException("Git repository has untracked files, exiting.")
    }

    log.info("Git repository contains no untracked files.")
  }

  val gitRepoPresentTask = gitRepoPresent := {
    // Validate that we are, in fact, in a git repository.
    // TODO(schmmd): Use JGit instead (http://www.eclipse.org/jgit/)
    if (Process(Seq("git", "status")).!(ProcessLogger(line => ())) != 0) {
      throw new IllegalStateException("Not in git repository, exiting.")
    }

    DeployLogger(streams.value.log).info("Git repository present.")
  }

  val cleanStageTask = cleanStage := {
    IO.delete((UniversalPlugin.autoImport.stagingDirectory in Universal).value)
  }

  lazy val npmBuildTask = Def.taskDyn {
    if ((nodeEnv in thisProject).value == "dev") {
      Def.task {
        NodeJsPlugin.execBuild(
          (NodeKeys.nodeProjectDir in Npm).value,
          (NodeKeys.buildScripts in Npm).value,
          NodeJsPlugin.getEnvironment("dev", (NodeKeys.nodeProjectTarget in Npm).value),
          (NodeKeys.npmLogLevel in Npm).value
        )
      }
    } else {
      Def.task {
        NodeJsPlugin.execBuild(
          (NodeKeys.nodeProjectDir in Npm).value,
          (NodeKeys.buildScripts in Npm).value,
          NodeJsPlugin.getEnvironment("prod", (NodeKeys.nodeProjectTarget in Npm).value),
          (NodeKeys.npmLogLevel in Npm).value
        )
      }
    }
  }

  lazy val deployTask = deploy := {
    // Dependencies
    gitRepoClean.value
    val log = DeployLogger(streams.value.log)

    val args: Seq[String] = Def.spaceDelimited("<arg>").parsed
    // Process any definition-like args.
    val (commandlineOverrides, reducedArgs) = parseDefines(args)

    if (reducedArgs.length != 1) {
      throw new IllegalArgumentException(Usage)
    }

    val workingDirectory = (baseDirectory in thisProject).value
    val configFile = new File(workingDirectory.getPath + "/conf/deploy.conf")
    if (!configFile.isFile) {
      throw new IllegalArgumentException(s"${configFile.getPath}: Must be a config file")
    }

    val deployTarget = reducedArgs.head

    val targetConfig = loadTargetConfig(commandlineOverrides, configFile, deployTarget)

    val deployConfig = parseConfig(deployTarget, targetConfig)

    // TODO(jkinkead): Allow for a no-op / dry-run flag that only prints the
    // commands.

    // Check out the provided version, if it's set.
    deployConfig.projectVersion foreach { version =>
      log.info(s"Checking out $version . . .")
      if (Process(Seq("git", "checkout", "-q", version)).! != 0) {
        throw new IllegalArgumentException(s"Could not checkout $version.")
      }
    }

    // Copy over the per-env config file, if it exists.
    val deployEnv = if (deployTarget.lastIndexOf('.') >= 0) {
      deployTarget.substring(deployTarget.lastIndexOf('.') + 1)
    } else {
      deployTarget
    }

    log.info(s"Building ${(name in thisProject).value} . . .")

    val universalStagingDir = stageAndCacheKey.value

    val envConfFile = new File(universalStagingDir, s"conf/$deployEnv.conf")
    val baseEnvConf = if (envConfFile.exists) {
      log.info(s"Getting base config for $deployEnv . . .")
      ConfigFactory.parseFile(envConfFile)
    } else {
      log.info("")
      log.info(s"WARNING: Could not find config file '$deployEnv.conf'!")
      log.info("")
      log.info("Press ENTER to continue with no environment configuration, CTRL-C to abort.")
      log.info("")
      System.console.readLine()
      ConfigFactory.empty
    }

    val hosts = deployConfig.replicaOverridesByHost.keys
    val deploys: Iterable[Future[Try[Unit]]] = hosts flatMap { hostConfig =>
      val deployHost = hostConfig.host
      val baseDeployDir = deployConfig.baseDirectory

      // Command to pass to rsync's "rsh" flag, and to use as the base of our ssh operations.
      val sshCommand = {
        val keyFile = sys.env.getOrElse("AWS_PEM_FILE", {
          throw new IllegalStateException(s"Environment variable 'AWS_PEM_FILE' is undefined")
        })
        require(keyFile.nonEmpty, "Environment variable 'AWS_PEM_FILE' is empty")

        Seq("ssh", "-i", keyFile, "-l", hostConfig.sshUser)
      }

      val replicas = deployConfig.replicaOverridesByHost(hostConfig)
      val numReplicas = replicas.length

      // First, stop and remove any replicas on the remote that won't be restarted
      // as part of the deploy.
      val (deployParent, deployTarget) = baseDeployDir.splitAt(baseDeployDir.lastIndexOf('/'))
      // Strip leading / from deployTarget so we can match names on it.
      val namePattern = deployTarget.tail
      // Build a regex matching the replicas we will eventually update / restart as part of this
      // process, and therefore DON'T want to stop and remove.
      val restartedPattern = if (numReplicas == 1) {
        namePattern
      } else {
        s"$namePattern-[1-$numReplicas]"
      }
      // Use find -exec to locate, stop, and remove stale replicas that won't be updated as part
      // of the current deploy.
      val findCommand = Seq(
        "find",
        s"$deployParent/*",
        "-prune",
        "-type", "d",
        "-name", "\"" + s"$namePattern*" + "\"",
        "-not", "-name", "\"" + restartedPattern + "\"",
        "-exec", s"{}/bin/$namePattern.sh stop \\;",
        "-exec", "rm -r {} \\;"
      )
      val stopCommand = Seq.concat(sshCommand, Seq(deployHost), findCommand)
      // Shell-friendly version of find command, for logging.
      val quotedStopCommand = Seq.concat(
        sshCommand,
        Seq(deployHost, findCommand.mkString("'", " ", "'"))
      )

      log.info("Running " + quotedStopCommand.mkString(" ") + " . . .")
      if (Process(stopCommand).! != 0) {
        sys.error(s"Error while trying to tear down stale replicas on '$deployHost'.")
      }

      replicas.zipWithIndex map {
        case (replicaConfig, i) => {
          Future {
            // Determine the specific remote directory for this replica.
            val deployDirectory = if (numReplicas > 1) {
              s"$baseDeployDir-${i + 1}"
            } else {
              baseDeployDir
            }

            // Command to pass to rsync's "rsync-path" flag to ensure creation of the remote dir.
            // http://www.schwertly.com/2013/07/forcing-rsync-to-create-a-remote-path-using-rsync-path/
            val pathCommand = Seq(
              "mkdir",
              "-p",
              deployDirectory,
              "&&",
              "rsync"
            )

            val rsyncDirs = deployDirs.value map { name => s"--include=/$name" }
            val rsyncCommand = Seq.concat(
              Seq(
                "rsync",
                "-vcrtzP",
                s"--rsync-path=${pathCommand.mkString(" ")}",
                s"--rsh=${sshCommand.mkString(" ")}"
              ),
              rsyncDirs,
              Seq(
                "--exclude=/*",
                "--delete",
                universalStagingDir.getPath + "/",
                deployHost + ":" + deployDirectory
              )
            )
            // Shell-friendly version of rsync command, with rsh value quoted.
            val quotedRsync = rsyncCommand.patch(
              2,
              Seq(
                "--rsync-path=" + pathCommand.mkString("\"", " ", "\""),
                "--rsh=" + sshCommand.mkString("\"", " ", "\"")
              ),
              2
            ).mkString(" ")

            // Environment config with replica-specific overrides to copy to the remote host.
            val envConf = replicaConfig.withFallback(baseEnvConf)
            // Command to create the remote conf directory.
            val prepConfCommand = Seq.concat(
              sshCommand,
              Seq(deployHost, s"mkdir -p $deployDirectory/conf")
            )
            // Command to print the full env config, to be piped to SSH.
            val echoEnvCommand = Seq(
              "echo",
              envConf.root().render(ConfigRenderOptions.concise)
            )
            val writeEnvCommand = Seq.concat(
              sshCommand,
              Seq(deployHost, s"cat > $deployDirectory/conf/env.conf")
            )
            // Shell-friendly version of env copy, with config and remote commands quoted.
            val quotedEnvCopy = Seq.concat(
              prepConfCommand.updated(
                prepConfCommand.length - 1,
                s"'mkdir -p $deployDirectory/conf'"
              ),
              Seq("&&"),
              echoEnvCommand.updated(
                echoEnvCommand.length - 1,
                s"'${envConf.root().render(ConfigRenderOptions.concise)}'"
              ),
              Seq("|"),
              writeEnvCommand.updated(
                writeEnvCommand.length - 1,
                s"'cat > $deployDirectory/conf/env.conf'"
              )
            ).mkString(" ")

            // Command to run the restart script on the remote host.
            val restartScript = s"$deployDirectory/${deployConfig.startupScript}"
            val restartCommand = Seq.concat(
              sshCommand,
              Seq(deployHost, restartScript, "restart")
            )

            // Run commands within a Try so we can collect errors using Future.fold.
            // TODO(danm): Use ProcessIO to capture and report the actual stderr of failures here.
            Try {
              log.info("Running " + quotedRsync + " . . .")
              if (Process(rsyncCommand).! != 0) {
                sys.error(s"Error running rsync to host '$deployHost'.")
              }

              log.info("Running " + quotedEnvCopy + " . . .")
              val createConf = Process(prepConfCommand)
              val copyConf = Process(echoEnvCommand) #| Process(writeEnvCommand)
              if ((createConf #&& copyConf).! != 0) {
                sys.error(s"Error writing config to host '$deployHost'.")
              }

              log.info("Running " + restartCommand.mkString(" ") + " . . .")
              if (Process(restartCommand).! != 0) {
                sys.error(s"Error running restart command on host '$deployHost'.")
              }
            }
          }
        }
      }
    }

    // Accumulate all error messages thrown when trying to deploy.
    val accumulatedErrors = Future.fold[Try[Unit], Seq[Throwable]](deploys)(Seq.empty) {
      case (acc, Success(_)) => acc
      case (acc, Failure(e)) => acc :+ e
    }

    // Throw an error if any deploy commands failed.
    val deployStatus = accumulatedErrors map { errorSeq =>
      if (errorSeq.nonEmpty) {
        val errors = s"[ ${errorSeq.mkString(", ")} ]"
        sys.error(s"Hit the following errors during deployment:\n$errors\n")
      }
    }
    Await.result(deployStatus, Duration.Inf)

    log.info("")
    // TODO(jkinkead): Run an automated "/info/name" check here to see if service is running.
    log.info("Deploy complete. Validate your server!")
  }

  // we stage and generate the cache key in the same task so that stage only runs once
  val stageAndCacheKeyTask = stageAndCacheKey := {
    import VersionInjectorPlugin.autoImport.gitLocalSha1
    val stageDir = (UniversalPlugin.autoImport.stage in thisProject).value
    val logger = streams.value.log
    val allFiles = ((stageDir / "lib") * "*.jar").get
    val filteredFilenames = dependentStagingArtifactFilenames.value ++
      filterNotCacheKeyGenFileNames.value :+
      stagingArtifactFilename.value
    val filesToHash = allFiles filterNot { f: File =>
      val fileName = f.getName
      filteredFilenames.exists(fileName.startsWith)
    }
    val hashes = filesToHash.map(Hash.apply).map(Hash.toHex)
    // we sort so that we're not dependent on filesystem or git sorting remaining stable in order for the cacheKey
    // to not change
    val cacheKey = Hash.toHex(Hash((hashes ++ dependentGitCommits.value :+ gitLocalSha1.value).sorted.mkString))

    val cacheKeyConfFile = new java.io.File(s"${stageDir.getCanonicalPath}/conf/cacheKey.Sha1")

    logger.info(s"Generating cacheKey.conf managed resource... (cacheKey: $cacheKey)")

    IO.write(cacheKeyConfFile, cacheKey)
    // return the stageDirectory so that others can depend on stage having happened via us
    stageDir
  }

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    gitRepoCleanTask,
    gitRepoPresentTask,
    cleanStageTask,
    deployDirs := Seq("bin", "lib", "public"),
    nodeEnv := "prod",
    deployTask,
    stageAndCacheKeyTask,
    // Clean up anything leftover in the staging directory before re-staging.
    UniversalPlugin.autoImport.stage <<=
      UniversalPlugin.autoImport.stage.dependsOn(cleanStage),
    // Create the required run-class.sh script before staging.
    UniversalPlugin.autoImport.stage <<=
      UniversalPlugin.autoImport.stage.dependsOn(CoreSettingsPlugin.autoImport.generateRunClass),

    // Add root run script.
    mappings in Universal += {
      (resourceManaged in Compile).value / "run-class.sh" -> "bin/run-class.sh"
    },

    // JavaAppPackaging creates non-daemon start scripts by default. Since we
    // provide our own run-class.sh script meant for running a daemon process,
    // we disable the creation of these scripts by default.
    // You can opt-in by setting `makeDefaultBashScript := true` in your
    // build.sbt
    makeDefaultBashScript := false,
    filterNotCacheKeyGenFileNames := Seq(),
    JavaAppPackaging.autoImport.makeBashScript := {
      if (makeDefaultBashScript.value) JavaAppPackaging.autoImport.makeBashScript.value else None
    },
    JavaAppPackaging.autoImport.makeBatScript := None,

    // Map src/main/resources => conf and src/main/bin => bin.
    // See http://www.scala-sbt.org/0.12.3/docs/Detailed-Topics/Mapping-Files.html
    // for more info on sbt mappings.
    mappings in Universal ++=
      (sourceDirectory.value / "main" / "resources" ** "*" pair
        rebase(sourceDirectory.value / "main" / "resources", "conf/")) ++
        (sourceDirectory.value / "main" / "bin" ** "*" pair
          relativeTo(sourceDirectory.value / "main"))
  )

  /** Parses all Java properties-style defines from the argument list, and
    * returns the Config generated from these properties, as well as the updated
    * argument list.
    */
  def parseDefines(args: Seq[String]): (Config, Seq[String]) = {
    val DefinePattern = "-D([^=]*)=(.*)".r

    val matchedArgs: Seq[Either[(String, String), String]] = for {
      arg <- args
    } yield arg match {
      case DefinePattern(key, value) => Left(key -> value)
      case _ => Right(arg)
    }

    val config = matchedArgs.foldLeft(ConfigFactory.empty("<commandline overrides>")) {
      (cfg, arg) =>
        arg match {
          case Left(configPair) => {
            val (key, value) = configPair
            cfg.withValue(key, ConfigValueFactory.fromAnyRef(value))
          }
          case Right(_) => cfg
        }
    }
    val prunedArgs: Seq[String] = for {
      arg <- matchedArgs
      value <- arg.right.toSeq
    } yield value

    (config, prunedArgs)
  }

  /** Loads the given config file, fetches the config at the given key, then
    * merges it with ~/.deployrc (if it exists), and the provided overrides.
    *
    * Exits the program if the file can't be parsed or the given key doesn't
    * exist.
    * @param overrides a config to use to override the values loaded from the file
    * @param configFile the config file to load
    * @param deployKey the key into the config file to return
    */
  def loadTargetConfig(overrides: Config, configFile: File, deployKey: String): Config = {
    // Load the config file.
    val deployConfig = try {
      ConfigFactory.parseFile(configFile).resolve
    } catch {
      case configError: ConfigException =>
        throw new IllegalArgumentException("Error loading config file:" + configError.getMessage)
    }

    // Validate that the user provided a target that exists.
    if (!deployConfig.hasPath(deployKey)) {
      println(s"Error: No configuration found for target '$deployKey'.")
      println("Possible root targets are:")
      val keys = for {
        key <- deployConfig.root.keySet.asScala
      } yield key
      println(s"    ${keys.mkString(" ")}")
      throw new IllegalArgumentException(s"Error: No configuration found for target '$deployKey'.")
    }
    val targetConfig = deployConfig.getConfig(deployKey)

    // Load .deployrc file, if it exists.
    val rcFile = new File(System.getenv("HOME"), ".deployrc")
    val rcConfig = if (rcFile.isFile) {
      ConfigFactory.parseFile(rcFile)
    } else {
      ConfigFactory.empty
    }

    // Final config order - overrides > rcFile > target.
    overrides.withFallback(rcConfig.withFallback(targetConfig)).resolve
  }

  /** Wrapper for config information required to deploy a project to many replicas.
    * @param baseDirectory the directory on remote hosts in which all project code should be placed
    * @param startupScript the path, relative to the deployed project's root, to the script that
    *                      should be used to restart the project after deploying the new code
    * @param projectVersion the version of the project being deployed
    * @param replicaOverridesByHost map from host information to a list of config overrides
    *                               representing the replicas
    */
  case class DeployConfig(
    baseDirectory: String,
    startupScript: String,
    projectVersion: Option[String],
    replicaOverridesByHost: Map[HostConfig, Seq[Config]]
  )

  /** Wrapper for config needed to access a remote host.
    * @param host the fully-qualified name of the host the project should be deployed to
    * @param sshUser the username to use when accessing the host
    */
  case class HostConfig(
    host: String,
    sshUser: String
  )

  /** Transform the given [[com.typesafe.config.Config]] object into a corresponding
    * [[org.allenai.plugins.DeployPlugin.DeployConfig]] object.
    * @param targetName the name of the target to print on error
    * @param targetConfig env-level deployment config to convert
    */
  def parseConfig(targetName: String, targetConfig: Config): DeployConfig = {

    val deployConfig = targetConfig.getConfig("deploy")

    // Check for both replica list and top-level host config.
    val replicasGiven = deployConfig.hasPath("replicas")
    val topHostGiven = deployConfig.hasPath("host") && deployConfig.hasPath("user.ssh_username")

    // Validate key layout.
    require(
      replicasGiven || topHostGiven,
      "Deploy requires either key 'replicas' or keys 'host' and 'user.ssh_username' to be set"
    )
    require(
      !(replicasGiven && topHostGiven),
      "Deploy supports only one of 'replicas' or 'host'&'user.ssh_username' to be set"
    )
    require(
      topHostGiven || !(deployConfig.hasPath("host") || deployConfig.hasPath("user.ssh_username")),
      "Specification of a top-level host requires both 'host' and 'user.ssh_username' keys"
    )

    // Parse common config.
    val baseDirectory = getStringValue(deployConfig, "directory")
    val startupScript = getStringValue(deployConfig, "startup_script")

    val projectVersion = try {
      Some(targetConfig.getString("project.version"))
    } catch {
      case _: ConfigException.Missing => None
      case _: ConfigException.WrongType =>
        throw new IllegalArgumentException(s"Error: 'project.version' must be a string")
    }

    val groupedOverrides = if (replicasGiven) {
      val globalOverrides = getConfigOverrides(deployConfig)
      val replicaList = try {
        deployConfig.getConfigList("replicas").asScala
      } catch {
        case _: ConfigException.WrongType =>
          throw new IllegalArgumentException("Error: 'replicas' must be a config list")
      }
      val parsedReplicas = replicaList map parseReplicaConfig

      parsedReplicas.foldLeft(Map[HostConfig, Seq[Config]]()) {
        case (hostMap, (hostConf, configOverrides)) =>
          val overrideAcc = hostMap.getOrElse(hostConf, Seq())
          hostMap + (hostConf -> (overrideAcc :+ configOverrides.withFallback(globalOverrides)))
      }

    } else {
      Map(parseReplicaConfig(deployConfig)) mapValues { Seq(_) }
    }

    DeployConfig(
      baseDirectory = baseDirectory,
      startupScript = startupScript,
      projectVersion = projectVersion,
      replicaOverridesByHost = groupedOverrides
    )
  }

  /** Transform the given [[com.typesafe.config.Config]] object into a corresponding
    * tuple of ([[HostConfig]], [[Config]]).
    * @param replicaConfig host-level config to convert
    */
  def parseReplicaConfig(replicaConfig: Config): (HostConfig, Config) = {

    val host = getStringValue(replicaConfig, "host")
    val sshUser = getStringValue(replicaConfig, "user.ssh_username")
    val configOverrides = getConfigOverrides(replicaConfig)

    HostConfig(host = host, sshUser = sshUser) -> configOverrides
  }

  /** Helper method to get a required string value from config. */
  def getStringValue(config: Config, key: String): String = {
    try {
      config.getString(key)
    } catch {
      case _: ConfigException.Missing =>
        throw new IllegalArgumentException(
          s"Error: ${config.root().render()} missing key '$key'."
        )
      case _: ConfigException.WrongType =>
        throw new IllegalArgumentException(
          s"Error: '$key' must be a string in ${config.root().render()}"
        )
    }
  }

  /** Helper method to get replica config overrides from config. */
  def getConfigOverrides(config: Config): Config = {
    try {
      config.getConfig("config_overrides")
    } catch {
      case _: ConfigException.Missing => ConfigFactory.empty
      case _: ConfigException.WrongType =>
        throw new IllegalArgumentException(
          s"Error: 'config_overrides' must be an object in ${config.root().render()}"
        )
    }
  }
}
