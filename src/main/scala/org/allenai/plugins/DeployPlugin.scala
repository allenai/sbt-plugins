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
        "defaults to bin/, conf/, lib/, and public/"
    )

    val gitRepoClean = taskKey[Unit]("Succeeds if the git repository is clean")

    val gitRepoPresent = taskKey[Unit]("Succeeds if a git repository is present in the cwd")
  }

  import autoImport._

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    gitRepoCleanTask,
    gitRepoPresentTask,
    cleanStageTask,
    deployDirs := Seq("bin", "conf", "lib", "public"),
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

  // We stage and generate the cache key in the same task so that stage only runs once.
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
    // We sort so that we're not dependent on filesystem or git sorting remaining stable in order
    // for the cacheKey to not change.
    val cacheKey = Hash.toHex(Hash((hashes ++ dependentGitCommits.value :+ gitLocalSha1.value).sorted.mkString))

    val cacheKeyConfFile = new java.io.File(s"${stageDir.getCanonicalPath}/conf/cacheKey.Sha1")

    logger.info(s"Generating cacheKey.conf managed resource... (cacheKey: $cacheKey)")

    IO.write(cacheKeyConfFile, cacheKey)
    // return the stageDirectory so that others can depend on stage having happened via us
    stageDir
  }

  lazy val deployTask = deploy := {
    // Task dependencies.
    gitRepoClean.value
    val log = DeployLogger(streams.value.log)
    val universalStagingDir = stageAndCacheKey.value
    val workingDirectory = (baseDirectory in thisProject).value
    val deployedDirs = deployDirs.value

    log.info(s"Building ${(name in thisProject).value} . . .")

    // Process any definition-like args.
    val args: Seq[String] = Def.spaceDelimited("<arg>").parsed
    val (commandlineOverrides, reducedArgs) = parseDefines(args)
    if (reducedArgs.length != 1) {
      throw new IllegalArgumentException(Usage)
    }
    val deployTarget = reducedArgs.head

    // Get configuration for the deploy process itself.
    val configFile = new File(workingDirectory.getPath + "/conf/deploy.conf")
    if (!configFile.isFile) {
      throw new IllegalArgumentException(s"${configFile.getPath}: Must be a config file")
    }
    val deployConfig = loadDeployConfig(commandlineOverrides, configFile, deployTarget)

    // TODO(jkinkead): Allow for a no-op / dry-run flag that only prints the commands.
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

    // Command to pass to rsync's "rsh" flag, and to use as the base of our ssh operations.
    val sshCommand = {
      val keyFile = sys.env.getOrElse("AWS_PEM_FILE", {
        throw new IllegalStateException(s"Environment variable 'AWS_PEM_FILE' is undefined")
      })
      require(keyFile.nonEmpty, "Environment variable 'AWS_PEM_FILE' is empty")

      Seq("ssh", "-i", keyFile, "-l", deployConfig.sshUser)
    }

    // Loop over all unique remote hosts listed in config, deploying to them in parallel.
    val hosts = deployConfig.replicaOverridesByHost.keys
    val deploys: Iterable[Future[Try[Unit]]] = hosts flatMap { hostName =>
      val baseDeployDir = deployConfig.baseDirectory

      val replicas = deployConfig.replicaOverridesByHost(hostName)
      val numReplicas = replicas.length

      // First, ensure parent directory structure of deploy exists.
      mkdirOnRemote(
        hostName,
        baseDeployDir.take(baseDeployDir.lastIndexOf('/')),
        sshCommand,
        log
      )

      // Then stop and remove any replicas that won't be restarted as part of the deploy.
      stopStaleReplicas(hostName, baseDeployDir, numReplicas, sshCommand, log)

      // Loop over all replicas being set up on the current remote, and deploy to them in parallel.
      replicas.zipWithIndex map {
        case (replicaConfig, i) => {
          // Determine the specific remote directory for this replica.
          val deployDirectory = if (numReplicas > 1) {
            s"$baseDeployDir-${i + 1}"
          } else {
            baseDeployDir
          }
          // Environment config with replica-specific overrides to copy to the remote host.
          val envConf = replicaConfig.withFallback(baseEnvConf)

          Future {
            // Ensure the target deploy directory exists on the remote (rsync won't do it for you).
            mkdirOnRemote(hostName, deployDirectory, sshCommand, log)
          } map { _ =>
            // Sync the project to the remote.
            rsyncToRemote(
              hostName,
              sshCommand,
              universalStagingDir.getPath + "/",
              deployDirectory,
              deployedDirs,
              log
            )
          } map { _ =>
            // Copy replica-specific config into the synchronized directory.
            copyEnvConfToRemote(hostName, envConf, s"$deployDirectory/conf", sshCommand, log)
          } map { _ =>
            // Restart the replica.
            restartReplica(
              hostName,
              s"$deployDirectory/${deployConfig.startupScript}",
              sshCommand,
              log
            )
          } map { _ =>
            // Wrap the entire chain in a Try so we can collect any errors that may have occurred.
            Success()
          } recover {
            case e => Failure(e)
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

  /** Wrapper for config information required to deploy a project to many replicas.
    * @param sshUser the username to use when accessing hosts
    * @param baseDirectory the directory on remote hosts in which all project code should be placed
    * @param startupScript the path, relative to the deployed project's root, to the script that
    *                      should be used to restart the project after deploying the new code
    * @param projectVersion the version of the project being deployed
    * @param replicaOverridesByHost map from host domain-name to a list of config overrides
    *                               representing the replicas that should be deployed on that host
    */
  case class DeployConfig(
    sshUser: String,
    baseDirectory: String,
    startupScript: String,
    projectVersion: Option[String],
    replicaOverridesByHost: Map[String, Seq[Config]]
  )

  /** Loads the given config file, fetches the config at the given key, then
    * merges it with ~/.deployrc (if it exists), and the provided overrides.
    * After merging, parses the result into a [[DeployConfig]] object for subsequent use.
    * Exits the program if the file can't be parsed or the given key doesn't
    * exist.
    * @param overrides a config to use to override the values loaded from the file
    * @param configFile the config file to load
    * @param deployKey the key into the config file to return
    */
  def loadDeployConfig(overrides: Config, configFile: File, deployKey: String): DeployConfig = {
    // Load the config file.
    val loadedConfig = try {
      ConfigFactory.parseFile(configFile).resolve
    } catch {
      case configError: ConfigException =>
        throw new IllegalArgumentException("Error loading config file:" + configError.getMessage)
    }

    // Validate that the user provided a target that exists.
    if (!loadedConfig.hasPath(deployKey)) {
      println(s"Error: No configuration found for target '$deployKey'.")
      println("Possible root targets are:")
      val keys = for {
        key <- loadedConfig.root.keySet.asScala
      } yield key
      println(s"    ${keys.mkString(" ")}")
      throw new IllegalArgumentException(s"Error: No configuration found for target '$deployKey'.")
    }
    val targetConfig = loadedConfig.getConfig(deployKey)

    // Load .deployrc file, if it exists.
    val rcFile = new File(System.getenv("HOME"), ".deployrc")
    val rcConfig = if (rcFile.isFile) {
      ConfigFactory.parseFile(rcFile)
    } else {
      ConfigFactory.empty
    }

    // Final config order - overrides > rcFile > target.
    val finalConfig = overrides.withFallback(rcConfig.withFallback(targetConfig)).resolve
    val deployConfig = finalConfig.getConfig("deploy")

    // Check for both replica list and top-level host config.
    val replicasGiven = deployConfig.hasPath("replicas")
    val topHostGiven = deployConfig.hasPath("host")

    // Validate key layout.
    require(
      replicasGiven || topHostGiven,
      "Deploy requires either 'deploy.replicas' or 'deploy.host' to be set"
    )
    require(
      !(replicasGiven && topHostGiven),
      "Deploy supports setting only one of 'deploy.replicas' or 'deploy.host'"
    )

    // Parse common config.
    val sshUser = getStringValue(deployConfig, "user.ssh_username")
    val baseDirectory = getStringValue(deployConfig, "directory")
    val startupScript = getStringValue(deployConfig, "startup_script")

    val projectVersion = try {
      Some(finalConfig.getString("project.version"))
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
          throw new IllegalArgumentException("Error: 'deploy.replicas' must be a config list")
      }
      val parsedReplicas = replicaList map parseReplicaConfig
      parsedReplicas.foldLeft(Map[String, Seq[Config]]()) {
        case (hostMap, (hostConf, configOverrides)) =>
          val overrideAcc = hostMap.getOrElse(hostConf, Seq())
          hostMap + (hostConf -> (overrideAcc :+ configOverrides.withFallback(globalOverrides)))
      }
    } else {
      Map(parseReplicaConfig(deployConfig)) mapValues { Seq(_) }
    }

    DeployConfig(
      sshUser = sshUser,
      baseDirectory = baseDirectory,
      startupScript = startupScript,
      projectVersion = projectVersion,
      replicaOverridesByHost = groupedOverrides
    )
  }

  /** Transform the given [[com.typesafe.config.Config]] object into a corresponding
    * tuple of ([[String]], [[Config]]) representing a host -> replica pair.
    * @param replicaConfig config to parse
    */
  def parseReplicaConfig(replicaConfig: Config): (String, Config) = {
    val host = getStringValue(replicaConfig, "host")
    val configOverrides = getConfigOverrides(replicaConfig)
    host -> configOverrides
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

  /** Use `find -exec` to locate, stop, and remove stale replicas that won't be updated as part
    * of a running deploy. Replicas are considered 'stale' if they are running in a directory
    * that will not be updated by subsequent `rsync` commands.
    * Directories are chosen to be `rsync`-ed according to the following scheme:
    *   - If only one replica is being deployed to a host, then the `rsync`-ed directory will be
    *     `baseDeployDirectory`.
    *   - If `n > 1` replicas are being deployed to a host, then the `rsync`-ed directories will
    *     be `baseDeployDirectory-i` for `i` in `1` to `n`.
    * @param host the remote host to clean up
    * @param baseDeployDirectory the base deploy directory for this project on the remote host
    * @param numReplicas the number of replicas that will later be deployed to the remote host
    *                    using `rsync`
    * @param sshCommand a constructed SSH command that should be used to communicate with the
    *                   remote host
    */
  def stopStaleReplicas(
    host: String,
    baseDeployDirectory: String,
    numReplicas: Int,
    sshCommand: Seq[String],
    log: DeployLogger
  ): Unit = {
    // The base deploy directory for this project includes the project name; extract it.
    val (deployParent, deployTarget) =
      baseDeployDirectory.splitAt(baseDeployDirectory.lastIndexOf('/'))

    // Strip leading '/' from project name so we can match names on it.
    val projectName = deployTarget.tail

    // Build a pattern matching the replica directories that we want to maintain for subsequent
    // updates. This pattern will be passed to `find` as a negated name match.
    val persistedPattern = if (numReplicas == 1) {
      // If only one replica is going to be deployed, we want to keep the deploy directory
      // without a numeric suffix.
      projectName
    } else {
      // If multiple replicas are going to be deployed, we want to keep one deploy directory for
      // each of them.
      s"$projectName-[1-$numReplicas]"
    }

    // Build the `find` command that will be executed on the remote host to stop & clean stale
    // replicas.
    val findCommand = Seq(
      "find",
      // Prevent `find` from reporting subdirectories of the deployed replicas.
      deployParent, "-maxdepth", "1",
      // Set `find` to only report directory names.
      "-type", "d",
      // Set `find` to only report directories named after this project (to avoid cleaning other
      // deployed projects).
      "-name", "\"" + projectName + "*\"",
      // Set `find` to ignore the directories we've decided to persist.
      "-not", "-name", "\"" + persistedPattern + "\"",
      // Within each replica directory meeting the above criteria, stop the service.
      "-exec", s"{}/bin/$projectName.sh stop \\;",
      // After stopping the replica service, delete it from the deploy directory.
      "-exec", "rm -r {} \\;"
    )

    // Build the SSH command that will be sent to the remote host to execute the above `find`.
    val teardownCommand = (sshCommand :+ host) ++ findCommand
    // Also build a shell-friendly version of the command to log out, for copy-paste.
    val quotedTeardownCommand = quoteSshCmd(sshCommand, host, findCommand)

    // Log and run the command on the remote host, and throw an error if it fails.
    log.info(s"Running $quotedTeardownCommand . . .")
    if (Process(teardownCommand).! != 0) {
      sys.error(s"Error while trying to tear down stale replicas on '$host'.")
    }
  }

  /** Run `mkdir` through SSH to create a remote directory.
    * @param host the remote host to make a directory on
    * @param dir the absolute path of the directory to create on the remote host
    * @param sshCommand a constructed SSH command that should be used to communicate with the
    *                   remote host
    */
  def mkdirOnRemote(host: String, dir: String, sshCommand: Seq[String], log: DeployLogger): Unit = {
    // Build the `mkdir` command that will be executed on the remote host.
    val mkdirCommand = Seq("mkdir", "-p", dir)

    // Build the SSH command that will be sent to the remote host to execute the above `mkdir`.
    val remoteCommand = (sshCommand :+ host) ++ mkdirCommand
    // Also build a shell-friendly version of the command to log out, for copy-paste.
    val quotedRemoteCommand = quoteSshCmd(sshCommand, host, mkdirCommand)

    // Log and run the command on the remote host, and throw an error if it fails.
    log.info(s"Running $quotedRemoteCommand . . .")
    if (Process(remoteCommand).! != 0) {
      sys.error(s"Error while trying to create directory '$dir' on '$host'")
    }
  }

  /** Run `rsync` to copy the staged contents of this project to a remote host.
    * @param host the remote host to `rsync` to
    * @param sshCommand the SSH command to pass as the `rsh` option when running `rsync`
    * @param srcDir the path to the local directory containing the staged contents of this project
    * @param targetDir the path to the directory on the remote host to sync with
    * @param deployedDirs the names of subdirectories of this project to copy to the remote host
    */
  def rsyncToRemote(
    host: String,
    sshCommand: Seq[String],
    srcDir: String,
    targetDir: String,
    deployedDirs: Seq[String],
    log: DeployLogger
  ): Unit = {
    // Build the `rsync` command.
    val rsyncIncludes = deployedDirs map { name => s"--include=/$name" }
    val rsyncCommand = Seq.concat(
      Seq(
        "rsync",
        "-vcrtzP",
        s"--rsh=${sshCommand.mkString(" ")}"
      ),
      rsyncIncludes,
      Seq(
        "--exclude=/*",
        "--delete",
        srcDir,
        s"$host:$targetDir"
      )
    )
    // Also build a shell-friendly version of the command to log out, for copy-paste.
    val quotedRsync = rsyncCommand.updated(
      2,
      s"--rsh=${sshCommand.mkString("'", " ", "'")}"
    ).mkString(" ")

    // Log and run the command, and throw an error if it fails.
    log.info(s"Running $quotedRsync . . .")
    if (Process(rsyncCommand).! != 0) {
      sys.error(s"Error while running rsync to host '$host'")
    }
  }

  /** Write a Typesafe config object to file on a remote host without creating a local temp file.
    * This is done by `echo`-ing the contents of the config locally, and piping the output into
    * an SSH command running `cat`.
    * We don't create a local temp file to avoid munging config overrides as many replicas deploy
    * in parallel.
    * The written file will be named `env.conf`.
    * @param host the remote host to write the conf go
    * @param envConf the Config object to write
    * @param confPath the absolute path to the remote directory in which the written file should
    *                 be placed
    * @param sshCommand a constructed SSH command that should be used to communicate with the
    *                   remote host
    */
  def copyEnvConfToRemote(
    host: String,
    envConf: Config,
    confPath: String,
    sshCommand: Seq[String],
    log: DeployLogger
  ): Unit = {
    // Get the config contents to write.
    val renderedConf = envConf.root().render(ConfigRenderOptions.concise)

    // Command to print the full env config, to be piped to SSH.
    val echoConfCommand = Seq("echo", renderedConf)
    // Shell-friendly version of above, for copy-paste.
    val quotedEcho = Seq("echo", s"'$renderedConf'").mkString(" ")

    // SSH command to receive `echo` results and redirect them to disk.
    val writeConfCommand = sshCommand :+ host :+ s"cat > $confPath/env.conf"
    // Shell-friendly version of above, for copy-paste.
    val quotedWrite = quoteSshCmd(sshCommand, host, Seq(s"cat > $confPath/env.conf"))

    // Log and run the commands, and throw an error if the combination fails.
    log.info(s"Running ${Seq(quotedEcho, quotedWrite).mkString(" | ")} . . .")
    if ((Process(echoConfCommand) #| Process(writeConfCommand)).! != 0) {
      sys.error(s"Error writing env config to host '$host'")
    }
  }

  /** Restart a replica on a remote host through SSH.
    * @param host the host the replica is running on
    * @param restartScriptPath the absolute path to the restart script of the replica
    * @param sshCommand a constructed SSH command that should be used to communicate with the
    *                   remote host
    */
  def restartReplica(
    host: String,
    restartScriptPath: String,
    sshCommand: Seq[String],
    log: DeployLogger
  ): Unit = {
    // Build the SSH command that will run the restart script in the remote replica.
    val restartCommand = (sshCommand :+ host) ++ Seq(restartScriptPath, "restart")
    // Also build a shell-friendly version of the command to log out for copy-paste.
    val quotedRestart = quoteSshCmd(sshCommand, host, Seq(restartScriptPath, "restart"))

    // Log and run the command, and throw an error if it fails.
    log.info(s"Running $quotedRestart . . .")
    if (Process(restartCommand).! != 0) {
      sys.error(s"Error running restart command on host '$host'")
    }
  }

  /** Helper method for producing a shell-friendly SSH command, for copy-paste. */
  def quoteSshCmd(sshCommand: Seq[String], host: String, remoteCommand: Seq[String]): String =
    Seq.concat(sshCommand, Seq(host, remoteCommand.mkString("'", " ", "'"))).mkString(" ")

}
