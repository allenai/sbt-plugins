/** This tests that files in test also get their style checked. It should
  * generate a few warnings.
  * Style rules defined in src/main/resources/allenai-style-config.xml
  */
object MainSpec extends App {
  // error: illegal import
  import scala.collection.JavaConversions._

  // should _not_ warn that the object is not capitalized
  object fooBar {
    // should _not_ warn
    println("this is " + 1)
  }
  val b = 1 + 3
}
