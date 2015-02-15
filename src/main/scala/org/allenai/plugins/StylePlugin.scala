package org.allenai.plugins

import sbt._
import sbt.Keys._

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
  }

  override def requires: Plugins = plugins.JvmPlugin

  override def trigger: PluginTrigger = allRequirements

  override def projectSettings: Seq[Def.Setting[_]] =
    // Add scalastyle settings.
    ScalastylePlugin.projectSettings ++
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
      // "q" for "quiet".
      val args = Seq("q")
      val configXml = configFile(target.value)
      // If set to true, the SBT task will treat style warnings as style errors.
      val warnIsError = false
      val sourceDir = (scalaSource in StyleKeys.styleCheck).value
      val outputXml = new File(scalastyleTarget(target.value), "scalastyle-results.xml")
      val localStreams = streams.value
      ScalastyleTasks.doScalastyle(
        args,
        configXml,
        None, // Config URL; overrides configXml.
        warnIsError,
        sourceDir,
        outputXml,
        localStreams,
        0, // How frequently, in hours, to refresh config from URL. Ignored if URL is None.
        target.value,
        "/dev/null" // URL cache file. Ignored if URL is None.
      )
    }
  )

  /** @return a `scalastyle` directory in `targetDir`, creating it if needed */
  def scalastyleTarget(targetDir: File): File = {
    val dir = new File(targetDir, "scalastyle")
    IO.createDirectory(dir)
    dir

  }

  /** Copies the resource config file to the local filesystem (in `target`), and returns the new
    * location.
    * @param targetDir the value of the `target` setting key
    */
  def configFile(targetDir: File): File = {
    val destinationFile = new File(scalastyleTarget(targetDir), "scalastyle-config.xml")

    if (!destinationFile.exists) {
      val resourceName = "allenai-style-config.xml"
      Option(getClass.getClassLoader.getResourceAsStream(resourceName)) match {
        case None => throw new NullPointerException(s"Failed to find $resourceName in resources")
        case Some(sourceStream) => IO.write(destinationFile, IO.readStream(sourceStream))

      }

    }
    destinationFile
  }
}
