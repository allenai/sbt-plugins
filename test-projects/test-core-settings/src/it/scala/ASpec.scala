package test

class ASpec {
  def sayHiFromShared: Unit = println(SharedTestHelper.sayHi)
}
