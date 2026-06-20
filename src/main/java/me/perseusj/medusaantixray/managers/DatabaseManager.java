package me.perseusj.medusaantixray.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import me.perseusj.medusaantixray.MedusaAntiXray;
import me.perseusj.medusaantixray.data.MineEvent;
import me.perseusj.medusaantixray.data.PlayerData;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DatabaseManager {
    private static final String CREATE_TABLE =
            "CREATE TABLE IF NOT EXISTS medusa_events (" +
            "uuid VARCHAR(36) NOT NULL, " +
            "player_name VARCHAR(32) NOT NULL, " +
            "timestamp BIGINT NOT NULL, " +
            "is_valuable BOOLEAN NOT NULL, " +
            "weight DOUBLE NOT NULL)";
    private static final String CREATE_INDEX =
            "CREATE INDEX IF NOT EXISTS idx_medusa_events_uuid ON medusa_events(uuid)";
    private static final String SELECT_EVENTS =
            "SELECT timestamp, is_valuable, weight FROM medusa_events WHERE uuid = ? ORDER BY timestamp ASC";
    private static final String DELETE_EVENTS =
            "DELETE FROM medusa_events WHERE uuid = ?";
    private static final String INSERT_EVENT =
            "INSERT INTO medusa_events (uuid, player_name, timestamp, is_valuable, weight) VALUES (?,?,?,?,?)";

    private final MedusaAntiXray plugin;
    private final ConfigManager config;
    private final ExecutorService executor;
    private HikariDataSource dataSource;
    private boolean available;

    public DatabaseManager(MedusaAntiXray plugin, ConfigManager config) {
        this.plugin = plugin;
        this.config = config;
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "MedusaAntiXray-DB-Worker");
            thread.setDaemon(true);
            return thread;
        });
        this.available = false;
    }

    public void init() {
        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().log(Level.SEVERE, "Could not create plugin data folder; database disabled.");
            return;
        }
        try {
            this.dataSource = buildDataSource();
            this.available = true;
            createSchema();
            plugin.getLogger().info("Database initialized (" + config.getDatabaseType() + ").");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize database; falling back to memory-only mode.", e);
            this.available = false;
            this.dataSource = null;
        }
    }

    public boolean isAvailable() {
        return available;
    }

    private HikariDataSource buildDataSource() {
        HikariConfig hc = new HikariConfig();
        String type = config.getDatabaseType();
        if (type.equalsIgnoreCase("mysql")) {
            String url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&autoReconnect=true&useUnicode=true&characterEncoding=UTF-8",
                    config.getMysqlHost(), config.getMysqlPort(), config.getMysqlDatabase());
            hc.setJdbcUrl(url);
            hc.setUsername(config.getMysqlUsername());
            hc.setPassword(config.getMysqlPassword());
            hc.setMaximumPoolSize(10);
            hc.setMinimumIdle(2);
        } else {
            java.io.File file = new java.io.File(plugin.getDataFolder(), config.getSqliteFile());
            hc.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath());
            hc.setDriverClassName("org.sqlite.JDBC");
            hc.setMaximumPoolSize(1);
        }
        hc.setPoolName("MedusaAntiXray-DB");
        hc.setConnectionTimeout(10_000L);
        hc.setIdleTimeout(60_000L);
        hc.setMaxLifetime(30 * 60_000L);
        return new HikariDataSource(hc);
    }

    private void createSchema() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(CREATE_TABLE);
            statement.executeUpdate(CREATE_INDEX);
        }
    }

    public void loadAsync(UUID uuid, Consumer<List<MineEvent>> callback) {
        if (!available) {
            return;
        }
        executor.submit(() -> {
            List<MineEvent> events = load(uuid);
            try {
                callback.accept(events);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error processing loaded data for " + uuid, t);
            }
        });
    }

    public void saveAsync(PlayerData data, Runnable onComplete) {
        if (!available) {
            if (onComplete != null) {
                onComplete.run();
            }
            return;
        }
        executor.submit(() -> {
            try {
                save(data);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save data for " + data.getPlayerName(), e);
            } finally {
                if (onComplete != null) {
                    onComplete.run();
                }
            }
        });
    }

    private List<MineEvent> load(UUID uuid) {
        List<MineEvent> events = new ArrayList<>();
        try (Connection connection = dataSource.getConnection();
             PreparedStatement ps = connection.prepareStatement(SELECT_EVENTS)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long timestamp = rs.getLong(1);
                    boolean isValuable = rs.getBoolean(2);
                    double weight = rs.getDouble(3);
                    events.add(new MineEvent(timestamp, isValuable, weight));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load data for " + uuid, e);
        }
        return events;
    }

    private void save(PlayerData data) throws SQLException {
        long cutoff = System.currentTimeMillis() - (config.getWindowMinutes() * 60_000L);
        data.purgeExpired(cutoff);
        List<MineEvent> events = data.snapshotEvents();
        String uuid = data.getPlayerUuid().toString();
        String name = data.getPlayerName();

        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement delete = connection.prepareStatement(DELETE_EVENTS);
                 PreparedStatement insert = connection.prepareStatement(INSERT_EVENT)) {
                delete.setString(1, uuid);
                delete.executeUpdate();
                for (MineEvent event : events) {
                    insert.setString(1, uuid);
                    insert.setString(2, name);
                    insert.setLong(3, event.timestamp());
                    insert.setBoolean(4, event.isValuable());
                    insert.setDouble(5, event.weight());
                    insert.addBatch();
                }
                if (!events.isEmpty()) {
                    insert.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public void shutdown() {
        try {
            executor.shutdown();
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Database executor did not terminate cleanly within 30s.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
