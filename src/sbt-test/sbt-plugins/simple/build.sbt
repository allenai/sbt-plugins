import org.allenai.plugins.StylePlugin.StyleKeys

name := "simple"

// Core project with shared code for testing things.
lazy val core = project.in(file("core"))

val scalaDocSubProject1 = project.in(file("one"))
val scalaDocSubProject2 = project.in(file("two"))
val scalaDocAggregateProject = project.in(file("aggregate"))
  .aggregate(scalaDocSubProject1, scalaDocSubProject2)
  .enablePlugins(ScaladocGenPlugin).settings(
    scaladocGenGitRemoteRepo := "git@github.com:allenai/sbt-plugins.git",
    scaladocGenExtraScaladocMap := scaladocGenExtraScaladocMap.value +
      ("foobar" -> url("http://foobar.com"))
)

val checkStyle = taskKey[Unit]("check style warnings")
checkStyle := {
  def expectCount(expected: Int, actual: Int, msg: String) =
    assert(expected == actual, s"Expected $expected $msg, actual: $actual")

  // core/src/main/scala/Main.scala should have one warning for line length
  val checkResult = StyleKeys.styleCheck.in(core, Compile).value
  expectCount(2, checkResult.files, "files checked")
  expectCount(0, checkResult.errors, "errors")
  expectCount(1, checkResult.warnings, "warnings")

  // core/src/test/scala/MainSpec.scala should have one error for illegal import
  val testCheckResult = StyleKeys.styleCheck.in(core, Test).value
  expectCount(2, testCheckResult.files, "files checked")
  expectCount(1, testCheckResult.errors, "errors")
  expectCount(0, testCheckResult.warnings, "warnings")
}

def fileAsString(file: File) = scala.io.Source.fromFile(file).getLines.mkString("\n")

val checkCompileDoesNotFormat = {
  taskKey[Unit]("validate that compilation does not trigger formatting")
}
checkCompileDoesNotFormat := {
  val filePath = sourceDirectory.in(core, Compile).value / "scala"/ "BadFormat.scala"
  // Dependency on `compile`.
  compile.in(core, Compile).value

  val actual = fileAsString(filePath)
  val expected = fileAsString(new File("BadFormat.scala.unformatted"))
  assert(actual == expected,
    s"format failed: (actual, expected):\nActual:\n$actual\n\nExpected:\n$expected")
}

val checkFormat = taskKey[Unit]("check that format correctly formats")
checkFormat := {
  val filePath = sourceDirectory.in(core, Compile).value / "scala"/ "BadFormat.scala"
  // Dependency on `formalt`.
  format.in(core, Compile).value

  val actual = fileAsString(filePath)
  val expected = fileAsString(new File("BadFormat.scala.formatted-expected"))
  assert(actual == expected,
    s"format failed: (actual, expected):\nActual:\n$actual\n\nExpected:\n$expected")
}
