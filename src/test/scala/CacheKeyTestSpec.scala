import org.scalatest.{ BeforeAndAfter, FlatSpecLike, Matchers, OneInstancePerTest }
import scala.sys.process._
import scala.io.Source

class CacheKeyTestSpec extends FlatSpecLike with Matchers with OneInstancePerTest with BeforeAndAfter {
  private var cacheKey1: Option[String] = None
  private var cacheKey2: Option[String] = None
  private var originalGitCommit: String = ""

  // Helper methods
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

  before {
    originalGitCommit = Seq("git", "log", "--pretty=format:%H", "-n1").!!.trim
  }

  after {
    Seq("git", "reset", "--hard", originalGitCommit).!!
  }

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
