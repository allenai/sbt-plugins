package org.allenai.plugins

import sbt.{ AutoPlugin, Command, ConfigKey, Def, IO, Keys, Plugins, Project, ProjectRef, Task }
// Implicit conversion for '/' operator.
import sbt.Path.richFile
import sbt.plugins.JvmPlugin

import java.io.File

import scala.collection.mutable
import scala.sys.process.Process

/** Plugin that provides an 'npm' command and tasks for test, clean, and build. */
object NodeJsPlugin extends AutoPlugin {
  private val watches = mutable.Map.empty[ProjectRef, Process]
  // Set up a shutdown hook to kill any running processes when we exit.
  Runtime.getRuntime.addShutdownHook(
    new Thread(new Runnable {
      override def run(): Unit = {
        watches.synchronized {
          watches.values.foreach { _.destroy() }
          watches.clear()
        }
      }
    })
  )

  object autoImport {
    val Npm = ConfigKey("npm")

    // Valid log levels for npm command from docs at: https://docs.npmjs.com/misc/config#loglevel
    sealed abstract class NpmLogLevel(level: String) {
      override def toString: String = level
    }
    object NpmLogLevel {
      case object Silent extends NpmLogLevel("silent")
      case object Error extends NpmLogLevel("error")
      case object Warn extends NpmLogLevel("warn")
      case object Http extends NpmLogLevel("http")
      case object Info extends NpmLogLevel("info")
      case object Verbose extends NpmLogLevel("verbose")
      case object Silly extends NpmLogLevel("silly")
    }

    object NodeKeys {
      val build = Def.taskKey[Seq[File]](
        "Executes the build script(s) with `npm run` in the Node application directory"
      )

      val install = Def.taskKey[Unit](
        "Executes `npm install` in the Node application directory to install dependencies"
      )

      // Note that this isn't called 'watch' because there's another SBT key with this name and a
      // different value.
      val nwatch = Def.taskKey[Unit]("Executes `npm watch` in the Node directory")

      val unwatch = Def.taskKey[Unit]("Kills any background watch in this project")

      val nodeProjectDir = Def.settingKey[File](
        "The directory containing the Node application"
      )

      val buildScripts = Def.settingKey[Seq[String]](
        "Build scripts defined in `package.json` to be executed by npm:build task"
      )

      val nodeEnv =
        Def.settingKey[String]("The value to use for NODE_ENV during development builds.")

      val environment = Def.settingKey[Map[String, String]](
        "Environment variable names and values to set for npm commands"
      )

      val nodeProjectTarget = Def.settingKey[File]("Target directory for Node application build")

      val npmLogLevel = Def.settingKey[NpmLogLevel]("Log level for npm commands.")
    }
  }

  import autoImport._
  import autoImport.NodeKeys._

  case object NpmMissingException extends RuntimeException("`npm` was not found on your PATH")

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Common settings / tasks / utilities used across tasks.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** Task def that builds and returns the node target directory. */
  lazy val makeNodeTargetDef: Def.Initialize[Task[File]] = Def.task {
    val targetDir = nodeProjectTarget.in(Npm).value
    if (!targetDir.exists) {
      targetDir.mkdir()
    }
    targetDir
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Definitions for plugin tasks.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** Task to install any node dependencies that are missing. */
  lazy val installDef: Def.Initialize[Task[Unit]] = Def.task {
    execInstall(
      nodeProjectDir.in(Npm).value,
      Keys.target.value,
      environment.in(Npm).value,
      npmLogLevel.in(Npm).value
    )
  }

  val npmTestTask = Keys.test.in(Npm) := {
    install.in(Npm).value
    exec(
      "run test",
      nodeProjectDir.in(Npm).value,
      environment.in(Npm).value,
      npmLogLevel.in(Npm).value
    )
  }

  val npmCleanTask = Keys.clean.in(Npm) := {
    exec(
      "run clean",
      nodeProjectDir.in(Npm).value,
      environment.in(Npm).value,
      npmLogLevel.in(Npm).value
    )
  }

  val npmEnvironmentSetting = environment.in(Npm) := {
    getEnvironment(nodeEnv.in(Npm).value, nodeProjectTarget.in(Npm).value)
  }

  val npmBuildTask = build.in(Npm) := {
    // Ensure that the target dir is created.
    val targetDir = makeNodeTargetDef.value

    execBuild(
      nodeProjectDir.in(Npm).value,
      Keys.target.value,
      buildScripts.in(Npm).value,
      environment.in(Npm).value,
      npmLogLevel.in(Npm).value
    )
    targetDir.listFiles.toSeq
  }

  val npmWatchTask = nwatch.in(Npm) := {
    val logger = Keys.streams.value.log

    // Require that the target directory be created.
    makeNodeTargetDef.value

    val projectRef = Keys.thisProjectRef.value
    val projectDirectory = nodeProjectDir.in(Npm).value
    val npmEnvironment = environment.in(Npm).value
    val logLevel = npmLogLevel.in(Npm).value

    // Check for a running process, and start one if it doesn't exist.
    val newProcessOption = watches.synchronized {
      if (!watches.contains(projectRef)) {
        execInstall(projectDirectory, Keys.target.value, npmEnvironment, logLevel)
        val process = fork("run watch", projectDirectory, npmEnvironment)
        watches(projectRef) = process
        Some(process)
      } else {
        logger.info("npm watch already running")
        None
      }
    }
  }

  val npmUnwatchTask = unwatch.in(Npm) := {
    val logger = Keys.streams.value.log
    val processOption = watches.synchronized {
      watches.remove(Keys.thisProjectRef.value)
    }
    processOption map { process =>
      logger.info("Killing background 'npm watch' . . .")
      process.destroy()
    }
  }

  override def requires: Plugins = JvmPlugin

  // TODO(markschaake): should check the `root` directory and read the `package.json` file to
  // 1) ensure it is a valid node project, and
  // 2) read in some attributes about the project.
  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    nodeProjectDir.in(Npm) := Keys.baseDirectory.value / "webapp",
    nodeProjectTarget.in(Npm) := Keys.baseDirectory.value / "public",
    buildScripts.in(Npm) := Seq("build"),
    nodeEnv.in(Npm) := "dev",
    npmLogLevel.in(Npm) := NpmLogLevel.Warn,
    environment.in(Npm) := {
      getEnvironment(nodeEnv.in(Npm).value, nodeProjectTarget.in(Npm).value)
    },
    npmTestTask,
    npmCleanTask,
    npmBuildTask,
    install.in(Npm) := installDef.value,
    npmWatchTask,
    npmUnwatchTask,
    Keys.commands += npm
  )

  /** Allows user to execute arbitrary npm command from the SBT console with working directory set
    * to nodeProjectDir
    */
  def npm = Command.args("npm", "<command>") { (state, args) =>
    val extracted = Project.extract(state)
    // we don't care if the command fails here because we are just
    // passing through to the local `npm`
    try {
      val env = extracted.getOpt(environment.in(Npm)).get
      val logLev = extracted.getOpt(npmLogLevel.in(Npm)).get
      exec(args.mkString(" "), extracted.getOpt(nodeProjectDir.in(Npm)).get, env, logLev)
      state
    } catch {
      case NpmMissingException => state.fail
      case t: Throwable => state
    }
  }

  /** @returns the node build environment variables, with the given NODE_ENV and NODE_BUILD_DIR set
    */
  def getEnvironment(nodeEnv: String, buildDir: File): Map[String, String] = {
    Map(
      "NODE_ENV" -> nodeEnv,
      "NODE_API_HOST" -> "/api",
      "NODE_BUILD_DIR" -> buildDir.getAbsolutePath
    )
  }

  /** Execute the prune and install commands with the given root + env, if project dependencies have
    * changed since the last time this was run.
    * This is used within the plugin, but exposed for other tasks to use as well.
    * Note: The npm execution synchronized to prevent weird npm concurrency issues that we've
    * experienced in CI when there are multiple subprojects executing `npm install` in parallel.
    */
  def execInstall(
    nodeProjectDirectory: File,
    sbtTargetDirectory: File,
    env: Map[String, String],
    npmLogLevel: NpmLogLevel
  ): Unit = {
    // Check to see if any dependencies have changed.
    val packageJson = nodeProjectDirectory / "package.json"
    val currentDependenciesHash = Utilities.hashFiles(Seq(packageJson), nodeProjectDirectory)
    val hashFile = sbtTargetDirectory / "node" / "packages.sha1"
    val previousDependenciesHash = if (hashFile.exists) {
      IO.read(hashFile)
    } else {
      ""
    }

    // Run `npm install` if any dependencies have changed.
    if (currentDependenciesHash != previousDependenciesHash) {
      this.synchronized {
        // In case node_modules have been cached from a prior build, prune out
        // any modules that we no longer use. This is important as it can cause
        // dependency conflicts during npm-install (we've seen this on Shippable, for example).
        exec("prune", nodeProjectDirectory, env, npmLogLevel)
        exec("install", nodeProjectDirectory, env, npmLogLevel)
      }
      IO.write(hashFile, currentDependenciesHash)
    }
  }

  /** Execute the build script(s) with the given root + env.
    * This is used within the plugin, but exposed for other tasks to use as well.
    */
  def execBuild(
    nodeProjectDirectory: File,
    sbtTargetDirectory: File,
    scripts: Seq[String],
    env: Map[String, String],
    npmLogLevel: NpmLogLevel
  ): Unit = {
    // Make sure we install dependencies prior to building.
    // This is necssary for building on a clean repository (e.g. CI server)
    execInstall(nodeProjectDirectory, sbtTargetDirectory, env, npmLogLevel)
    scripts foreach { script =>
      exec(s"run $script", nodeProjectDirectory, env, npmLogLevel)
    }
  }

  // TODO(jkinkead): Make the below take logs instead of prinlning.

  /** Forks and executes `cmd` with `npm` setting `root` as the working directory, returning the
    * resulting process.
    */
  private def fork(cmd: String, root: File, env: Map[String, String]): Process = {
    val isTravis = sys.env.contains("TRAVIS")

    def npmInstalled: Boolean = Process("which npm").! match {
      case 0 => true
      case x =>
        println("`which npm` had a nonzero exit code: " + x)
        false
    }

    if (isTravis || npmInstalled) {
      println(s"Will execute `npm ${cmd}` in file ${root.getAbsolutePath}")
      Process(s"npm ${cmd}", root, env.toSeq: _*).run()
    } else {
      println("'npm' is required. Please install it and add it to your PATH.")
      throw NpmMissingException
    }
  }

  /** Execute `cmd` with `npm` setting `root` as the working directory.
    * @throws Exception if the process returns an non-zero exit code
    */
  private def exec(cmd: String, root: File, env: Map[String, String], npmLogLevel: NpmLogLevel): Unit = {
    fork(s"$cmd --loglevel ${npmLogLevel}", root, env).exitValue() match {
      case 0 => // we're good
      case exitCode => throw new Exception(s"Failed process call `npm ${cmd}`. Exit code: $exitCode")
    }
  }
}
