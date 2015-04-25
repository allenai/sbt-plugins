package org.allenai.plugins.archetypes

import org.allenai.plugins.NodeJsPlugin
import org.allenai.plugins.NodeJsPlugin.autoImport._
import org.allenai.plugins.DeployPlugin.autoImport._

import com.typesafe.sbt.packager
import com.typesafe.sbt.SbtNativePackager.Universal
import com.typesafe.sbt.packager.MappingsHelper
import spray.revolver.RevolverPlugin.Revolver

import sbt._
import sbt.Keys._

object WebappPlugin extends AutoPlugin {

  override def requires: Plugins = WebServicePlugin && NodeJsPlugin

  object autoImport {
    val Webapp = ConfigKey("webapp")

    object WebappKeys {
      val logNodeEnvironment = TaskKey[Unit](
        "logNodeEnvironment",
        "Logs the NodeJs build environment to SBT console"
      )
    }
  }

  import autoImport._

  val logNodeEnvTask = WebappKeys.logNodeEnvironment in Webapp := {
    val log = streams.value.log
    val env = (NodeKeys.buildEnvironment in Npm).value
    log.info(s"[webapp] NODE_ENV = '$env'")
  }

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    logNodeEnvTask,
    packager.Keys.stage <<= packager.Keys.stage.dependsOn(WebappKeys.logNodeEnvironment in Webapp),
    NodeKeys.nodeProjectDir in Npm := (baseDirectory in thisProject).value / "webapp",
    // Set NODE_ENV to the deploy target (e.g. 'prod', 'staging', etc.)
    NodeKeys.buildEnvironment in Npm := deployEnvironment.value.getOrElse("sbt-dev"),
    // Print the node environment on stage
    // Force npm:build when using sbt-revolver re-start to ensure UI is built
    Revolver.reStart <<= Revolver.reStart.dependsOn(NodeKeys.build in Npm),
    mappings in Universal <++= (NodeKeys.nodeProjectTarget in Npm) map MappingsHelper.directory,
    mappings in Universal <<= (mappings in Universal).dependsOn(NodeKeys.build in Npm)
  )
}
