package org.allenai.sbt.webservice

import org.allenai.sbt.core.CoreSettingsPlugin
import org.allenai.sbt.core.CoreDependencies._
import org.allenai.sbt.deploy.DeployPlugin
import spray.revolver.RevolverPlugin.Revolver

import sbt._
import sbt.Keys._

object WebServicePlugin extends AutoPlugin {

  override def requires = DeployPlugin && CoreSettingsPlugin

  override def projectSettings: Seq[Setting[_]] =
    Revolver.settings ++ Seq(
      libraryDependencies ++= Seq(
        akkaActor,
        akkaLogging,
        sprayCan,
        sprayRouting,
        sprayCaching,
        sprayJson,
        typesafeConfig,
        allenAiCommon,
        allenAiTestkit % "test"
      ))
}
