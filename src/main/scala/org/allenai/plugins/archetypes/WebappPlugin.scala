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
    // Run "npm watch" when we run a re-start.
    // TODO(jkinkead): The below triggeres a warning due to use of `<<=`. We should figure out how
    // to replicate the below. It is tricky because `reStart` is in InputTask, not a regular Task.
    Revolver.reStart <<= Revolver.reStart.dependsOn(NodeKeys.nwatch in Npm),
    // Kill background watches on re-stop.
    Revolver.reStop := Def.taskDyn(NodeKeys.unwatch.in(Npm).map(_ => Revolver.reStop.value)).value,
    // Run client-side tests when tests are run.
    test in Test := Def.taskDyn(test.in(Npm).map(_ => test.in(Test).value)).value,
    // Clean node files on clean.
    cleanFiles += (NodeKeys.nodeProjectTarget in Npm).value,
    // Build the node project on stage (for deploys).
    UniversalPlugin.autoImport.stage := Def.taskDyn {
      DeployPlugin.autoImport.deployNpmBuild.map(_ => UniversalPlugin.autoImport.stage.value)
    }.value,
    // Copy the built node project into our staging directory, too!
    mappings.in(Universal) ++= MappingsHelper.directory(NodeKeys.nodeProjectTarget.in(Npm).value)
  )
}
