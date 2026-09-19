package com.example.analyzeproject.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.util.StringUtils;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url:#{null}}")
    private String configuredUrl;

    @Value("${spring.datasource.username:#{null}}")
    private String configuredUsername;

    @Value("${spring.datasource.password:#{null}}")
    private String configuredPassword;

    @Value("${spring.datasource.driver-class-name:org.postgresql.Driver}")
    private String driverClassName;

    @Value("${spring.datasource.hikari.maximum-pool-size:5}")
    private int maxPoolSize;

    @Value("${spring.datasource.hikari.minimum-idle:2}")
    private int minIdle;

    @Bean
    @Primary
    public DataSource dataSource(DataSourceProperties properties) {
        String databaseUrl = System.getenv("DATABASE_URL");
        if (!StringUtils.hasText(databaseUrl)) {
            databaseUrl = System.getenv("JDBC_DATABASE_URL");
        }
        if (!StringUtils.hasText(databaseUrl)) {
            databaseUrl = configuredUrl;
        }

        HikariConfig config = new HikariConfig();
        config.setDriverClassName(driverClassName);
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(minIdle);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(30000);

        // If database URL comes from Render (e.g., postgres://username:password@host:port/database)
        if (StringUtils.hasText(databaseUrl) && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            try {
                URI uri = new URI(databaseUrl);
                String host = uri.getHost();
                int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                String path = uri.getPath();
                String jdbcUrl = "jdbc:postgresql://" + host + ":" + port + path;

                config.setJdbcUrl(jdbcUrl);

                String userInfo = uri.getUserInfo();
                if (userInfo != null && userInfo.contains(":")) {
                    String[] credentials = userInfo.split(":", 2);
                    config.setUsername(credentials[0]);
                    config.setPassword(credentials[1]);
                } else if (userInfo != null) {
                    config.setUsername(userInfo);
                    if (StringUtils.hasText(configuredPassword)) {
                        config.setPassword(configuredPassword);
                    }
                } else {
                    config.setUsername(configuredUsername);
                    config.setPassword(configuredPassword);
                }
                return new HikariDataSource(config);
            } catch (Exception e) {
                // Fallback to standard property resolution
            }
        }

        // Standard configuration (from application.properties)
        config.setJdbcUrl(properties.determineUrl());
        config.setUsername(properties.determineUsername());
        config.setPassword(properties.determinePassword());
        return new HikariDataSource(config);
    }
}
