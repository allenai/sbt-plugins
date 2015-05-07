package org.allenai.plugins.archetypes

import org.allenai.plugins.NodeJsPlugin
import org.allenai.plugins.NodeJsPlugin.autoImport._
import org.allenai.plugins.DeployPlugin

import com.typesafe.sbt.packager
import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.MappingsHelper
import com.typesafe.sbt.packager.universal.UniversalPlugin
import spray.revolver.RevolverPlugin.Revolver

import sbt._
import sbt.Keys._

/** Plugin that configures a webapp for building. This makes the `re-start`, `test`, and `clean`
  * tasks execute the appropriate node builds, configures node for deploy with the `deploy` command.
  */
object WebappPlugin extends AutoPlugin {

  override def requires: Plugins = WebServicePlugin && NodeJsPlugin && DeployPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    // Expect the node project in a "webapp" subdirectory.
    NodeKeys.nodeProjectDir in Npm := (baseDirectory in thisProject).value / "webapp",
    // Force npm:build when using sbt-revolver re-start to ensure UI is built
    Revolver.reStart <<= Revolver.reStart.dependsOn(NodeKeys.build in Npm),
    // Run client-side tests when tests are run.
    test in Test <<= (test in Test).dependsOn(test in Npm),
    // Clean node files on clean.
    cleanFiles += (NodeKeys.nodeProjectTarget in Npm).value,
    // Build the node project on stage (for deploys).
    UniversalPlugin.autoImport.stage <<=
      UniversalPlugin.autoImport.stage.dependsOn(DeployPlugin.npmBuildTask),
    // Copy the built node project into our staging directory, too!
    mappings in Universal <++= (NodeKeys.nodeProjectTarget in Npm) map MappingsHelper.directory
  )
}
