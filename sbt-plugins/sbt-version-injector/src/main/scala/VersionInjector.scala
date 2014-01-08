import sbt._
import Keys._

/** Injects artifact and git information into a project.
  *
  * For this to work, you will need:
  * 1.  An organization defined that does not include hyphens.
  * 2.  An annotated tag in your current git repository.
  */
object VersionInjector {

  def settings: Seq[Setting[_]] = Seq(
    injectVersionTask,
    injectArtifactTask,
    injectGitTask,
    resourceGenerators in Compile <+= injectVersion)

  val injectArtifact = TaskKey[File]("injectArtifact", "Generate the artifact.conf resource")
  val injectGit = TaskKey[File]("injectGit", "Generate the git.conf resource")

  /** injectVersion Aggregates the two resource generating tasks */
  val injectVersion = TaskKey[Seq[File]]("injectVersion")
  val injectVersionTask = injectVersion <<= (injectArtifact in Compile, injectGit in Compile) map { (artifactFile, gitFile) =>
    Seq(artifactFile, gitFile)
  }

  val injectArtifactTask = injectArtifact <<= (resourceManaged in Compile, organization, name, version, streams) map { (resourceManaged, org, name, version, s) =>
    val artifactConfFile = resourceManaged / org / cleanArtifactName(name) / "artifact.conf"
    val artifactContents =
      "name: \"" + name + "\"\n" +
        "version: \"" + version + "\""

    s.log.info(s"Generating artifact.conf managed resource... (name|version: $name|$version")

    IO.write(artifactConfFile, artifactContents)
    artifactConfFile
  }

  val injectGitTask = injectGit <<= (resourceManaged in Compile, organization, name, version, streams) map { (resourceManaged, org, name, version, s) =>
    val gitConfFile = resourceManaged / org / cleanArtifactName(name) / "git.conf"

    val commandName = "git"
    val exec = executableName(commandName)

    def cmd(args: Any*): ProcessBuilder = Process(exec +: args.map(_.toString))

    /** Run `git describe`
      *
      * This will fail if there are no annotated tags.
      */
    lazy val describe = (cmd("describe").!!).trim

    /** The SHA1 on the HEAD revision. */
    def sha1() = (cmd("rev-parse", "HEAD").!!).trim

    s.log.info(s"Generating git.conf managed resource... (describe: ${describe}")

    val gitContents =
      "describe: \"" + describe + "\"\n" +
        "sha1: \"" + sha1() + "\""
    IO.write(gitConfFile, gitContents)
    gitConfFile
  }

  private def executableName(command: String) = {
    val maybeOsName = sys.props.get("os.name").map(_.toLowerCase)
    val maybeIsWindows = maybeOsName.filter(_.contains("windows"))
    maybeIsWindows.map(_ => command + ".exe").getOrElse(command)
  }

  private def cleanArtifactName(string: String) = string.replaceAll("-", "")

}
