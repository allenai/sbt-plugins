package org.allenai.plugins

import com.typesafe.sbt.SbtScalariform
import sbt.Keys._
import sbt._

import scalariform.formatter.ScalaFormatter
import scalariform.formatter.preferences._
import scalariform.parser.ScalaParserException

import java.nio.file.Files
import scala.collection.immutable

object CoreSettingsPlugin extends AutoPlugin {

  // Automatically add the StylePlugin and VersionInjectorPlugin
  override def requires: Plugins = SbtScalariform && StylePlugin && VersionInjectorPlugin

  // Automatically enable the plugin (no need for projects to `enablePlugins(CoreSettingsPlugin)`)
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val CoreResolvers = CoreRepositories.Resolvers
    val PublishTo = CoreRepositories.PublishTo

    val generateRunClass = taskKey[File](
      "creates the run-class.sh script in the managed resources directory"
    )

    val generateAutoformatGitHook = taskKey[Unit](
      "Generates a .git/hooks/pre-commit hook that enforces code formatting prior to commit."
    )

    val scalariformPreferences = settingKey[IFormattingPreferences](
      "The Scalariform preferences to use in formatting."
    )

    val format = taskKey[Seq[File]]("Format all scala source files, returning the changed files")

    val formatCheck = taskKey[Seq[File]](
      "Check for misformatted scala files, and print out & return those with errors"
    )

    val formatCheckStrict = taskKey[Unit](
      "Check for misformatted scala files, print out the names of those with errors, " +
        "and throw an error if any do have errors"
    )
  }

  import autoImport._

  private val generateRunClassTask = autoImport.generateRunClass := {
    val logger = streams.value.log
    logger.debug("Generating run-class.sh")
    // Copy the run-class.sh resource into managed resources.
    val destination = (resourceManaged in Compile).value / "run-class.sh"
    Utilities.copyResourceToFile(this.getClass, "run-class.sh", destination)

    destination
  }

  case class FormatResult(sourceFile: File, original: String, formatted: String)

  // Private task implementation for generating output.
  // Returns FormatResult for all *.scala files in `sourceDirectories`, also honoring the in-scope
  // `includeFilter` and `excludeFilter`.
  private val formatInternal = Def.task {
    val preferences = scalariformPreferences.value
    // Find all of the scala source files, then run them through scalariform.
    val sourceFiles = sourceDirectories.value.descendantsExcept(
      includeFilter.value || "*.scala",
      excludeFilter.value
    ).get
    val scalaMajorVersion = scalaVersion.value.split("-").head
    for {
      sourceFile <- sourceFiles
      original = IO.read(sourceFile)
      formatted = try {
        ScalaFormatter.format(original, preferences, scalaVersion = scalaMajorVersion)
      } catch {
        // A sclariform parse error generally means a file that won't compile.
        case e: ScalaParserException =>
          streams.value.log.error(s"Scalariform parser error in file $sourceFile: ${e.getMessage}")
          original
      }
    } yield FormatResult(sourceFile, original, formatted)
  }

  val baseScalariformSettings: Seq[Def.Setting[_]] = Seq(
    // Override the SbtScalariform formatter, since it gets plugged in awkwardly (as a dependency on
    // compile).
    SbtScalariform.autoImport.scalariformFormat := { immutable.Seq.empty[File] },
    format := {
      // The mainline SbtScalariform uses FileFunction to cache this, but it's not really worth the
      // effort here - especially given that we actually don't want to cache for formatCheck.
      for {
        FormatResult(sourceFile, original, formatted) <- formatInternal.value
        if original != formatted
      } yield {
        // Shorten the name to a friendlier path.
        val shortName = sourceFile.relativeTo(baseDirectory.value).getOrElse(sourceFile)
        streams.value.log.info(s"Formatting $shortName . . .")
        IO.write(sourceFile, formatted)
        sourceFile
      }
    },
    formatCheck := {
      val misformatted = for {
        FormatResult(sourceFile, original, formatted) <- formatInternal.value
        if original != formatted
      } yield sourceFile

      if (misformatted.nonEmpty) {
        val log = streams.value.log
        log.error("""Some files contain formatting errors; please run "sbt format" to fix.""")
        log.error("")
        log.error("Files with errors:")
        for (result <- misformatted) {
          // TODO(jkinkead): Log some / all of the diffs?
          log.error(s"\t$result")
        }
      }
      misformatted
    },
    formatCheckStrict := {
      val misformatted = formatCheck.value
      if (misformatted.nonEmpty) {
        throw new MessageOnlyException("Some files have formatting errors.")
      }
    }
  )

  // Add the IntegrationTest config to the project. The `extend(Test)` part makes it so
  // classes in src/it have a classpath dependency on classes in src/test. This makes
  // it simple to share common test helper code.
  // See http://www.scala-sbt.org/release/docs/Testing.html#Custom+test+configuration
  override val projectConfigurations = Seq(Configurations.IntegrationTest extend (Test))

  /** Scalariform settings we use that are different from the defaults */
  val ScalariformDefaultOverrides: Seq[(PreferenceDescriptor[_], Boolean)] = Seq(
    (DoubleIndentClassDeclaration, true),
    (MultilineScaladocCommentsStartOnFirstLine, true),
    (PlaceScaladocAsterisksBeneathSecondAsterisk, true),
    (SpacesAroundMultiImports, true)
  )

  // These settings will be automatically applied to the build exactly once and will not be applied
  // to individual subprojects. Tasks added to buildSettings will _not_ show up in tab-completion in
  // the SBT console. However, this is a good place to put tasks that you do not want executed once
  // per subproject.
  override def buildSettings: Seq[Setting[_]] = Seq(
    generateAutoformatGitHook := {
      val expectedGitHooksDir = (baseDirectory in ThisBuild).value / ".git" / "hooks"
      val preCommitFile = expectedGitHooksDir / "pre-commit"
      val scalariformFile = expectedGitHooksDir / "scalariform.jar"
      def requireFilesDontExist(files: File*) = {
        files find (_.exists) foreach { file =>
          sys.error(s"You already have .git/hooks/${file.getName}. Remove or rename the file and run again.")
        }
      }
      requireFilesDontExist(preCommitFile, scalariformFile)

      // generate the pre-commit hook with scalariform options injected:
      val scalariformOpts = (ScalariformDefaultOverrides map {
        case (descriptor, enable) =>
          val enableDisable = if (enable) "+" else "-"
          s"${enableDisable}${descriptor.key}"
      }).mkString("(", " ", ")")
      val lines = IO.readLinesURL(getClass.getClassLoader.getResource("autoformat/pre-commit"))
        .map(_.replace("__scalariform_opts__", scalariformOpts))
      IO.writeLines(preCommitFile, lines.toList)
      // git hooks must be executable
      preCommitFile.setExecutable(true)

      // copy the scalariform.jar
      val scalariformIs = getClass.getClassLoader.getResourceAsStream("autoformat/scalariform.jar")
      try {
        Files.copy(scalariformIs, scalariformFile.toPath)
      } finally {
        scalariformIs.close()
      }
    }
  )

  // These settings will be automatically applied to projects
  override def projectSettings: Seq[Setting[_]] = {
    Defaults.itSettings ++
      SbtScalariform.defaultScalariformSettingsWithIt ++
      inConfig(Compile)(baseScalariformSettings) ++
      inConfig(Test)(baseScalariformSettings) ++
      inConfig(Configurations.IntegrationTest)(baseScalariformSettings) ++
      Seq(
        generateRunClassTask,
        fork := true, // Forking for run, test is required sometimes, so fork always.
        // Use a sensible default for the logback appname.
        javaOptions += s"-Dlogback.appname=${name.value}",
        scalaVersion := CoreDependencies.defaultScalaVersion,
        scalacOptions ++= Seq("-target:jvm-1.7", "-Xlint", "-deprecation", "-feature"),
        javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
        resolvers ++= CoreRepositories.Resolvers.defaults,
        dependencyOverrides ++= CoreDependencies.loggingDependencyOverrides,
        dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value,
        // Override default scalariform settings.
        SbtScalariform.autoImport.scalariformPreferences := {
          ScalariformDefaultOverrides.foldLeft(FormattingPreferences()) {
            case (carry, (descriptor, enable)) =>
              carry.setPreference(descriptor.asInstanceOf[PreferenceDescriptor[Any]], enable)
          }
        },
        // Configure root-level tasks to aggregate accross configs
        format := {
          (format in Compile).value
          (format in Test).value
          (format in IntegrationTest).value
        },
        formatCheck := {
          (formatCheck in Compile).value
          (formatCheck in Test).value
          (formatCheck in IntegrationTest).value
        },
        formatCheckStrict := {
          (formatCheckStrict in Compile).value
          (formatCheckStrict in Test).value
          (formatCheckStrict in IntegrationTest).value
        }
      )
  }
}
