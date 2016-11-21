package org.allenai.plugins.archetypes

import org.allenai.plugins.{ DeployPlugin, NodeJsPlugin }
import org.allenai.plugins.NodeJsPlugin.autoImport.{ NodeKeys, Npm }

import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.MappingsHelper
import com.typesafe.sbt.packager.universal.UniversalPlugin
import spray.revolver.RevolverPlugin.Revolver

import sbt.{ AutoPlugin, Keys, Plugins, Setting, Test }

/** Plugin that configures a webapp for building. This makes the `re-start`, `test`, and `clean`
  * tasks execute the appropriate node builds. It also configures the deploy plugin and docker
  * plugin appropriately.
  */
object WebappPlugin extends AutoPlugin {
  override def requires: Plugins = WebServicePlugin && NodeJsPlugin && DeployPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    // Run "npm watch" when we run a re-start.
    Revolver.reStart := Revolver.reStart.dependsOn(NodeKeys.nwatch.in(Npm)).evaluated,
    // Kill background watches on re-stop.
    Revolver.reStop := Revolver.reStop.dependsOn(NodeKeys.unwatch.in(Npm)).value,
    // Run client-side tests when tests are run.
    Keys.test.in(Test) := Keys.test.in(Test).dependsOn(Keys.test.in(Npm)).value,
    // Clean node files on clean.
    Keys.cleanFiles += NodeKeys.nodeProjectTarget.in(Npm).value,
    // Build the node project on stage (for deploys).
    UniversalPlugin.autoImport.stage :=
      UniversalPlugin.autoImport.stage.dependsOn(DeployPlugin.autoImport.deployNpmBuild).value,
    // Copy the built node project into our staging directory, too!
    Keys.mappings.in(Universal) ++=
      MappingsHelper.directory(NodeKeys.nodeProjectTarget.in(Npm).value)
  )
}
