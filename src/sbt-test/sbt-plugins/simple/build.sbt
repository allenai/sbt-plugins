import Dependencies._

import org.allenai.plugins.StylePlugin.StyleKeys

name := "simple"

lazy val core = project.in(file("core")).settings(
  libraryDependencies ++= Seq(
    jodaTime, // declared in Dependencies
    sprayCan, // declared in CoreDependencies
    sprayRouting,
    akkaActor,
    sprayJson
  )
)

lazy val cli = project.in(file("cli")).dependsOn(core).enablePlugins(CliPlugin)

lazy val webService = project.in(file("webservice")).dependsOn(core).enablePlugins(WebServicePlugin)

lazy val webApp = project.in(file("webapp")).dependsOn(core).enablePlugins(WebappPlugin)
  .settings(NodeKeys.nodeProjectDir in Npm := file("client"))

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
  val checkResult = (StyleKeys.styleCheck in (core, Compile)).value
  expectCount(2, checkResult.files, "files checked")
  expectCount(0, checkResult.errors, "errors")
  expectCount(1, checkResult.warnings, "warnings")

  // core/src/test/scala/MainSpec.scala should have one error for illegal import
  val testCheckResult = (StyleKeys.styleCheck in (core, Test)).value
  expectCount(2, testCheckResult.files, "files checked")
  expectCount(1, testCheckResult.errors, "errors")
  expectCount(0, testCheckResult.warnings, "warnings")
}

def fileAsString(file: File) = scala.io.Source.fromFile(file).getLines.mkString("\n")

val checkFormat = taskKey[Unit]("check formatting")
checkFormat := {
  val actual = fileAsString((format in (core, Compile)).value.find(_.getName == "BadFormat.scala").get)
  val expected = fileAsString(new File("BadFormat.scala.formatted-expected"))
  assert(actual == expected, s"format failed: (actual, expected):\nActual:\n$actual\n\nExpected:\n$expected")
}

val assertCacheKeysEqual = taskKey[Unit]("Assert that cacheKey1.Sha1 == cacheKey2.Sha1")
assertCacheKeysEqual := {
  val cacheKey1 = fileAsString(file("cacheKey1.Sha1"))
  val cacheKey2 = fileAsString(file("cacheKey2.Sha1"))
  assert(cacheKey1 == cacheKey2, s"Cache keys were not equal:\ncacheKey1 : $cacheKey1\ncacheKey2: $cacheKey2")
}

val assertCacheKeysNotEqual = taskKey[Unit]("Assert that cacheKey1.Sha1 == cacheKey2.Sha1")
assertCacheKeysNotEqual := {
  val cacheKey1 = fileAsString(file("cacheKey1.Sha1"))
  val cacheKey2 = fileAsString(file("cacheKey2.Sha1"))
  assert(cacheKey1 != cacheKey2, "Cache keys were equal")
}
