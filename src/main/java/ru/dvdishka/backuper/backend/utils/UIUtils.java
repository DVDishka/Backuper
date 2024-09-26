package ru.dvdishka.backuper.backend.utils;

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
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.handlers.commands.Permissions;

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
            sender.sendMessage(color + message);
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
            sender.sendMessage(color + message);
        } catch (Exception ignored) {
        }
    }

    public static void sendMessage(String message, CommandSender sender) {
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

    public static void sendFramedMessage(Component message, CommandSender sender) {
        try {

            Component framedMessage = Component.empty();

            if (sender instanceof ConsoleCommandSender) {
                framedMessage = framedMessage
                        .append(Component.newline());
            }

            framedMessage = framedMessage
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x143E77)))
                    .append(Component.newline());

            framedMessage = framedMessage.append(message);

            framedMessage = framedMessage
                    .append(Component.newline())
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x143E77)));

            sender.sendMessage(framedMessage);

        } catch (Exception ignored) {
        }
    }

    public static void sendFramedMessage(Component message, int dashNumber, CommandSender sender) {
        try {

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

            sender.sendMessage(framedMessage);

        } catch (Exception ignored) {
        }
    }

    public static void sendFramedMessage(Component header, Component message, CommandSender sender) {
        try {

            Component framedMessage = Component.empty();

            if (sender instanceof ConsoleCommandSender) {
                framedMessage = framedMessage
                        .append(Component.newline());
            }

            framedMessage = framedMessage
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x143E77)))
                    .append(Component.newline());

            framedMessage = framedMessage
                    .append(header)
                    .append(Component.newline());

            framedMessage = framedMessage
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b")))
                    .append(Component.newline());

            framedMessage = framedMessage.append(message);

            framedMessage = framedMessage
                    .append(Component.newline())
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0x143E77)));

            sender.sendMessage(framedMessage);

        } catch (Exception ignored) {
        }
    }

    public static void sendFramedMessage(Component header, Component message, int dashNumber, CommandSender sender) {
        try {

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

            sender.sendMessage(framedMessage);

        } catch (Exception ignored) {
        }
    }

    public static void sendBackupAlert(long timeSeconds, String afterBackup) {

        String action = "backed\nup ";
        boolean restart = false;

        if (afterBackup.equals("STOP")) {
            Logger.getLogger().log("Server will be backed up and stopped in " + timeSeconds + " second(s)");
            action = "backed\nup and restarted\n";
            restart = true;
        }
        if (afterBackup.equals("RESTART")) {
            Logger.getLogger().log("Server will be backed up and restarted in " + timeSeconds + " second(s)");
            action = "backed\nup and restarted\n";
            restart = true;
        }
        if (afterBackup.equals("NOTHING")) {
            Logger.getLogger().log("Server will be backed up in " + timeSeconds + " second(s)");
            action = "backed\nup ";
        }

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (!player.hasPermission(Permissions.ALERT.getPermission())) {
                continue;
            }

            if (restart || !Config.getInstance().isAlertOnlyServerRestart()) {

                Component header = Component.empty();

                header = header
                        .append(Component.text("Alert")
                                .decorate(TextDecoration.BOLD));

                Component message = Component.empty();

                message = message
                        .append(Component.text("Server will be " + action + "in "))
                        .append(Component.text(timeSeconds)
                                .color(NamedTextColor.RED)
                                .decorate(TextDecoration.BOLD))
                        .append(Component.text(" second(s)"));

                sendFramedMessage(header, message, 15, player);
                notificationSound(player);
            }
        }
    }
}
