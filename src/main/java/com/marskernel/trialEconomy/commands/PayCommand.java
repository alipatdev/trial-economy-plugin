package com.marskernel.trialEconomy.commands;

import com.marskernel.trialEconomy.manager.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Comando /pay - Trasferisce denaro tra giocatori
 * Permesso richiesto: economy.pay
 * Uso: /pay <giocatore> <importo>
 */
public class PayCommand implements CommandExecutor, TabCompleter {

    private final EconomyManager economyManager;

    public PayCommand(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Questo comando può essere usato solo dai giocatori!");
            return true;
        }

        if (!player.hasPermission("economy.pay")) {
            player.sendMessage(ChatColor.RED + "Non hai il permesso per usare questo comando!");
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(ChatColor.RED + "Uso corretto: /pay <giocatore> <importo>");
            return true;
        }

        String targetName = args[0];
        String amountString = args[1];

        // Validazione importo
        BigDecimal amount;
        try {
            amount = new BigDecimal(amountString);
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Importo non valido! Usa un numero (es: 100 o 50.50)");
            return true;
        }

        // Controlla che l'importo sia positivo
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            player.sendMessage(ChatColor.RED + "L'importo deve essere maggiore di zero!");
            return true;
        }

        // Controlla importo minimo
        BigDecimal minTransaction = economyManager.getMinTransaction();

        if (amount.compareTo(minTransaction) < 0) {
            player.sendMessage(ChatColor.RED + "L'importo minimo trasferibile è " +
                    economyManager.formatAmount(minTransaction) + "!");
            return true;
        }

        // Non puoi pagare te stesso
        if (targetName.equalsIgnoreCase(player.getName())) {
            player.sendMessage(ChatColor.RED + "Non puoi inviare denaro a te stesso!");
            return true;
        }

        // Cerca il giocatore target (online o offline)
        player.sendMessage(ChatColor.YELLOW + "Ricerca del giocatore in corso...");

        // Prima prova con giocatore online
        Player targetPlayer = Bukkit.getPlayer(targetName);
        if (targetPlayer != null) {
            executeTransfer(player, targetPlayer.getUniqueId(), targetPlayer.getName(), amount, targetPlayer);
            return true;
        }

        // Cerca nel database per giocatori offline
        economyManager.getDatabase().getPlayerUUIDByName(targetName)
                .thenAccept(targetUUID -> {
                    if (targetUUID == null) {
                        player.sendMessage(ChatColor.RED + "Giocatore '" + targetName + "' non trovato!");
                        return;
                    }

                    OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetUUID);
                    executeTransfer(player, targetUUID, offlineTarget.getName(), amount, null);
                })
                .exceptionally(throwable -> {
                    player.sendMessage(ChatColor.RED + "Errore durante la ricerca del giocatore!");
                    throwable.printStackTrace();
                    return null;
                });

        return true;
    }

    /**
     * Esegue il trasferimento di denaro
     */
    private void executeTransfer(Player sender, UUID receiverUUID, String receiverName, BigDecimal amount, Player receiverPlayer) {
        economyManager.transfer(
                sender.getUniqueId(),
                sender.getName(),
                receiverUUID,
                receiverName,
                amount
        ).thenAccept(result -> {
            if (result.isSuccess()) {
                // Successo - Notifica il sender
                sender.sendMessage(ChatColor.GREEN + "✓ Hai inviato " + economyManager.formatAmount(amount) +
                        " a " + ChatColor.YELLOW + receiverName + ChatColor.GREEN + "!");

                // Notifica il receiver se online
                if (receiverPlayer != null && receiverPlayer.isOnline()) {
                    receiverPlayer.sendMessage(ChatColor.GREEN + "✓ Hai ricevuto " +
                            economyManager.formatAmount(amount) + " da " +
                            ChatColor.YELLOW + sender.getName() + ChatColor.GREEN + "!");
                }
            } else {
                // Errore
                sender.sendMessage(ChatColor.RED + "✗ " + result.getMessage());
            }
        }).exceptionally(throwable -> {
            sender.sendMessage(ChatColor.RED + "Errore durante il trasferimento!");
            throwable.printStackTrace();
            return null;
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partialName = args[0].toLowerCase();
            completions = Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(partialName))
                    .filter(name -> !name.equals(sender.getName())) // Escludi il sender
                    .collect(Collectors.toList());
        } else if (args.length == 2) {
            // Suggerimenti per importi comuni
            completions.add("10");
            completions.add("50");
            completions.add("100");
            completions.add("500");
            completions.add("1000");
        }

        return completions;
    }
}
