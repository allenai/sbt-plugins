package org.allenai.plugins.archetypes

import org.allenai.plugins.NodeJsPlugin
import org.allenai.plugins.NodeJsPlugin.autoImport._
import org.allenai.plugins.DeployPlugin.autoImport._

import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtNativePackager.NativePackagerHelper._
import com.typesafe.sbt.packager.universal.{ Keys => UniversalKeys }
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

      val reStartWebapp = TaskKey[Unit](
        "reStartWebapp",
        "Runs re-volover's reStart after running npm:build"
      )
    }
  }

  import autoImport._

  val logNodeEnvTask = WebappKeys.logNodeEnvironment in Webapp := {
    val log = streams.value.log
    val env = (NodeKeys.buildEnvironment in Npm).value
    log.info(s"[webapp] NODE_ENV = '$env'")
  }

  val reStartWebappTask = WebappKeys.reStartWebapp := {
    (NodeKeys.build in Npm).value
    Revolver.reStart.value
  }

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    logNodeEnvTask,
    reStartWebappTask,
    UniversalKeys.stage <<= UniversalKeys.stage.dependsOn(WebappKeys.logNodeEnvironment in Webapp),
    NodeKeys.nodeProjectDir in Npm := (baseDirectory in thisProject).value / "webapp",
    // Set NODE_ENV to the deploy target (e.g. 'prod', 'staging', etc.)
    NodeKeys.buildEnvironment in Npm := deployEnvironment.value.getOrElse("sbt-dev"),
    mappings in Universal <++= (NodeKeys.nodeProjectTarget in Npm) map directory,
    mappings in Universal <<= (mappings in Universal).dependsOn(NodeKeys.build in Npm)
  )
}
