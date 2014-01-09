import sbt._
import sbt.Keys._
import scala.util.Try

object TravisPublisher {
  val publishMasterOnTravis = taskKey[Unit]("publish to travis if on master branch")

  /** Publish only if this is a Travis build on branch master and it is not a
    * pull request.
    */
  def publishMasterOnTravisImpl = Def.taskDyn {

    val travis = Try(sys.env("TRAVIS")).getOrElse("false") == "true"

    val pr = Try(sys.env("TRAVIS_PULL_REQUEST")).getOrElse("false") == "true"

    val branch = Try(sys.env("TRAVIS_BRANCH")).getOrElse("??")

    val snapshot = version.value.trim.endsWith("SNAPSHOT")

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
