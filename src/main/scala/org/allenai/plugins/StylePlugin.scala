package org.allenai.plugins

import sbt._
import sbt.Keys._

import org.scalastyle.{ Output => ScalastyleOutput, _ }
import org.scalastyle.sbt.{
  ScalastylePlugin,
  Tasks => ScalastyleTasks
}
import com.typesafe.config.ConfigFactory

/** Plugin wrapping the scalastyle SBT plugin. This uses the configuration resource in this package
  * to configure scalastyle, and sets up test and compile to depend on it.
  */
object StylePlugin extends AutoPlugin {
  object StyleKeys {
    val styleCheck = taskKey[OutputResult]("Check scala file style using scalastyle")
    val styleCheckStrict = taskKey[Unit](
      "Check scala file style using scalastyle, failing if an unformatted file is found"
    )
  }

  lazy val enableLineLimit = {
    settingKey[Boolean]("If true, enable the line length check in Scalastyle")
  }

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] =
    // Add scalastyle settings.
    ScalastylePlugin.projectSettings ++
      Seq(enableLineLimit := true) ++
      // Add our default settings to test & compile.
      inConfig(Compile)(configSettings) ++
      inConfig(Test)(configSettings) ++
      Seq(
        // Check style before compile.
        compileInputs.in(Test, compile) := {
          compileInputs.in(Test, compile).dependsOn(StyleKeys.styleCheck.in(Test)).value
        },
        compileInputs.in(Compile, compile) := {
          compileInputs.in(Compile, compile).dependsOn(StyleKeys.styleCheck.in(Compile)).value
        }
      )

  val styleCheckTask = StyleKeys.styleCheck := {
    val configuration = configWithLineCheck(enableLineLimit.value)

    val messages = new ScalastyleChecker()
      .checkFiles(configuration, Directory.getFiles(None, List(scalaSource.value)))

    new SbtLogOutput(streams.value.log).output(messages)
  }

  val styleCheckStrictTask = StyleKeys.styleCheckStrict := {
    val outputResult = StyleKeys.styleCheck.value

    if (outputResult.errors > 0) {
      sys.error("Style check failed.")
    }
  }

  // Settings used for a particular configuration (such as Compile).
  def configSettings: Seq[Setting[_]] = Seq(styleCheckTask, styleCheckStrictTask)

  /** @return the configuration object with the given line limit check status */
  def configWithLineCheck(enableLineLimit: Boolean): ScalastyleConfiguration = {
    val baseConfig = embeddedConfig
    if (!enableLineLimit) {
      // Filter out the line length rule (with a custom ID).
      val filteredChecks = baseConfig.checks.filterNot { _.customId == Some("line.length") }
      baseConfig.copy(checks = filteredChecks)
    } else {
      baseConfig
    }
  }

  /** @return the configuration file embedded with the plugin */
  def embeddedConfig: ScalastyleConfiguration = {
    val resourceName = "allenai-style-config.xml"
    val configString = Option(getClass.getClassLoader.getResourceAsStream(resourceName)) match {
      case None => throw new NullPointerException(s"Failed to find $resourceName in resources")
      case Some(sourceStream) => IO.readStream(sourceStream)
    }
    ScalastyleConfiguration.readFromString(configString)
  }
}

/** Message handler for Scalastyle. Adapted from the scalastyle plugin, where the equivalent class
  * is private.
  */
class SbtLogOutput[T <: FileSpec](logger: Logger) extends ScalastyleOutput[T] {
  private val messageHelper = new MessageHelper(ConfigFactory.empty)

  override def message(m: Message[T]): Unit = m match {
    case StartWork() => logger.verbose("Starting scalastyle")
    case EndWork() =>
    case StartFile(file) => logger.verbose("start file " + file)
    case EndFile(file) => logger.verbose("end file " + file)
    case StyleError(file, clazz, key, level, args, line, column, customMessage) => {
      plevel(level)(location(file, line, column) + ": " +
        ScalastyleOutput.findMessage(messageHelper, key, args, customMessage))
    }
    case StyleException(file, clazz, message, stacktrace, line, column) =>
      logger.error(location(file, line, column) + ": " + message)
  }

  private[this] def plevel(level: Level)(msg: => String): Unit = level match {
    case ErrorLevel => logger.error(msg)
    case WarningLevel => logger.warn(msg)
    case InfoLevel => logger.info(msg)
  }

  private[this] def location(file: T, line: Option[Int], column: Option[Int]): String = {
    val location = new StringBuilder(file.name)
    if (line.nonEmpty) {
      location.append(':').append(line.get)
      if (column.nonEmpty) {
        location.append(':').append(column.get)
      }
    }
    location.toString
  }
}
