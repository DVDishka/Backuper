package ru.dvdishka.backuper.backend.utils;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;

public class UIUtils {
    public static void returnFailure(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
        } catch (Exception ignored) {}
    }

    public static void returnFailure(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(Component.text(message).color(color));
        } catch (Exception ignored) {}
    }

    public static void returnSuccess(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message));
        } catch (Exception ignored) {}
    }

    public static void returnSuccess(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(color + message);
        } catch (Exception ignored) {}
    }

    public static void returnWarning(String message, CommandSender sender) {
        try {
            sender.sendMessage(Component.text(message).color(NamedTextColor.RED));
        } catch (Exception ignored) {}
    }

    public static void returnWarning(String message, CommandSender sender, TextColor color) {
        try {
            sender.sendMessage(color + message);
        } catch (Exception ignored) {}
    }

    public static void sendMessage(String message, @NotNull CommandSender sender) {
        try {
            sender.sendMessage(message);
        } catch (Exception ignored) {}
    }

    public static void cancelSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("block.anvil.place"), Sound.Source.MASTER, 50, 1)).build());
        } catch (Exception ignored) {}
    }

    public static void normalSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("ui.button.click"), Sound.Source.MASTER, 50, 1)).build());
        } catch (Exception ignored) {}
    }

    public static void successSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 50, 1)).build());
        } catch (Exception ignored) {}
    }

    public static void notificationSound(CommandSender sender) {
        try {
            Class.forName("net.kyori.adventure.sound.Sound").getMethod("sound");
            sender.playSound(Sound.sound(Sound.sound(Key.key("entity.player.levelup"), Sound.Source.MASTER, 50, 50)).build());
        } catch (Exception ignored) {}
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

        } catch (Exception ignored) {}
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

        } catch (Exception ignored) {}
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

        } catch (Exception ignored) {}
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

        } catch (Exception ignored) {}
    }
}
