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
    val injectCacheKey = TaskKey[File](
      "injectCacheKey",
      "Generate the cacheKey.conf resource"
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
    val cacheKey = TaskKey[String](
      "cacheKey",
      "CacheKey for current project - changes on new commits to src directory and dependency changes"
    )
  }

  import autoImport._

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    injectVersionTask,
    injectArtifactTask,
    injectCacheKeyTask,
    injectGitTask,
    gitDescribeTask,
    gitLocalSha1Task,
    gitCommitDateTask,
    gitSha1Task,
    gitRemotesTask,
    cacheKeyTask,
    resourceGenerators in Compile <+= injectVersion
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

  def gitMostRecentCommit(path: File): String = {
    gitCommand("log", "-1", "--format=%H", path).!!.trim
  }

  val gitLocalSha1Task = gitLocalSha1 := {
    gitMostRecentCommit(baseDirectory.value)
  }

  val gitCommitDateTask =
    gitCommitDate := (gitCommand("log", "-1", "--format=%ct", "HEAD").!!).trim.toLong * 1000
  val gitSha1Task = gitSha1 := (gitCommand("rev-parse", "HEAD").!!).trim
  val gitRemotesTask = gitRemotes := {
    val remotes = gitCommand("remote").lines.toList
    for (remote <- remotes) yield {
      val url = (gitCommand("config", "--get", s"remote.${remote}.url").!!).trim
      url
    }
  }

  val injectVersionTask = injectVersion <<= (injectArtifact in Compile, injectGit in Compile) map {
    (artifactFile, gitFile) => Seq(artifactFile, gitFile)
  }

  val injectArtifactTask =
    injectArtifact <<= (resourceManaged in Compile, organization, name, version, streams) map {
      (resourceManaged, org, name, version, s) =>
        val artifactConfFile = resourceManaged / org / cleanArtifactName(name) / "artifact.conf"
        val artifactContents =
          "name: \"" + name + "\"\n" +
            "version: \"" + version + "\""

        s.log.info(s"Generating artifact.conf managed resource... (name|version: $name|$version)")

        IO.write(artifactConfFile, artifactContents)
        artifactConfFile
    }

  val injectGitTask =
    injectGit <<= (resourceManaged in Compile, organization, name, version, streams, gitRemotes, gitSha1, gitCommitDate) map { // scalastyle:ignore
      (resourceManaged, org, name, version, s, remotes, sha1, date) =>
        val gitConfFile = resourceManaged / org / cleanArtifactName(name) / "git.conf"

        s.log.info(s"Generating git.conf managed resource... (sha1: ${sha1})")

        def quote(s: String) = "\"" + s + "\""
        val gitContents =
          "sha1: " + quote(sha1) + "\n" +
            "remotes: " + (remotes map quote).mkString("[", ", ", "]") + "\n" +
            "date: " + quote(date.toString)
        IO.write(gitConfFile, gitContents)
        gitConfFile
    }

  val injectCacheKeyTask = injectCacheKey := {
    val values = ((resourceManaged in Compile).value.absolutePath, organization.value, name.value, streams.value, cacheKey.value)
    val (resourceManagedPath, org, nameValue, s, cacheKeyResult) = values
    val cacheKeyConfFile = new java.io.File(s"$resourceManagedPath/$org/${cleanArtifactName(nameValue)}/cacheKey.conf")
    s.log.info(s"Generating cacheKey.conf managed resource... (cacheKey: $cacheKeyResult)")

    val cacheKeyContents = s"""cacheKey: "$cacheKeyResult""""
    IO.write(cacheKeyConfFile, cacheKeyContents)
    cacheKeyConfFile
  }

  //Gives us the git most recent commits for all the local projects that this project depends on
  lazy val gitMRCs = Def.taskDyn { // We have to use a dynamic task because generating tasks based on project dependencies
    // necessarily alters the task graph
    val MRCs = buildDependencies.value.classpathRefs(thisProjectRef.value).sorted // get the local dependencies
    val filter = ScopeFilter(inProjects(MRCs: _*)) // this is weird, we create a scopefilter on the dependencies
    gitLocalSha1.all(filter) // odd piece of syntax - returns a list of tasks (we're inside a taskDyn block) that is
    //applying gitLocalSHa1 to all scopes in the scopefilter 'filter'
  }

  val cacheKeyTask = cacheKey := {
    import java.io.{ File => JFile }

    def getFileList(dir: String): Seq[JFile] = new JFile(dir) match {
      case f if f.exists && f.isDirectory => f.listFiles.filter(_.isFile).toSeq
      case _ => Seq.empty
    }
    val stageDir = baseDirectory.value + "/target/universal/stage"
    val allFiles: Seq[JFile] = Seq("bin", "conf", "lib", "public")
      .map { dir => stageDir + "/" + dir }
      .flatMap(getFileList)

    val filesToHash = allFiles filterNot { f: JFile =>
      //TODO: make this a setting
      val fileName = f.getName
      fileName.startsWith("org.allenai.ari-api") ||
        fileName.startsWith("org.allenai.solvers-") ||
        fileName.startsWith("org.allenai.ari-solvers-")
    }
    val hashes = filesToHash.sorted
      .map(Hash.apply)
      .map(Hash.toHex)
    Hash.toHex(Hash((hashes ++ gitMRCs.value :+ gitLocalSha1.value).sorted.mkString))
  }

  private def cleanArtifactName(string: String) = string.replaceAll("-", "")

}
