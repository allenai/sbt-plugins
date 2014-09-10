package org.allenai.sbt.webapp

import org.allenai.sbt.webservice.WebServicePlugin
import org.allenai.sbt.nodejs.NodeJsPlugin
import org.allenai.sbt.nodejs.NodeJsPlugin.autoImport._

import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtNativePackager.NativePackagerHelper._
import spray.revolver.RevolverPlugin.Revolver

import sbt._
import sbt.Keys._

object WebappPlugin extends AutoPlugin {

  override def requires = WebServicePlugin && NodeJsPlugin

  override lazy val projectSettings: Seq[Setting[_]] = Seq(
    NodeKeys.nodeProjectDir in Npm := (baseDirectory in thisProject).value / "webapp",
    // Force npm:build when using sbt-revolver re-start to ensure UI is built
    Revolver.reStart <<= (Revolver.reStart).dependsOn(NodeKeys.build in Npm),
    mappings in Universal <++= (NodeKeys.nodeProjectTarget in Npm) map directory,
    mappings in Universal <<= (mappings in Universal).dependsOn(NodeKeys.build in Npm))
}
