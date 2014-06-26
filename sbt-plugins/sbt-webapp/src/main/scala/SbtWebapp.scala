package org.allenai.sbt.webapp

import org.allenai.sbt.deploy.DeployPlugin
import org.allenai.sbt.nodejs.NodeJsPlugin
import org.allenai.sbt.nodejs.NodeJsPlugin.autoImport._

import com.typesafe.sbt.SbtNativePackager._
import com.typesafe.sbt.SbtNativePackager.NativePackagerHelper._
import sbt._
import sbt.Keys._

object WebappPlugin extends AutoPlugin {

  override def requires = NodeJsPlugin && DeployPlugin

  override def projectSettings: Seq[Setting[_]] = Seq(
    NodeKeys.npmRoot in Npm := (baseDirectory in thisProject).value / "webapp",
    
      mappings in Universal <++= (NodeKeys.buildDir in Npm) map directory,
      mappings in Universal <<= (mappings in Universal).dependsOn(NodeKeys.build in Npm))

}
