package org.allenai.plugins

import sbt._
import sbt.Keys._

object CoreSettingsPlugin extends AutoPlugin {

  // Automatically add the StylePlugin and VersionInjectorPlugin
  override def requires = StylePlugin && VersionInjectorPlugin

  // Automatically enable the plugin (no need for projects to `enablePlugins(CoreSettingsPlugin)`)
  override def trigger = allRequirements

  object autoImport {
    val CoreResolvers = CoreRepositories.Resolvers
    val PublishTo = CoreRepositories.PublishTo
  }

  // These settings will be automatically applied to projects
  override def projectSettings: Seq[Setting[_]] =
    CoreDependencies.addLoggingDependencies(libraryDependencies) ++
      Seq(
        scalaVersion := CoreDependencies.defaultScalaVersion,
        scalacOptions ++= Seq("-target:jvm-1.7", "-Xlint", "-deprecation", "-feature"),
        javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
        conflictManager := ConflictManager.strict,
        resolvers ++= CoreRepositories.Resolvers.defaults,
        dependencyOverrides ++= CoreDependencies.defaultDependencyOverrides)
}
