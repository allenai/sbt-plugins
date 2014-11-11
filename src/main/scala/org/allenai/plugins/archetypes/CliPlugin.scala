package org.allenai.plugins.archetypes

import org.allenai.plugins.CoreSettingsPlugin
import org.allenai.plugins.CoreDependencies

import sbt._
import sbt.Keys._

object CliPlugin extends AutoPlugin {
  override def requires: Plugins = CoreSettingsPlugin

  override def projectSettings: Seq[Def.Setting[_]] = Seq(
    libraryDependencies += CoreDependencies.scopt
  )
}
