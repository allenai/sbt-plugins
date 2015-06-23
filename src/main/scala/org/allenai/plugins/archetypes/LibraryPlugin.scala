package org.allenai.plugins.archetypes

import org.allenai.plugins.CoreSettingsPlugin
import org.allenai.plugins.Ai2ReleasePlugin

import sbt.{ AutoPlugin, Plugins }

object LibraryPlugin extends AutoPlugin {
  override def requires: Plugins = CoreSettingsPlugin && Ai2ReleasePlugin
}
