package ru.dvdishka.backuper.backend.util;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;

public class UIUtils {

    public static void returnFailure(String message, CommandSender sender) {
        Component text = Component.text(message).color(NamedTextColor.RED);
        sendMessage(text, sender);
    }

    public static void returnSuccess(String message, CommandSender sender) {
        Component text = Component.text(message).color(NamedTextColor.GREEN);
        sendMessage(text, sender);
    }

    public static void returnWarning(String message, CommandSender sender) {
        Component text = Component.text(message).color(NamedTextColor.YELLOW);
        sendMessage(text, sender);
    }

    public static void sendMessage(String message, CommandSender sender) {
        sendMessage(Component.text(message), sender);
    }

    public static void sendMessage(Component message, CommandSender sender) {
        try {
            if (sender instanceof ConsoleCommandSender) {
                Backuper.getInstance().getLogManager().log(message, sender);
            } else {
                sender.sendMessage(message);
            }
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

    public static void buttonSound(CommandSender sender) {
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
                        .color(UIUtils.getMainColor()))
                .append(Component.newline());

        framedMessage = framedMessage
                .append(header)
                .append(Component.newline());

        framedMessage = framedMessage
                .append(Component.text("-".repeat(dashNumber))
                        .decorate(TextDecoration.BOLD)
                        .color(UIUtils.getSecondaryColor()))
                .append(Component.newline());

        framedMessage = framedMessage.append(message);

        framedMessage = framedMessage
                .append(Component.newline())
                .append(Component.text("-".repeat(dashNumber))
                        .decorate(TextDecoration.BOLD)
                        .color(UIUtils.getMainColor()));

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
                        .color(UIUtils.getMainColor()))
                .append(Component.newline());

        framedMessage = framedMessage.append(message);

        framedMessage = framedMessage
                .append(Component.newline())
                .append(Component.text("-".repeat(dashNumber))
                        .decorate(TextDecoration.BOLD)
                        .color(UIUtils.getMainColor()));
        return framedMessage;
    }

    public static Component getFramedMessage(Component message, CommandSender sender) {
        return getFramedMessage(message, 42, sender);
    }

    public static void sendFramedMessage(Component header, Component message, CommandSender sender) {
        sendFramedMessage(header, message, 42, sender);
    }

    public static void sendFramedMessage(Component header, Component message, int dashNumber, CommandSender sender) {
        sendMessage(getFramedMessage(header, message, dashNumber, sender), sender);
    }

    public static void sendFramedMessage(Component message, int dashNumber, CommandSender sender) {
        sendMessage(getFramedMessage(message, dashNumber, sender), sender);
    }

    public static void sendFramedMessage(Component message, CommandSender sender) {
        sendFramedMessage(message, 42, sender);
    }

    public static TextColor getMainColor() {
        return TextColor.color(0x550D77);
    }

    public static TextColor getSecondaryColor() {
        return TextColor.color(0x831012);
    }
}
