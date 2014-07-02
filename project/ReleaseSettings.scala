import sbt._
import Keys._

import sbtrelease._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.ReleaseKeys._

import java.text.SimpleDateFormat
import java.util.Date

// TODO(markschaake): consider turning this into a standalone plugin
object ReleaseSettings {

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

  lazy val settings = ReleasePlugin.releaseSettings ++ Seq(
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
    },
    // release plugin checks to make sure the publishTo setting is
    // set. However, we're using bintray for publishing and are not
    // using the publishTo setting. To make the release plugin happy,
    // we just set it to a fake resolver value.
    publishTo := Some("fake" at "for sbt-release plugin happiness"))
}
