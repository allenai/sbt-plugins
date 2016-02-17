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

    val filterNotCacheKeyGenFileNames = settingKey[Seq[String]]("starts of jars in stage you don't want hashed for cachekey")

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
    if (envConfFile.exists) {
      log.info(s"Copying config for $deployEnv . . .")
      val destConfFile = new File(universalStagingDir, "conf/env.conf")
      IO.copyFile(envConfFile, destConfFile)
    } else {
      log.info("")
      log.info(s"WARNING: Could not find config file '$deployEnv.conf'!")
      log.info("")
      log.info("Press ENTER to continue with no environment configuration, CTRL-C to abort.")
      log.info("")
      System.console.readLine()
    }

    val deploys: Seq[Future[Try[Unit]]] = deployConfig.hostConfigs map { hostConfig =>
      Future {
        // Run commands within a Try so we can fold over any errors.
        Try {
          // Command to pass to rsync's "rsh" flag, and to use as the base of our ssh
          // operations.
          val sshCommand = Seq("ssh", "-l", hostConfig.sshUser)
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
          log.info("Running " + quotedRsync + " . . .")
          if (Process(rsyncCommand).! != 0) {
            throw new IllegalArgumentException(s"Error running rsync to host '$deployHost'.")
          }

          // Now, ssh to the remote host and run the restart script.
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
          log.info("Running " + restartCommand.mkString(" ") + " . . .")
          if (Process(restartCommand).! != 0) {
            throw new IllegalArgumentException(s"Error running restart command on host '$deployHost'.")
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
        throw new IllegalArgumentException(
          "Hit the following errors during deployment:" +
            (errorSeq map { _.getMessage } mkString ("\n", "\n", "\n"))
        )
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

  /** Container for config needed to deploy a project to a host.
    * @param host The fully-qualified name of the host the project should be deployed to
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
    * @param projectVersion the version of the project being deployed
    */
  case class DeployConfig(hostConfigs: Seq[HostConfig], projectVersion: Option[String])

  /** Transform the given [[com.typesafe.config.Config]] object into a corresponding
    * [[org.allenai.plugins.DeployPlugin.DeployConfig]] object.
    * @param targetName the name of the target to print on error
    * @param targetConfig env-level deployment config to convert
    */
  def parseConfig(targetName: String, targetConfig: Config): DeployConfig = {

    DeployConfig(
      hostConfigs = Seq(parseHostConfig(targetConfig.getConfig("deploy"))),
      projectVersion = try {
        Some(targetConfig.getString("project.version"))
      } catch {
        case _: ConfigException.Missing => None
        case _: ConfigException.WrongType =>
          throw new IllegalArgumentException(s"Error: 'project.version' must be a string")
      }
    )
  }

  /** Transform the given [[com.typesafe.config.Config]] object into a corresponding
    * [[org.allenai.plugins.DeployPlugin.HostConfig]] object.
    * @param hostConfig host-level config to convert
    */
  def parseHostConfig(hostConfig: Config): HostConfig = {
    val renderedConfig = hostConfig.root().render()

    val requiredKeys = Seq("host", "directory", "startup_script", "user.ssh_username")

    val confMap = (requiredKeys map { key =>
      val value = try {
        hostConfig.getString(key)
      } catch {
        case _: ConfigException.Missing =>
          throw new IllegalArgumentException(s"Error: $renderedConfig missing key '$key'.")
        case _: ConfigException.WrongType =>
          throw new IllegalArgumentException(s"Error: '$key' must be a string in $renderedConfig")
      }

      key -> value
    }).toMap

    val startArgs = try {
      hostConfig.getStringList("startup_args").asScala
    } catch {
      case _: ConfigException.Missing => Seq()
      case _: ConfigException.WrongType =>
        throw new IllegalArgumentException(
          s"Error: 'startup_args' must be an array of strings in $renderedConfig"
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
