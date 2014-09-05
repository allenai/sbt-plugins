package org.allenai.sbt.webservice

import org.allenai.sbt.core.CoreDependencies._

import spray.revolver.RevolverPlugin.Revolver

import sbt._
import sbt.Keys._

object WebServicePlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  override def projectSettings: Seq[Setting[_]] =
    Revolver.settings ++ Seq(
      libraryDependencies ++= Seq(
        akkaActor,
        akkaLogging,
        sprayCan,
        sprayRouting,
        sprayJson))
}
