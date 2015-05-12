DatabasePlugin
====================

**Required by**: None

**Requires**: CoreSettings

The `DatabasePlugin` provides [Slick](http://slick.typesafe.com/) dependencies and [Flyway](http://flywaydb.org/) database migration.

## Usage

### build.sbt set up

```scala
// in build.sbt
enablePlugins(DatabasePlugin)

// you need to provide a JDBC driver for flyway migrations
libraryDependencies += org.allenai.plugins.CoreDependencies.postgresDriver

// Migrations:
// Specify a database URL for performing migrations. Only local dev
// URL should be checked in to source control. Migrating production databases
// should be a carefule one-time event that is preceded by a backup.
flywayUrl := "jdbc:postgresql://localhost:5432/databaseplugin"
flywayUser := "ai2dev"
flywayPassword := "ai2dev"
```

### Migrations

The `DatabasePlugin` provides the `sqlMigrationsInit` task that will create a directory
where you will put your SQL migration scripts. It will also create an initial migration
file with comments to help you understand the conventions.

1. Initialize db-migrations directory:

  ```shell
  $ sbt
  > sqlMigrationsInit
  [info] Generating initial SQL migration file in /Users/markschaake/ai2/git/ai2-sbt-plugins/test-projects/test-database/db-migrations/V1__initial-schema.sql
  [success]

2. If the database does not already exist, make sure to create it (and the user you specify in build.sbt)

3. Run flywayMigrate:

  ```shell
  $ sbt
  > flywayMigrate
  ```

Note: you can always "clean" your local database, but this will remove all tables and data.

  ```shell
  $ sbt
  > ;flywayClean;flywayMigrate
  ```

TODO: add a code generation task to generate Scala source code from table schema.
