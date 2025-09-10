package ru.dvdishka.backuper.backend.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.Permission;

public class UIUtils {
    public static void returnFailure(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
        } catch (Exception ignored) {
        }
    }

    public static void returnFailure(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(Component.text(message).color(color));
        } catch (Exception ignored) {
        }
    }

    public static void returnSuccess(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message));
        } catch (Exception ignored) {
        }
    }

    public static void returnSuccess(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(Component.text(message).color(color));
        } catch (Exception ignored) {
        }
    }

    public static void returnWarning(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
        } catch (Exception ignored) {
        }
    }

    public static void returnWarning(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(Component.text(message).color(color));
        } catch (Exception ignored) {
        }
    }

    public static void sendMessage(String message, CommandSender sender) {
        try {
            sender.sendMessage(message);
        } catch (Exception ignored) {
        }
    }

    public static void sendMessage(Component message, CommandSender sender) {
        try {
            sender.sendMessage(message);
        } catch (Exception ignored) {
        }
    }

    public static void cancelSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("block.anvil.place"), Sound.Source.MASTER, 50, 1)).build());
        } catch (Exception ignored) {
        }
    }

    public static void normalSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 50, 1)).build());
        } catch (Exception ignored) {
        }
    }

    public static void successSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 50, 1)).build());
        } catch (Exception ignored) {
        }
    }

    public static void notificationSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 50, 50)).build());
        } catch (Exception ignored) {
        }
    }

    public static Component getFramedMessage(Component header, Component message, CommandSender sender) {
        return getFramedMessage(header, message, 42, sender);
    }

    public static Component getFramedMessage(Component header, Component message, int dashNumber, CommandSender sender) {
        Component framedMessage = Component.empty();

        if (sender instanceof ConsoleCommandSender) {
            framedMessage = framedMessage
                    .append(Component.newline());
        }

        framedMessage = framedMessage
                .append(Component.text("-".repeat(dashNumber))
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0x143E77)))
                .append(Component.newline());

        framedMessage = framedMessage
                .append(header)
                .append(Component.newline());

        framedMessage = framedMessage
                .append(Component.text("-".repeat(dashNumber))
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.fromHexString("#129c9b")))
                .append(Component.newline());

        framedMessage = framedMessage.append(message);

        framedMessage = framedMessage
                .append(Component.newline())
                .append(Component.text("-".repeat(dashNumber))
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0x143E77)));

        return framedMessage;
    }

    public static Component getFramedMessage(Component message, int dashNumber, CommandSender sender) {
        Component framedMessage = Component.empty();

        if (sender instanceof ConsoleCommandSender) {
            framedMessage = framedMessage
                    .append(Component.newline());
        }

        framedMessage = framedMessage
                .append(Component.text("-".repeat(dashNumber))
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0x143E77)))
                .append(Component.newline());

        framedMessage = framedMessage.append(message);

        framedMessage = framedMessage
                .append(Component.newline())
                .append(Component.text("-".repeat(dashNumber))
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0x143E77)));
        return framedMessage;
    }

    public static Component getFramedMessage(Component message, CommandSender sender) {
        return getFramedMessage(message, 42, sender);
    }

    public static void sendFramedMessage(Component header, Component message, CommandSender sender) {
        sendFramedMessage(header, message, 42, sender);
    }

    public static void sendFramedMessage(Component header, Component message, int dashNumber, CommandSender sender) {
        try {
            sender.sendMessage(getFramedMessage(header, message, dashNumber, sender));
        } catch (Exception ignored) {
        }
    }

    public static void sendFramedMessage(Component message, int dashNumber, CommandSender sender) {
        try {
            sender.sendMessage(getFramedMessage(message, dashNumber, sender));
        } catch (Exception ignored) {
        }
    }

    public static void sendFramedMessage(Component message, CommandSender sender) {
        sendFramedMessage(message, 42, sender);
    }

    public static void sendBackupAlert(long timeSeconds, String afterBackup) {

        boolean restart = false;

        if (afterBackup.equals("STOP")) {
            Backuper.getInstance().getLogManager().log(Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupRestartMessage().formatted(timeSeconds));
            restart = true;
        }
        if (afterBackup.equals("RESTART")) {
            Backuper.getInstance().getLogManager().log(Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupRestartMessage().formatted(timeSeconds));
            restart = true;
        }
        if (afterBackup.equals("NOTHING")) {
            Backuper.getInstance().getLogManager().log(Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupMessage().formatted(timeSeconds));
        }

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (!player.hasPermission(Permission.ALERT.getPermission())) {
                continue;
            }

            if (restart || !Backuper.getInstance().getConfigManager().getServerConfig().isAlertOnlyServerRestart()) {

                Component header = Component.empty();

                header = header
                        .append(Component.text("Alert")
                                .decorate(TextDecoration.BOLD));

                Component message = Component.empty();

                message = message
                        .append(Component.text((restart ? Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupRestartMessage() :
                                Backuper.getInstance().getConfigManager().getServerConfig().getAlertBackupMessage()).formatted(timeSeconds)));

                sendFramedMessage(header, message, 15, player);
                notificationSound(player);
            }
        }
    }
}
