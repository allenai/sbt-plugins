package org.allenai.plugins

import org.allenai.plugins.NodeJsPlugin.autoImport.{ NodeKeys, Npm }
import com.typesafe.config._
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.universal.UniversalPlugin
import sbt._
import sbt.Keys._
import sbt.complete.Parser
import sbt.complete.DefaultParsers._

import java.io.File
import java.net.URLEncoder
import scala.collection.JavaConverters._
import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
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
  * task via Java property overrides (-Dprop.path=propvalue). This is the easiest way to specify
  * variable info. These key/value pairs are imported into the deploy target's scope.
  *
  * Argument overrides have precedence over .deployrc overrides.
  */
object DeployPlugin extends AutoPlugin {

  // JavaAppPackaging gives us the `stage` command, and NodeJsPlugin lets us deploy webapps.
  override def requires: Plugins = plugins.JvmPlugin && JavaAppPackaging && NodeJsPlugin

  object autoImport {

    val cleanEnvConfig = taskKey[Unit]("Clean generated environment configuration.")

    val deploy = inputKey[Unit](
      """Deploy this project to a remote host specified in conf/deploy.conf.
        |  Usage: deploy [-Ddeploy.config.key=override ...] deploy-target
      """.stripMargin
    )

    // The reason this is a Setting instead of just including * is that including * in the rsync
    // command causes files created on the server side (like log files and .pid files) to be
    // deleted when the rsync runs, which we don't want to happen.
    val deployDirs = settingKey[Seq[String]](
      "Subdirectories from the stage task to copy during deploy. " +
        "Defaults to bin/, conf/, lib/, and public/."
    )

    val envConfigSource = settingKey[File](
      "The source directory containing environment config files for this project."
    )

    val envConfigTarget = settingKey[File](
      "The directory to which fully-resolved env config should be written prior to deployment."
    )

    val filterNotCacheKeyGenFileNames = settingKey[Seq[String]](
      "Starts of jars in stage you don't want hashed for cachekey."
    )

    val gitRepoClean = taskKey[Unit]("Assert that this project's git repository is clean.")

    val gitRepoPresent = taskKey[Unit]("Assert that there is a git repository in the cwd")

    val generateEnvConfig = inputKey[Unit](
      """Generate the environment configuration for this project that will be used by deployed instances.
        |  Usage: generateEnvConfig deploy-target
      """.stripMargin
    )

    val makeDefaultBashScript = settingKey[Boolean](
      "If true, will create executable bash script per JavaAppPackaging defaults. " +
        "Defaults to false."
    )

    // TODO(danm): Create a Deploy scope to reduce confusion around duplication of this setting
    // between this plugin and the NodeJsPlugin.
    val nodeEnv = settingKey[String]("The value to use for NODE_ENV during deploy builds.")

    val stageAndCacheKey = taskKey[File]("Stage the current project, and calculate its cache key.")

    // Clients may override to perform a different action in addition to staging (i.e. running
    // database migrations).
    val preDeploy = taskKey[Unit]("Prep the project for a deploy. Defaults to `stageAndCacheKey`.")

    val deployNpmBuild = taskKey[Unit]("Runs the npm build")
  }

  import autoImport._

  /** Default settings and task dependencies for the Keys defined by this plugin. */
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    cleanEnvConfigTask,
    deployDirs := Seq("bin", "conf", "lib", "public"),
    deployTask,
    envConfigSource := (sourceDirectory in thisProject).value / "main" / "resources",
    envConfigTarget := (target in thisProject).value / "deploy",
    filterNotCacheKeyGenFileNames := Seq(),
    gitRepoCleanTask,
    gitRepoPresentTask,
    generateEnvConfigTask,
    loadDeployConfigTask,
    nodeEnv := "prod",
    deployNpmBuild := npmBuildTask.value,
    stageAndCacheKeyTask,
    // By default, this only depends on staging and generating the cache key.
    preDeploy := { stageAndCacheKey.value },
    // Create the required run-class.sh script before staging.
    UniversalPlugin.autoImport.stage := UniversalPlugin.autoImport.stage.dependsOn(
      CoreSettingsPlugin.autoImport.generateRunClass
    ).value,

    // JavaAppPackaging creates non-daemon start scripts by default. Since we
    // provide our own run-class.sh script meant for running a daemon process,
    // we disable the creation of these scripts by default.
    // You can opt-in by setting `makeDefaultBashScript := true` in your
    // build.sbt
    makeDefaultBashScript := false,
    JavaAppPackaging.autoImport.makeBashScript := {
      if (makeDefaultBashScript.value) JavaAppPackaging.autoImport.makeBashScript.value else None
    },
    JavaAppPackaging.autoImport.makeBatScript := None,

    // Add root run script.
    mappings in Universal += {
      (resourceManaged in Compile).value / "run-class.sh" -> "bin/run-class.sh"
    },
    // Map src/main/resources => conf and src/main/bin => bin.
    // See http://www.scala-sbt.org/0.12.3/docs/Detailed-Topics/Mapping-Files.html
    // for more info on sbt mappings.
    mappings in Universal ++=
      (sourceDirectory.value / "main" / "resources" ** "*" pair
        rebase(sourceDirectory.value / "main" / "resources", "conf/")) ++
        (sourceDirectory.value / "main" / "bin" ** "*" pair
          relativeTo(sourceDirectory.value / "main"))
  )

  /* ==========>  Miscellaneous.  <========== */

  /** sbt.Logger wrapper that prepends [deploy] to log messages */
  case class DeployLogger(sbtLogger: Logger) {
    private def logMsg(msg: String) = s"[deploy] $msg"
    def info(msg: String): Unit = sbtLogger.info(logMsg(msg))
    def error(msg: String): Unit = sbtLogger.error(logMsg(msg))
    def warn(msg: String): Unit = sbtLogger.warn(logMsg(msg))
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

  // TODO(danm): Update the NodeJsPlugin's task definition to actually support "dev" vs "prod".
  /** Wrapper around the NodeJSPlugin's npmBuildTask.
    * We wrap this here because the other plugin's definition doesn't support distinguishing
    * between "dev" and "prod" environments.
    * Used by the WebappArchetype plugin.
    */
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

  /* ==========>  Cache key generation.  <========== */

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
    Utilities.jarName(projectID.value, artifact.value)
  }

  /** Returns all of the local dependencies' staging artifact filenames. */
  lazy val dependentStagingArtifactFilenames: Def.Initialize[Task[Seq[String]]] = Def.taskDyn {
    stagingArtifactFilename.all(dependencyFilter.value)
  }

  /** Task used to generate the cache key for the current project.
    * We stage and generate the cache key in the same task so that stage only runs once.
    */
  val stageAndCacheKeyTask = stageAndCacheKey := {
    import VersionInjectorPlugin.autoImport.gitLocalSha1
    val logger = streams.value.log

    logger.info(s"Building ${(name in thisProject).value} . . .")
    val stageDir = (UniversalPlugin.autoImport.stage in thisProject).value

    val allFiles = ((stageDir / "lib") * "*.jar").get
    val filteredFilenames = Seq.concat(
      dependentStagingArtifactFilenames.value,
      filterNotCacheKeyGenFileNames.value :+ stagingArtifactFilename.value
    )
    val filesToHash = allFiles filterNot { f: File =>
      val fileName = f.getName
      filteredFilenames.exists(fileName.startsWith)
    }
    val fileHash = Utilities.hashFiles(filesToHash, stageDir)

    // We sort so that we're not dependent on filesystem or git sorting remaining stable in order
    // for the cacheKey to not change.
    val cacheKey = Hash.toHex(
      Hash((dependentGitCommits.value :+ fileHash :+ gitLocalSha1.value).sorted.mkString)
    )
    val cacheKeyConfFile = new java.io.File(s"${stageDir.getCanonicalPath}/conf/cacheKey.Sha1")

    logger.info(s"Generating cacheKey.conf managed resource... (cacheKey: $cacheKey)")
    IO.write(cacheKeyConfFile, cacheKey)

    // Return the stageDirectory so that others can depend on stage having happened via us.
    stageDir
  }

  /* ==========>  Deploy config parsing.  <========== */

  /** Wrapper for config common to all hosts in a deploy.
    * @param projectName the name of the current project
    * @param rootDeployDir the root directory to which all replicas should be deployed on every
    *     remote host the project is copied to
    * @param sshUser the username to use when accessing remote hosts
    * @param startupScript the path, relative to a deployed instance of this project's root, to
    *     the script that should be used to restart the project after deploying updates
    * @param replicasByHost a map from host URLs to collections of info for every replica that
    *     should be deployed on the respective hosts
    */
  case class DeployConfig(
    projectName: String,
    rootDeployDir: String,
    sshUser: String,
    startupScript: String,
    replicasByHost: Map[String, Seq[ReplicaConfig]]
  )

  /** Config information required to deploy one replica of a project to a remote host.
    * @param directory the directory on the host, relative to the root deploy directory, in which
    *     all project code for this replica should be placed
    * @param envOverrides configuration overrides specific to this replica that should be merged
    *     into the application configuration of the deploy environment
    */
  case class ReplicaConfig(
    directory: String,
    envOverrides: Config
  )

  /** Parser used for reading deploy targets. */
  lazy val deployTargetParser: Parser[String] = {
    token(StringBasic, "<env>")
  }

  /** Parser used for reading deploy-config overrides into a [[Config]] object.
    * Overrides are passed using property-style syntax: -Dpath1=value1 -Dpath2=value2, etc.
    */
  lazy val overridesParser: Parser[Config] = {
    // Parser for a single property-style config override.
    // Equivalent of extracting from the regex: `-D([^=])*=(.*)`.
    val overrideParser: Parser[(String, String)] = token(
      ("-D" ~> charClass(_ != '=').*.string) ~ ('=' ~> StringBasic),
      "-D<deploy.key>=<override>"
    )

    // Parser for one or more space-delimited config overrides.
    val overridesParser: Parser[Seq[(String, String)]] =
      rep1sep(overrideParser, token(Space))

    // Parser which folds some number of overrides into a Config object.
    overridesParser map { overrides =>
      overrides.foldLeft(ConfigFactory.empty) {
        case (config, (path, value)) => config.withValue(path, ConfigValueFactory.fromAnyRef(value))
      }
    }
  }

  /** Parser used for reading input for the deploy and generateEnvConfig tasks. */
  lazy val deployParser: State => Parser[(Config, String)] = { state =>
    token(Space) ~> (overridesParser <~ token(Space)).? ~ deployTargetParser map {
      case (overrides, env) => (overrides getOrElse ConfigFactory.empty, env)
    }
  }

  /** Load the [[Config]] object containing deploy configuration for all environments from
    * the file conf/deploy.conf.
    */
  lazy val loadDeployConfig = taskKey[Config]("Load top-level deploy configuration.")
  lazy val loadDeployConfigTask = loadDeployConfig := {
    val workingDirectory = (baseDirectory in thisProject).value
    val configFile = workingDirectory / "conf" / "deploy.conf"
    if (!configFile.isFile) {
      throw new IllegalArgumentException(s"'${configFile.getPath}' must be a config file.")
    }

    // Load the config file.
    try {
      ConfigFactory.parseFile(configFile).resolve
    } catch {
      case configError: ConfigException =>
        throw new IllegalArgumentException("Error loading config file:" + configError.getMessage)
    }
  }

  /** Task used to parse deploy-related input into [[DeployConfig]] objects. */
  lazy val parseDeployInputTask = Def.inputTask[(String, DeployConfig)] {
    // Process command-line input.
    val (commandlineOverrides, deployTarget) = deployParser.parsed

    // Load base deploy config.
    val deployConfig = loadDeployConfig.value

    // Apply config overrides for the deploy process itself.
    val overrides = {
      val rcFile = new File(System.getenv("HOME"), ".deployrc")
      val rcConfig = if (rcFile.isFile) {
        ConfigFactory.parseFile(rcFile)
      } else {
        ConfigFactory.empty
      }
      // command-line > ~/.deployrc.
      commandlineOverrides.withFallback(rcConfig)
    }

    // Parse deploy configuration with overrides.
    deployTarget -> parseDeployConfig(deployConfig, deployTarget, overrides)
  }

  /** Transform a [[Config]] object into a [[DeployConfig]] object. */
  def parseDeployConfig(
    deployConfig: Config,
    deployEnv: String,
    deployOverrides: Config = ConfigFactory.empty
  ): DeployConfig = {
    val envConfig = {
      require(
        deployConfig.hasPath(deployEnv),
        s"'$deployEnv' is not a configured deploy environment! Configured environments are: " +
          deployConfig.root.keySet.asScala.mkString("[", ", ", "]")
      )
      deployOverrides.withFallback(deployConfig.getConfig(deployEnv)).getConfig("deploy")
    }

    // Check for both replica list and top-level host config.
    val replicasGiven = envConfig.hasPath("replicas")
    val topHostGiven = envConfig.hasPath("host")

    // Validate key layout.
    require(
      replicasGiven || topHostGiven,
      s"Deploy requires either 'deploy.replicas' or 'deploy.host' to be set in '$deployEnv'"
    )
    require(
      !(replicasGiven && topHostGiven),
      s"Deploy supports setting only one of 'deploy.replicas' or 'deploy.host' in '$deployEnv'"
    )

    // Parse common config.
    val sshUser = getStringValue(envConfig, "user.ssh_username")
    val defaultDirectory = getStringValue(envConfig, "directory")
    val startupScript = getStringValue(envConfig, "startup_script")

    // Break default directory into root directory and project name.
    val (rootDeployDir, projectName) =
      defaultDirectory.splitAt(defaultDirectory.lastIndexOf('/') + 1)

    // Get the list of config objects representing replicas to deploy.
    val replicaList = if (replicasGiven) {
      try {
        envConfig.getConfigList("replicas").asScala.toSeq
      } catch {
        case _: ConfigException.WrongType =>
          throw new IllegalArgumentException(
            s"Error: 'deploy.replicas' must be a config list in '$deployEnv'"
          )
      }
    } else {
      Seq(envConfig)
    }

    // Build map from host to replica collection.
    val globalOverrides = getReplicaEnvOverrides(envConfig)
    val replicaOverridesByHost = replicaList.foldLeft(Map.empty[String, Seq[Config]]) {
      case (acc, replicaConf) => {
        val (host, overrides) = parseReplicaConfig(replicaConf)
        acc.updated(host, acc.getOrElse(host, { Seq() }) :+ overrides.withFallback(globalOverrides))
      }
    }
    val replicasByHost = replicaOverridesByHost mapValues { overrides =>
      val numReplicas = overrides.size
      overrides.zipWithIndex map {
        case (envOverrides, i) => {
          val directory = if (numReplicas == 1) projectName else s"$projectName-${i + 1}"
          ReplicaConfig(directory, envOverrides)
        }
      }
    }

    // Wrap all parsed data.
    DeployConfig(
      projectName,
      // No trailing '/' for the root directory.
      rootDeployDir.dropRight(1),
      sshUser,
      startupScript,
      replicasByHost
    )
  }

  /** Transform the given [[com.typesafe.config.Config]] object into a corresponding
    * tuple of ([[String]], [[Config]]) representing a host -> replica pair.
    * @param replicaConfig config to parse
    */
  def parseReplicaConfig(replicaConfig: Config): (String, Config) = {
    val host = getStringValue(replicaConfig, "host")
    val configOverrides = getReplicaEnvOverrides(replicaConfig)
    (host, configOverrides)
  }

  /** Helper method to get replica config overrides from config. */
  def getReplicaEnvOverrides(config: Config): Config = {
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

  /* ==========>  Environment config generation.  <========== */

  /** Task used to clean the target directory for generated environment config. */
  lazy val cleanEnvConfigTask = cleanEnvConfig := {
    IO.delete((envConfigTarget in thisProject).value)
  }

  /** Task used to generate the environment config for a given deploy target and overrides. */
  lazy val generateEnvConfigTask = generateEnvConfig := {
    val (deployEnv, deployConfig) = parseDeployInputTask.evaluated
    implicit val log = DeployLogger(streams.value.log)
    writeEnvConfig(
      (envConfigSource in thisProject).value,
      (envConfigTarget in thisProject).value,
      deployEnv,
      deployConfig
    )
  }

  /** Helper function for writing fully-resolved environment configuration to disk.
    * @param srcDir the directory from which environment configuration should be loaded
    * @param targetDir the directory to which fully-resolved environment configuration
    *     should be written
    * @param deployTarget the name of the deploy environment to generate config for
    * @param deployConfig parsed deployConfiguration for the given deploy environment
    */
  def writeEnvConfig(
    srcDir: File,
    targetDir: File,
    deployTarget: String,
    deployConfig: DeployConfig
  )(implicit log: DeployLogger): Unit = {
    // Try to get environment config definitions.
    val deployEnv = if (deployTarget.contains('.')) {
      deployTarget.substring(deployTarget.lastIndexOf('.') + 1)
    } else {
      deployTarget
    }
    val envConfFile = new File(srcDir, s"$deployEnv.conf")
    val fullEnvConf = if (envConfFile.exists) {
      log.info(s"Resolving environment configuration for $deployEnv . . .")
      ConfigFactory.parseFile(envConfFile)
    } else {
      log.warn("")
      log.warn(s"WARNING: Could not find config file '$deployEnv.conf'!")
      log.warn("")
      log.warn("Press ENTER to continue with no environment configuration, CTRL-C to abort.")
      log.warn("")
      System.console.readLine()
      ConfigFactory.empty
    }

    // Resolve and write config files for all replicas, including their specified overrides.
    deployConfig.replicasByHost foreach {
      case (host, replicas) => replicas foreach { replica =>
        // Encode hostname to reduce chances of naming problems.
        val encodedHost = URLEncoder.encode(host, "utf-8")

        // Merge replica overrides into default environment config.
        val confWithOverrides = replica.envOverrides.withFallback(fullEnvConf)

        // Write the fully-resolved environment configuration to disk.
        val targetFile = targetDir / encodedHost / replica.directory / "env.conf"
        log.info(s"Writing environment config for '$deployEnv' to '${targetFile.getPath}'")
        IO.write(targetFile, confWithOverrides.root.render(ConfigRenderOptions.concise))
      }
    }
  }

  /* ==========>  Git checks.  <========== */

  /** Task that checks for a git repository in the project's directory. */
  lazy val gitRepoPresentTask = gitRepoPresent := {
    // Validate that we are, in fact, in a git repository.
    if (HelperDefs.gitRepoPresentDef.value) {
      throw new IllegalStateException("Not in git repository, exiting.")
    }

    DeployLogger(streams.value.log).info("Git repository present.")
  }

  /** Task that checks if the project's git repository is clean. */
  lazy val gitRepoCleanTask = gitRepoClean := {
    HelperDefs.gitRepoCleanDef.value match {
      case None => streams.value.log.info("Git repository is clean.")
      case Some(error) => throw new IllegalStateException(error)
    }
  }

  /* ==========>  The main deploy task.  <========== */

  /** Wrapper for information used to access a remote host. */
  class SshInfo(val host: String, user: String, keyFile: Option[String]) {
    private val keyfileFlag = keyFile.toSeq flatMap { Seq("-i", _) }

    /** The command to pass as the --rsh flag of an rsync call to the remote host. */
    val rshCmd: Seq[String] = ("ssh" +: keyfileFlag) ++ Seq("-l", user)
    /** The command to use when running SSH to the remote host. */
    val sshCmd: Seq[String] = rshCmd :+ host
    /** Build a command to copy a local file to a directory on the remote host. */
    def scpCmd(srcPath: String, targetPath: String): Seq[String] =
      ("scp" +: keyfileFlag) ++ Seq(srcPath, s"$user@$host:$targetPath")
  }

  /** Task used to deploy the current project to a remote host. */
  lazy val deployTask = deploy := {
    implicit val log = DeployLogger(streams.value.log)

    // Ensure the git repo is clean.
    gitRepoClean.value

    // Parse input.
    val (deployTarget, parsedConfig) = parseDeployInputTask.evaluated

    // Generate environment config.
    writeEnvConfig(
      (envConfigSource in thisProject).value,
      (envConfigTarget in thisProject).value,
      deployTarget,
      parsedConfig
    )

    // Stage the project.
    val universalStagingDir = stageAndCacheKey.value

    // Global optional identity file for AWS access.
    val maybeKeyFile = sys.env.get("AWS_PEM_FILE")

    val rootDeployDir = parsedConfig.rootDeployDir
    val deployedDirs = (deployDirs in thisProject).value
    val envSrc = (envConfigTarget in thisProject).value

    // TODO(jkinkead): Allow for a no-op / dry-run flag that only prints the commands.
    val deployErrors: Future[Seq[Throwable]] = {
      // Deploy to hosts, collecting any errors that occur.
      val deployErrorsByHost = parsedConfig.replicasByHost map {
        case (host, replicas) => {
          implicit val sshInfo = new SshInfo(host, parsedConfig.sshUser, maybeKeyFile)

          Future {
            // First, ensure parent directory structure of deploy exists.
            mkdirOnRemote(rootDeployDir)
          } map { _ =>
            // Then stop any replicas already running on the remote.
            stopStaleReplicas(rootDeployDir, parsedConfig.projectName, parsedConfig.startupScript)
          } flatMap { _ =>
            val replicaDeploys = replicas map { replica =>
              Future {
                // Ensure the target deploy directory for this replica exists on the remote
                // (rsync won't do it for you).
                val replicaDir = s"$rootDeployDir/${replica.directory}"
                mkdirOnRemote(replicaDir)
                replicaDir
              } map { replicaDir =>
                // Sync the project to the remote replica.
                rsyncToRemote(
                  universalStagingDir.getPath + "/",
                  replicaDir,
                  deployedDirs
                )
                replicaDir
              } map { replicaDir =>
                // Copy replica-specific config into the synchronized directory.
                val encodedHost = URLEncoder.encode(host, "utf-8")
                copyEnvConfToRemote(
                  (envSrc / encodedHost / replica.directory / "env.conf").getPath,
                  replicaDir + "/conf/env.conf"
                )
                replicaDir
              } map { replicaDir =>
                // Start the updated replica.
                startReplica(s"$replicaDir/${parsedConfig.startupScript}")
              } map {
                // Wrap the whole chain in a Try so we can collect errors.
                Success(_)
              } recover {
                case e => Failure(e)
              }
            }

            Future.fold[Try[Unit], Seq[Throwable]](replicaDeploys)(Seq.empty) {
              case (acc, Success(_)) => acc
              case (acc, Failure(e)) => acc :+ e
            }
          }
        }
      }

      // Flatten errors across hosts.
      Future.reduce(deployErrorsByHost) {
        case (acc, errors) => acc ++ errors
      }
    }

    // Throw an error if any deploy commands failed.
    val deployStatus = deployErrors map { errorSeq =>
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

  /** Use `find -exec` to locate and stop replicas that won't be updated as part of a running
    * deploy. Replicas are considered 'stale' if they are running in a directory that will
    * not be updated by subsequent `rsync` commands.
    * Directories are chosen to be `rsync`-ed according to the following scheme:
    *   - If only one replica is being deployed to a host, then the `rsync`-ed directory will be
    *     `baseDeployDirectory`.
    *   - If `n > 1` replicas are being deployed to a host, then the `rsync`-ed directories will
    *     be `baseDeployDirectory-i` for `i` in `1` to `n`.
    * @param rootDeployDirectory the root deploy directory for all deploys on the remote host
    * @param projectName the base name of the current project, used to find replicas to stop
    * @param stopScriptPath the path to the stop script of the replica, relative to the
    *     root of the replica's deploy directory
    */
  def stopStaleReplicas(
    rootDeployDirectory: String,
    projectName: String,
    stopScriptPath: String
  )(implicit sshInfo: SshInfo, log: DeployLogger): Unit = {
    // Build the `find` command that will be executed on the remote host to stop stale replicas.
    val findCommand = Seq(
      "find",
      // Prevent `find` from reporting subdirectories of the deployed replicas.
      rootDeployDirectory + "/", "-maxdepth", "1",
      // Set `find` to only report directory names.
      "-type", "d",
      // Set `find` to only report directories named after this project, with an optional numeric
      // suffix (to avoid stopping other deployed projects).
      "-regex", s""""$rootDeployDirectory/$projectName\\(-[0-9]+\\)?"""",
      // Within each replica directory meeting the above criteria, stop the service.
      "-exec", s"{}/$stopScriptPath stop \\;"
    )

    // Build the SSH command that will be sent to the remote host to execute the above `find`.
    val stopCommand = sshInfo.sshCmd ++ findCommand
    // Also build a shell-friendly version of the command to log out, for copy-paste.
    val quotedStopCommand = quoteSshCmd(sshInfo, findCommand)

    // Log and run the command on the remote host, and throw an error if it fails.
    // TODO(danm): Should we support ignoring an error at this stage?
    logAndRun(stopCommand, quotedStopCommand)
  }

  /** Run `mkdir` through SSH to create a remote directory.
    * @param dir the absolute path of the directory to create on the remote host
    */
  def mkdirOnRemote(dir: String)(implicit sshInfo: SshInfo, log: DeployLogger): Unit = {
    // Build the `mkdir` command that will be executed on the remote host.
    val mkdirCommand = Seq("mkdir", "-p", dir)

    // Build the SSH command that will be sent to the remote host to execute the above `mkdir`.
    val remoteCommand = sshInfo.sshCmd ++ mkdirCommand
    // Also build a shell-friendly version of the command to log out, for copy-paste.
    val quotedRemoteCommand = quoteSshCmd(sshInfo, mkdirCommand)

    logAndRun(remoteCommand, quotedRemoteCommand)
  }

  /** Run `rsync` to copy the staged contents of this project to a remote host.
    * @param srcDir the path to the local directory containing the staged contents of this project
    * @param targetDir the path to the directory on the remote host to sync with
    * @param deployedDirs the names of subdirectories of this project to copy to the remote host
    */
  def rsyncToRemote(
    srcDir: String,
    targetDir: String,
    deployedDirs: Seq[String]
  )(implicit sshInfo: SshInfo, log: DeployLogger): Unit = {
    // Build the `rsync` command.
    val rsyncIncludes = deployedDirs map { name => s"--include=/$name" }
    val rsh = sshInfo.rshCmd
    val rsyncCommand = Seq.concat(
      Seq(
        "rsync",
        "-vcrtzP",
        s"--rsh=${rsh.mkString(" ")}"
      ),
      rsyncIncludes,
      Seq(
        "--exclude=/*",
        "--delete",
        srcDir,
        s"${sshInfo.host}:$targetDir"
      )
    )
    // Also build a shell-friendly version of the command to log out, for copy-paste.
    val quotedRsync = rsyncCommand.updated(
      2,
      s"--rsh=${rsh.mkString("'", " ", "'")}"
    ).mkString(" ")

    logAndRun(rsyncCommand, quotedRsync)
  }

  /** Copy environment config from a local file to a remote host.
    * The written file will be named `env.conf`.
    * @param srcPath the path to the local file that should be copied
    * @param targetDir the remote directory the file should be copied into
    */
  def copyEnvConfToRemote(
    srcPath: String,
    targetDir: String
  )(implicit sshInfo: SshInfo, log: DeployLogger): Unit = {
    // Build the scp command that will copy the file.
    // There's no need to specially quote this one for logging.
    val scpCmd = sshInfo.scpCmd(srcPath, targetDir)
    logAndRun(scpCmd, scpCmd.mkString(" "))
  }

  /** Start a replica on a remote host through SSH.
    * @param startScriptPath the absolute path to the start script of the replica
    */
  def startReplica(startScriptPath: String)(implicit sshInfo: SshInfo, log: DeployLogger): Unit = {
    // Build the SSH command that will run the restart script in the remote replica.
    val startCommand = sshInfo.sshCmd ++ Seq(startScriptPath, "start")
    // Also build a shell-friendly version of the command to log out for copy-paste.
    val quotedStart = quoteSshCmd(sshInfo, Seq(startScriptPath, "start"))

    logAndRun(startCommand, quotedStart)
  }

  /** Helper method for producing a shell-friendly SSH command, for copy-paste. */
  def quoteSshCmd(sshInfo: SshInfo, remoteCommand: Seq[String]): String =
    (sshInfo.sshCmd :+ remoteCommand.mkString("'", " ", "'")).mkString(" ")

  /** Helper method for logging and running a system command. */
  def logAndRun(
    process: ProcessBuilder,
    quotedCommand: String
  )(implicit log: DeployLogger): Unit = {
    log.info(s"Running $quotedCommand . . .")
    if (process.! != 0) {
      sys.error(s"Error running command: $quotedCommand")
    }
  }
}
