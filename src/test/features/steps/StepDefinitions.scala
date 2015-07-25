package features.steps

import org.scalatest.Matchers
import cucumber.api.scala.{ ScalaDsl, EN }
import cucumber.api.DataTable
import cucumber.api.PendingException

class StepDefinitions extends ScalaDsl with EN with Matchers {
  private var cacheKey1 = "cacheKey1"
  private var cacheKey2 = "cacheKey2"

  def runStageAndCacheKey(): Boolean = false

  def getCacheKey(): Option[String] = None

  def generateCacheKey(): Option[String] = {
    runStageAndCacheKey()
    getCacheKey()
  }

  def changeDependencies(): Unit = {}

  def makeGitCommit(): Unit = {}

  def cleanUp(): Unit = {}

  Given("""^we have run the stageAndCacheKey task$""") { () => runStageAndCacheKey() }

  Then("""^the cachekey should exist in the right location$""") { () => generateCacheKey() != None }

  Given("""^we have generated a cachekey$""") { () => cacheKey1 = generateCacheKey() }

  When("""^we generate a cachekey again$""") { () => cacheKey2 = generateCacheKey() }

  Then("""^the cachekeys should be the same$""") { () => cacheKey1 == cacheKey2 }

  When("""^we change the dependencies$""") { () => changeDependencies() }

  Then("""^the cachekeys should be different$""") { () => cacheKey1 != cacheKey2 }

  When("""^we make a git commit to a local dependency$""") { () => makeGitCommit() }

  When("""^we make a git commit to the src directory of the project$""") { () => makeGitCommit() }

  When("""^we clean up the mess we've made$""") { () => cleanUp() }

  Then("""^the tests are over!$""") { () => true }
}
