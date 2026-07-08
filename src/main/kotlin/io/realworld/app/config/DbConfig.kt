package io.realworld.app.config

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.h2.tools.Server
import org.jetbrains.exposed.sql.Database

object DbConfig {
    // The H2 PG server binds a fixed port, so start it at most once per JVM. Without this guard the
    // second call to setup() (e.g. the second integration test in a class) fails to bind the port.
    private var pgServerStarted = false

    fun setup(jdbcUrl: String, username: String, password: String) {
        if (!pgServerStarted) {
            Server.createPgServer().start()
            pgServerStarted = true
        }
        val config = HikariConfig().also { config ->
            config.jdbcUrl = jdbcUrl
            config.username = username
            config.password = password
        }
        Database.connect(HikariDataSource(config))
    }
}
