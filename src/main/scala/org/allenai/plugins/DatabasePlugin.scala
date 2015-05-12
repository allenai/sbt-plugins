package org.allenai.plugins

import sbt._
import sbt.Keys._

import org.flywaydb.sbt.FlywayPlugin

object DatabasePlugin extends AutoPlugin {

  object autoImport {
    val sqlMigrationsDir = settingKey[File]("Directory containing SQL migrations")
    val sqlMigrationsInit = taskKey[Unit](
      "Creates db-migrations directory and seeds it with an inital migration"
    )
  }

  import autoImport._

  override def requires: Plugins = CoreSettingsPlugin

  override def projectSettings: Seq[Setting[_]] = FlywayPlugin.flywaySettings ++ Seq(
    libraryDependencies += CoreDependencies.slick,
    sqlMigrationsDir := baseDirectory.value / "db-migrations",
    FlywayPlugin.flywayLocations := {
      val sqlDir = sqlMigrationsDir.value
      Seq(s"filesystem:${sqlDir.getAbsolutePath}")
    },
    sqlMigrationsInit := {
      val sqlDir = sqlMigrationsDir.value
      val log = streams.value.log
      sqlDir.mkdirs()
      if (sqlDir.listFiles.isEmpty) {
        val initialSqlFile = new File(sqlDir, "V1__initial-schema.sql")
        log.info(s"Generating initial SQL migration file in ${initialSqlFile.toString}")
        val stubbedFileContents =
          """|-- All migration files must be named in the format:
             |-- V[version]__some-descriptive-text.sql
             |
             |-- Put your initial database schema here:
             |-- CREATE TABLE IF NOT EXISTS foo (
             |--
             |-- );
             |""".stripMargin
        IO.write(initialSqlFile, stubbedFileContents)
      } else {
        log.warn(s"${sqlDir.toString} already exists and contains migrations.")
      }
    }
  )
}
