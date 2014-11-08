package org.allenai.plugins.archetypes

import org.allenai.plugins.CoreSettingsPlugin
import org.allenai.plugins.ReleasePlugin

import sbt.AutoPlugin

object LibraryPlugin extends AutoPlugin {
  override def requires = CoreSettingsPlugin && ReleasePlugin
}
