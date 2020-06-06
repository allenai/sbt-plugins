package org.allenai.plugins

import sbt.{
  AutoPlugin,
  ConsoleLogger,
  Def,
  Hash,
  IO,
  InputKey,
  InputTask,
  Keys,
  Logger,
  Path,
  PathFinder,
  Plugins,
  SettingKey,
  Task,
  TaskKey
}
import sbt.complete.DefaultParsers
import sbt.plugins.JvmPlugin

import java.io.File
import java.lang.Runtime
import java.util.UUID

import scala.collection.mutable
import scala.sys.process.Process

/** Plugin for building docker images. */
object DockerBuildPlugin extends AutoPlugin {

  /** The default Docker registry used for making image names. Must be overridden to push images.
    * Used as the default value for the dockerImageRegistryHost setting.
    */
  val DEFAULT_REGISTRY = "allenai-sbt-plugins-default-FAKE-registry.allenai.org"

  /** The default value for the dockerImageBase setting. */
  val DEFAULT_BASE_IMAGE = DEFAULT_REGISTRY + "/oracle-java:8"

  /** The name of the startup script, located in this class's resources. This will also be the name
    * of the script in the `bin` directory in the generated image.
    */
  val STARTUP_SCRIPT_NAME = "run-docker.sh"

  /** The string (one full line) that separates the autogenerated Dockerfile contents from any
    * user-provided additions.
    */
  val DOCKERFILE_SIGIL = "#+" * 50

  /** Extractor pattern matching the registry portion of a docker image name. */
  val IMAGE_REGISTRY = "^([^/]+)/".r.unanchored

  /** Set of names of currently-running docker containers. Access is synchronized on
    * `runningContainers`.
    */
  private val runningContainers = mutable.Set.empty[String]
  // Set up a shutdown hook to stop any running containers when we exit.
  Runtime.getRuntime.addShutdownHook(
    new Thread(new Runnable {

      override def run(): Unit = {
        runningContainers.synchronized {
          runningContainers.foreach { container =>
            stopContainer(container, ConsoleLogger(System.out))
          }
          runningContainers.clear()
        }
      }
    })
  )

  /** Requires the JvmPlugin, since this will be building a jar dependency tree. */
  override def requires: Plugins = JvmPlugin

  object autoImport {
    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The following settings affect both the images and Dockerfiles generated by this plugin. When
    // you update these settings in a build.sbt file, you'll want to re-generate your Dockerfiles.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    val dockerfileLocation: SettingKey[File] = Def.settingKey[File](
      "The location of the Dockerfile to use in building the main project image. Defaults to " +
        "`srcDirectory.value + \"docker/Dockerfile\"`, typically \"src/main/docker/Dockerfile\"."
    )

    // The following three settings control how the generated image is tagged. The image portion of
    // image tags will be, for the main image:
    //   ${dockerImageRegistryHost}/${dockerImageNamePrefix}/${dockerImageName}
    // and for the dependency image will be:
    //   ${dockerImageRegistryHost}/${dockerImageNamePrefix}/${dockerImageName}-dependency
    //
    // See the documentation for details on which tags will be used by `dockerBuild` and
    // `dockerPush`.
    val dockerImageRegistryHost: SettingKey[String] = Def.settingKey[String](
      "The base name of the image you're creating. Defaults to " + DEFAULT_REGISTRY + "."
    )

    val dockerImageNamePrefix: SettingKey[String] = Def.settingKey[String](
      "The image name prefix (\"repository\", in Docker terms) of the image you're creating. " +
        "Defaults to organization.value.stripPrefix(\"org.allenai.\") . " +
        "This is typically the github repository name."
    )

    val dockerImageName: SettingKey[String] = Def.settingKey[String](
      "The name of the image you're creating. Defaults to the sbt project name (the `name` " +
        "setting key)."
    )

    val dockerImageBase: SettingKey[String] = Def.settingKey[String](
      "The base image to use when creating your image. Defaults to " + DEFAULT_BASE_IMAGE + "."
    )

    val dockerDependencyExtra: SettingKey[Seq[String]] = Def.settingKey[Seq[String]](
      "Extra lines to add to the dependency Dockerfile. These will be interpreted directly into " +
        "the dependency Dockerfile, immediately before the start script and library jars COPY " +
        "commands. Defaults to Seq.empty[String]."
    )

    val dockerCopyMappings: SettingKey[Seq[(File, String)]] = Def.settingKey[Seq[(File, String)]](
      "Mappings to add to the Docker image. Relative file paths will be interpreted as being " +
        "relative to the base directory (`baseDirectory.value`). See " +
        "https://www.scala-sbt.org/0.12.3/docs/Detailed-Topics/Mapping-Files.html for detailed " +
        "info on sbt mappings. Defaults to mapping src/main/resources to conf on the image."
    )

    val dockerPorts: SettingKey[Seq[Int]] = Def.settingKey[Seq[Int]](
      "The value(s) to use for EXPOSE when generating your Dockerfile. Defaults to `Seq.empty`."
    )

    val dockerPortMappings: SettingKey[Seq[(Int, Int)]] = Def.settingKey[Seq[(Int, Int)]](
      "The port mapping(s) to use when running your docker image via `dockerRun`, as " +
        "(hostPort, containerPort). Defaults to mapping all of the values `dockerPorts.value` " +
        "to themselves (identity mapping)."
    )

    val dockerMainArgs: SettingKey[Seq[String]] = Def.settingKey[Seq[String]](
      "The value to use for CMD in order to pass default arguments to your application. Defaults " +
        "to `Seq.empty`."
    )

    val dockerWorkdir: SettingKey[String] = Def.settingKey[String](
      "The value to use for WORKDIR when generating your Dockerfile. Defaults to \"/stage\"."
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The following settings affect how the plugin behaves, but don't affect file or image output.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    val verifyDockerfileIsStrict: SettingKey[Boolean] = Def.settingKey[Boolean](
      "If true, the `verifyDockerfile` task will raise an error if the current Dockerfile does " +
        "not match the generated one. Defaults to true."
    )

    val verifyDockerfileOnBuild: SettingKey[Boolean] = Def.settingKey[Boolean](
      "If true, `dockerBuild` will depend on `verifyDockerfile`, and will not build if the " +
        "Dockerfile is not up-to-date. Defaults to false."
    )

    val dockerRunFlags: SettingKey[Seq[String]] = Def.settingKey[Seq[String]](
      "Any commandline flags to pass to docker when using the `dockerRun` task. Defaults to an " +
        "empty Seq."
    )

    val skipEcrLogin: SettingKey[Boolean] = Def.settingKey[Boolean](
      "If set, don't ever log in to Amazon ECR, even if images look like they require it. " +
        "Defaults to false."
    )

    ////////////////////////////////////////////////////////////////////////////////////////////////
    // The following keys are for generating dockerfiles; and staging, building, running, and
    // pushing images. These should not be overridden from the defaults unless you know what you're
    // doing.
    ////////////////////////////////////////////////////////////////////////////////////////////////

    val generateDockerfile: TaskKey[Unit] = Def.taskKey[Unit](
      "Generates a Dockerfile for the main project image at the location pointed to by " +
        "`dockerfileLocation`."
    )

    val verifyDockerfile: TaskKey[Boolean] = Def.taskKey[Boolean](
      "Checks if the Dockerfile that would be generated by `generateDockerfile` is the same as " +
        "the Dockerfile at `dockerfileLocation`. This will raise an error if " +
        "`verifyDockerfileIsStrict` is true, and print a warning otherwise."
    )

    val dockerDependencyStage: TaskKey[File] = Def.taskKey[File](
      "Builds a staged directory under target/docker/dependencies containing project " +
        "dependencies. This will include a generated Dockerfile. This returns the staging " +
        "directory location."
    )

    val dockerMainStage: TaskKey[File] = Def.taskKey[File](
      "Builds a staged directory under target/docker/main containing the staged project, minus " +
        "dependencies. If a Dockerfile is present in `dockerfileLocation.value`, it will be " +
        "placed in the staging directory. This returns the staging directory location."
    )

    val dockerDependencyBuild: TaskKey[String] = Def.taskKey[String](
      "Builds the dependency image for project, returning the image ID with unique tag. This is " +
        "not manually run as part of a normal workflow, but can be useful for debugging."
    )

    val dockerBuild: TaskKey[String] = Def.taskKey[String](
      "Builds a docker image for this project, returning the image ID with unique tag. This " +
        "requires that a Dockerfile exist at `dockerfileLocation.value`."
    )

    val dockerRun: TaskKey[Unit] = Def.taskKey[Unit](
      "Builds a docker image for this project, then runs it locally in a container."
    )

    val dockerStop: TaskKey[Unit] = Def.taskKey[Unit](
      "Stops any currently-running docker container for this project."
    )

    val dockerPush: InputKey[Unit] = Def.inputKey[Unit](
      "Pushes the project's docker image. This task accepts tags as arguments, which will be " +
        "applied to the image locally and pushed. If no tags are provided, the SHA-1 tag will be " +
        "pushed instead."
    )
  }
  import autoImport._

  /** The default copy mapping, set to copy src/main/resources to conf in the docker image. */
  lazy val defaultCopyMappings = Def.setting {
    // TODO(jkinkead): Update this to use src/main/conf instead of src/main/resources, since the
    // `resources` directory is a special-use directory for files bundled into jars.
    Seq((new File(sourceMain.value, "resources"), "conf"))
  }

  /** The image name minus the repository host. */
  lazy val mainImageNameSuffix: Def.Initialize[String] = Def.setting {
    if (dockerImageNamePrefix.value.nonEmpty) {
      dockerImageNamePrefix.value + '/' + dockerImageName.value
    } else {
      dockerImageName.value
    }
  }

  /** The full image name, derived from the user-provided settings. */
  lazy val mainImageName: Def.Initialize[String] = Def.setting {
    dockerImageRegistryHost.value + '/' + mainImageNameSuffix.value
  }

  /** The full name of the dependency image. */
  lazy val dependencyImageName: Def.Initialize[String] = Def.setting {
    mainImageName.value + "-dependencies"
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Common settings / tasks / utilities used across tasks.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** Logs in to Amazon ECR, using `aws ecr get-login`, if the given image is an ECR-hosted image.
    */
  def loginIfEcrImage(imageName: String, logger: Logger): Unit = {
    imageName match {
      case IMAGE_REGISTRY(registry) if registry.endsWith(".amazonaws.com") =>
        logger.info("Logging in to ECR...")
        val command = Seq("bash", "-c", "eval $(aws ecr get-login)")
        val result = Process(command).!(Utilities.NIL_PROCESS_LOGGER)
        if (result != 0) {
          logger.warn("Failure logging in to ECR.")
        }
      case _ => // No-op.
    }
  }

  /** Builds a docker image in the given directory. Before building, hash the contents, and check to
    * see if the hash is different from what's in the given hash file. If the contents are
    * unchanged, docker is not invoked. If the contents have checked, the old image is untagged, and
    * the new image is built with the tags "latest" and a tag with the contents hash.
    * @return the full image name, with the content hash tag included
    */
  def buildImageIfUpdated(
    imageDir: File,
    imageName: String,
    hashFile: File,
    logger: Logger
  ): String = {
    // Calculate the checksum of the contents of the main image.
    val allFiles = PathFinder(imageDir).allPaths.filter(_.isFile).get
    val newHash = Utilities.hashFiles(allFiles, imageDir)

    val oldHash = if (hashFile.exists) {
      IO.read(hashFile)
    } else {
      ""
    }

    val imageNameWithLabel = imageName + ':' + newHash

    // Invalidate the hash if the image isn't saved, for any reason.
    val imageMissing = {
      val inspect = Seq("docker", "inspect", "--type", "image", imageName, imageNameWithLabel)
      Process(inspect).!(Utilities.NIL_PROCESS_LOGGER) != 0
    }

    if (newHash != oldHash || imageMissing) {
      // Build a new docker image.
      val buildCommand =
        Seq("docker", "build", "-t", imageName, "-t", imageNameWithLabel, imageDir.toString)
      logger.info("Building image...")
      val exitCode = Process(buildCommand).!
      if (exitCode != 0) {
        sys.error("Error running " + buildCommand.mkString(" "))
      }

      if (oldHash != "" && oldHash != newHash) {
        logger.info("Removing stale image...")
        // Remove the old image label. Note that we ignore any errors - we don't care if the image
        // doesn't exist or if the remove fails.
        val untagCommand = Seq("docker", "rmi", imageName + ':' + oldHash)
        Process(untagCommand).!
      }

      // Write out the hash file.
      IO.write(hashFile, newHash)
    } else {
      logger.info("Image unchanged.")
    }

    imageNameWithLabel
  }

  /** Stops the given docker container. This will first rename the container to a UUID, then run
    * `docker stop` against the new name. The rename is done to avoid race conditions in the docker
    * daemon: It doesn't clean up the name of a stopped container before `docker stop` exits, so
    * immediately restarting the container results in an error.
    */
  def stopContainer(container: String, logger: Logger): Unit = {
    logger.info(s"Stopping $container...")
    val newName = container + '-' + UUID.randomUUID.toString
    Process(Seq("docker", "rename", container, newName)).!(Utilities.NIL_PROCESS_LOGGER)
    Process(Seq("docker", "stop", newName)).!(Utilities.NIL_PROCESS_LOGGER)
  }

  /** Task initializer to return the git commit for the current project, if the git repo is present
    * and clean. This is used for cache key generation.
    */
  lazy val gitCommitIfClean: Def.Initialize[Task[Option[String]]] = Def.task {
    HelperDefs.gitRepoCleanDef.value match {
      // TODO(jkinkead): Move gitLocalSha1 to a common location.
      case None    => Some(VersionInjectorPlugin.autoImport.gitLocalSha1.value)
      case Some(_) => None
    }
  }

  /** The src/main directory. This does not appear to be a setting key in sbt. */
  lazy val sourceMain: Def.Initialize[File] = Def.setting {
    new File(Keys.sourceDirectory.value, "main")
  }

  /** The location of the docker target directory containing all output for the docker plugin. */
  lazy val dockerTargetDir: Def.Initialize[File] = Def.setting {
    new File(Keys.target.value, "docker")
  }

  /** The location of the staged dependency image. */
  lazy val dependencyImageDir: Def.Initialize[File] = Def.setting {
    new File(dockerTargetDir.value, "dependencies")
  }

  /** The location of the built dependency image's hash file. */
  lazy val dependencyHashFile: Def.Initialize[File] = Def.setting {
    new File(dockerTargetDir.value, "dependencies.sha1")
  }

  /** The location of the staged main image. */
  lazy val mainImageDir: Def.Initialize[File] = Def.setting {
    new File(dockerTargetDir.value, "main")
  }

  /** Task which requires that `docker` exists on the commandline path. */
  lazy val requireDocker: Def.Initialize[Task[Unit]] = Def.task {
    if (Process(Seq("which", "docker")).!(Utilities.NIL_PROCESS_LOGGER) != 0) {
      sys.error("`docker` not found on path. Please install the docker client.")
    }
  }

  /** Task to read the current text of the main Dockerfile below the sigil line. This returns None
    * if and only if the file exists, but the sigil line cannot be found, since this is considered
    * a potential user error.
    */
  lazy val customDockerfileContents: Def.Initialize[Task[Option[String]]] = Def.task {
    val dockerfile = dockerfileLocation.value
    if (dockerfile.exists) {
      val lines = IO.readLines(dockerfile)
      val remainder = lines.dropWhile(_ != DOCKERFILE_SIGIL)
      if (remainder.nonEmpty) {
        Some(remainder.tail.mkString("\n") + "\n")
      } else {
        None
      }
    } else {
      Some("")
    }
  }

  /** Task to build dockerfile contents using the current project's sbt settings and return it. */
  lazy val generatedDockerfileContents: Def.Initialize[Task[String]] = Def.task {
    val logger = Keys.streams.value.log

    // Create the copy commands.
    val copyText = dockerCopyMappings.value
      .map {
        case (_, destination) => s"COPY $destination $destination"
      }
      .mkString("\n")
    // Create the sbt setting to recreate the copy mappings.
    val dockerCopyMappingsText = {
      // Generate the tuples.
      val tupleValues = dockerCopyMappings.value
        .map {
          case (file, destination) =>
            val basePath = Keys.baseDirectory.value.toPath
            // Relativize the file to the project root.
            val relativeFile = if (file.isAbsolute) {
              basePath.relativize(file.toPath).toFile
            } else {
              file
            }
            s"""(file("$relativeFile"), "$destination")"""
        }
        .mkString("#     ", ",\n#     ", "\n")
      // Turn into an sbt setting.
      "#   dockerCopyMappings := Seq(\n" + tupleValues + "#   )"
    }

    // Create the text for javaOptions & JVM_ARGS.
    val javaOptionsText = Keys.javaOptions.value.map('"' + _ + '"').mkString(", ")
    val jvmArgsText = Keys.javaOptions.value.mkString(" ")

    // Create the text for dockerPorts and EXPOSE commands.
    val dockerPortsText = dockerPorts.value.mkString(", ")
    val exposeText = dockerPorts.value.map("EXPOSE " + _).mkString("\n")

    // Create any environment variables specified in sbt.
    // This throws an error if the environment variable name contains a space, since this is not
    // handled graciously by Docker.
    val envVarsText = Keys.envVars.value
      .map {
        case (key, value) =>
          if (key.contains(" ")) {
            sys.error(s"""Environment variable name can't be exported to Docker: \"$key\"""")
          }
          val quotedValue =
            value.replaceAllLiterally("\\", "\\\\").replaceAllLiterally("\"", "\\\"")
          s""""$key" -> "$quotedValue""""
      }
      .mkString(", ")
    val envVarsCommandsText = Keys.envVars.value
      .map {
        case (key, value) =>
          val escapedValue = value
            .replaceAllLiterally("\\", "\\\\")
            .replaceAllLiterally(
              "$",
              "\\$"
            )
            .replaceAllLiterally("\"", "\\\"")
          s"ENV $key $escapedValue"
      }
      .mkString("\n")

    // Check for a main class, and warn if it's missing.
    val (javaMainText, mainClassText) = Keys.mainClass.?.value.getOrElse(None) match {
      case Some(mainClass) =>
        (s"ENV JAVA_MAIN $${JAVA_MAIN:-$mainClass}", s"""#   mainClass := Some("$mainClass")""")
      case None =>
        logger.warn(
          "No `mainClass` set! Your image will not run without manually setting " +
            "the JAVA_MAIN environment variable."
        )
        ("# (No mainClass set)", "#  mainClass := None")
    }

    // Create the text for dockerMainArgs and CMD command.
    val dockerMainArgsText = dockerMainArgs.value.map('"' + _ + '"').mkString(", ")

    s"""# AUTOGENERATED
# Most lines in this file are derived from sbt settings. These settings are printed above the lines
# they affect.
#
# IMPORTANT: If you wish to make edits to this file, make changes BELOW the line starting with
# "#+#". Any updates to commands above this line should happen through sbt, and pushed to the
# Dockerfile using the `generateDockerfile` task.

# This image depends on the dependency image.
#
# The dependency image inherits from:
#   dockerImageBase := "${dockerImageBase.value}"
FROM ${dependencyImageName.value}

# The ports which are available to map in the image.
# sbt setting:
#   dockerPorts := Seq[Int]($dockerPortsText)
$exposeText

# The variable determining which typesafe config file to use. You can override this with the -e
# flag:
#   docker run -e CONFIG_ENV=prod ${mainImageName.value}
# Note the default is "dev".
ENV CONFIG_ENV $${CONFIG_ENV:-dev}

# The arguments to send to the JVM. These can be overridden at runtime with the -e flag:
#   docker run -e JVM_ARGS="-Xms=1G -Xmx=1G" ${mainImageName.value}
#
# sbt setting:
#   javaOptions := Seq($javaOptionsText)
ENV JVM_ARGS $${JVM_ARGS:-$jvmArgsText}

# Other environment variables to set when running your command. All of these can be overridden at
# runtime with the -e flag:
#   docker run -e VAR_NAME=newValue ${mainImageName.value}
# sbt setting:
#   envVars := Map($envVarsText)
$envVarsCommandsText

# The main class to execute when using the ENTRYPOINT command. You can override this at runtime with
# the -e flag:
#   docker run -e JAVA_MAIN=org.allenai.HelloWorld ${mainImageName.value}
# sbt setting:
$mainClassText
$javaMainText

# The default arguments to use for running the image.
# See https://docs.docker.com/engine/reference/builder/#/understand-how-cmd-and-entrypoint-interact
# for detailed information on CMD vs ENTRYPOINT.
# sbt setting:
#   dockerMainArgs := Seq[String]($dockerMainArgsText)
CMD [$dockerMainArgsText]

# The script for this application to run. This can be overridden with the --entrypoint flag:
#   docker run --entrypoint /bin/bash ${mainImageName.value}
ENTRYPOINT ["bin/$STARTUP_SCRIPT_NAME"]

# The directories in the staging directory which will be mapping into the Docker image.
$dockerCopyMappingsText
$copyText

# lib is always copied, since it has the built jars.
COPY lib lib

# Any additions to the file below this line will be retained when `generateDockerfile` is run.
# Do not remove this line unless you want your changes overwritten!
$DOCKERFILE_SIGIL
"""
  }

  /** Task to check the current dockerfile contents against the generated contents, returning true
    * if they are up-to-date.
    */
  lazy val checkDockerfile: Def.Initialize[Task[Boolean]] = Def.task {
    val newContents = {
      val generatedContents = generatedDockerfileContents.value
      val customContents = customDockerfileContents.value.getOrElse("")
      generatedContents + customContents
    }
    val dockerfile = dockerfileLocation.value
    val oldContents = if (dockerfile.exists) IO.read(dockerfileLocation.value) else ""

    newContents == oldContents
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  // Definitions for plugin tasks.
  //////////////////////////////////////////////////////////////////////////////////////////////////

  /** Task to build a dockerfile using the current project's sbt settings to populate it. */
  lazy val generateDockerfileDef: Def.Initialize[Task[Unit]] = Def.task {
    val generatedContents = generatedDockerfileContents.value
    val customContents = customDockerfileContents.value.getOrElse {
      Keys.streams.value.log(s"Overwriting Dockerfile at ${dockerfileLocation.value}...")
      ""
    }
    IO.write(dockerfileLocation.value, generatedContents + customContents)
  }

  /** Task verify that the current Dockerfile is up-to-date. Raises an error if
    * verifyDockerfileIsStrict is true.
    */
  lazy val verifyDockerfileDef: Def.Initialize[Task[Boolean]] = Def.task {
    val isUnchanged = checkDockerfile.value
    if (!isUnchanged) {
      val message = s"Dockerfile ${dockerfileLocation.value} is not up-to-date. Run " +
        "the `generateDockerfile` task to fix."
      if (verifyDockerfileIsStrict.value) {
        sys.error(message)
      } else {
        Keys.streams.value.log.warn(message)
      }
    }
    isUnchanged
  }

  /** Task to stage a docker image containing the dependencies of the current project. This is used
    * to build a base image for the main project image.
    *
    * The result of this task is the directory containing the staged image. The directory will
    * contain a Dockerfile to build the image and a `lib` folder with all dependency jars.
    */
  lazy val dependencyStageDef: Def.Initialize[Task[File]] = Def.task {
    val logger = Keys.streams.value.log
    logger.info(s"Staging dependency image for ${mainImageNameSuffix.value}...")

    // Create the destination directory.
    val imageDirectory = dependencyImageDir.value
    IO.createDirectory(imageDirectory)
    val lib = new File(dependencyImageDir.value, "lib")
    IO.createDirectory(lib)

    // Create the Dockerfile for the dependency image.
    val dockerfileContents = s"""
                                |FROM ${dockerImageBase.value}
                                |WORKDIR ${dockerWorkdir.value}
                                |STOPSIGNAL SIGINT
                                |${dockerDependencyExtra.value.mkString("\n")}
                                |COPY bin bin
                                |COPY lib lib
                                |""".stripMargin
    val dependencyDockerfile = new File(dependencyImageDir.value, "Dockerfile")
    IO.write(dependencyDockerfile, dockerfileContents)

    // Copy the startup script.
    val bin = new File(dependencyImageDir.value, "bin")
    if (!bin.exists) {
      IO.createDirectory(bin)
    }
    val startupScriptDestination = new File(bin, STARTUP_SCRIPT_NAME)
    Utilities.copyResourceToFile(getClass, STARTUP_SCRIPT_NAME, startupScriptDestination)
    startupScriptDestination.setExecutable(true)

    // Copy all of the library dependencies, saving the end location.
    val copiedFiles: Seq[File] = HelperDefs.remoteDependencies.value.map {
      case (file, destination) =>
        val destinationFile = new File(lib, destination)
        // Don't push bytes around unnecessarily. Note that this might leave stale snapshot
        // (dynamic) jars around long if they aren't named in a standard way.
        // A `clean` will wipe these out if needed.
        if (!destinationFile.exists || destinationFile.getName.contains("-SNAPSHOT")) {
          IO.copyFile(file, destinationFile)
        }
        destinationFile
    }

    // Remove any items in `lib` that are stale.
    val staleItems = lib.listFiles.toSet -- copiedFiles
    staleItems.foreach(_.delete())

    imageDirectory
  }

  /** Task to build a docker image containing the dependencies of the current project. This is used
    * as a base image for the main project image.
    */
  lazy val dependencyBuildDef: Def.Initialize[Task[String]] = Def.task {
    // This task requires docker to be installed.
    requireDocker.value

    // This task requires that the docker dependency stage have been run.
    dockerDependencyStage.value

    val logger = Keys.streams.value.log

    // Ensure that the base image is up to date. This also works around an issue wherein the
    // `docker build` command will fail to authenticate to a repository even when credentials are
    // valid. The `docker pull` command doesn't exhibit this issue.
    if (!skipEcrLogin.value) {
      loginIfEcrImage(dockerImageBase.value, logger)
    }
    // TODO: We should invalidate the dependency hash if this image has changed.
    logger.info(s"Updating base image ${dockerImageBase.value}...")
    val exitCode = Process(Seq("docker", "pull", dockerImageBase.value)).!
    if (exitCode != 0) {
      sys.error("Failed to update base image.")
    }

    logger.info(s"Building dependency image for ${mainImageNameSuffix.value}...")

    buildImageIfUpdated(
      dependencyImageDir.value,
      dependencyImageName.value,
      dependencyHashFile.value,
      logger
    )
  }

  /** Task to stage the main docker image for the project. */
  lazy val mainImageStageDef: Def.Initialize[Task[File]] = Def.task {
    val logger = Keys.streams.value.log

    val dockerfile = dockerfileLocation.value
    if (!dockerfile.exists) {
      sys.error(
        s"No Dockerfile found at $dockerfile .\n" +
          "Maybe you should generate one with the `generateDockerfile` task?"
      )
    }

    logger.info(s"Staging main image ${mainImageNameSuffix.value}...")

    // Create the destination directory.
    val imageDirectory = mainImageDir.value
    IO.createDirectory(imageDirectory)

    // Create the destination for libraries.
    val lib = new File(imageDirectory, "lib")
    if (!lib.exists) {
      IO.createDirectory(lib)
    }

    // Copy the Dockerfile.
    IO.copyFile(dockerfile, new File(imageDirectory, "Dockerfile"))
    // Copy the mappings.
    dockerCopyMappings.value.foreach {
      case (maybeRelativeSource, relativeDestination) =>
        // Make any relative path relative to the base directory.
        val source = if (maybeRelativeSource.isAbsolute) {
          maybeRelativeSource
        } else {
          new File(Keys.baseDirectory.value, maybeRelativeSource.toString)
        }
        val destination = new File(imageDirectory, relativeDestination)
        if (source.exists) {
          // The IO object's methods do not preserve executable bits, so we have to manually set
          // these ourself.
          if (source.isDirectory) {
            IO.createDirectory(destination)
            val toCopy = PathFinder(source).allPaths.pair(Path.rebase(source, destination))
            IO.copy(toCopy)
            toCopy.foreach {
              case (source, destination) => destination.setExecutable(source.canExecute)
            }
          } else {
            IO.copyFile(source, destination)
            destination.setExecutable(source.canExecute)
          }
        } else {
          // The Dockerfile command COPY will error if the source doesn't exist, and Dockerfile
          // generation can't see what'll exist in the staged directory, so create a dummy file if
          // there's no source to copy from.
          IO.write(destination, "(dummy)")
        }
    }

    // Map the local dependencies to their target location.
    val localDependencyMapping: Seq[(File, File)] = HelperDefs.localDependencies.value.map {
      case (file, destinationName) => (file, new File(lib, destinationName))
    }

    // Copy the local project jars.
    localDependencyMapping.foreach { case (file, destination) => IO.copyFile(file, destination) }

    // Generate the cache key file.
    val cacheKeyDestination = new File(imageDirectory, "conf/cacheKey.sha1")
    val cacheKeyContentsOption: Option[String] = {
      // Hash the dependency image directly.
      val stableContentsHash = {
        val dependencyDir = dependencyImageDir.value
        Utilities.hashFiles(PathFinder(dependencyDir).allPaths.filter(_.isFile).get, dependencyDir)
      }

      // Find the git commits for the current project and local dependencies, since this is more
      // stable than a hash of the contents.
      val unstableContentsCommits: Seq[Option[String]] = {
        (Def.taskDyn(gitCommitIfClean.all(HelperDefs.dependencyFilter.value)).value :+
          gitCommitIfClean.value)
      }

      // If any of the local projects had dirty contents, don't produce a cache key.
      if (unstableContentsCommits.forall(_.nonEmpty)) {
        // Sort items before hashing to ensure a stable hash.
        val components = (stableContentsHash +: unstableContentsCommits.flatten).sorted
        Some(Hash.toHex(Hash(components.mkString)))
      } else {
        None
      }
    }
    // Generate the cache key only for pristine repos.
    cacheKeyContentsOption match {
      case Some(cacheKeyContents) => IO.write(cacheKeyDestination, cacheKeyContents)
      case None                   => IO.delete(cacheKeyDestination)
    }

    imageDirectory
  }

  /** Task to build the main docker image for the project. This returns the image ID. */
  lazy val mainImageBuildDef: Def.Initialize[Task[String]] = Def.task {
    // This task requires docker to be installed.
    requireDocker.value

    // This task requires that the dependency image be created, and that the main image be staged.
    dockerDependencyBuild.value
    dockerMainStage.value

    // Verify the dockerfile.
    if (verifyDockerfileOnBuild.value) {
      val isUnchanged = checkDockerfile.value
      // Note that due to sbt dependency semantics (the fact that all dependencies are always run),
      // we duplicate the below logic in the verifyDockerfile task def. Otherwise, we couldn't
      // control the error behavior via the verifyDockerfileOnBuild flag above.
      if (!isUnchanged) {
        if (verifyDockerfileIsStrict.value) {
          sys.error(
            s"Dockerfile ${dockerfileLocation.value} is not up-to-date, stopping build. Run " +
              "`generateDockerfile` to update."
          )
        } else {
          Keys.streams.value.log.warn(
            s"Dockerfile ${dockerfileLocation.value} is not up-to-date. Run `generateDockerfile` " +
              "to update."
          )
        }
      }
    }

    val logger = Keys.streams.value.log
    logger.info(s"Building main image ${mainImageNameSuffix.value}...")

    // Copy in the dependency hash file in order to include it in our image hash. This ensures we
    // rebuild if dependencies change, even if the source code remains the same.
    IO.copyFile(dependencyHashFile.value, new File(mainImageDir.value, "dependencies.sha1"))

    buildImageIfUpdated(
      mainImageDir.value,
      mainImageName.value,
      new File(dockerTargetDir.value, "main.sha1"),
      logger
    )
  }

  /** Task to execute `docker run` in the background. Note that this doesn't use -d, since we want
    * stdout to show up in the sbt console. This will stop any currently-running image, if we've
    * already started one for this sbt instance.
    */
  lazy val dockerRunDef: Def.Initialize[Task[Unit]] = Def.task {
    // This task requires that our main image be built.
    dockerBuild.value

    val logger = Keys.streams.value.log

    val containerName = dockerImageName.value
    runningContainers.synchronized {
      // Stop any currently-executing container.
      if (runningContainers(containerName)) {
        stopContainer(containerName, logger)
      }
      // Don't run with -d, since we want to see the output.
      val baseCommand = Seq("docker", "run", "--rm", "--name", containerName)
      // Build the port arguments.
      val portArgs: Seq[String] = dockerPortMappings.value.flatMap {
        case (hostPort, containerPort) => Seq("-p", s"$hostPort:$containerPort")
      }
      // Start up the container.
      val fullCommand = baseCommand ++ portArgs ++ dockerRunFlags.value :+ mainImageName.value
      logger.info("Running command: " + fullCommand.mkString(" "))
      Process(fullCommand).run()
      // Save it to the list of running containers.
      runningContainers.add(containerName)
    }
  }

  /** Task to stop the currently-running docker container, if it exists. */
  lazy val dockerStopDef: Def.Initialize[Task[Unit]] = Def.task {
    // Block while stopping, then remove the image (if we're tracking it).
    runningContainers.synchronized {
      stopContainer(dockerImageName.value, Keys.streams.value.log)
      runningContainers.remove(dockerImageName.value)
    }
  }

  /** Task to push the project image. This accepts any number of tags as input, which will be added
    * to the image and pushed.
    */
  lazy val dockerPushDef: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val logger = Keys.streams.value.log

    // Build the main image and retrieve the current tag.
    val currentTag = dockerBuild.value

    val tags = DefaultParsers.spaceDelimited("<tag>").parsed

    if (!skipEcrLogin.value) {
      loginIfEcrImage(mainImageName.value, logger)
    }

    if (tags.nonEmpty) {
      // Generate the full image names.
      val tagNamePairs = tags.map(tag => (tag, mainImageName.value + ':' + tag))
      // Add all of the user-provided tags.
      tagNamePairs.foreach {
        case (tag, newName) =>
          logger.info(s"Adding tag $tag...")
          if (Process(Seq("docker", "tag", currentTag, newName)).! != 0) {
            sys.error(s"""Could not create tag "$tag", stopping.""")
          }
      }
      // Push all of the names.
      tagNamePairs.foreach {
        case (tag, newName) =>
          if (Process(Seq("docker", "push", newName)).! != 0) {
            sys.error(s"""There was a problem pushing tag "$tag", stopping.""")
          }
      }
      val tagsString = tags.mkString("[\"", "\", \"", "\"]")
      logger.info(s"Pushed tags $tagsString.")
    } else {
      val currentHash = currentTag.stripPrefix(mainImageName.value + ':')

      // Push the current SHA1 tag.
      if (Process(Seq("docker", "push", currentTag)).! != 0) {
        sys.error(s"""There was a problem pushing tag "$currentHash", stopping.""")
      }
      logger.info(s"""Pushed tag "$currentTag".""")
    }
  }

  /** Adds the settings to configure the `dockerBuild` command. */
  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    dockerfileLocation := {
      new File(sourceMain.value, "docker" + File.separatorChar + "Dockerfile")
    },
    dockerCopyMappings := defaultCopyMappings.value,
    dockerImageRegistryHost := DEFAULT_REGISTRY,
    dockerImageNamePrefix := Keys.organization.value.stripPrefix("org.allenai."),
    dockerImageName := Keys.name.value,
    dockerImageBase := DEFAULT_BASE_IMAGE,
    dockerDependencyExtra := Seq.empty,
    dockerPorts := Seq.empty,
    dockerPortMappings := {
      val exposedPorts = dockerPorts.value
      exposedPorts.zip(exposedPorts)
    },
    dockerMainArgs := Seq.empty,
    dockerWorkdir := "/stage",
    verifyDockerfileIsStrict := true,
    verifyDockerfileOnBuild := false,
    skipEcrLogin := false,
    dockerRunFlags := Seq.empty,
    generateDockerfile := generateDockerfileDef.value,
    verifyDockerfile := verifyDockerfileDef.value,
    dockerDependencyStage := dependencyStageDef.value,
    dockerMainStage := mainImageStageDef.value,
    dockerDependencyBuild := dependencyBuildDef.value,
    dockerBuild := mainImageBuildDef.value,
    dockerRun := dockerRunDef.value,
    dockerStop := dockerStopDef.value,
    dockerPush := dockerPushDef.evaluated
  )
}
