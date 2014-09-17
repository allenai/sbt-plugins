package org.allenai.sbt.style

import sbt._
import sbt.Keys._

import org.scalastyle.sbt.{
  PluginKeys => ScalastylePluginKeys, ScalastylePlugin, Tasks => ScalastyleTasks
}

import java.io.File

/** Plugin wrapping the scalastyle SBT plugin. This uses the configuration resource in this package
  * to configure scalastyle, and sets up test and compile to depend on it. */
object StylePlugin extends AutoPlugin {
  object StyleKeys {
    val styleCheck = TaskKey[Unit]("styleCheck", "Check scala file style")
  }

  override def requires = plugins.JvmPlugin

  override def trigger = allRequirements

  override def projectSettings = ScalastylePlugin.Settings ++
    inConfig(Compile)(configSettings) ++
    inConfig(Test)(configSettings) ++
    // Check style on compile.
    Seq(compileInputs in (Compile, compile) <<=
        (compileInputs in (Compile, compile)) dependsOn (StyleKeys.styleCheck in Compile),
      compileInputs in (Test, compile) <<=
        (compileInputs in (Test, compile)) dependsOn (StyleKeys.styleCheck in Test))

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
        args, configXml, warnIsError, sourceDir, outputXml,  localStreams)
    })

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
      val sourceStream = getClass.getClassLoader.getResourceAsStream("./allenai-style-config.xml")
      IO.write(destinationFile, IO.readStream(sourceStream))
    }
    destinationFile
  }
}
