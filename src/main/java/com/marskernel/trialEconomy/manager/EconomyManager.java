package com.marskernel.trialEconomy.manager;

import com.marskernel.trialEconomy.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Gestore principale dell'economia con sistema di caching avanzato
 * Ottimizzato per zero impatto TPS con operazioni asincrone
 */
public class EconomyManager {

    private final JavaPlugin plugin;
    private final DatabaseManager database;
    private final Logger logger;

    // Cache in memoria per giocatori attivi (performance ottimizzata)
    private final ConcurrentHashMap<UUID, BigDecimal> balanceCache;
    private final ConcurrentHashMap<UUID, Long> cacheTimestamps;

    // Configurazione
    private BigDecimal startingBalance;
    private BigDecimal maxBalance;
    private BigDecimal minTransaction;
    private long cacheDuration; // In millisecondi

    // Scheduler per salvataggio automatico
    private final ScheduledExecutorService saveScheduler;

    public EconomyManager(JavaPlugin plugin, DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
        this.logger = plugin.getLogger();

        this.balanceCache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();

        loadConfiguration();

        this.saveScheduler = Executors.newScheduledThreadPool(1);
        this.saveScheduler.scheduleAtFixedRate(this::saveAllCachedBalances, 5, 5, TimeUnit.MINUTES);

        logger.info("EconomyManager inizializzato con sistema di caching!");
    }

    /**
     * Carica la configurazione dal config.yml
     */
    private void loadConfiguration() {
        this.startingBalance = BigDecimal.valueOf(plugin.getConfig().getDouble("starting-balance", 1000.0));
        this.maxBalance = BigDecimal.valueOf(plugin.getConfig().getDouble("max-balance", 1000000000.0));
        this.minTransaction = BigDecimal.valueOf(plugin.getConfig().getDouble("min-transaction", 0.01));
        this.cacheDuration = plugin.getConfig().getLong("cache-duration", 1800) * 1000; // Converti in ms

        logger.info("Configurazione economy caricata: Start=" + startingBalance + ", Max=" + maxBalance);
    }

    /**
     * Ottiene il bilancio di un giocatore (con caching)
     */
    public CompletableFuture<BigDecimal> getBalance(UUID playerUUID, String playerName) {
        // Controlla cache
        if (balanceCache.containsKey(playerUUID)) {
            // Verifica se la cache è ancora valida
            long cacheAge = System.currentTimeMillis() - cacheTimestamps.getOrDefault(playerUUID, 0L);
            if (cacheAge < cacheDuration) {
                return CompletableFuture.completedFuture(balanceCache.get(playerUUID));
            }
        }

        // Carica da database e aggiorna cache
        return database.loadBalance(playerUUID, playerName, startingBalance)
                .thenApply(balance -> {
                    balanceCache.put(playerUUID, balance);
                    cacheTimestamps.put(playerUUID, System.currentTimeMillis());
                    return balance;
                });
    }

    /**
     * Imposta il bilancio di un giocatore
     */
    public CompletableFuture<Boolean> setBalance(UUID playerUUID, String playerName, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            return CompletableFuture.completedFuture(false);
        }

        if (amount.compareTo(maxBalance) > 0) {
            amount = maxBalance;
        }

        // Aggiorna cache
        balanceCache.put(playerUUID, amount);
        cacheTimestamps.put(playerUUID, System.currentTimeMillis());

        // Salva in database asincrono
        final BigDecimal finalAmount = amount;
        return database.saveBalance(playerUUID, playerName, finalAmount);
    }

    /**
     * Aggiunge denaro al bilancio di un giocatore
     */
    public CompletableFuture<Boolean> addBalance(UUID playerUUID, String playerName, BigDecimal amount) {
        return getBalance(playerUUID, playerName)
                .thenCompose(currentBalance -> {
                    BigDecimal newBalance = currentBalance.add(amount);
                    if (newBalance.compareTo(maxBalance) > 0) {
                        newBalance = maxBalance;
                    }
                    return setBalance(playerUUID, playerName, newBalance);
                });
    }

    /**
     * Sottrae denaro dal bilancio di un giocatore
     */
    public CompletableFuture<Boolean> removeBalance(UUID playerUUID, String playerName, BigDecimal amount) {
        return getBalance(playerUUID, playerName)
                .thenCompose(currentBalance -> {
                    BigDecimal newBalance = currentBalance.subtract(amount);
                    if (newBalance.compareTo(BigDecimal.ZERO) < 0) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return setBalance(playerUUID, playerName, newBalance);
                });
    }

    /**
     * Controlla se un giocatore ha abbastanza fondi
     */
    public CompletableFuture<Boolean> hasBalance(UUID playerUUID, String playerName, BigDecimal amount) {
        return getBalance(playerUUID, playerName)
                .thenApply(balance -> balance.compareTo(amount) >= 0);
    }

    /**
     * Trasferisce denaro tra due giocatori (transazione atomica)
     */
    public CompletableFuture<TransactionResult> transfer(UUID senderUUID, String senderName,
                                                         UUID receiverUUID, String receiverName,
                                                         BigDecimal amount) {
        // Validazione importo
        if (amount.compareTo(minTransaction) < 0) {
            return CompletableFuture.completedFuture(
                    new TransactionResult(false, "L'importo è inferiore al minimo trasferibile!")
            );
        }

        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return CompletableFuture.completedFuture(
                    new TransactionResult(false, "L'importo deve essere positivo!")
            );
        }

        // Controlla se il sender ha fondi sufficienti
        return hasBalance(senderUUID, senderName, amount)
                .thenCompose(hasFunds -> {
                    if (!hasFunds) {
                        return CompletableFuture.completedFuture(
                                new TransactionResult(false, "Fondi insufficienti!")
                        );
                    }

                    return removeBalance(senderUUID, senderName, amount)
                            .thenCompose(removed -> {
                                if (!removed) {
                                    return CompletableFuture.completedFuture(
                                            new TransactionResult(false, "Errore durante il trasferimento!")
                                    );
                                }

                                return addBalance(receiverUUID, receiverName, amount)
                                        .thenApply(added -> {
                                            if (!added) {
                                                // Rollback
                                                addBalance(senderUUID, senderName, amount);
                                                return new TransactionResult(false, "Errore durante il trasferimento!");
                                            }

                                            // Log transazione
                                            database.logTransaction(senderUUID, receiverUUID, amount, "TRANSFER",
                                                    senderName + " -> " + receiverName);

                                            return new TransactionResult(true,
                                                    "Trasferimento di " + formatAmount(amount) + " completato con successo!");
                                        });
                            });
                });
    }

    /**
     * Formatta un importo con il simbolo della valuta
     */
    public String formatAmount(BigDecimal amount) {
        String symbol = plugin.getConfig().getString("currency-symbol", "$");
        String formatted = String.format("%.2f", amount);
        return symbol + formatted;
    }

    /**
     * Ottiene il nome della valuta
     */
    public String getCurrencyName() {
        return plugin.getConfig().getString("currency-name", "dollari");
    }

    /**
     * Carica il bilancio quando un giocatore si connette
     */
    public void onPlayerJoin(Player player) {
        getBalance(player.getUniqueId(), player.getName()).thenAccept(balance ->
                logger.info("Bilancio caricato per " + player.getName() + ": " + formatAmount(balance))
        );
    }

    /**
     * Salva il bilancio quando un giocatore si disconnette
     */
    public void onPlayerQuit(Player player) {
        UUID uuid = player.getUniqueId();
        if (balanceCache.containsKey(uuid)) {
            database.saveBalance(uuid, player.getName(), balanceCache.get(uuid))
                    .thenAccept(success -> {
                        if (success) {
                            logger.info("Bilancio salvato per " + player.getName());
                        }
                        // Rimuovi dalla cache dopo un delay
                        Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            balanceCache.remove(uuid);
                            cacheTimestamps.remove(uuid);
                        }, 20L * 300); // 5 minuti dopo il logout
                    });
        }
    }

    /**
     * Salva tutti i bilanciamenti in cache
     */
    public void saveAllCachedBalances() {
        logger.info("Salvataggio automatico di " + balanceCache.size() + " bilanciamenti in cache...");

        for (Map.Entry<UUID, BigDecimal> entry : balanceCache.entrySet()) {
            UUID uuid = entry.getKey();
            BigDecimal balance = entry.getValue();
            Player player = Bukkit.getPlayer(uuid);
            String name = player != null ? player.getName() : "Unknown";

            database.saveBalance(uuid, name, balance);
        }

        logger.info("Salvataggio automatico completato!");
    }

    /**
     * Shutdown dell'economy manager
     */
    public void shutdown() {
        logger.info("Shutdown EconomyManager in corso...");

        saveAllCachedBalances();

        saveScheduler.shutdown();
        try {
            if (!saveScheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                saveScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            saveScheduler.shutdownNow();
        }

        logger.info("EconomyManager chiuso correttamente!");
    }

    /**
     * Classe risultato transazione
     */
    public static class TransactionResult {
        private final boolean success;
        private final String message;

        public TransactionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        public boolean isSuccess() {
            return success;
        }

        public String getMessage() {
            return message;
        }
    }

    public DatabaseManager getDatabase() {
        return database;
    }

    /**
     * Ottiene l'importo minimo per le transazioni
     * @return Importo minimo
     */
    public BigDecimal getMinTransaction() {
        return minTransaction;
    }

    /**
     * Ottiene il bilancio massimo consentito
     * @return Bilancio massimo
     */
    public BigDecimal getMaxBalance() {
        return maxBalance;
    }
}
