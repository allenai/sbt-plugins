package org.allenai.plugins

import sbt._
import sbt.Keys._

import org.scalastyle.ScalastyleConfiguration
import org.scalastyle.sbt.{
  ScalastylePlugin,
  Tasks => ScalastyleTasks
}

/** Plugin wrapping the scalastyle SBT plugin. This uses the configuration resource in this package
  * to configure scalastyle, and sets up test and compile to depend on it.
  */
object StylePlugin extends AutoPlugin {
  object StyleKeys {
    val styleCheck = TaskKey[Unit]("styleCheck", "Check scala file style using scalastyle")
    val styleCheckStrict = TaskKey[Unit](
      "styleCheckStrict",
      "Check scala file style using scalastyle, failing if an unformatted file is found"
    )
  }

  lazy val enableLineLimit = SettingKey[Boolean](
    "enableLineLimit",
    "If true, enable the line length check in Scalastyle"
  )

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
        // Check style on compile.
        compileInputs in (Test, compile) <<=
          (compileInputs in (Test, compile)) dependsOn (StyleKeys.styleCheck in Test),
        compileInputs in (Compile, compile) <<=
          (compileInputs in (Compile, compile)) dependsOn (StyleKeys.styleCheck in Compile)
      )

  // Settings used for a particular configuration (such as Compile).
  def configSettings: Seq[Setting[_]] = Seq(
    StyleKeys.styleCheck := {
      ScalastyleTasks.doScalastyle(
        args = Seq("q"), // "q" for "quiet".
        config = configFile(target.value, enableLineLimit.value), // Configuration file
        configUrl = None, // Config URL; overrides configXml.
        failOnError = false, // If true, the SBT task will treat style warnings as style errors.
        scalaSource = (scalaSource in StyleKeys.styleCheck).value,
        scalastyleTarget = new File(scalastyleTarget(target.value), "scalastyle-results.xml"),
        streams = streams.value,
        refreshHours = 0, // How frequently, in hours, to refresh config from URL. Ignored if None.
        target = target.value,
        urlCacheFile = "/dev/null" // URL cache file. Ignored if URL is None.
      )
    },
    StyleKeys.styleCheckStrict := {
      ScalastyleTasks.doScalastyle(
        args = Seq("q"), // "q" for "quiet".
        config = configFile(target.value, enableLineLimit.value), // Configuration file
        configUrl = None, // Config URL; overrides configXml.
        failOnError = true, // If true, the SBT task will treat style errors as build errors.
        scalaSource = (scalaSource in StyleKeys.styleCheck).value,
        scalastyleTarget = new File(scalastyleTarget(target.value), "scalastyle-results.xml"),
        streams = streams.value,
        refreshHours = 0, // How frequently, in hours, to refresh config from URL. Ignored if None.
        target = target.value,
        urlCacheFile = "/dev/null" // URL cache file. Ignored if URL is None.
      )
    }
  )

  /** @return a `scalastyle` directory in `targetDir`, creating it if needed */
  def scalastyleTarget(targetDir: File): File = {
    val dir = new File(targetDir, "scalastyle")
    IO.createDirectory(dir)
    dir

  }

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

  /** Creates a config file in the target directory with the correct contents, and returns the
    * location of the file.
    * @param targetDir the value of the `target` setting key
    * @param enableLineLimit if true, enable the line length check
    */
  def configFile(targetDir: File, enableLineLimit: Boolean): File = {
    val config = configWithLineCheck(enableLineLimit)
    // TODO(jkinkead): Update our plugin to just call scalastyle directly; this is dumb.
    val destinationFile = new File(scalastyleTarget(targetDir), "scalastyle-config.xml")
    IO.write(destinationFile, ScalastyleConfiguration.toXmlString(config, 0, 0))
    destinationFile
  }
}
