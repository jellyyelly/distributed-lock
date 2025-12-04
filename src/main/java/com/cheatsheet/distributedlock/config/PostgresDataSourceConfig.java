package com.cheatsheet.distributedlock.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

@Configuration
public class PostgresDataSourceConfig {

    @Bean(name = "postgresDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.postgresql")
    public DataSource postgresDataSource() {
        return DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "postgresJdbcTemplate")
    public JdbcTemplate postgresJdbcTemplate() {
        return new JdbcTemplate(postgresDataSource());
    }
}
