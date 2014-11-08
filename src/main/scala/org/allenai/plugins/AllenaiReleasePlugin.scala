package org.allenai.plugins

import sbt._
import sbt.Keys._

import sbtrelease._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.ReleaseKeys._

import java.text.SimpleDateFormat
import java.util.Date

object AllenaiReleasePlugin extends AutoPlugin {

  override def requires = plugins.JvmPlugin

  private def todayVersion: String = {
    val df = new SimpleDateFormat("yyyy.MM.dd")
    df.format(new Date())
  }

  val VersionPattern = """(\d+\.\d+\.\d+)-(\d+)(?:-SNAPSHOT)?""".r

  def incrementVersion(prev: String): String = {
    prev match {
      case VersionPattern(prefix, num) => s"${prefix}-${num.toInt + 1}"
      case _ => throw new IllegalStateException(s"Invalid version number: ${prev}")
    }
  }

  override lazy val projectSettings = ReleasePlugin.releaseSettings ++ Seq(
    releaseVersion := { ver =>
      val today = todayVersion
      if (ver.startsWith(today)) {
        ver.replace("-SNAPSHOT", "")
      } else {
        s"${today}-0"
      }
    },
    nextVersion := { ver =>
      s"${incrementVersion(ver)}-SNAPSHOT"
    })
}
