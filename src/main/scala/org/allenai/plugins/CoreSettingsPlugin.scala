package org.allenai.plugins

import sbt._
import sbt.Keys._

import scalariform.formatter.preferences.{ DoubleIndentClassDeclaration, FormattingPreferences }
import scalariform.sbt.ScalariformPlugin

object CoreSettingsPlugin extends AutoPlugin {

  // Automatically add the StylePlugin and VersionInjectorPlugin
  override def requires: Plugins = ScalariformPlugin && StylePlugin && VersionInjectorPlugin

  // Automatically enable the plugin (no need for projects to `enablePlugins(CoreSettingsPlugin)`)
  override def trigger: PluginTrigger = allRequirements

  object autoImport {
    val CoreResolvers = CoreRepositories.Resolvers
    val PublishTo = CoreRepositories.PublishTo
  }

  // These settings will be automatically applied to projects
  override def projectSettings: Seq[Setting[_]] =
    CoreDependencies.addLoggingDependencies(libraryDependencies) ++
      Seq(
        fork := true, // Forking for run, test is required sometimes, so fork always.
        scalaVersion := CoreDependencies.defaultScalaVersion,
        scalacOptions ++= Seq("-target:jvm-1.7", "-Xlint", "-deprecation", "-feature"),
        javacOptions ++= Seq("-source", "1.7", "-target", "1.7"),
        conflictManager := ConflictManager.strict,
        resolvers ++= CoreRepositories.Resolvers.defaults,
        dependencyOverrides ++= CoreDependencies.loggingDependencyOverrides,
        dependencyOverrides += "org.scala-lang" % "scala-library" % scalaVersion.value,
        // Override default scalariform settings.
        ScalariformPlugin.autoImport.scalariformPreferences := {
          FormattingPreferences().setPreference(DoubleIndentClassDeclaration, true)
        }
      )
}
