

import cucumber.api.scala.{ ScalaDsl, EN }
import org.scalatest.matchers.MustMatchers
import cucumber.api.PendingException

import scala.sys.process._
class StepDefinitions extends ScalaDsl with EN with MustMatchers {
  private var cacheKey1 = Option("cacheKey1")
  private var cacheKey2 = Option("cacheKey2")

  def runStageAndCacheKey(): Boolean = {
    Process(Seq("sbt", ";clean", ";stageAndCacheKey"), new java.io.File("./test-projects/test-deploy")).!!
    true
  }

  def getCacheKey(): Option[String] = {
    import scala.io.Source
    try {
      Some(Source.fromFile("./test-projects/test-deploy/service/target/universal/stage/conf/cacheKey.Sha1").getLines.mkString)
    } catch { case _ => None }
  }

  def generateCacheKey(): Option[String] = {
    runStageAndCacheKey()
    getCacheKey()
  }

  def addADependency(): Unit = {
    Seq("bash", "-c", "echo libraryDependencies += \\\"org.apache.derby\\\" % \\\"derby\\\" % \\\"10.4.1.3\\\" >> build.sbt").!!
  }

  def makeGitCommit(): Unit = {
    Seq("bash", "-c", "echo \"something\" >> test-projects/test-deploy/service/src/main/resources/forCommit.txt").!!
    Seq("git", "add", "test-projects/test-deploy/service/src/main/resources/forCommit.txt").!!
    Seq("git", "commit", "-m", "\"commit for testing \"").!!
  }

  def cleanUp(): Unit = {
    // Remove dependency that was added (sed remove last line from file)
    Seq("sed", "-i", "$ d", "build.sbt").!!
    // Remove git commit that was added (git remove last commit & reset file state)
    Seq("git", "reset", "--hard", "HEAD~1").!!
  }

  Given("""^we have run the stageAndCacheKey task$""") { () => runStageAndCacheKey() }

  Then("""^the cachekey should exist in the right location$""") { () => getCacheKey() != None }

  Given("""^we have generated a cachekey$""") { () => cacheKey1 = generateCacheKey() }

  When("""^we generate a cachekey again$""") { () => cacheKey2 = generateCacheKey() }

  Then("""^the cachekeys should be the same$""") { () => cacheKey1 == cacheKey2 }

  When("""^we change the dependencies$""") { () => addADependency() }

  Then("""^the cachekeys should be different$""") { () => cacheKey1 != cacheKey2 }

  When("""^we make a git commit to a local dependency$""") { () => makeGitCommit() }

  When("""^we make a git commit to the src directory of the project$""") { () => makeGitCommit() }

  When("""^we clean up the mess we've made$""") { () => cleanUp() }

  Then("""^the tests are over!$""") { () => true }
}
