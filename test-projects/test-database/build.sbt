enablePlugins(DatabasePlugin)

// You have to provide the database driver that corresponds to the JDBC URL
libraryDependencies += org.allenai.plugins.CoreDependencies.postgresDriver

// In order migratinos to work, you need to have postgres running
// at the following URL, and have a database already created
// named 'databaseplugin'
flywayUrl := "jdbc:postgresql://localhost:5432/databaseplugin"

// You must have alredy created the 'ai2dev' user. You can do so
// in psql with the following statement:
// $ sudo -u postgres psql -c "CREATE ROLE ai2dev PASSWORD 'ai2dev' SUPERUSER CREATEDB CREATEROLE INHERIT LOGIN;"
flywayUser := "ai2dev"
flywayPassword := "ai2dev"
