package com.marskernel.trialEconomy;

import com.marskernel.trialEconomy.commands.BalanceCommand;
import com.marskernel.trialEconomy.commands.PayCommand;
import com.marskernel.trialEconomy.database.DatabaseManager;
import com.marskernel.trialEconomy.listeners.PlayerListener;
import com.marskernel.trialEconomy.manager.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Trial: Economy Plugin
 *
 * Caratteristiche principali:
 * - Database H2 con connection pooling HikariCP
 * - Sistema di caching avanzato per zero impatto TPS
 * - Supporto per 500+ giocatori concorrenti
 * - Operazioni completamente asincrone
 * - Logging completo delle transazioni
 * - Supporto giocatori offline
 *
 * @author Patrizio
 * @version 1.0-SNAPSHOT
 */
public final class TrialEconomy extends JavaPlugin {

    private DatabaseManager databaseManager;
    private EconomyManager economyManager;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        getLogger().info("=========================================");
        getLogger().info("  Trial Economy Plugin");
        getLogger().info("  Sistema Economy ad Alte Prestazioni");
        getLogger().info("  Versione: " + getDescription().getVersion());
        getLogger().info("=========================================");

        saveDefaultConfig();
        getLogger().info("Configurazione caricata!");

        // Inizializza database H2
        try {
            databaseManager = new DatabaseManager(getDataFolder(), getLogger());
            getLogger().info("✓ Database H2 inizializzato con successo!");
        } catch (Exception e) {
            getLogger().severe("✗ Errore critico durante l'inizializzazione del database!");
            e.printStackTrace();
            getLogger().severe("Il plugin verrà disabilitato.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            economyManager = new EconomyManager(this, databaseManager);
            getLogger().info("✓ EconomyManager inizializzato!");
        } catch (Exception e) {
            getLogger().severe("✗ Errore durante l'inizializzazione dell'EconomyManager!");
            e.printStackTrace();
            getLogger().severe("Il plugin verrà disabilitato.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        try {
            BalanceCommand balanceCommand = new BalanceCommand(economyManager);
            PayCommand payCommand = new PayCommand(economyManager);

            getCommand("balance").setExecutor(balanceCommand);
            getCommand("balance").setTabCompleter(balanceCommand);

            getCommand("pay").setExecutor(payCommand);
            getCommand("pay").setTabCompleter(payCommand);

            getLogger().info("✓ Comandi registrati: /balance, /pay");
        } catch (Exception e) {
            getLogger().severe("✗ Errore durante la registrazione dei comandi!");
            e.printStackTrace();
        }

        try {
            Bukkit.getPluginManager().registerEvents(new PlayerListener(economyManager), this);
            getLogger().info("✓ Event listeners registrati!");
        } catch (Exception e) {
            getLogger().severe("✗ Errore durante la registrazione degli event listeners!");
            e.printStackTrace();
        }

        // Carica bilanciamenti per giocatori già online (in caso di reload)
        Bukkit.getOnlinePlayers().forEach(player -> {
            economyManager.onPlayerJoin(player);
        });

        long endTime = System.currentTimeMillis();
        long loadTime = endTime - startTime;

        getLogger().info("=========================================");
        getLogger().info("  Trial Economy abilitato con successo!");
        getLogger().info("  Tempo di caricamento: " + loadTime + "ms");
        getLogger().info("  Giocatori online: " + Bukkit.getOnlinePlayers().size());
        getLogger().info("=========================================");
    }

    @Override
    public void onDisable() {
        getLogger().info("=========================================");
        getLogger().info("  Disabilitazione Trial Economy...");
        getLogger().info("=========================================");

        // Salva tutti i bilanciamenti prima dello shutdown
        if (economyManager != null) {
            try {
                getLogger().info("Salvataggio bilanciamenti in corso...");
                economyManager.shutdown();
                getLogger().info("✓ EconomyManager chiuso correttamente!");
            } catch (Exception e) {
                getLogger().severe("✗ Errore durante lo shutdown dell'EconomyManager!");
                e.printStackTrace();
            }
        }

        // Chiudi database
        if (databaseManager != null) {
            try {
                databaseManager.close();
                getLogger().info("✓ Database chiuso correttamente!");
            } catch (Exception e) {
                getLogger().severe("✗ Errore durante la chiusura del database!");
                e.printStackTrace();
            }
        }

        getLogger().info("=========================================");
        getLogger().info("  Trial Economy disabilitato!");
        getLogger().info("  Grazie per aver usato il plugin!");
        getLogger().info("=========================================");
    }

    /**
     * Ottiene l'istanza dell'EconomyManager
     * @return EconomyManager
     */
    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    /**
     * Ottiene l'istanza del DatabaseManager
     * @return DatabaseManager
     */
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
}
