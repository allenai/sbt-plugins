package org.allenai.plugins.archetypes

import org.allenai.plugins.CoreSettingsPlugin
import org.allenai.plugins.CoreDependencies._
import org.allenai.plugins.DeployPlugin
import spray.revolver.RevolverPlugin.Revolver

import sbt._
import sbt.Keys._

object WebServicePlugin extends AutoPlugin {

  override def requires: Plugins = DeployPlugin && CoreSettingsPlugin

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
