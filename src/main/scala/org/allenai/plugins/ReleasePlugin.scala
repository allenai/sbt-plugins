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

    def computeReleaseVersion(version: String) = {
      val today = todayVersion
      if (version.startsWith(today)) {
        version.replace("-SNAPSHOT", "")
      } else {
        s"${today}-0"
      }
    }

    def computeNextVersion(version: String) = {
      s"${incrementVersion(version)}-SNAPSHOT"
    }

    def settings: Seq[Def.Setting[_]] = Seq(
      releaseVersion := { version => DateVersion.computeReleaseVersion(version) },
      nextVersion := { version => DateVersion.computeNextVersion(version) }
    )
  }

  object SemanticVersion {
    def computeNextVersion(version: String) = {
      sbtrelease.Version(version).
        map(_.bump(Version.Bump.Next).asSnapshot.string).
        getOrElse(sbtrelease.versionFormatError)
    }

    def computeReleaseVersion(version: String) = {
      sbtrelease.Version(version).
        map(_.withoutQualifier.string).
        getOrElse(sbtrelease.versionFormatError)
    }

    def settings: Seq[Def.Setting[_]] = Seq(
      releaseVersion := { version => SemanticVersion.computeReleaseVersion(version) },
      nextVersion := { version => SemanticVersion.computeNextVersion(version) }
    )
  }

  override lazy val projectSettings: Seq[Def.Setting[_]] =
    WrappedReleasePlugin.releaseSettings ++ DateVersion.settings
}
