import sbt._
import Keys._

import sbtrelease._
import sbtrelease.ReleaseStateTransformations._
import sbtrelease.ReleasePlugin.ReleaseKeys._

import java.util.Calendar

// TODO(markschaake): consider turning this into a standalone plugin
object ReleaseSettings {

  private def calendarVersion(cal: Calendar): String = {
    def zeroPad(n: Int) =
      if (n < 10) s"0${n}"
      else s"${n}"

    val year = cal.get(Calendar.YEAR)
    val month = zeroPad(cal.get(Calendar.MONTH) + 1)
    val day = zeroPad(cal.get(Calendar.DAY_OF_MONTH))
    s"${year}.${month}.${day}"
  }

  private def todayVersion = calendarVersion(Calendar.getInstance())

  private def tomorrowVersion = {
    val cal = Calendar.getInstance
    cal.add(Calendar.DATE, 1)
    calendarVersion(cal)
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
