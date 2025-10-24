package com.marskernel.trialEconomy.commands;

import com.marskernel.trialEconomy.manager.EconomyManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/**
 * Comando /balance - Mostra il bilancio del giocatore
 * Permesso richiesto: economy.balance
 */
public class BalanceCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;

    public BalanceCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Questo comando può essere usato solo dai giocatori!");
            return true;
        }

        if (!player.hasPermission("economy.balance")) {
            player.sendMessage(ChatColor.RED + "Non hai il permesso per usare questo comando!");
            return true;
        }

        // Ottieni bilancio (operazione asincrona)
        economyManager.getBalance(player.getUniqueId(), player.getName())
                .thenAccept(balance -> {
                    // Messaggio formattato con il bilancio
                    String currencyName = economyManager.getCurrencyName();
                    String formattedBalance = economyManager.formatAmount(balance);

                    player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                    player.sendMessage(ChatColor.YELLOW + "  " + ChatColor.BOLD + "IL TUO BILANCIO");
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GREEN + "  » " + ChatColor.WHITE + formattedBalance + " " + currencyName);
                    player.sendMessage("");
                    player.sendMessage(ChatColor.GOLD + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                })
                .exceptionally(throwable -> {
                    player.sendMessage(ChatColor.RED + "Errore durante il recupero del bilancio!");
                    throwable.printStackTrace();
                    return null;
                });

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        // Nessun tab completion necessario per /balance
        return new ArrayList<>();
    }
}
