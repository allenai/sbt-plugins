package foo

/**
 * This File is incorrectly formatted
 *
 *
 */
object BadFormat {
    // indentation wrong
  val tooMuchSpace   = 10


  // colon spacing needs fixup
  case class Foo(str:String, int :Int)

  // too much body space follows


}
