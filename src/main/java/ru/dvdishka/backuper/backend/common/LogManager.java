package ru.dvdishka.backuper.backend.common;

import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.task.TaskException;
import ru.dvdishka.backuper.backend.util.UIUtils;

import java.util.Arrays;

public class LogManager {

    public void log(String text) {
        Backuper.getInstance().getLogger().info(text);
    }

    public void log(String text, CommandSender sender) {

        Backuper.getInstance().getLogger().info(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                UIUtils.sendMessage(text, sender);
            } catch (Exception ignored) {
            }
        }
    }

    public void devLog(String text) {
        if (Config.getInstance().isBetterLogging()) {
            Backuper.getInstance().getLogger().info(text);
        }
    }

    public void devLog(String text, CommandSender sender) {

        if (Config.getInstance().isBetterLogging()) {
            Backuper.getInstance().getLogger().info(text);

            if (!(sender instanceof ConsoleCommandSender)) {
                try {
                    UIUtils.sendMessage(text, sender);
                } catch (Exception ignored) {
                }
            }
        }
    }

    public void warn(String text) {
        Backuper.getInstance().getLogger().warning(text);
    }

    public void warn(TaskException taskException) {
        Backuper.getInstance().getLogger().warning(taskException.getMessage());
        Backuper.getInstance().getLogger().warning(taskException.getException().getMessage());
        Backuper.getInstance().getLogger().warning(Arrays.toString(taskException.getException().getStackTrace()));
    }

    public void warn(String text, CommandSender sender) {

        Backuper.getInstance().getLogger().warning(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                UIUtils.returnWarning(text, sender);
            } catch (Exception ignored) {
            }
        }
    }

    public void success(String text) {
        Backuper.getInstance().getLogger().info(text);
    }

    public void success(String text, CommandSender sender) {

        Backuper.getInstance().getLogger().info(text);

        if (!(sender instanceof ConsoleCommandSender)) {
            try {
                UIUtils.returnSuccess(text, sender);
            } catch (Exception ignored) {
            }
        }
    }

    public void devWarn(String text) {

        if (!Backuper.getInstance().isEnabled()) {
            return;
        }

        if (Config.getInstance().isBetterLogging()) {
            Backuper.getInstance().getLogger().warning(text);
        }
    }

    public void warn(Exception exception) {

        if (!Backuper.getInstance().isEnabled()) {
            return;
        }

        Backuper.getInstance().getLogger().warning("%s\n%s".formatted(exception.getMessage(), Arrays.toString(exception.getStackTrace())));
    }

    public void devWarn(Exception exception) {

        if (!Backuper.getInstance().isEnabled()) {
            return;
        }

        if (Config.getInstance().isBetterLogging()) {
            Backuper.getInstance().getLogger().warning("%s\n%s".formatted(exception.getMessage(), Arrays.toString(exception.getStackTrace())));
        }
    }
}
