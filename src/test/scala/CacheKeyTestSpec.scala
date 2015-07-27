import org.scalatest._

import scala.sys.process._
class cacheKeyTest {
  private var cacheKey1 = Option("cacheKey1")
  private var cacheKey2 = Option("cacheKey2")
  private var gitCommitsMade = 0
  private var dependenciesAdded = 0

  def cleanUp(): Unit = {
    // Remove dependency that was added (sed remove last line from file)
    (1 to gitCommitsMade) foreach (x => Seq("sed", "-i", "$ d", "build.sbt").!!)
    // Remove git commit that was added (git remove last commit & reset file state)
    (1 to dependenciesAdded) foreach (x => Seq("git", "reset", "--hard", "HEAD~1").!!)
  }

  def generateCacheKey1() =  { cacheKey1 = generateCacheKey() }

  def generateCacheKey2() =  { cacheKey2 = generateCacheKey() }

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

  def areCacheKeysEqual() = cacheKey1 == cacheKey2
}

class CacheKeyTestSpec extends FeatureSpec with GivenWhenThen {
  private var cacheKey1 = Option("cacheKey1")
  private var cacheKey2 = Option("cacheKey2")


  feature("CacheKey Generation") {
    scenario("CacheKeys remain the same on rebuild") {
      Given("we have generated a cacheKey")
      val cacheKeyTest = new CacheKeyTest
      cacheKeyTest.generateCacheKey1()

      Then("the cachekey should exist in the right location")
      assert(cacheKeyTest.getCacheKey() != None)     
    }

    scenario("CacheKeys remain the same on rebuild") {
      Given("we have generated a cacheKey")
      val cacheKeyTest = new CacheKeyTest
      cacheKeyTest.generateCacheKey1()
      
      When("we generate a cacheKey again")
      cacheKeyTest.generateCacheKey2()  
      
      Then("the cachekeys should be different")
      assert(!cacheKeyTest.areCacheKeysEqual())
    }

    scenario("CacheKeys change on dependency changes") {
      Given("we have generated a cacheKey")
      val cacheKeyTest = new CacheKeyTest
      cacheKeyTest.generateCacheKey1()

      When("we change the dependencies and generate a cacheKey again")
      cacheKeyTest.addDependency()
      cacheKeyTest.generateCacheKey2()

      Then("the cachekeys should be different")
      assert(!cacheKeyTest.areCacheKeysEqual()
    }
    
    scenario("CacheKeys change on git commits to local dependencies") {
      Given("we have generated a cacheKey")
      val cacheKeyTest = new CacheKeyTest
      cacheKeyTest.generateCacheKey1()
      
      When("we make a git commit to a local dependency and generate a cacheKey again")
      cacheKeyTest.makeGitCommit()
      cacheKeyTest.generateCacheKey2()

      Then("the cacheKeys should be different")
      assert(!cacheKeyTest.areCacheKeysEqual())
    }

    scenario("CacheKeys change on git commits to src dir of project") {
      Given("we have generated a cacheKey")
      val cacheKeyTest = new CacheKeyTest
      cacheKeyTest.generateCacheKey1()
      
      When("we make a git commit to src dir and generate a cacheKey again")
      cacheKeyTest.makeGitCommit()
      cacheKeyTest.generateCacheKey2()

      Then("the cacheKeys should be different")
      assert(!cacheKeyTest.areCacheKeysEqual())
    }


  }

