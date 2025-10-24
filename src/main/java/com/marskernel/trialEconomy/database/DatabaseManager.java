package com.marskernel.trialEconomy.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * Gestore del database H2 con connection pooling HikariCP
 * Ottimizzato per alte prestazioni (500+ giocatori concorrenti)
 */
public class DatabaseManager {

    private final HikariDataSource dataSource;
    private final Logger logger;

    public DatabaseManager(File dataFolder, Logger logger) {
        this.logger = logger;

        // Configurazione HikariCP per massime prestazioni
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:h2:" + new File(dataFolder, "economy").getAbsolutePath() + ";MODE=MySQL");
        config.setDriverClassName("org.h2.Driver");

        // Ottimizzazioni per alta concorrenza
        config.setMaximumPoolSize(20); // Pool size ottimale per 500+ giocatori
        config.setMinimumIdle(5);
        config.setMaxLifetime(1800000); // 30 minuti
        config.setConnectionTimeout(10000); // 10 secondi
        config.setLeakDetectionThreshold(60000); // Rilevamento memory leak

        // Performance tuning
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

        this.dataSource = new HikariDataSource(config);

        // Inizializza schema database
        initializeDatabase();

        logger.info("Database H2 inizializzato con successo!");
    }

    /**
     * Crea le tabelle necessarie se non esistono
     */
    private void initializeDatabase() {
        String createTableSQL = """
            CREATE TABLE IF NOT EXISTS player_balances (
                player_uuid VARCHAR(36) PRIMARY KEY,
                player_name VARCHAR(16) NOT NULL,
                balance DECIMAL(20, 2) NOT NULL DEFAULT 0.00,
                last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );

            CREATE INDEX IF NOT EXISTS idx_player_name ON player_balances(player_name);

            CREATE TABLE IF NOT EXISTS transaction_logs (
                id INT AUTO_INCREMENT PRIMARY KEY,
                sender_uuid VARCHAR(36),
                receiver_uuid VARCHAR(36),
                amount DECIMAL(20, 2) NOT NULL,
                transaction_type VARCHAR(20) NOT NULL,
                timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                description VARCHAR(255)
            );

            CREATE INDEX IF NOT EXISTS idx_transaction_timestamp ON transaction_logs(timestamp);
            """;

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(createTableSQL)) {
            stmt.execute();
            logger.info("Schema database creato con successo!");
        } catch (SQLException e) {
            logger.severe("Errore durante la creazione dello schema database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Ottiene una connessione dal pool
     */
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    /**
     * Carica il bilancio di un giocatore in modo asincrono
     */
    public CompletableFuture<BigDecimal> loadBalance(UUID playerUUID, String playerName, BigDecimal defaultBalance) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT balance FROM player_balances WHERE player_uuid = ?";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, playerUUID.toString());
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return rs.getBigDecimal("balance");
                } else {
                    // Crea nuovo account con bilancio predefinito
                    createAccount(playerUUID, playerName, defaultBalance);
                    return defaultBalance;
                }
            } catch (SQLException e) {
                logger.severe("Errore durante il caricamento del bilancio per " + playerName + ": " + e.getMessage());
                return defaultBalance;
            }
        });
    }

    /**
     * Salva il bilancio di un giocatore in modo asincrono
     */
    public CompletableFuture<Boolean> saveBalance(UUID playerUUID, String playerName, BigDecimal balance) {
        return CompletableFuture.supplyAsync(() -> {
            String upsert = """
                MERGE INTO player_balances (player_uuid, player_name, balance, last_updated)
                KEY(player_uuid)
                VALUES (?, ?, ?, CURRENT_TIMESTAMP)
                """;

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(upsert)) {

                stmt.setString(1, playerUUID.toString());
                stmt.setString(2, playerName);
                stmt.setBigDecimal(3, balance);

                stmt.executeUpdate();
                return true;
            } catch (SQLException e) {
                logger.severe("Errore durante il salvataggio del bilancio per " + playerName + ": " + e.getMessage());
                return false;
            }
        });
    }

    /**
     * Crea un nuovo account giocatore
     */
    private void createAccount(UUID playerUUID, String playerName, BigDecimal startingBalance) {
        String insert = "INSERT INTO player_balances (player_uuid, player_name, balance) VALUES (?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(insert)) {

            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, playerName);
            stmt.setBigDecimal(3, startingBalance);

            stmt.executeUpdate();
            logger.info("Nuovo account economy creato per " + playerName + " con bilancio iniziale: " + startingBalance);
        } catch (SQLException e) {
            logger.severe("Errore durante la creazione dell'account per " + playerName + ": " + e.getMessage());
        }
    }

    /**
     * Registra una transazione nel log
     */
    public CompletableFuture<Void> logTransaction(UUID sender, UUID receiver, BigDecimal amount, String type, String description) {
        return CompletableFuture.runAsync(() -> {
            String insert = "INSERT INTO transaction_logs (sender_uuid, receiver_uuid, amount, transaction_type, description) VALUES (?, ?, ?, ?, ?)";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(insert)) {

                stmt.setString(1, sender != null ? sender.toString() : null);
                stmt.setString(2, receiver != null ? receiver.toString() : null);
                stmt.setBigDecimal(3, amount);
                stmt.setString(4, type);
                stmt.setString(5, description);

                stmt.executeUpdate();
            } catch (SQLException e) {
                logger.severe("Errore durante il logging della transazione: " + e.getMessage());
            }
        });
    }

    /**
     * Cerca un giocatore per nome (supporta offline players)
     */
    public CompletableFuture<UUID> getPlayerUUIDByName(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String query = "SELECT player_uuid FROM player_balances WHERE LOWER(player_name) = LOWER(?)";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(query)) {

                stmt.setString(1, playerName);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    return UUID.fromString(rs.getString("player_uuid"));
                }
                return null;
            } catch (SQLException e) {
                logger.severe("Errore durante la ricerca del giocatore " + playerName + ": " + e.getMessage());
                return null;
            }
        });
    }

    /**
     * Chiude il pool di connessioni
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Database chiuso correttamente!");
        }
    }
}
