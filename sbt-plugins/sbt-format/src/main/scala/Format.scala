import sbt._
import sbt.Keys._

import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences.IFormattingPreferences
import scalariform.formatter.ScalaFormatter
import scalariform.parser.ScalaParserException

object Format {
  import scalariform.formatter.preferences._

  // Default settings for formatting.
  def settings =
    // adds scalariformFormat task
    defaultScalariformSettings ++
      // formatting for config settings
      inConfig(Compile)(configSettings) ++
      inConfig(Test)(configSettings) ++
      Seq(
        // check formatting on compile
        compileInputs in (Compile, compile) <<= (compileInputs in (Compile, compile)) dependsOn (FormatKeys.formatCheck in Compile),
        compileInputs in (Test, compile) <<= (compileInputs in (Test, compile)) dependsOn (FormatKeys.formatCheck in Test),
        // scalariform settings
        ScalariformKeys.preferences := formattingPreferences)

  // Settings used for a particular configuration (such as Compile).
  def configSettings: Seq[Setting[_]] =
    // configures scalariform settings
    Seq(
      FormatKeys.format := ScalariformKeys.format.value,
      FormatKeys.formatCheck := checkFormatting(
        ScalariformKeys.preferences.value,
        (sourceDirectories in ScalariformKeys.format).value.toList,
        (includeFilter in ScalariformKeys.format).value,
        (excludeFilter in ScalariformKeys.format).value,
        thisProjectRef.value,
        configuration.value,
        streams.value,
        scalaVersion.value),
      FormatKeys.formatCheckStrict <<= (FormatKeys.formatCheck) map { files: Seq[File] =>
        if (files.size > 0) {
          throw new IllegalArgumentException("Unformatted files.")
        }
      })

  object FormatKeys {
    val formatCheck: TaskKey[Seq[File]] =
      TaskKey[Seq[File]](
        "formatCheck",
        "Check (Scala) sources using scalariform")

    val formatCheckStrict: TaskKey[Unit] =
      TaskKey[Unit](
        "formatCheckStrict",
        "Check (Scala) sources using scalariform, failing if an unformatted file is found")

    val format: TaskKey[Seq[File]] =
      TaskKey[Seq[File]](
        "format",
        "Format (Scala) sources using scalariform")
  }

  lazy val formattingPreferences = {
    FormattingPreferences().
      setPreference(DoubleIndentClassDeclaration, true).
      setPreference(MultilineScaladocCommentsStartOnFirstLine, true).
      setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true)
  }

  def checkFormatting(
    preferences: IFormattingPreferences,
    sourceDirectories: Seq[File],
    includeFilter: FileFilter,
    excludeFilter: FileFilter,
    ref: ProjectRef,
    configuration: Configuration,
    streams: TaskStreams,
    scalaVersion: String): Seq[File] = {

    def unformattedFiles(files: Set[File]): Set[File] =
      for {
        file <- files if file.exists
        contents = IO.read(file)
        formatted = try {
          ScalaFormatter.format(
            contents,
            preferences,
            scalaVersion = pureScalaVersion(scalaVersion))
        } catch {
          case e: ScalaParserException =>
            streams.log.warn("Scalariform parser error for %s: %s".format(file, e.getMessage))
            contents
        }
        if formatted != contents
      } yield (file)

    streams.log("Checking scala formatting...")
    val files = sourceDirectories.descendantsExcept(includeFilter, excludeFilter).get.toSet
    val unformatted = unformattedFiles(files).toSeq sortBy (_.getName)
    for (file <- unformatted) {
      streams.log.warn(f"misformatted: ${file.getName}")
    }

    unformatted
  }

  def pureScalaVersion(scalaVersion: String): String =
    scalaVersion.split("-").head
}
