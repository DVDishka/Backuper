package ru.dvdishka.backuper.back;

import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import ru.dvdishka.backuper.back.config.Config;
import ru.dvdishka.backuper.back.common.Common;
import ru.dvdishka.backuper.back.common.Logger;
import ru.dvdishka.backuper.handlers.worldchangecatch.WorldChangeCatcher;
import ru.dvdishka.backuper.back.common.Scheduler;
import ru.dvdishka.backuper.handlers.commands.menu.Menu;
import ru.dvdishka.backuper.handlers.commands.menu.delete.Delete;
import ru.dvdishka.backuper.handlers.commands.menu.delete.DeleteConfirmation;
import ru.dvdishka.backuper.handlers.commands.menu.toZIP.ToZIP;
import ru.dvdishka.backuper.handlers.commands.menu.toZIP.ToZIPConfirmation;
import ru.dvdishka.backuper.handlers.commands.menu.unZIP.UnZIP;
import ru.dvdishka.backuper.handlers.commands.menu.unZIP.UnZIPConfirmation;
import ru.dvdishka.backuper.handlers.commands.reload.Reload;
import ru.dvdishka.backuper.handlers.commands.common.Permissions;
import ru.dvdishka.backuper.handlers.commands.backup.Backup;
import ru.dvdishka.backuper.handlers.commands.list.List;
import ru.dvdishka.backuper.handlers.commands.backup.BackupProcessStarter;
import ru.dvdishka.backuper.handlers.worldchangecatch.WorldChangeCatcherNew;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;

public class Initialization implements Listener {

    public static void initBStats(JavaPlugin plugin) {
        @SuppressWarnings("unused")
        Metrics bStats = new Metrics(plugin, Common.bStatsId);
    }

    public static void initAutoBackup() {

        if (Config.getInstance().isAutoBackup()) {

            long delay;

            if (Config.getInstance().isFixedBackupTime()) {

                if (Config.getInstance().getBackupTime() > LocalDateTime.now().getHour()) {
                    delay = (long) Config.getInstance().getBackupTime() * 60 * 60 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());

                } else {
                    delay = (long) Config.getInstance().getBackupTime() * 60 * 60 + 86400 - (LocalDateTime.now().getHour() * 60 * 60 + LocalDateTime.now().getMinute() * 60 + LocalDateTime.now().getSecond());
                }
            } else {
                delay = Config.getInstance().getBackupPeriod() * 60L - (LocalDateTime.now().toEpochSecond(ZoneOffset.UTC) - Config.getInstance().getLastBackup());
            }

            if (delay <= 0) {
                delay = 1;
            }

            Logger.getLogger().devLog("Delay: " + delay);

            Scheduler.getScheduler().runSyncRepeatingTask(Common.plugin, new BackupProcessStarter(Config.getInstance().getAfterBackup(), true), delay * 20, Config.getInstance().getBackupPeriod() * 60L * 20L);
        }
    }

    public static void initConfig(File configFile, CommandSender sender) {

        if (configFile.exists()) {

            Config.getInstance().load(configFile, sender);

        } else {

            try {

                Common.plugin.saveDefaultConfig();
                Config.getInstance().load(configFile, sender);

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong when trying to create config file!", sender);
                Logger.getLogger().devWarn("Initialization", e.getMessage());
            }
        }
    }

    public static void initCommands() {

        CommandTree backupCommandTree = new CommandTree("backup");

        backupCommandTree.executes((sender, args) -> {

            if (sender.hasPermission(Permissions.BACKUP.getPermission())) {
                new Backup().execute(sender, args);
            } else {
                Common.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
            }
        })

                .then(new LiteralArgument("STOP").withPermission(Permissions.STOP.getPermission())

                        .executes((sender, args) -> {

                            new Backup("STOP").execute(sender, args);
                        })
                )

                .then(new LiteralArgument("RESTART").withPermission(Permissions.RESTART.getPermission())

                        .executes((sender, args) -> {

                            new Backup("RESTART").execute(sender, args);
                        })
                )
        ;
        backupCommandTree.register();

        CommandTree backupListCommandTree = new CommandTree("backup").withPermission(Permissions.LIST.getPermission());
        backupListCommandTree

                .then(new LiteralArgument("list").withPermission(Permissions.LIST.getPermission())

                        .executes((sender, args) -> {

                            new List().execute(sender, args);
                        })

                        .then(new IntegerArgument("pageNumber").withPermission(Permissions.LIST.getPermission())

                                .executes((sender, args) -> {

                                    new List().execute(sender, args);
                                })
                        )
                )
        ;
        backupListCommandTree.register();

        CommandTree backupReloadCommandTree = new CommandTree("backup");
        backupReloadCommandTree
                .then(new LiteralArgument("reload").withPermission(Permissions.RELOAD.getPermission())

                        .executes((sender, args) -> {

                            new Reload().execute(sender, args);
                        })
                )
        ;
        backupReloadCommandTree.register();

        CommandTree backupMenuCommandTree = new CommandTree("backup");
        backupMenuCommandTree

                .then(new LiteralArgument("menu").withPermission(Permissions.LIST.getPermission())

                        .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollection((info) -> {

                            ArrayList<LocalDateTime> backups = Common.getBackups();
                            ru.dvdishka.backuper.back.common.Backup.sortLocalDateTimeDecrease(backups);

                            ArrayList<String> backupSuggestions = new ArrayList<>();

                            for (LocalDateTime backupName : backups) {
                                backupSuggestions.add("\"" + backupName.format(ru.dvdishka.backuper.back.common.Backup.dateTimeFormatter) + "\"");
                            }
                            return backupSuggestions;
                        }))

                                        .executes((sender, args) -> {

                                            new Menu().execute(sender, args);
                                        })

                                        .then(new StringArgument("action")
                                                .replaceSuggestions(ArgumentSuggestions.strings("delete", "toZIP", "unZIP"))

                                                .executes((sender, args) -> {

                                                    if (Objects.equals(args.get("action"), "deleteConfirmation")) {
                                                        if (sender.hasPermission(Permissions.DELETE.getPermission())) {
                                                            new DeleteConfirmation().execute(sender, args);
                                                        } else {
                                                            Common.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "delete")) {
                                                        if (sender.hasPermission(Permissions.DELETE.getPermission())) {
                                                            new Delete().execute(sender, args);
                                                        } else {
                                                            Common.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "toZIPConfirmation")) {
                                                        if (sender.hasPermission(Permissions.TO_ZIP.getPermission())) {
                                                            new ToZIPConfirmation().execute(sender, args);
                                                        } else {
                                                            Common.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "toZIP")) {
                                                        if (sender.hasPermission(Permissions.TO_ZIP.getPermission())) {
                                                            new ToZIP().execute(sender, args);
                                                        } else {
                                                            Common.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "unZIPConfirmation")) {
                                                        if (sender.hasPermission(Permissions.UNZIP.getPermission())) {
                                                            new UnZIPConfirmation().execute(sender, args);
                                                        } else {
                                                            Common.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "unZIP")) {
                                                        if (sender.hasPermission(Permissions.UNZIP.getPermission())) {
                                                            new UnZIP().execute(sender, args);
                                                        } else {
                                                            Common.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }
                                                })
                                        )
                        )
                )
        ;
        backupMenuCommandTree.register();
    }

    public static void initEventHandlers() {

        Bukkit.getPluginManager().registerEvents(new Initialization(), Common.plugin);
        Bukkit.getPluginManager().registerEvents(new WorldChangeCatcher(), Common.plugin);

        boolean areWorldChangeEventsExists = true;
        for (String eventName : WorldChangeCatcherNew.eventNames) {
            try {
                Class.forName(eventName);
            } catch (Exception e) {
                areWorldChangeEventsExists = false;
            }
        }
        if (areWorldChangeEventsExists) {
            Bukkit.getPluginManager().registerEvents(new WorldChangeCatcherNew(), Common.plugin);
        }
    }

    public static void checkDependencies() {

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Common.isFolia = true;
            Logger.getLogger().devLog("Folia/Paper(1.20+) has been detected!");
        } catch (Exception e) {
            Common.isFolia = false;
            Logger.getLogger().devLog("Folia/Paper(1.20+) has not been detected!");
        }
    }

    public static void checkOperatingSystem() {
        if (Common.isWindows) {
            ru.dvdishka.backuper.back.common.Backup.dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH;mm;ss");
        }
    }

    public static void checkPluginVersion() {

        try {

            HttpURLConnection connection = (HttpURLConnection) Common.getLatestVersionURL.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String input;
            StringBuilder response = new StringBuilder();

            while ((input = in.readLine()) != null) {
                response.append(input);
            }
            in.close();

            if (response.toString().equals(Common.getProperty("version"))) {
                Common.isUpdatedToLatest = true;
                Logger.getLogger().log("You are using the latest version of Backuper!");
            } else {

                Common.isUpdatedToLatest = false;

                String message = "You are using an outdated version of Backuper, please update it to the latest and check the changelist!";
                for (String downloadLink : Common.downloadLinks) {
                    message = message.concat("\nDownload link: " + downloadLink);
                }

                Logger.getLogger().warn(message);
            }

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to check Backuper updates!");
            Logger.getLogger().devWarn("Initialization", e);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        if (event.getPlayer().isOp() && !Common.isUpdatedToLatest) {

            Component message = Component.empty();

            message = message
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)))
                    .append(Component.newline());

            message = message
                    .append(Component.text("You are using an outdated version of Backuper!\nPlease update it to the latest and check the changelist!")
                            .decorate(TextDecoration.BOLD)
                            .color(NamedTextColor.RED));

            int downloadLLinkNumber = 0;
            for (String downloadLink : Common.downloadLinks) {

                message = message.append(Component.newline());

                message = message
                        .append(Component.text("Download link: " + Common.downloadLinksName.get(downloadLLinkNumber))
                                .clickEvent(ClickEvent.openUrl(downloadLink))
                                .decorate(TextDecoration.UNDERLINED));

                downloadLLinkNumber++;
            }

            message = message
                    .append(Component.newline())
                    .append(Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)));

            event.getPlayer().sendMessage(message);
        }
    }

    @EventHandler
    public void onStartCompleted(ServerLoadEvent event) {
        Initialization.initAutoBackup();
    }
}
