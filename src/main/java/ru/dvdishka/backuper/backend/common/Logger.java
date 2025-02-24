package ru.dvdishka.backuper.backend.common;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.util.Arrays;
import java.util.Objects;

public class Logger {

    public static Logger getLogger() {
        return new Logger();
    }

    public void log(String text) {
        Utils.plugin.getLogger().info(text);
    }

    public void log(String text, CommandSender sender) {

        Utils.plugin.getLogger().info(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                UIUtils.sendMessage(text, sender);
            } catch (Exception ignored) {
            }
        }
    }

    public void devLog(String text) {
        if (Config.getInstance().isBetterLogging()) {
            Utils.plugin.getLogger().info(text);
        }
    }

    public void devLog(String text, CommandSender sender) {

        if (Config.getInstance().isBetterLogging()) {
            Utils.plugin.getLogger().info(text);

            if (!(sender instanceof ConsoleCommandSender)) {
                try {
                    UIUtils.sendMessage(text, sender);
                } catch (Exception ignored) {
                }
            }
        }
    }


    public void warn(String text) {
        Utils.plugin.getLogger().warning(text);
    }

    public void warn(String text, CommandSender sender) {

        Utils.plugin.getLogger().warning(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                UIUtils.returnWarning(text, sender);
            } catch (Exception ignored) {
            }
        }
    }

    public void warn(Class<?> sourceClass, String text, CommandSender sender) {

        if (sourceClass == GoogleDriveUtils.class && !Utils.plugin.isEnabled()) {
            return;
        }

        Utils.plugin.getLogger().warning(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                UIUtils.returnWarning(text, sender);
            } catch (Exception ignored) {
            }
        }
    }

    public void success(String text) {
        Utils.plugin.getLogger().info(text);
    }

    public void success(String text, CommandSender sender) {

        Utils.plugin.getLogger().info(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                UIUtils.returnSuccess(text, sender);
            } catch (Exception ignored) {
            }
        }
    }

    public void devWarn(Class<?> sourceClass, String text) {

        if (sourceClass == GoogleDriveUtils.class && !Utils.plugin.isEnabled()) {
            return;
        }

        if (Config.getInstance().isBetterLogging()) {
            Utils.plugin.getLogger().warning(sourceClass.getSimpleName() + ": " + text);
        }
    }

    public void devWarn(String sourceClassName, String text) {

        if (Objects.equals(sourceClassName, "GoogleDriveUtils") && !Utils.plugin.isEnabled()) {
            return;
        }

        if (Config.getInstance().isBetterLogging()) {
            Utils.plugin.getLogger().warning(sourceClassName + ": " + text);
        }
    }

    public void warn(Class<?> sourceClass, Exception exception) {

        if (sourceClass == GoogleDriveUtils.class && !Utils.plugin.isEnabled()) {
            return;
        }

        Utils.plugin.getLogger().warning(sourceClass.getSimpleName() + ": " + exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
    }

    public void warn(String sourceClassName, Exception exception) {

        if (Objects.equals(sourceClassName, "GoogleDriveUtils") && !Utils.plugin.isEnabled()) {
            return;
        }

        Utils.plugin.getLogger().warning(sourceClassName + ": " + exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
    }

    public void devWarn(Class<?> sourceClass, Exception exception) {

        if (sourceClass == GoogleDriveUtils.class && !Utils.plugin.isEnabled()) {
            return;
        }

        if (Config.getInstance().isBetterLogging()) {
            Utils.plugin.getLogger().warning(sourceClass.getSimpleName() + ": " + exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
        }
    }

    public void devWarn(String sourceClassName, Exception exception) {

        if (Objects.equals(sourceClassName, "GoogleDriveUtils") && !Utils.plugin.isEnabled()) {
            return;
        }

        if (Config.getInstance().isBetterLogging()) {
            Utils.plugin.getLogger().warning(sourceClassName + ": " + exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
        }
    }
}
