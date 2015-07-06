package org.allenai.plugins

import org.allenai.plugins.NodeJsPlugin.autoImport.{ NodeKeys, Npm }
import com.typesafe.config.{ Config, ConfigException, ConfigFactory, ConfigValueFactory }
import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.packager.archetypes.JavaAppPackaging
import com.typesafe.sbt.packager.universal.UniversalPlugin
import sbt._
import sbt.Keys._

import java.io.File

import scala.collection.JavaConverters._
import scala.sys.process.{ Process, ProcessLogger }
import scala.util.Try

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
  val Usage = "Usage: deploy [overrides] [deploy target]";

  object autoImport {

    val deploy = inputKey[Unit](Usage)

    val nodeEnv = settingKey[String]("The value to use for NODE_ENV during deploy builds.")

    val makeDefaultBashScript = settingKey[Boolean](
      "If true, will create executable bash script per JavaAppPackaging defaults. " +
        "Defaults to false"
    )

    val cleanStage = taskKey[Unit](
      "Cleans the staging directory. This is not done by default by the universal packager."
    )

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

  /** sbt.Logger wrapper that prepends [deploy] to log messages */
  case class DeployLogger(sbtLogger: Logger) {
    private def logMsg(msg: String) = s"[deploy] ${msg}"
    def info(msg: String): Unit = sbtLogger.info(logMsg(msg))
    def error(msg: String): Unit = sbtLogger.error(logMsg(msg))
  }

  val gitRepoCleanTask = gitRepoClean := {
    // Dependencies
    gitRepoPresent.value

    val log = DeployLogger(streams.value.log)

    // Validate that the git repository is clean.
    if (Process(Seq("git", "diff", "--shortstat")).!! != "") {
      throw new IllegalArgumentException("Git repository is dirty, exiting.")
    }

    log.info("Git repository is clean.")

    // Validate that the git repository has no untracked files.
    if (Process(Seq("git", "clean", "-n")).!! != "") {
      throw new IllegalArgumentException("Git repository has untracked files, exiting.")
    }

    log.info("Git repository contains no untracked files.")
  }

  val gitRepoPresentTask = gitRepoPresent := {
    // Validate that we are, in fact, in a git repository.
    // TODO(schmmd): Use JGit instead (http://www.eclipse.org/jgit/)
    if (Process(Seq("git", "status")).!(ProcessLogger(line => ())) != 0) {
      throw new IllegalArgumentException("Not in git repository, exiting.")
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
          NodeJsPlugin.getEnvironment("dev", (NodeKeys.nodeProjectTarget in Npm).value),
          (NodeKeys.npmLogLevel in Npm).value
        )
      }
    } else {
      Def.task {
        NodeJsPlugin.execBuild(
          (NodeKeys.nodeProjectDir in Npm).value,
          NodeJsPlugin.getEnvironment("prod", (NodeKeys.nodeProjectTarget in Npm).value),
          (NodeKeys.npmLogLevel in Npm).value
        )
      }
    }
  }

  lazy val deployTask = deploy := {
    // Dependencies
    gitRepoClean.value
    VersionInjectorPlugin.autoImport.injectCacheKey.value

    val log = DeployLogger(streams.value.log)

    val args: Seq[String] = Def.spaceDelimited("<arg>").parsed
    // Process any definition-like args.
    val (commandlineOverrides, reducedArgs) = parseDefines(args)

    if (reducedArgs.length != 1) {
      throw new IllegalArgumentException(Usage)
    }

    val workingDirectory = (baseDirectory in thisProject).value
    val configFile = new File(workingDirectory.getPath + "/conf/deploy.conf")
    if (!configFile.isFile()) {
      throw new IllegalArgumentException(s"${configFile.getPath()}: Must be a config file")
    }
    val deployTarget = reducedArgs(0)

    val targetConfig = loadTargetConfig(commandlineOverrides, configFile, deployTarget)

    val configMap = validateAsMap(deployTarget, targetConfig)

    // TODO(jkinkead): Allow for a no-op / dry-run flag that only prints the
    // commands.

    // Check out the provided version, if it's set.
    for (version <- configMap.get("project.version")) {
      log.info(s"Checking out ${version} . . .")
      if (Process(Seq("git", "checkout", "-q", version)).! != 0) {
        throw new IllegalArgumentException(s"Could not checkout ${version}.")
      }
    }

    // Copy over the per-env config file, if it exists.
    val deployEnv = if (deployTarget.lastIndexOf('.') >= 0) {
      deployTarget.substring(deployTarget.lastIndexOf('.') + 1)
    } else {
      deployTarget
    }

    log.info(s"Building ${(name in thisProject).value} . . .")

    val universalStagingDir = (UniversalPlugin.autoImport.stage in thisProject).value

    val envConfFile = new File(universalStagingDir, s"conf/${deployEnv}.conf")
    if (envConfFile.exists) {
      log.info(s"Copying config for ${deployEnv} . . .")
      val destConfFile = new File(universalStagingDir, "conf/env.conf")
      IO.copyFile(envConfFile, destConfFile)
    } else {
      log.info("")
      log.info(s"WARNING: Could not find config file ${deployEnv}.conf!")
      log.info("")
      log.info("Press ENTER to continue with no environment configuration, CTRL-C to abort.")
      log.info("")
      System.console.readLine()
    }

    // Command to pass to rsync's "rsh" flag, and to use as the base of our ssh
    // operations.
    val sshCommand = {
      val pemEnvVar = "AWS_PEM_FILE"
      val sshKeyfile = sys.env(pemEnvVar)
      val sshUser = configMap("deploy.user.ssh_username")
      Seq("ssh", "-i", sshKeyfile, "-l", sshUser)
    }
    val deployHost = configMap("deploy.host")
    val deployDirectory = configMap("deploy.directory")

    val rsyncDirs = deployDirs.value map (name => s"--include=/${name}")
    val rsyncCommand = Seq("rsync", "-vcrtzP", "--rsh=" + sshCommand.mkString(" ")) ++ rsyncDirs ++
      Seq(
        "--exclude=/*",
        "--delete",
        universalStagingDir.getPath + "/",
        deployHost + ":" + deployDirectory
      )

    // Shell-friendly version of rsync command, with rsh value quoted.
    val quotedRsync = rsyncCommand.patch(
      2, Seq("--rsh=" + sshCommand.mkString("\"", " ", "\"")), 1
    ).mkString(" ")
    log.info("Running " + quotedRsync + " . . .")
    if (Process(rsyncCommand).! != 0) {
      throw new IllegalArgumentException("Error running rsync.")
    }

    // Now, ssh to the remote host and run the restart script.
    val restartScript = deployDirectory + "/" + configMap("deploy.startup_script")
    val restartCommand = sshCommand ++ Seq(deployHost, restartScript, "restart")
    log.info("Running " + restartCommand.mkString(" ") + " . . .")
    if (Process(restartCommand).! != 0) {
      throw new IllegalArgumentException("Error running restart command.")
    }
    log.info("")
    // TODO(jkinkead): Run an automated "/info/name" check here to see if service is running.
    log.info("Deploy complete. Validate your server!")
  }

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    gitRepoCleanTask,
    gitRepoPresentTask,
    cleanStageTask,
    deployDirs := Seq("bin", "conf", "lib", "public"),
    nodeEnv := "prod",
    deployTask,
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
    *
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
    val rcConfig = if (rcFile.isFile()) {
      ConfigFactory.parseFile(rcFile)
    } else {
      ConfigFactory.empty
    }

    // Final config order - overrides > rcFile > target.
    overrides.withFallback(rcConfig.withFallback(targetConfig)).resolve
  }

  /** Translates the given deploy Config object into a flat Map[String, String]
    * containing all the relevant deployment keys.
    *
    * Exits the program if any of the keys required for deployment are missing.
    *
    * @param targetName the name of the target to print on error
    * @param targetConfig the config to parse
    */
  def validateAsMap(targetName: String, targetConfig: Config): Map[String, String] = {
    // Validate that the target has all the required environment variables.
    val requiredEnv = Seq("AWS_PEM_FILE")
    for (variable <- requiredEnv) {
      val value = sys.env.get(variable).getOrElse {
        throw new IllegalArgumentException(s"Environment variable ${variable} undefined.")
      }

      require(!value.isEmpty, "Environment variable ${value} is empty.")
    }

    // Validate that the target has all the required keys.
    val requiredKeys = Seq("deploy.host", "deploy.directory",
      "deploy.startup_script", "deploy.user.ssh_username")
    val requiredKeyPairs: Seq[(String, String)] = for {
      key <- requiredKeys
    } yield key -> {
      try {
        targetConfig.getString(key)
      } catch {
        case configError: ConfigException =>
          configError match {
            case _: ConfigException.Missing =>
              throw new IllegalArgumentException(s"Error: ${targetName} missing key ${key}.")
            case _: ConfigException.WrongType =>
              throw new IllegalArgumentException(s"Error: ${targetName}.${key} must be a string.")
          }
      }
    }
    // Coerce optional keys into strings.
    val optionalKeys = Seq("project.version")
    val optionalKeyPairs: Seq[(String, String)] = for {
      key <- optionalKeys
      valueTry: Try[String] = Try(targetConfig.getString(key))
      if valueTry.isSuccess
    } yield key -> valueTry.get
    // Final flag config map.
    (requiredKeyPairs ++ optionalKeyPairs).toMap
  }
}
