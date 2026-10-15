package no.nav.helsearbeidsgiver.db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import no.nav.helsearbeidsgiver.Env.getPropertyOrNull
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database

class DatabaseConfig(
    jdbcUrl: String = requiredProperty("NAIS_DATABASE_REFUSJON_REFUSJON_JDBC_URL"),
    username: String = requiredProperty("NAIS_DATABASE_REFUSJON_REFUSJON_USERNAME"),
    password: String = requiredProperty("NAIS_DATABASE_REFUSJON_REFUSJON_PASSWORD"),
) : AutoCloseable {
    private val dataSource =
        HikariDataSource(
            HikariConfig().apply {
                this.jdbcUrl = jdbcUrl
                this.username = username
                this.password = password
                maximumPoolSize = 3
                transactionIsolation = "TRANSACTION_READ_COMMITTED"
                connectionTimeout = 5_000
                validationTimeout = 3_000
                addDataSourceProperty("connectTimeout", "5")
                addDataSourceProperty("socketTimeout", "15")
                addDataSourceProperty("options", "-c statement_timeout=10000 -c lock_timeout=5000")
            },
        )

    val database: Database

    init {
        try {
            Flyway
                .configure()
                .validateMigrationNaming(true)
                .dataSource(dataSource)
                .load()
                .migrate()
            database = Database.connect(dataSource)
        } catch (exception: Exception) {
            dataSource.close()
            throw exception
        }
    }

    override fun close() {
        dataSource.close()
    }
}

private fun requiredProperty(name: String): String = requireNotNull(getPropertyOrNull(name)) { "$name ikke satt" }
