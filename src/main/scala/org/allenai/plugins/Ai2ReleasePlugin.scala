package org.allenai.plugins

import sbt._
import sbt.Keys._

import bintray.BintrayKeys

import sbtrelease.ReleasePlugin
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtrelease.Version

import java.text.SimpleDateFormat
import java.util.Date

object Ai2ReleasePlugin extends AutoPlugin {

  override def requires: Plugins = plugins.JvmPlugin && ReleasePlugin

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
      releaseNextVersion := { version => DateVersion.computeNextVersion(version) }
    )
  }

  object SemanticVersion {
    def computeNextVersion(version: String) = {
      Version(version).
        map(_.bump(Version.Bump.Next).asSnapshot.string).
        getOrElse(sbtrelease.versionFormatError)
    }

    def computeReleaseVersion(version: String) = {
      Version(version).
        map(_.withoutQualifier.string).
        getOrElse(sbtrelease.versionFormatError)
    }

    def settings: Seq[Def.Setting[_]] = Seq(
      releaseVersion := { version => SemanticVersion.computeReleaseVersion(version) },
      releaseNextVersion := { version => SemanticVersion.computeNextVersion(version) }
    )
  }

  val checkBranchIsNotMaster: State => State = { st: State =>
    val vcs = Project.extract(st).get(releaseVcs).getOrElse {
      sys.error("Aborting release. Working directory is not a repository of a recognized VCS.")
    }

    if (vcs.currentBranch == "master") {
      sys.error("Current branch is master.  At AI2, releases are done from another branch and " +
        "then merged into master via pull request.  Shippable, our continuous build system does " +
        "the actual publishing of the artifacts.")
    }

    st
  }

  override lazy val projectSettings: Seq[Def.Setting[_]] = {
    SemanticVersion.settings ++ Seq(
      BintrayKeys.bintrayRepository in ThisBuild := "maven",
      BintrayKeys.bintrayOrganization in ThisBuild := Some("allenai"),
      releaseProcess := Seq[ReleaseStep](
        checkBranchIsNotMaster,
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
