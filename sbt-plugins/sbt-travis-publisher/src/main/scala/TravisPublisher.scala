import sbt._
import sbt.Keys._
import scala.util.Try

object TravisPublisher {
  val publishMasterOnTravis = taskKey[Unit]("publish to travis if on master branch")

  val sonatypePublishToSetting =
    publishTo := {
      if (isSnapshot.value)
        Some("snapshots" at "https://oss.sonatype.org/content/repositories/snapshots")
      else
        Some("releases" at "https://oss.sonatype.org/service/local/staging/deploy/maven2")
    }

  val ai2PublishToSetting = {
    val ai2NexusHost = "utility.allenai.org"
    val ai2NexusUrl = s"http://${ai2NexusHost}:8081/nexus/content/repositories/"
    publishTo := {
      if (isSnapshot.value)
        Some("snapshots" at ai2NexusUrl + "content/repositories/snapshots")
      else
        Some("releases" at ai2NexusUrl + "service/local/staging/deploy/maven2")
    }
  }

  /** Publish only if this is a Travis build on branch master and it is not a
    * pull request.
    */
  def publishMasterOnTravisImpl = Def.taskDyn {

    val travis = Try(sys.env("TRAVIS")).getOrElse("false") == "true"

    val pr = Try(sys.env("TRAVIS_PULL_REQUEST")).getOrElse("") != "false"

    val branch = Try(sys.env("TRAVIS_BRANCH")).getOrElse("??")

    val commit = Try(sys.env("TRAVIS_COMMIT")).getOrElse("??")

    val snapshot = version.value.trim.endsWith("SNAPSHOT")

    println {
      "Travis Environment:\n\t" +
        s"On Travis: $travis\n\t" +
        s"Is pull request: $pr\n\t" +
        s"Branch: $branch\n\t" +
        s"Snapshot: $snapshot\n\t" +
        s"Commit: $commit"
    }

    if (travis && !pr && branch == "master" && snapshot) {
      println("Publishing master on travis: " + name.value)
      publish
    } else if (!travis) {
      println(s"Not publishing on travis because this is not a travis build: " + name.value)
      Def.task()
    } else if (branch != "master") {
      println(s"Not publishing on travis because branch '$branch' is not 'master': " + name.value)
      Def.task()
    } else if (pr) {
      println(s"Not publishing on travis because this is a pull request: " + name.value)
      Def.task()
    } else if (!snapshot) {
      println(s"Not publishing on travis because this is not a SNAPSHOT version: " + name.value)
      Def.task()
    } else {
      println(s"Not publishing on travis for an unknown reason: " + name.value)
      Def.task()
    }
  }

  def settings = Seq(publishMasterOnTravis := publishMasterOnTravisImpl.value)
}
