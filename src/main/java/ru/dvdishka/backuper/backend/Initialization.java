package ru.dvdishka.backuper.backend;

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
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.backend.utils.Utils;
import ru.dvdishka.backuper.backend.utils.Logger;
import ru.dvdishka.backuper.handlers.WorldChangeCatcher;
import ru.dvdishka.backuper.backend.utils.Scheduler;
import ru.dvdishka.backuper.handlers.commands.menu.MenuCommand;
import ru.dvdishka.backuper.handlers.commands.menu.delete.DeleteCommand;
import ru.dvdishka.backuper.handlers.commands.menu.delete.DeleteConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.menu.toZIP.ToZIPCommand;
import ru.dvdishka.backuper.handlers.commands.menu.toZIP.ToZIPConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.menu.unZIP.UnZIPCommand;
import ru.dvdishka.backuper.handlers.commands.menu.unZIP.UnZIPConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.reload.ReloadCommand;
import ru.dvdishka.backuper.handlers.commands.Permissions;
import ru.dvdishka.backuper.handlers.commands.backup.BackupCommand;
import ru.dvdishka.backuper.handlers.commands.list.ListCommand;
import ru.dvdishka.backuper.handlers.commands.backup.BackupProcessStarter;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Objects;

import static java.lang.Math.max;
import static java.lang.Math.min;

public class Initialization implements Listener {

    public static void initBStats(JavaPlugin plugin) {
        @SuppressWarnings("unused")
        Metrics bStats = new Metrics(plugin, Utils.bStatsId);
    }

    public static void initAutoBackup() {

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

            new BackupProcessStarter("NOTHING").runDeleteOldBackupsSync();

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

                if (Config.getInstance().getAlertTimeBeforeRestart() != -1) {

                    long firstAlertDelay = max((delay - Config.getInstance().getAlertTimeBeforeRestart()) * 20, 1);
                    long alertTime = min(Config.getInstance().getAlertTimeBeforeRestart(), delay);

                    Scheduler.getScheduler().runSyncDelayed(Utils.plugin, () -> {

                        Backup.sendBackupAlert(alertTime, Config.getInstance().getAfterBackup());

                    }, firstAlertDelay);

                    long secondAlertDelay = max((delay + Config.getInstance().getBackupPeriod() * 60L - Config.getInstance().getAlertTimeBeforeRestart()) * 20, 1);

                    Scheduler.getScheduler().runSyncRepeatingTask(Utils.plugin, () -> {
                        Backup.sendBackupAlert(Config.getInstance().getAlertTimeBeforeRestart(), Config.getInstance().getAfterBackup());
                    }, secondAlertDelay, Config.getInstance().getBackupPeriod() * 60L * 20L);
                }
                Scheduler.getScheduler().runSyncRepeatingTask(Utils.plugin, new BackupProcessStarter(Config.getInstance().getAfterBackup(), true), delay * 20, Config.getInstance().getBackupPeriod() * 60L * 20L);
            }
        });
    }

    public static void initConfig(File configFile, CommandSender sender) {

        if (configFile.exists()) {

            Config.getInstance().load(configFile, sender);

        } else {

            try {

                Utils.plugin.saveDefaultConfig();
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
                new BackupCommand(sender, args).execute();
            } else {
                Utils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
            }
        })

                .then(new LongArgument("delaySeconds")

                        .executes((sender, args) -> {

                            new BackupCommand(sender, args).execute();
                        })
                )

                .then(new LiteralArgument("STOP").withPermission(Permissions.STOP.getPermission())

                        .executes((sender, args) -> {

                            new BackupCommand(sender, args, "STOP").execute();
                        })

                        .then(new LongArgument("delaySeconds")

                                .executes((sender, args) -> {

                                    new BackupCommand(sender, args, "STOP").execute();
                                })
                        )
                )

                .then(new LiteralArgument("RESTART").withPermission(Permissions.RESTART.getPermission())

                        .executes((sender, args) -> {

                            new BackupCommand(sender, args, "RESTART").execute();
                        })

                        .then(new LongArgument("delaySeconds")

                                .executes((sender, args) -> {

                                    new BackupCommand(sender, args, "RESTART").execute();
                                })
                        )
                )
        ;
        backupCommandTree.register();

        CommandTree backupListCommandTree = new CommandTree("backup").withPermission(Permissions.LIST.getPermission());
        backupListCommandTree

                .then(new LiteralArgument("list").withPermission(Permissions.LIST.getPermission())

                        .executes((sender, args) -> {

                            new ListCommand(sender, args).execute();
                        })

                        .then(new IntegerArgument("pageNumber").withPermission(Permissions.LIST.getPermission())

                                .executes((sender, args) -> {

                                    new ListCommand(sender, args).execute();
                                })
                        )
                )
        ;
        backupListCommandTree.register();

        CommandTree backupReloadCommandTree = new CommandTree("backup");
        backupReloadCommandTree
                .then(new LiteralArgument("reload").withPermission(Permissions.RELOAD.getPermission())

                        .executes((sender, args) -> {

                            new ReloadCommand(sender, args).execute();
                        })
                )
        ;
        backupReloadCommandTree.register();

        CommandTree backupMenuCommandTree = new CommandTree("backup");
        backupMenuCommandTree

                .then(new LiteralArgument("menu").withPermission(Permissions.LIST.getPermission())

                        .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollection((info) -> {

                            ArrayList<LocalDateTime> backups = Utils.getBackups();
                            ru.dvdishka.backuper.backend.utils.Backup.sortLocalDateTimeDecrease(backups);

                            ArrayList<String> backupSuggestions = new ArrayList<>();

                            for (LocalDateTime backupName : backups) {
                                backupSuggestions.add("\"" + backupName.format(ru.dvdishka.backuper.backend.utils.Backup.dateTimeFormatter) + "\"");
                            }
                            return backupSuggestions;
                        }))

                                        .executes((sender, args) -> {

                                            new MenuCommand(sender, args).execute();
                                        })

                                        .then(new StringArgument("action")
                                                .replaceSuggestions(ArgumentSuggestions.strings("delete", "toZIP", "unZIP"))

                                                .executes((sender, args) -> {

                                                    if (Objects.equals(args.get("action"), "deleteConfirmation")) {
                                                        if (sender.hasPermission(Permissions.DELETE.getPermission())) {
                                                            new DeleteConfirmationCommand(sender, args).execute();
                                                        } else {
                                                            Utils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "delete")) {
                                                        if (sender.hasPermission(Permissions.DELETE.getPermission())) {
                                                            new DeleteCommand(sender, args).execute();
                                                        } else {
                                                            Utils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "toZIPConfirmation")) {
                                                        if (sender.hasPermission(Permissions.TO_ZIP.getPermission())) {
                                                            new ToZIPConfirmationCommand(sender, args).execute();
                                                        } else {
                                                            Utils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "toZIP")) {
                                                        if (sender.hasPermission(Permissions.TO_ZIP.getPermission())) {
                                                            new ToZIPCommand(sender, args).execute();
                                                        } else {
                                                            Utils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "unZIPConfirmation")) {
                                                        if (sender.hasPermission(Permissions.UNZIP.getPermission())) {
                                                            new UnZIPConfirmationCommand(sender, args).execute();
                                                        } else {
                                                            Utils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }

                                                    if (Objects.equals(args.get("action"), "unZIP")) {
                                                        if (sender.hasPermission(Permissions.UNZIP.getPermission())) {
                                                            new UnZIPCommand(sender, args).execute();
                                                        } else {
                                                            Utils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                        }
                                                    }
                                                })
                                        )
                        )
                )
        ;
        backupMenuCommandTree.register();

        CommandTree backupStatusCommandTree = new CommandTree("backup");

        backupStatusCommandTree.then(new LiteralArgument("status")
                .withPermission(Permissions.STATUS.getPermission())

                .executes((sender, args) -> {

                    new StatusCommand(sender, args).execute();
                })
        );

        backupStatusCommandTree.register();
    }

    public static void initEventHandlers() {

        Bukkit.getPluginManager().registerEvents(new Initialization(), Utils.plugin);
        Bukkit.getPluginManager().registerEvents(new WorldChangeCatcher(), Utils.plugin);
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
            Utils.isFolia = true;
            Logger.getLogger().devLog("Folia/Paper(1.20+) has been detected!");
        } catch (Exception e) {
            Utils.isFolia = false;
            Logger.getLogger().devLog("Folia/Paper(1.20+) has not been detected!");
        }
    }

    public static void checkOperatingSystem() {
        if (Utils.isWindows) {
            ru.dvdishka.backuper.backend.utils.Backup.dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH;mm;ss");
        }
    }

    public static void checkPluginVersion() {

        try {

            HttpURLConnection connection = (HttpURLConnection) Utils.getLatestVersionURL.openConnection();
            connection.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String input;
            StringBuilder response = new StringBuilder();

            while ((input = in.readLine()) != null) {
                response.append(input);
            }
            in.close();

            if (response.toString().equals(Utils.getProperty("version"))) {
                Utils.isUpdatedToLatest = true;
                Logger.getLogger().log("You are using the latest version of Backuper!");
            } else {

                Utils.isUpdatedToLatest = false;

                String message = "You are using an outdated version of Backuper, please update it to the latest and check the changelist!";
                for (String downloadLink : Utils.downloadLinks) {
                    message = message.concat("\nDownload link: " + downloadLink);
                }

                Logger.getLogger().warn(message);
            }

        } catch (Exception e) {

            Logger.getLogger().warn("Failed to check Backuper updates!");
            Logger.getLogger().warn("Initialization", e);
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {

        if (event.getPlayer().isOp() && !Utils.isUpdatedToLatest) {

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
            for (String downloadLink : Utils.downloadLinks) {

                message = message.append(Component.newline());

                message = message
                        .append(Component.text("Download link: " + Utils.downloadLinksName.get(downloadLLinkNumber))
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
