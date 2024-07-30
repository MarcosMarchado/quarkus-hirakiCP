package org.acme;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

@Startup
@ApplicationScoped
public class HirakiCPConfig {

    private static HikariDataSource dataSource;

    @PostConstruct
    public void configuraDataSource(){
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/exampledb?useSSL=false");
        config.setUsername("root");
        config.setPassword("password");
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        // Configurações do pool
        config.setMinimumIdle(100);
        config.setMaximumPoolSize(60); // Ajustar o pool de conexões
        config.setConnectionTimeout(300000); // 5 minutos
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        dataSource = new HikariDataSource(config);
    }

    @Produces
    @Singleton
    public HikariDataSource getDataSource() {
        return dataSource;
    }
}
