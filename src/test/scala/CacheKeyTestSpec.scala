import org.allenai.common.testkit.UnitSpec
import scala.sys.process._
import scala.io.Source

class CacheKeyTestSpec extends UnitSpec {
  private var cacheKey1: Option[String] = None
  private var cacheKey2: Option[String] = None
  private var gitCommitsMade = 0
  private var dependenciesAdded = 0

  // Helper methods
  def cleanUp(): Unit = {
    // Remove dependency that was added (sed remove last line from file)
    (1 to gitCommitsMade) foreach (x => Seq("sed", "-i", "$ d", "build.sbt").!!)
    // Remove git commit that was added (git remove last commit & reset file state)
    (1 to dependenciesAdded) foreach (x => Seq("git", "reset", "--hard", "HEAD~1").!!)
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

  def runStageAndCacheKey(): Boolean = {
    Process(Seq("sbt", ";clean", ";stageAndCacheKey"), new java.io.File("./test-projects/test-deploy")).!!
    true
  }

  def getCacheKey(): Option[String] = {
    try {
      Some(Source.fromFile("./test-projects/test-deploy/service/target/universal/stage/conf/cacheKey.Sha1").getLines.mkString)
    } catch { case _ => None }
  }

  def areCacheKeysEqual() = cacheKey1 == cacheKey2

  "A cachekey" should "be injected properly" in {
    val cacheKey = generateCacheKey()
    assert(cacheKey != None)
  }

  "A cachekey" should "be the same on rebuild" in {
    val cacheKey1 = generateCacheKey()
    val cacheKey2 = generateCacheKey()
    assert(cacheKey1 == cacheKey2)
  }

  "A cachekey" should "change on dependency changes" in {
    val cacheKey1 = generateCacheKey()
    addADependency()
    val cacheKey2 = generateCacheKey()
    assert(cacheKey1 != cacheKey2)
  }

  "A cachekey" should "change on git commits to local dependencies" in {
    val cacheKey1 = generateCacheKey()
    makeGitCommit()
    val cacheKey2 = generateCacheKey()
    assert(cacheKey1 != cacheKey2)
  }

  "A cachekey" should "change on git commits to src dir of project"{
    val cacheKey1 = generateCacheKey()
    makeGitCommit()
    val cacheKey2 = generateCacheKey()
    assert(cacheKey1 != cacheKey2)
  }
}
