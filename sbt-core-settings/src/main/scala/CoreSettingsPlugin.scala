package org.allenai.sbt.core

import org.allenai.sbt.format.FormatPlugin
import org.allenai.sbt.versioninjector.VersionInjectorPlugin

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

  override def projectSettings: Seq[Setting[_]] =
    CoreDependencies.addLoggingDependencies(libraryDependencies) ++
      Seq(conflictManager := ConflictManager.strict)

}
