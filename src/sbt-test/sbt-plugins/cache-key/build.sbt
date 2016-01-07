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

lazy val webService = project.in(file("webservice")).dependsOn(core).enablePlugins(WebServicePlugin)

def fileAsString(file: File) = scala.io.Source.fromFile(file).getLines.mkString("\n")

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
