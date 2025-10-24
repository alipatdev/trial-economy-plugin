package com.marskernel.trialEconomy.listeners;

import com.marskernel.trialEconomy.manager.EconomyManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listener per eventi dei giocatori
 * Gestisce il caricamento e salvataggio automatico dei bilanciamenti
 */
public class PlayerListener implements Listener {

    private final EconomyManager economyManager;

    public PlayerListener(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    /**
     * Carica il bilancio del giocatore quando si connette
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        economyManager.onPlayerJoin(event.getPlayer());
    }

    /**
     * Salva il bilancio del giocatore quando si disconnette
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(PlayerQuitEvent event) {
        economyManager.onPlayerQuit(event.getPlayer());
    }
}
