package org.b0basaurea.life.Commands;

import org.b0basaurea.life.Managers.FinalHuntManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class FinalHuntCommand implements CommandExecutor, TabCompleter {

    private final FinalHuntManager finalHuntManager;

    public FinalHuntCommand(FinalHuntManager finalHuntManager) {
        this.finalHuntManager = finalHuntManager;
    }

    @Override
    public boolean onCommand(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (!sender.isOp()) {
            sender.sendMessage("You do not have permission for this command.");
            return true;
        }

        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "start" -> {
                finalHuntManager.startCountdown();
                sender.sendMessage("Final Hunt countdown started.");
            }

            case "end", "stop" -> {
                finalHuntManager.stopHunt();
                sender.sendMessage("Final Hunt stopped.");
            }

            case "status" -> {
                if (finalHuntManager.isCountdownActive()) {
                    sender.sendMessage("The Final Hunt countdown is active.");
                } else if (finalHuntManager.isHuntActive()) {
                    sender.sendMessage("The Final Hunt is active.");
                } else {
                    sender.sendMessage("The Final Hunt is inactive.");
                }
            }

            default -> sendUsage(sender);
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("Usage: /finalhunt <start|stop|status>");
    }

    @Override
    public @Nullable List<String> onTabComplete(
            @NotNull CommandSender sender,
            @NotNull Command command,
            @NotNull String label,
            @NotNull String @NotNull [] args
    ) {
        if (!sender.isOp() || args.length != 1) {
            return List.of();
        }

        return List.of("start", "stop", "status").stream()
                .filter(option -> option.startsWith(args[0].toLowerCase()))
                .toList();
    }
}