import sbt._
import sbt.Keys._
import sbt.inc.Analysis

// imports standard command parsing functionality
import complete.DefaultParsers._

/** Plugin that provides an 'npm' command and tasks for test, clean, and build.
  */
object NodeJsPlugin extends Plugin {

  val Npm = ConfigKey("npm")

  object NodeKeys {
    val build = TaskKey[Seq[File]]("build", "Execution `npm run build` in the Node application directory")
    val npmRoot = SettingKey[File]("npmRoot", "The directory containing the Node application")

    // Environment variables that are set for npm commands
    val buildDir = SettingKey[File]("buildDir", "Target directory for Node application build")
    val environment = SettingKey[Map[String, String]]("environment", "Environment variable names and values to set for npm commands")
  }

  case object NpmMissingException extends RuntimeException("`npm` was not found on your PATH")

  import NodeKeys._

  val npmTestTask = test in Npm := {
    exec("run test", (npmRoot in Npm).value, (environment in Npm).value)
  }

  val npmCleanTask = clean in Npm := {
    exec("run clean", (npmRoot in Npm).value, (environment in Npm).value)
  }

  val npmBuildTask = build in Npm := {
    val env = (environment in Npm).value
    val newEnv: Map[String, String] = env + ("NODE_BUILD_DIR" -> (buildDir in Npm).value.getAbsolutePath)
    exec("run build", (npmRoot in Npm).value, newEnv)
    (buildDir in Npm).value.listFiles.toSeq
  }

  /** Execute `cmd` with `npm` setting `root` as the working directory */
  def exec(cmd: String, root: File, env: Map[String, String]) = {
    Process("command -v npm >/dev/null 2>&1").! match {
      case 0 => // we're good, npm is installed
      case _ =>
        println("'npm' is required. Please install it and add it to your PATH.")
        throw NpmMissingException
    }

    Process(s"npm ${cmd}", root, env.toSeq: _*).! match {
      case 0 => // we're good
      case _ => throw new Exception("failed process call")
    }
  }

  val defaultEnvironmentVariables: Map[String, String] = Map(
    "NODE_ENV" -> "prod",
    "NODE_API_HOST" -> "/api")

  /** Settings for a single node project located at `root`
    * @param root  the directory containing the node project.
    */
  // TODO(markschaake): should check the `root` directory and read the `package.json` file to
  // 1) ensure it is a valid node project, and
  // 2) read in some attributes about the project.
  def nodeJsSettings(root: String) = Seq(
    npmRoot in Npm := file(root),
    environment in Npm := defaultEnvironmentVariables,
    npmTestTask,
    npmCleanTask,
    npmBuildTask,
    test in Test <<= (test in Test).dependsOn(test in Npm),
    clean in Compile <<= (clean in Compile).dependsOn(clean in Npm),
    resourceGenerators in Compile += (build in Npm).taskValue,
    commands += npm)

  /** Allows user to execute arbitrary npm command from the SBT console with working directory set to npmRoot */
  def npm = Command.args("npm", "<command>") { (state, args) =>
    val extracted = Project.extract(state)
    // we don't care if the command fails here because we are just
    // passing through to the local `npm`
    try {
      exec(args.mkString(" "), extracted.getOpt(npmRoot in Npm).get, extracted.getOpt(environment in Npm).get)
      state
    } catch {
      case NpmMissingException => state.fail
      case t: Throwable => state
    }
  }
}
