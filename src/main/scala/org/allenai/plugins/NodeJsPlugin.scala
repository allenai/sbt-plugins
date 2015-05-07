package org.allenai.plugins

import sbt._
import sbt.Keys._
import sbt.inc.Analysis

// imports standard command parsing functionality
import complete.DefaultParsers._

/** Plugin that provides an 'npm' command and tasks for test, clean, and build.
  */
object NodeJsPlugin extends AutoPlugin {

  object autoImport {
    val Npm = ConfigKey("npm")

    object NodeKeys {
      val build = taskKey[Seq[File]]("Execution `npm run build` in the Node application directory")

      val install = taskKey[Unit](
        "Execution `npm install` in the Node application directory to install dependencies"
      )

      val nodeProjectDir = settingKey[File](
        "The directory containing the Node application"
      )

      val nodeEnv = settingKey[String]("The value to use for NODE_ENV during development builds.")

      val environment = settingKey[Map[String, String]](
        "Environment variable names and values to set for npm commands"
      )

      val nodeProjectTarget = settingKey[File]("Target directory for Node application build")
    }
  }

  import autoImport._
  import NodeKeys._

  case object NpmMissingException extends RuntimeException("`npm` was not found on your PATH")

  val npmInstallTask = install in Npm := {
    execInstall((nodeProjectDir in Npm).value, (environment in Npm).value)
  }

  val npmTestTask = test in Npm := {
    (install in Npm).value
    exec("run test", (nodeProjectDir in Npm).value, (environment in Npm).value)
  }

  val npmCleanTask = clean in Npm := {
    exec("run clean", (nodeProjectDir in Npm).value, (environment in Npm).value)
  }

  val npmEnvironmentSetting = environment in Npm := {
    getEnvironment((nodeEnv in Npm).value, (nodeProjectTarget in Npm).value)
  }

  val npmBuildTask = build in Npm := {
    execBuild((nodeProjectDir in Npm).value, (environment in Npm).value)
    (nodeProjectTarget in Npm).value.listFiles.toSeq
  }

  override def requires: Plugins = plugins.JvmPlugin

  /** Settings for a single node project located at `root`
    * @param root  the directory containing the node project.
    */
  // TODO(markschaake): should check the `root` directory and read the `package.json` file to
  // 1) ensure it is a valid node project, and
  // 2) read in some attributes about the project.
  override lazy val projectSettings: Seq[Def.Setting[_]] = Seq(
    nodeProjectDir in Npm := baseDirectory.value / "webclient",
    nodeProjectTarget in Npm := baseDirectory.value / "public",
    nodeEnv in Npm := "dev",
    npmEnvironmentSetting,
    npmTestTask,
    npmCleanTask,
    npmBuildTask,
    npmInstallTask,
    commands += npm
  )

  /** Allows user to execute arbitrary npm command from the SBT console with working directory set
    * to nodeProjectDir
    */
  def npm = Command.args("npm", "<command>") { (state, args) =>
    val extracted = Project.extract(state)
    // we don't care if the command fails here because we are just
    // passing through to the local `npm`
    try {
      val env = extracted.getOpt(environment in Npm).get
      exec(args.mkString(" "), extracted.getOpt(nodeProjectDir in Npm).get, env)
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

  /** Execute the prune and install commands with the given root + env.
    * This is used within the plugin, but exposed for other tasks to use as well.
    */
  def execInstall(root: File, env: Map[String, String]): Unit = {
    // In case node_modules have been cached from a prior build, prune out
    // any modules that we no longer use. This is important as it can cause
    // dependency conflicts during npm-install (we've seen this on Shippable, for example).
    exec("prune", root, env)
    exec("install --quiet", root, env)
  }

  /** Execute the build command with the given root + env.
    * This is used within the plugin, but exposed for other tasks to use as well.
    */
  def execBuild(root: File, env: Map[String, String]): Unit = {
    // Make sure we install dependencies prior to building.
    // This is necssary for building on a clean repository (e.g. CI server)
    execInstall(root, env)
    exec("run build", root, env)
  }

  /** Execute `cmd` with `npm` setting `root` as the working directory */
  private def exec(cmd: String, root: File, env: Map[String, String]): Unit = {

    val isTravis = sys.env.contains("TRAVIS")

    def npmInstalled: Boolean = Process("which npm").! match {
      case 0 => true
      case x =>
        println("`which npm` had a nonzero exit code: " + x)
        false
    }

    if (isTravis || npmInstalled) {
      println(s"Will execute `npm ${cmd}` in file ${root.getAbsolutePath}")
      Process(s"npm ${cmd}", root, env.toSeq: _*).! match {
        case 0 => // we're good
        case _ => throw new Exception(s"Failed process call `npm ${cmd}`")
      }
    } else {
      println("'npm' is required. Please install it and add it to your PATH.")
      throw NpmMissingException
    }
  }
}
