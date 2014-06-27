package org.allenai.sbt.nodejs

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
      val build = TaskKey[Seq[File]]("build", "Execution `npm run build` in the Node application directory")
      val install = TaskKey[Unit]("install", "Execution `npm install` in the Node application directory to install dependencies")
      val nodeProjectDir = SettingKey[File]("nodeProjectDir", "The directory containing the Node application")
      val environment = TaskKey[Map[String, String]]("environment", "Environment variable names and values to set for npm commands")

      // Environment variables that are set for npm commands
      val nodeProjectTarget = SettingKey[File]("nodeProjectTarget", "Target directory for Node application build")
    }
  }

  import autoImport._
  import NodeKeys._

  case object NpmMissingException extends RuntimeException("`npm` was not found on your PATH")

  val npmInstallTask = install in Npm := {
    exec("install", (nodeProjectDir in Npm).value, (environment in Npm).value)
  }

  val npmTestTask = test in Npm := {
    (install in Npm).value
    exec("run test", (nodeProjectDir in Npm).value, (environment in Npm).value)
  }

  val npmCleanTask = clean in Npm := {
    exec("run clean", (nodeProjectDir in Npm).value, (environment in Npm).value)
  }

  val npmEnvironmentTask = environment in Npm := {
    Map(
      "NODE_ENV" -> "prod",
      "NODE_API_HOST" -> "/api",
      "NODE_BUILD_DIR" -> (nodeProjectTarget in Npm).value.getAbsolutePath)
  }

  val npmBuildTask = build in Npm := {
    // make sure we install dependencies first.
    // this is necssary for building on a clean repository (e.g. Travis)
    (install in Npm).value
    exec("run build", (nodeProjectDir in Npm).value, (environment in Npm).value)
    (nodeProjectTarget in Npm).value.listFiles.toSeq
  }

  override def requires = plugins.JvmPlugin

  /** Settings for a single node project located at `root`
    * @param root  the directory containing the node project.
    */
  // TODO(markschaake): should check the `root` directory and read the `package.json` file to
  // 1) ensure it is a valid node project, and
  // 2) read in some attributes about the project.
  override lazy val projectSettings = Seq(
    nodeProjectDir in Npm := baseDirectory.value / "webclient",
    nodeProjectTarget in Npm := baseDirectory.value / "public",
    npmEnvironmentTask,
    npmTestTask,
    npmCleanTask,
    npmBuildTask,
    npmInstallTask,
    test in Test <<= (test in Test).dependsOn(test in Npm),
    cleanFiles += (nodeProjectTarget in Npm).value,
    commands += npm)

  /** Allows user to execute arbitrary npm command from the SBT console with working directory set to nodeProjectDir */
  def npm = Command.args("npm", "<command>") { (state, args) =>
    val extracted = Project.extract(state)
    // we don't care if the command fails here because we are just
    // passing through to the local `npm`
    try {
      val (newState, env) = extracted.runTask(environment in Npm, state)
      exec(args.mkString(" "), extracted.getOpt(nodeProjectDir in Npm).get, env)
      newState
    } catch {
      case NpmMissingException => state.fail
      case t: Throwable => state
    }
  }

  /** Execute `cmd` with `npm` setting `root` as the working directory */
  private def exec(cmd: String, root: File, env: Map[String, String]) = {

    val isTravis = sys.env.get("TRAVIS") match {
      case Some(_) => true
      case None => false
    }

    def npmInstalled = Process("which npm").! match {
      case 0 => true
      case x => println("`which npm` had a nonzero exit code: " + x); false
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
