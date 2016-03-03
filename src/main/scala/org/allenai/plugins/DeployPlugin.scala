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

    // The universal packager doesn't clean the staging directory by default.
    val cleanStage = taskKey[Unit]("Clean the staging directory.")

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

  }

  import autoImport._

  /** Default settings and task dependencies for the Keys defined by this plugin. */
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    cleanEnvConfigTask,
    cleanStageTask,
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
    stageAndCacheKeyTask,
    // Clean up anything leftover in the staging directory before re-staging.
    UniversalPlugin.autoImport.stage <<=
      UniversalPlugin.autoImport.stage.dependsOn(cleanStage),
    // Create the required run-class.sh script before staging.
    UniversalPlugin.autoImport.stage <<=
      UniversalPlugin.autoImport.stage.dependsOn(CoreSettingsPlugin.autoImport.generateRunClass),

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

  /** Task used to clean the staging directory. */
  lazy val cleanStageTask = cleanStage := {
    IO.delete((UniversalPlugin.autoImport.stagingDirectory in Universal).value)
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
    val hashes = filesToHash.map(Hash.apply).map(Hash.toHex)

    // We sort so that we're not dependent on filesystem or git sorting remaining stable in order
    // for the cacheKey to not change.
    val cacheKey = Hash.toHex(
      Hash((hashes ++ dependentGitCommits.value :+ gitLocalSha1.value).sorted.mkString)
    )
    val cacheKeyConfFile = new java.io.File(s"${stageDir.getCanonicalPath}/conf/cacheKey.Sha1")

    logger.info(s"Generating cacheKey.conf managed resource... (cacheKey: $cacheKey)")
    IO.write(cacheKeyConfFile, cacheKey)

    // Return the stageDirectory so that others can depend on stage having happened via us.
    stageDir
  }

  /* ==========>  Environment config generation.  <========== */

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

  /** Parser used for reading deploy targets.
    * A string parser with auto-suggestion of valid deploy target environments.
    */
  lazy val deployTargetParser: State => Parser[String] = { state =>
    val (_, deployConf) = Project.extract(state).runTask(loadDeployConfig, state)
    val validTargets = deployConf.root.keySet.asScala map Parser.literal
    token(validTargets.reduce(_ | _))
  }

  /** Parser used for reading input for the generateEnvConfig task. */
  lazy val genEnvConfigParser: State => Parser[String] = { state =>
    Space ~> deployTargetParser(state)
  }

  /** Task used to clean the target directory for generated environment config. */
  lazy val cleanEnvConfigTask = cleanEnvConfig := {
    IO.delete((envConfigTarget in thisProject).value)
  }

  /** Task used to generate the environment config for a given deploy target and overrides. */
  lazy val generateEnvConfigTask = generateEnvConfig := {
    implicit val log = DeployLogger(streams.value.log)
    writeEnvConfig(
      (envConfigSource in thisProject).value,
      (envConfigTarget in thisProject).value,
      genEnvConfigParser.parsed,
      loadDeployConfig.value
    )
  }

  /** Helper function for writing fully-resolved environment configuration to disk.
    * @param srcDir the directory from which environment configuration should be loaded
    * @param targetDir the directory to which fully-resolved environment configuration should be
    *                  written
    * @param deployTarget the name of the deploy environment to generate config for
    * @param deployConfig deploy config with top-level keys corresponding to the names of valid
    *                     depoy environments
    */
  def writeEnvConfig(
    srcDir: File,
    targetDir: File,
    deployTarget: String,
    deployConfig: Config
  )(implicit log: DeployLogger): Unit = {
    // Extract needed information from deploy config.
    val hostName = deployConfig.getString(s"$deployTarget.deploy.host")
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
    // Write the fully-resolved environment configuration to disk.
    val targetFile = targetDir / URLEncoder.encode(hostName, "utf-8") / "env.conf"
    log.info(s"Writing environment config for '$deployEnv' to '${targetFile.getPath}'")
    IO.write(targetFile, fullEnvConf.root.render(ConfigRenderOptions.concise))
  }

  /* ==========>  Git checks.  <========== */

  /** Task that checks for a git repository in the project's directory. */
  lazy val gitRepoPresentTask = gitRepoPresent := {
    // Validate that we are, in fact, in a git repository.
    // TODO(schmmd): Use JGit instead (http://www.eclipse.org/jgit/)
    if (Process(Seq("git", "status")).!(ProcessLogger(line => ())) != 0) {
      throw new IllegalStateException("Not in git repository, exiting.")
    }

    DeployLogger(streams.value.log).info("Git repository present.")
  }

  /** Task that checks if the project's git repository is clean. */
  lazy val gitRepoCleanTask = gitRepoClean := {
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

  /* ==========>  The main deploy task.  <========== */

  /** Parser used for reading deploy-config overrides into a [[Config]] object.
    * Overrides are passed using property-style syntax: -Dpath1=value1 -Dpath2=value2, etc.
    */
  lazy val overridesParser: State => Parser[Config] = { _ =>
    // Parser for a single property-style config override.
    // Equivalent of extracting from the regex: `-D([^=])*=(.*)`.
    val overrideParser: Parser[(String, String)] = token(
      ("-D" ~> charClass(_ != '=').*.string) ~ ('=' ~> StringBasic),
      "-D<deploy.key>=<override>"
    )

    // Parser for one or more space-delimited config overrides.
    val overridesParser: Parser[Seq[(String, String)]] =
      rep1sep(overrideParser, Space)

    // Parser which folds some number of overrides into a Config object.
    overridesParser map { overrides =>
      overrides.foldLeft(ConfigFactory.empty) {
        case (config, (path, value)) => config.withValue(path, ConfigValueFactory.fromAnyRef(value))
      }
    }
  }

  /** Parser used for reading input for the deploy task. */
  lazy val deployParser: State => Parser[(Config, String)] = { state =>
    Space ~> ((overridesParser(state) <~ Space).? ~ deployTargetParser(state)) map {
      case (overrides, env) => (overrides getOrElse ConfigFactory.empty, env)
    }
  }

  /** Task used to deploy the current project to a remote host. */
  lazy val deployTask = deploy := {
    implicit val log = DeployLogger(streams.value.log)

    // Ensure the git repo is clean.
    gitRepoClean.value

    // Process command-line input.
    val (commandlineOverrides, deployTarget) = deployParser.parsed

    val deployConfig = loadDeployConfig.value
    // Generate environment config.
    writeEnvConfig(
      (envConfigSource in thisProject).value,
      (envConfigTarget in thisProject).value,
      deployTarget,
      loadDeployConfig.value
    )

    // Apply config overrides.
    val targetConfig = {
      val rcFile = new File(System.getenv("HOME"), ".deployrc")
      val rcConfig = if (rcFile.isFile) {
        ConfigFactory.parseFile(rcFile)
      } else {
        ConfigFactory.empty
      }
      // Final config order: command-line > ~/.deployrc > conf/deploy.conf.
      commandlineOverrides.withFallback(rcConfig).withFallback(deployConfig.getConfig(deployTarget))
    }

    val parsedConfig = parseConfig(deployTarget, targetConfig)

    // TODO(danm): This should be optional.
    val keyFile = sys.env.getOrElse("AWS_PEM_FILE", {
      throw new IllegalStateException(s"Environment variable 'AWS_PEM_FILE' is undefined")
    })
    require(keyFile.nonEmpty, "Environment variable 'AWS_PEM_FILE' is empty")

    // TODO(jkinkead): Allow for a no-op / dry-run flag that only prints the commands.
    val universalStagingDir = stageAndCacheKey.value
    val deploys: Seq[Future[Try[Unit]]] = parsedConfig.hostConfigs map { hostConfig =>
      Future {
        // Command to pass to rsync's "rsh" flag, and to use as the base of our ssh operations.
        val sshCommand = Seq("ssh", "-i", keyFile, "-l", hostConfig.sshUser)

        val deployHost = hostConfig.host
        val deployDirectory = hostConfig.directory

        val rsyncDirs = deployDirs.value map (name => s"--include=/$name")
        val rsyncCommand = Seq.concat(
          Seq("rsync", "-vcrtzP", "--rsh=" + sshCommand.mkString(" ")),
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
          2, Seq("--rsh=" + sshCommand.mkString("\"", " ", "\"")), 1
        ).mkString(" ")

        // Command used to scp the generated environment config to the remote host.
        val encodedHost = URLEncoder.encode(deployHost, "utf-8")
        val envConf = (envConfigTarget in thisProject).value / encodedHost / "env.conf"
        val scpCommand = Seq(
          "scp",
          "-i", keyFile,
          envConf.getPath,
          s"${hostConfig.sshUser}@$deployHost:$deployDirectory/conf/env.conf"
        )

        // Command used to ssh to the remote host and run the restart script.
        val restartScript = deployDirectory + "/" + hostConfig.startupScript
        val restartArgs = if (hostConfig.startupArgs.nonEmpty) {
          "--" +: hostConfig.startupArgs
        } else {
          Seq()
        }
        val restartCommand = Seq.concat(
          sshCommand,
          Seq(deployHost, restartScript, "restart"),
          restartArgs
        )

        // Run commands within a Try so we can collect errors using Future.fold.
        // TODO(danm): Use ProcessIO to capture and report the actual stderr of failures here.
        Try {
          log.info("Running " + quotedRsync + " . . .")
          if (Process(rsyncCommand).! != 0) {
            sys.error(s"Error running rsync to host '$deployHost'.")
          }

          log.info("Running " + scpCommand.mkString(" ") + " . . .")
          if (Process(scpCommand).! != 0) {
            sys.error(s"Error running scp to host '$deployHost'.")
          }

          log.info("Running " + restartCommand.mkString(" ") + " . . .")
          if (Process(restartCommand).! != 0) {
            sys.error(s"Error running restart command on host '$deployHost'.")
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

  /** Container for config needed to deploy a project to a host.
    * @param host the fully-qualified name of the host the project should be deployed to
    * @param directory The directory on the host in which the project's code should be placed
    * @param sshUser The username to use when accessing the host
    * @param startupScript The path, relative to the deploy directory, to the script that should
    *                      be used to restart the project after deploying the new code
    * @param startupArgs Arguments to pass to the startup script
    */
  case class HostConfig(
    host: String,
    directory: String,
    sshUser: String,
    startupScript: String,
    startupArgs: Seq[String]
  )

  /** Wrapper for many [[org.allenai.plugins.DeployPlugin.HostConfig]] objects, along with any data
    * that should be shared between them, when deploying a project.
    * @param hostConfigs configuration objects specifying how the project should be deployed to
    *                    many hosts
    */
  case class DeployConfig(hostConfigs: Seq[HostConfig])

  /** Transform the given [[com.typesafe.config.Config]] object into a corresponding
    * [[org.allenai.plugins.DeployPlugin.DeployConfig]] object.
    * @param targetName the name of the target to print on error
    * @param targetConfig env-level deployment config to convert
    */
  def parseConfig(targetName: String, targetConfig: Config): DeployConfig = {
    DeployConfig(Seq(parseHostConfig(targetConfig.getConfig("deploy"))))
  }

  /** Transform the given [[com.typesafe.config.Config]] object into a corresponding
    * [[org.allenai.plugins.DeployPlugin.HostConfig]] object.
    * @param hostConfig host-level config to convert
    */
  def parseHostConfig(hostConfig: Config): HostConfig = {
    val requiredKeys = Seq("host", "directory", "startup_script", "user.ssh_username")

    val confMap = (requiredKeys map { key =>
      val value = try {
        hostConfig.getString(key)
      } catch {
        case _: ConfigException.Missing =>
          throw new IllegalArgumentException(
            s"Error: ${hostConfig.root().render()} missing key '$key'."
          )
        case _: ConfigException.WrongType =>
          throw new IllegalArgumentException(
            s"Error: '$key' must be a string in ${hostConfig.root().render()}"
          )
      }

      key -> value
    }).toMap

    val startArgs = try {
      hostConfig.getStringList("startup_args").asScala
    } catch {
      case _: ConfigException.Missing => Seq()
      case _: ConfigException.WrongType =>
        throw new IllegalArgumentException(
          s"Error: 'startup_args' must be an array of strings in ${hostConfig.root().render()}"
        )
    }

    HostConfig(
      host = confMap("host"),
      directory = confMap("directory"),
      sshUser = confMap("user.ssh_username"),
      startupScript = confMap("startup_script"),
      startupArgs = startArgs
    )
  }
}
