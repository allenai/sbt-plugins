package org.allenai.sbt.core

import org.allenai.sbt.style.StylePlugin
import org.allenai.sbt.versioninjector.VersionInjectorPlugin

import sbt._
import sbt.Keys._

object CoreSettingsPlugin extends AutoPlugin {

  // Automatically add the StylePlugin and VersionInjectorPlugin
  override def requires = StylePlugin && VersionInjectorPlugin

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
        scalacOptions ++= Seq("-Xlint", "-deprecation", "-feature"),
        conflictManager := ConflictManager.strict,
        resolvers ++= CoreDependencies.defaultResolvers,
        dependencyOverrides ++= CoreDependencies.defaultDependencyOverrides)
}
