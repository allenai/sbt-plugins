package org.allenai.plugins

import sbt._
import Keys._

/** Injects artifact and git information into a project.
  *
  * For this to work, you will need:
  * 1.  An organization defined that does not include hyphens.
  * 2.  An annotated tag in your current git repository.
  */
object VersionInjectorPlugin extends AutoPlugin {

  object autoImport {
    val injectVersion = TaskKey[Seq[File]](
      "injectVersion",
      "Generates the artifact.conf and git.conf version files"
    )
    val injectArtifact = TaskKey[File](
      "injectArtifact",
      "Generate the artifact.conf resource"
    )
    val injectGit = TaskKey[File](
      "injectGit",
      "Generate the git.conf resource"
    )
    val gitCommitDate = TaskKey[Long](
      "gitCommitDate",
      "The date in milliseconds of the current git commit"
    )
    val gitRemotes = TaskKey[Seq[String]](
      "gitRemotes",
      "A list of the remotes of this git repository"
    )
    val gitSha1 = TaskKey[String](
      "gitSha1",
      "The sha1 hash of the current git commit"
    )
    val gitDescribe = TaskKey[String](
      "gitDescribe",
      "The description of the current git commit"
    )
    val gitLocalSha1 = TaskKey[String](
      "gitLocalSha1",
      "Most recent commit in src directory of current project"
    )
  }

  import autoImport._

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    injectVersionTask,
    injectArtifactTask,
    injectGitTask,
    gitDescribeTask,
    gitLocalSha1Task,
    gitCommitDateTask,
    gitSha1Task,
    gitRemotesTask,
    resourceGenerators.in(Compile) += injectVersion.taskValue
  )

  private def executableName(command: String) = {
    val maybeOsName = sys.props.get("os.name").map(_.toLowerCase)
    val maybeIsWindows = maybeOsName.filter(_.contains("windows"))
    maybeIsWindows.map(_ => command + ".exe").getOrElse(command)
  }

  private def gitCommand(args: Any*) = Process(executableName("git") +: args.map(_.toString))

  // Git tasks
  val gitDescribeTask = gitDescribe := {
    (gitCommand("describe").!!).trim
  }

  val gitLocalSha1Task = gitLocalSha1 := {
    gitCommand("log", "-1", "--format=%H", baseDirectory.value).!!.trim // gets git commit of latest commit in basedir
  }

  val gitCommitDateTask =
    gitCommitDate := (gitCommand("log", "-1", "--format=%ct", "HEAD").!!).trim.toLong * 1000
  val gitSha1Task = gitSha1 := (gitCommand("rev-parse", "HEAD").!!).trim
  val gitRemotesTask = gitRemotes := {
    val remotes = gitCommand("remote").lines.toList
    remotes.map { remote =>
      (gitCommand("config", "--get", s"remote.${remote}.url").!!).trim
    }
  }

  val injectVersionTask = injectVersion := {
    Seq(injectArtifact.in(Compile).value, injectGit.in(Compile).value)
  }

  /** The directory the the git and artifact conf files go into. */
  lazy val injectedConfFilesDir: Def.Initialize[File] = Def.setting {
    resourceManaged.in(Compile).value / organization.value / cleanArtifactName(name.value)
  }

  /** Generates the given resource file if the current contents differ from the given contents. This
    * keeps the timestamp on the file from changing, which in turn makes the generated jar's
    * contents stable between builds.
    */
  def generateIfUpdated(file: File, newContents: String): Unit = {
    val currentContents = if (file.exists) {
      Some(IO.read(file))
    } else {
      None
    }

    if (currentContents != Some(newContents)) {
      IO.write(file, newContents)
    }
  }

  val injectArtifactTask = injectArtifact := {
    val artifactConfFile = injectedConfFilesDir.value / "artifact.conf"
    val artifactContents = s"""name: "${name.value}"
                              |version: "${version.value}"
                              |""".stripMargin

    generateIfUpdated(artifactConfFile, artifactContents)
    artifactConfFile
  }

  val injectGitTask = injectGit := {
    val gitConfFile = injectedConfFilesDir.value / "git.conf"

    streams.value.log.debug(s"Generating git.conf managed resource...")

    val remotesText = gitRemotes.value.mkString("\"", "\", \"", "\"")
    val gitContents = s"""sha1: "${gitSha1.value}"
                         |remotes: [$remotesText]
                         |date: "${gitCommitDate.value}"
                         |""".stripMargin

    generateIfUpdated(gitConfFile, gitContents)
    gitConfFile
  }

  // Gives us the git most recent commits for all the local projects that this project depends on
  // We have to use a dynamic task because generating tasks based on project dependencies
  // necessarily alters the task graph.

  private def cleanArtifactName(string: String) = string.replaceAll("-", "")

}
