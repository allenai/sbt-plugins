package org.allenai.plugins

import sbt._
import codeartifact.CodeArtifactKeys

import java.io.File
import java.nio.file.Files

object CoreSettingsPlugin extends AutoPlugin {

  // Automatically add the VersionInjectorPlugin
  override def requires: Plugins = VersionInjectorPlugin

  // Automatically enable the plugin (no need for projects to `enablePlugins(CoreSettingsPlugin)`)
  override def trigger: PluginTrigger = allRequirements

  object autoImport {

    val generateRunClass = Def.taskKey[File](
      "creates the run-class.sh script in the managed resources directory"
    )
  }

  import autoImport._

  private val generateRunClassTask = autoImport.generateRunClass := {
    val logger = Keys.streams.value.log
    logger.debug("Generating run-class.sh")
    // Copy the run-class.sh resource into managed resources.
    val destination = Keys.resourceManaged.in(Compile).value / "run-class.sh"
    Utilities.copyResourceToFile(this.getClass, "run-class.sh", destination)

    destination
  }

  // These settings will be automatically applied to projects
  override def projectSettings: Seq[Setting[_]] = {
    Defaults.itSettings ++ Seq(
      generateRunClassTask,
      Keys.fork := true, // Forking for run, test is required sometimes, so fork always.
      // Use a sensible default for the logback appname.
      Keys.javaOptions += s"-Dlogback.appname=${Keys.name.value}",
      Keys.scalaVersion := CoreDependencies.defaultScalaVersion,
      Keys.scalacOptions ++= Seq("-target:jvm-1.8", "-Xlint", "-deprecation", "-feature"),
      Keys.javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),
      Keys.dependencyOverrides ++= CoreDependencies.loggingDependencyOverrides.toSeq,
      Keys.dependencyOverrides += "org.scala-lang" % "scala-library" % Keys.scalaVersion.value,
      CodeArtifactKeys.codeArtifactUrl := "https://org-allenai-s2-896129387501.d.codeartifact.us-west-2.amazonaws.com/maven/private"
    )
  }
}
