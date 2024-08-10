package hotfies.perfectpartysystem.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import hotfies.perfectpartysystem.PerfectPartySystem;
import hotfies.perfectpartysystem.managers.ConfigManager;

import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private final PerfectPartySystem plugin;
    private HikariDataSource dataSource;

    public DatabaseManager(PerfectPartySystem plugin, ConfigManager configManager) {
        this.plugin = plugin;
        initDatabase(configManager);
    }

    private void initDatabase(ConfigManager configManager) {
        String host = configManager.getConfig().getString("database.host");
        int port = configManager.getConfig().getInt("database.port");
        String name = configManager.getConfig().getString("database.name");
        String user = configManager.getConfig().getString("database.username");
        String password = configManager.getConfig().getString("database.password");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + name);
        config.setUsername(user);
        config.setPassword(password);

        // Настройки пула
        config.setMaximumPoolSize(10);
        config.setIdleTimeout(60000);
        config.setMaxLifetime(1800000);
        config.setConnectionTimeout(30000);

        this.dataSource = new HikariDataSource(config);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }
}
