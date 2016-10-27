package org.allenai.plugins

import org.scalatest.{ BeforeAndAfter, FlatSpecLike, Matchers, OneInstancePerTest }
import sbt.{ Hash, IO }

import java.io.File
import java.nio.file.Files

/** Tests for the plugin utilities. */
class UtilitiesSpec extends FlatSpecLike with Matchers with OneInstancePerTest with BeforeAndAfter {
  val tempDirectory = {
    val directory = Files.createTempDirectory("utilities-test").toFile
    directory.deleteOnExit
    directory
  }

  val fooFile = {
    val foo = new File(tempDirectory, "foo.txt")
    IO.write(foo, "foo")
    foo
  }

  val barFile = {
    val bar = new File(tempDirectory, "bar.txt")
    IO.write(bar, "bar")
    bar
  }

  after {
    tempDirectory.delete()
  }

  "hashFiles" should "return the same hash when called on the same files" in {
    val firstHash = Utilities.hashFiles(Seq(fooFile, barFile), tempDirectory)
    val secondHash = Utilities.hashFiles(Seq(fooFile, barFile), tempDirectory)
    firstHash shouldBe secondHash
  }

  it should "return the same hash when called with files in a different order" in {
    val firstHash = Utilities.hashFiles(Seq(fooFile, barFile), tempDirectory)
    // Swap the file order.
    val secondHash = Utilities.hashFiles(Seq(barFile, fooFile), tempDirectory)
    firstHash shouldBe secondHash
  }

  it should "return the same hash when called with the same files in a different directory" in {
    val firstHash = Utilities.hashFiles(Seq(fooFile, barFile), tempDirectory)

    // Create the same directory structure in a new temp directory.
    val newTempDirectory = Files.createTempDirectory("utilities-test").toFile
    try {
      val newFoo = new File(newTempDirectory, "foo.txt")
      val newBar = new File(newTempDirectory, "bar.txt")
      IO.copyFile(fooFile, newFoo)
      IO.copyFile(barFile, newBar)
      val secondHash = Utilities.hashFiles(Seq(newFoo, newBar), newTempDirectory)

      firstHash shouldBe secondHash
    } finally {
      newTempDirectory.delete()
    }
  }

  it should "return a different hash when a file is renamed" in {
    val firstHash = Utilities.hashFiles(Seq(fooFile, barFile), tempDirectory)

    val newFoo = new File(tempDirectory, "new-foo.txt")
    IO.move(fooFile, newFoo)

    val secondHash = Utilities.hashFiles(Seq(newFoo, barFile), tempDirectory)

    firstHash shouldNot be(secondHash)
  }

  it should "return a different hash when a file's contents change" in {
    val firstHash = Utilities.hashFiles(Seq(fooFile, barFile), tempDirectory)
    IO.write(fooFile, "foo2")

    val secondHash = Utilities.hashFiles(Seq(fooFile, barFile), tempDirectory)

    firstHash shouldNot be(secondHash)
  }
}
