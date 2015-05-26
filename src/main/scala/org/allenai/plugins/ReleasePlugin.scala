package org.allenai.plugins

import sbt._
import sbt.Keys._

import sbtrelease.{ ReleasePlugin => WrappedReleasePlugin, _ }
import sbtrelease.ReleaseStep
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.ReleaseKeys

import java.text.SimpleDateFormat
import java.util.Date

object ReleasePlugin extends AutoPlugin {
  override def requires: Plugins = plugins.JvmPlugin

  val blacklistedBranches = SettingKey[Seq[String]](
    "blacklistedBranches",
    "Branches that cannot be released from"
  )

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
      ReleaseKeys.releaseVersion := { version => DateVersion.computeReleaseVersion(version) },
      ReleaseKeys.nextVersion := { version => DateVersion.computeNextVersion(version) }
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
      ReleaseKeys.releaseVersion := { version => SemanticVersion.computeReleaseVersion(version) },
      ReleaseKeys.nextVersion := { version => SemanticVersion.computeNextVersion(version) }
    )
  }

  def checkBranchNotBlacklisted(blacklist: Set[String]): State=>State = { st: State =>
    val vcs = Project.extract(st).get(ReleaseKeys.versionControlSystem).getOrElse {
      sys.error("Aborting release. Working directory is not a repository of a recognized VCS.")
    }

    val branch = vcs.currentBranch
    println(branch)
    println(blacklist)
    if (blacklist(vcs.currentBranch)) {
      sys.error(s"Current branch ${branch} is blacklisted.  At AI2, releases are done from " +
        "another branch and then merged via pull request.  Shippable, our continuous build " +
        "system does the actual publishing of the artifacts.")
    }

    st
  }

  override lazy val projectSettings: Seq[Def.Setting[_]] = {
    WrappedReleasePlugin.releaseSettings ++
      SemanticVersion.settings ++ Seq(
        blacklistedBranches := Seq("master"),
        bintray.Keys.repository in bintray.Keys.bintray in ThisBuild := "maven",
        bintray.Keys.bintrayOrganization in bintray.Keys.bintray in ThisBuild := Some("allenai"),
        ReleaseKeys.releaseProcess := Seq[ReleaseStep](
          checkBranchNotBlacklisted(blacklistedBranches.value.toSet),
          checkSnapshotDependencies,
          inquireVersions,
          runTest,
          setReleaseVersion,
          commitReleaseVersion,
          tagRelease,
          setNextVersion,
          commitNextVersion,
          pushChanges
        )
      )
  }
}
