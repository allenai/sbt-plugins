import sbt._
import Keys._
import com.typesafe.sbt.SbtScalariform.scalariformSettings
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._
import sbtrelease.ReleasePlugin._

object BuildSettings {

  lazy val basicSettings = releaseSettings ++ scalariformSettings ++ Seq(
    isSnapshot := version.value.trim.endsWith("SNAPSHOT"),
    organization := "org.allenai.plugins",
    scalacOptions := Seq(
      "-encoding", "utf8",
      "-feature",
      "-unchecked",
      "-deprecation",
      //"-target:jvm-1.7",
      "-language:_",
      "-Xlog-reflective-calls"),
    scalaVersion := "2.10.4",
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(MultilineScaladocCommentsStartOnFirstLine, true)
      .setPreference(PlaceScaladocAsterisksBeneathSecondAsterisk, true))

  lazy val sbtPluginSettings = basicSettings ++ ai2PublishSettings ++ Seq(
    sbtPlugin := true
  )

  lazy val noPublishing = Seq(
    publish := (),
    publishLocal := (),
    // required until these tickets are closed https://github.com/sbt/sbt-pgp/issues/42,
    // https://github.com/sbt/sbt-pgp/issues/36
    publishTo := None
  )

  // TODO: consider moving publishSettings into its own plugin
  // It may make sense to ultimately have a single "sbt-ai2-settings"" plugin that covers
  // version, publish, and scalariform
  lazy val ai2PublishSettings = Seq(
    credentials += Credentials("Sonatype Nexus Repository Manager",
                               "utility.allenai.org",
                               "deployment",
                               "answermyquery"),
    publishTo <<= isSnapshot { isSnap =>
      val nexus = s"http://utility.allenai.org:8081/nexus/content/repositories/"
      if(isSnap)
        Some("snapshots" at nexus + "snapshots")
      else
        Some("releases"  at nexus + "releases")
    })

  lazy val sonatypePublishSettings = Seq(
    credentials += Credentials("Sonatype Nexus Repository Manager",
                               "oss.sonatype.org",
                               "marksai2",
                               "answermyquery"),
    publishTo <<= isSnapshot { isSnap =>
      if(isSnap)
        Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
      else
        Some("releases"  at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    })

}
