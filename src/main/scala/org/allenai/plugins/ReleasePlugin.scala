package org.allenai.plugins

import sbt._
import sbt.Keys._

import sbtrelease.{ ReleasePlugin => WrappedReleasePlugin, _ }
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.ReleaseKeys._

import java.text.SimpleDateFormat
import java.util.Date

object ReleasePlugin extends AutoPlugin {

  override def requires: Plugins = plugins.JvmPlugin

  object DateVersion {
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

    def releaseVersion(version: String) = {
      val today = todayVersion
      if (version.startsWith(today)) {
        version.replace("-SNAPSHOT", "")
      } else {
        s"${today}-0"
      }
    }

    def nextVersion(version: String) = {
      s"${incrementVersion(version)}-SNAPSHOT"
    }
  }

  object SemanticVersion {
    def nextVersion(version: String) = {
      sbtrelease.Version(version).
        map(_.bump(Version.Bump.Next).asSnapshot.string).
        getOrElse(sbtrelease.versionFormatError)
    }
    def releaseVersion(version: String) = {
      sbtrelease.Version(version).
        map(_.withoutQualifier.string).
        getOrElse(sbtrelease.versionFormatError)
    }
  }

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    WrappedReleasePlugin.releaseSettings ++ Seq(
      releaseVersion := { version => DateVersion.releaseVersion(version) },
      nextVersion := { version => DateVersion.nextVersion(version) }
    )
}
