package org.allenai.sbt.library

import org.allenai.sbt.core.CoreSettingsPlugin
import org.allenai.sbt.release.AllenaiReleasePlugin

import sbt.AutoPlugin

object LibraryPlugin extends AutoPlugin {
  override def requires = CoreSettingsPlugin && AllenaiReleasePlugin
}
