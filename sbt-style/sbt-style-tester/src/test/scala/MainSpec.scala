/** This tests that files in test also get their style checked. It should
  * generate a few warnings. */
object MainSpec extends App {
  object fooBar {
    println("this is "+1)
  }
}
