package org.allenai.plugins

import sbt._
import sbt.Keys._

/** Declares common dependencies with specific versions to encourage
  * consistency accross projects
  */
trait CoreDependencies {
  val defaultScalaVersion = "2.11.5"

  object Logging {
    val slf4jVersion = "1.7.10"
    val logbackVersion = "1.1.2"
    // The logging API to use. This should be the only logging dependency of any API artifact
    // (anything that's going to be depended on outside of this SBT project).
    val slf4jApi = "org.slf4j" % "slf4j-api" % slf4jVersion
    val logbackCore = "ch.qos.logback" % "logback-core" % logbackVersion
    val logbackClassic = "ch.qos.logback" % "logback-classic" % logbackVersion
  }

  // slf4j implementation (logback), and the log4j -> slf4j bridge.
  // This should be called on libraryDependencies like:
  // addLoggingDependencies(libraryDependencies)
  // TODO(markschaake&jkinkead): more comments about what is going on here
  def addLoggingDependencies(deps: SettingKey[Seq[ModuleID]]): Seq[Setting[Seq[ModuleID]]] = {
    import Logging._
    val cleanedDeps = deps ~= { seq =>
      seq map { module =>
        // Exclude the transitive dependencies that might mess things up for us.
        // slf4j replaces log4j.
        (module
          exclude ("log4j", "log4j")
          exclude ("commons-logging", "commons-logging")
          // We're using logback as the slf4j implementation, and we're providing it below.
          exclude ("org.slf4j", "slf4j-log4j12")
          exclude ("org.slf4j", "slf4j-jdk14")
          exclude ("org.slf4j", "slf4j-jcl")
          exclude ("org.slf4j", "slf4j-simple")
          // We'll explicitly provide the logback version; this avoids having to do an override.
          exclude ("ch.qos.logback", "logback-core")
          exclude ("ch.qos.logback", "logback-classic")
          // We add bridges explicitly as well
          exclude ("org.slf4j", "log4j-over-slf4j")
          exclude ("org.slf4j", "jcl-over-slf4j"))
      }
    }
    // Now, add the logging libraries.
    val logbackDeps = deps ++= Seq(
      slf4jApi,
      // Bridge log4j logging to slf4j.
      "org.slf4j" % "log4j-over-slf4j" % slf4jVersion,
      // Bridge jcl logging to slf4j.
      "org.slf4j" % "jcl-over-slf4j" % slf4jVersion,
      // Use logback for the implementation.
      logbackCore,
      logbackClassic
    )
    Seq(cleanedDeps, logbackDeps)
  }

  /* ==========>  AI2 common libraries <========== */

  /** Global version for all allenAi common modules.
    * Override this value before referencing any of the common modules to set the version globally.
    *
    * E.g.
    * format: OFF
    * {{{
    * // in project/Dependencies.scala
    * import org.allenai.plugins.CoreDependencies
    *
    * object Dependencies extends CoreDependencies {
    *   // purposefully downgrade all common modules to a previous version
    *   override val allenAiCommonVersion = "1.1.2"
    * }
    *
    * // in build.sbt
    * libraryDependencies += Dependencies.allenAiIndexing
    * }}}
    */
  val allenAiCommonVersion = "1.2.1"

  def allenAiCommonModule(name: String) = "org.allenai.common" %% s"common-$name" % allenAiCommonVersion

  lazy val allenAiCommon = allenAiCommonModule("core")
  lazy val allenAiGuice = allenAiCommonModule("guice")
  lazy val allenAiTestkit = allenAiCommonModule("testkit")
  lazy val allenAiWebapp = allenAiCommonModule("webapp")
  lazy val allenAiIndexing = allenAiCommonModule("indexing")
  lazy val allenAiCache = {
    val splitVersion = allenAiCommonVersion.split('.')
    require(
      splitVersion(0).toInt >= 1 && splitVersion(1).toInt >= 2,
      "The 'cache' module of allenai/common only exists in versions >= 1.2.0"
    )
    allenAiCommonModule("cache")
  }

  val scopt = "com.github.scopt" %% "scopt" % "3.3.0"
  val typesafeConfig = "com.typesafe" % "config" % "1.2.1"

  /* ==========>  Akka <========== */

  val defaultAkkaVersion = "2.4.1"

  /** Generates an akka module dependency
    * @param id The akka module ID. E.g. `actor` or `cluster`
    * @param version Specific akka version. Defaults to `defaultAkkaVersion`
    */
  def akkaModule(id: String, version: String = defaultAkkaVersion): ModuleID =
    "com.typesafe.akka" %% s"akka-$id" % version

  val akkaActor = akkaModule("actor") exclude ("com.typesafe", "config")
  val akkaLogging = akkaModule("slf4j")
  val akkaTestkit = akkaModule("testkit")

  // Akka HTTP is still experimental, but we are starting to use it in some projects.
  // Once Akka HTTP is no longer experimental, we should remove the spray dependencies
  val defaultAkkaHttpVersion = "2.0.1"
  def akkaHttpModule(id: String, version: String = defaultAkkaHttpVersion): ModuleID =
    "com.typesafe.akka" %% s"akka-http-$id" % version
  val akkaHttp = akkaHttpModule("experimental")
  val akkaHttpTestkit = akkaHttpModule("testkit-experimental")
  val akkaHttpSprayJsonSupport = akkaHttpModule("spray-json-experimental")

  /* ==========>  Spray <========== */

  val sprayVersion = "1.3.3"
  def sprayModule(id: String): ModuleID = "io.spray" %% s"spray-$id" % sprayVersion
  val sprayCan = sprayModule("can")
  val sprayRouting = sprayModule("routing")
  val sprayClient = sprayModule("client")
  val sprayTestkit = sprayModule("testkit")
  val sprayCaching = sprayModule("caching")

  // Spray json (separate from Spray toolkit)
  val sprayJson = "io.spray" %% "spray-json" % "1.3.2"

  /* ==========>  Slick <========== */

  // Slick for database integration
  // TODO consider upgrading to slick 3.0. All of our existing projects use
  // slick 2.1.0, but we should considering migrating to 3.0.
  val defaultSlickVersion = "2.1.0"

  /** Generates a slick module dependency */
  def slickModule(id: String, version: String = defaultSlickVersion): ModuleID =
    "com.typesafe.slick" %% id % version

  val slick = slickModule("slick")
  val slickCodegen = slickModule("slick-codegen")

  val postgresDriver = "org.postgresql" % "postgresql" % "9.4-1201-jdbc41"

  val loggingDependencyOverrides = Set(
    Logging.slf4jApi,
    Logging.logbackCore,
    Logging.logbackClassic
  )
}

object CoreDependencies extends CoreDependencies
