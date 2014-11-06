package org.allenai.plugins.archetypes

import org.allenai.plugins.CoreSettingsPlugin
import org.allenai.plugins.AllenaiReleasePlugin

import sbt.AutoPlugin

object LibraryPlugin extends AutoPlugin {
  override def requires = CoreSettingsPlugin && AllenaiReleasePlugin
}
