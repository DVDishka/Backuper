package ru.dvdishka.backuper.backend.utils;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.backend.config.Config;

import java.util.Arrays;

public class Logger {

    public static Logger getLogger() {
        return new Logger();
    }

    public void log(String text) {
        Common.plugin.getLogger().info(text);
    }

    public void log(String text, CommandSender sender) {

        Common.plugin.getLogger().info(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                Common.sendMessage(text, sender);
            } catch (Exception ignored) {}
        }
    }

    public void devLog(String text) {
        if (Config.getInstance().isBetterLogging()) {
            Common.plugin.getLogger().info(text);
        }
    }

    public void devLog(String text, CommandSender sender) {

        if (Config.getInstance().isBetterLogging()) {
            Common.plugin.getLogger().info(text);

            if (!(sender instanceof ConsoleCommandSender)) {
                try {
                    Common.sendMessage(text, sender);
                } catch (Exception ignored) {}
            }
        }
    }


    public void warn(String text) {
        Common.plugin.getLogger().warning(text);
    }

    public void warn(String text, CommandSender sender) {

        Common.plugin.getLogger().warning(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                Common.returnWarning(text, sender);
            } catch (Exception ignored) {}
        }
    }

    public void success(String text) {
        Common.plugin.getLogger().info(text);
    }

    public void success(String text, CommandSender sender) {

        Common.plugin.getLogger().info(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                Common.returnSuccess(text, sender);
            } catch (Exception ignored) {}
        }
    }

    public void devWarn(Object sourceClass, String text) {
        if (Config.getInstance().isBetterLogging()) {
            Common.plugin.getLogger().warning(sourceClass.getClass().getSimpleName() + ": " + text);
        }
    }

    public void devWarn(String sourceClassName, String text) {
        if (Config.getInstance().isBetterLogging()) {
            Common.plugin.getLogger().warning(sourceClassName + ": " + text);
        }
    }

    public void warn(Object sourceClass, Exception exception) {
        Common.plugin.getLogger().warning(sourceClass.getClass().getSimpleName() + ": " + exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
    }

    public void warn(String sourceClassName, Exception exception) {
        Common.plugin.getLogger().warning(sourceClassName + ": " + exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
    }
}
