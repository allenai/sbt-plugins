package org.allenai.sbt.core

import sbt._
import sbt.Keys._

object CoreSettingsPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  // Automatically enable the plugin (no need for projects to `enablePlugins(CoreSettingsPlugin)`)
  override def trigger = allRequirements

  object autoImport {
    object CoreSettings {
      val Dependencies = CoreDependencies
    }
  }

  // These settings will be automatically applied to projects
  override def projectSettings: Seq[Setting[_]] =
    CoreDependencies.addLoggingDependencies(libraryDependencies) ++
      Seq(
        scalaVersion := CoreDependencies.defaultScalaVersion,
        conflictManager := ConflictManager.strict,
        dependencyOverrides ++= CoreDependencies.defaultDependencyOverrides)
}
