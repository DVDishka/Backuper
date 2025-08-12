package ru.dvdishka.backuper.backend;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.quartz.CronTrigger;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.config.ConfigBackwardsCompatibility;
import ru.dvdishka.backuper.backend.quartzjob.AutoBackupQuartzJob;
import ru.dvdishka.backuper.backend.util.*;
import ru.dvdishka.backuper.handlers.commands.Permissions;
import ru.dvdishka.backuper.handlers.commands.backup.BackupCommand;
import ru.dvdishka.backuper.handlers.commands.googleDrive.GoogleDriveLinkCommand;
import ru.dvdishka.backuper.handlers.commands.list.ListCommand;
import ru.dvdishka.backuper.handlers.commands.menu.MenuCommand;
import ru.dvdishka.backuper.handlers.commands.menu.copyToFtp.CopyToFtpCommand;
import ru.dvdishka.backuper.handlers.commands.menu.copyToFtp.CopyToFtpConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.menu.copyToGoogleDrive.CopyToGoogleDriveCommand;
import ru.dvdishka.backuper.handlers.commands.menu.copyToGoogleDrive.CopyToGoogleDriveConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.menu.copyToLocal.CopyToLocalCommand;
import ru.dvdishka.backuper.handlers.commands.menu.copyToLocal.CopyToLocalConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.menu.copyToSftp.CopyToSftpCommand;
import ru.dvdishka.backuper.handlers.commands.menu.copyToSftp.CopyToSftpConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.menu.delete.DeleteCommand;
import ru.dvdishka.backuper.handlers.commands.menu.delete.DeleteConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.menu.toZIP.ToZIPCommand;
import ru.dvdishka.backuper.handlers.commands.menu.toZIP.ToZIPConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.menu.unZIP.UnZIPCommand;
import ru.dvdishka.backuper.handlers.commands.menu.unZIP.UnZIPConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.reload.ReloadCommand;
import ru.dvdishka.backuper.handlers.commands.task.cancel.CancelCommand;
import ru.dvdishka.backuper.handlers.commands.task.cancel.CancelConfirmationCommand;
import ru.dvdishka.backuper.handlers.commands.task.status.StatusCommand;
import ru.dvdishka.backuper.handlers.worldchangecatch.WorldChangeCatcher;
import ru.dvdishka.backuper.handlers.worldchangecatch.WorldChangeCatcherNew;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class Initialization implements Listener {

    public static void initBStats(JavaPlugin plugin) {

        Backuper.getInstance().getLogManager().log("Initializing BStats...");

        @SuppressWarnings("unused")
        Metrics bStats = new Metrics(plugin, Utils.bStatsId);

        bStats.addCustomChart(new SimplePie("local_storage", () -> Config.getInstance().getLocalConfig().isEnabled() ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("ftp_storage", () -> Config.getInstance().getFtpConfig().isEnabled() ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("sftp_storage", () -> Config.getInstance().getSftpConfig().isEnabled() ? "enabled" : "disabled"));
        bStats.addCustomChart(new SimplePie("google_drive_storage", () -> Config.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.checkConnection() ? "enabled" : "disabled"));

        Backuper.getInstance().getLogManager().log("BStats initialization completed");
    }

    public static void indexStorages(CommandSender sender) {

        Backuper.getInstance().getLogManager().log("Indexing storages...");

        if (Config.getInstance().getLocalConfig().isEnabled()) {
            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                try {
                    Backuper.getInstance().getLogManager().devLog("Indexing local storage...");
                    new ListCommand("local", false, sender, new CommandArguments(new Objects[]{}, new HashMap<String, Object>(), new String[]{}, new HashMap<String, String>(), "")).execute();
                    Backuper.getInstance().getLogManager().devLog("Local storage has been indexed");
                } catch (Exception ignored) {}
            });
        }
        if (Config.getInstance().getFtpConfig().isEnabled()) {
            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                Backuper.getInstance().getLogManager().devLog("Indexing FTP storage...");
                new ListCommand("ftp", false, sender, new CommandArguments(new Objects[]{}, new HashMap<String, Object>(), new String[]{}, new HashMap<String, String>(), "")).execute();
                Backuper.getInstance().getLogManager().devLog("FTP storage has been indexed");
            });
        }
        if (Config.getInstance().getSftpConfig().isEnabled()) {
            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                try {
                    Backuper.getInstance().getLogManager().devLog("Indexing SFTP storage...");
                    new ListCommand("sftp", false, sender, new CommandArguments(new Objects[]{}, new HashMap<String, Object>(), new String[]{}, new HashMap<String, String>(), "")).execute();
                    Backuper.getInstance().getLogManager().devLog("SFTP storage has been indexed");
                } catch (Exception e) {
                    Backuper.getInstance().getLogManager().devWarn(e);
                }
            });
        }
        if (Config.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.checkConnection()) {
            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                try {
                    Backuper.getInstance().getLogManager().devLog("Indexing GoogleDrive storage...");
                    new ListCommand("googleDrive", false, sender, new CommandArguments(new Objects[]{}, new HashMap<String, Object>(), new String[]{}, new HashMap<String, String>(), "")).execute();
                    Backuper.getInstance().getLogManager().devLog("GoogleDrive storage has been indexed");
                } catch (Exception ignored) {}
            });
        }
    }

    public static void initAutoBackup() {

        Backuper.getInstance().getScheduleManager().runAsync(() -> {

            Backuper.getInstance().getLogManager().log("Initializing auto backup...");

            CronTrigger autoBackupJobTrigger = Backuper.getInstance().getScheduleManager().runCronScheduledJob(AutoBackupQuartzJob.class, "backup", "auto", Config.getInstance().getAutoBackupCron());
            AutoBackupQuartzJob.scheduleNextBackupAlert(autoBackupJobTrigger); // Prepare alert for backup above

            Backuper.getInstance().getLogManager().log("Auto backup initialization completed");
        });
    }

    public static void initConfig(File configFile, CommandSender sender) {

        Backuper.getInstance().getLogManager().log("Loading config...");

        if (configFile.exists()) {

            Config.getInstance().load(configFile, sender);

        } else {

            try {

                Backuper.getInstance().saveDefaultConfig();
                Config.getInstance().load(configFile, sender);

            } catch (Exception e) {

                Backuper.getInstance().getLogManager().warn("Something went wrong when trying to create config file!", sender);
                Backuper.getInstance().getLogManager().warn(e);
            }
        }

        FtpUtils.init();
        SftpUtils.init();
        GoogleDriveUtils.init();

        Backuper.getInstance().getLogManager().log("Config loading completed", sender);
    }

    public static void initCommands() {

        CommandTree backupCommandTree = new CommandTree("backuper").withPermission(Permissions.BACKUPER.getPermission());

        backupCommandTree

                .then(new LiteralArgument("backup").withPermission(Permissions.BACKUP.getPermission())

                        .then(new StringArgument("storage").includeSuggestions(ArgumentSuggestions.stringCollection((sender) -> {
                                            ArrayList<String> suggestions = new ArrayList<>();

                                            if (Config.getInstance().getLocalConfig().isEnabled()) {
                                                suggestions.add("local");
                                            }
                                            if (Config.getInstance().getFtpConfig().isEnabled()) {
                                                suggestions.add("ftp");
                                            }
                                            if (Config.getInstance().getSftpConfig().isEnabled()) {
                                                suggestions.add("sftp");
                                            }
                                            if (Config.getInstance().getGoogleDriveConfig().isEnabled()) {
                                                suggestions.add("googleDrive");
                                            }
                                            int storageCount = suggestions.size();

                                            for (int i = 0; i < storageCount; i++) {
                                                for (int j = i + 1; j < storageCount; j++) {
                                                    suggestions.add(suggestions.get(i) + "-" + suggestions.get(j));
                                                }
                                            }

                                            for (int i = 0; i < storageCount; i++) {
                                                for (int j = i + 1; j < storageCount; j++) {
                                                    for (int k = j + 1; k < storageCount; k++) {
                                                        suggestions.add(suggestions.get(i) + "-" + suggestions.get(j) + "-" + suggestions.get(k));
                                                    }
                                                }
                                            }

                                            for (int i = 0; i < storageCount; i++) {
                                                for (int j = i + 1; j < storageCount; j++) {
                                                    for (int k = j + 1; k < storageCount; k++) {
                                                        for (int l = k + 1; l < storageCount; l++) {
                                                            suggestions.add(suggestions.get(i) + "-" + suggestions.get(j) + "-" + suggestions.get(k) + "-" + suggestions.get(l));
                                                        }
                                                    }
                                                }
                                            }

                                            return suggestions;
                                        }))

                                        .executes((sender, args) -> {

                                            new BackupCommand(sender, args).execute();
                                        })

                                        .then(new LongArgument("delaySeconds")
                                                .executes((sender, args) -> {

                                                    new BackupCommand(sender, args).execute();
                                                })
                                        )
                                        .then(new LiteralArgument("stop").withPermission(Permissions.STOP.getPermission())
                                                .executes((sender, args) -> {

                                                    new BackupCommand(sender, args, "STOP").execute();
                                                })

                                                .then(new LongArgument("delaySeconds")
                                                        .executes((sender, args) -> {

                                                            new BackupCommand(sender, args, "STOP").execute();
                                                        })
                                                )
                                        )

                                        .then(new LiteralArgument("restart").withPermission(Permissions.RESTART.getPermission())
                                                .executes((sender, args) -> {

                                                    new BackupCommand(sender, args, "RESTART").execute();
                                                })

                                                .then(new LongArgument("delaySeconds")
                                                        .executes((sender, args) -> {

                                                            new BackupCommand(sender, args, "RESTART").execute();
                                                        })
                                                )
                                        )
                        )
                )
        ;
        backupCommandTree.register();

        CommandTree backupListCommandTree = new CommandTree("backuper").withPermission(Permissions.BACKUPER.getPermission());
        backupListCommandTree

                .then(new LiteralArgument("list")

                        .then(new LiteralArgument("local").withRequirement((sender) -> {
                                            return Config.getInstance().getLocalConfig().isEnabled();
                                        }).withPermission(Permissions.LOCAL_LIST.getPermission())

                                        .executes((sender, args) -> {

                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new ListCommand("local", sender, args).execute();
                                            });
                                        })

                                        .then(new IntegerArgument("pageNumber")

                                                .executes((sender, args) -> {

                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new ListCommand("local", sender, args).execute();
                                                    });
                                                })
                                        )
                        )

                        .then(new LiteralArgument("sftp").withRequirement((sender -> {
                                            return Config.getInstance().getSftpConfig().isEnabled();
                                        })).withPermission(Permissions.SFTP_LIST.getPermission())

                                        .executes((sender, args) -> {

                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new ListCommand("sftp", sender, args).execute();
                                            });
                                        })

                                        .then(new IntegerArgument("pageNumber")

                                                .executes((sender, args) -> {

                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new ListCommand("sftp", sender, args).execute();
                                                    });
                                                })
                                        )
                        )

                        .then(new LiteralArgument("ftp").withRequirement((sender -> {
                                            return Config.getInstance().getFtpConfig().isEnabled();
                                        })).withPermission(Permissions.FTP_LIST.getPermission())

                                        .executes((sender, args) -> {

                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new ListCommand("ftp", sender, args).execute();
                                            });
                                        })

                                        .then(new IntegerArgument("pageNumber")

                                                .executes((sender, args) -> {

                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new ListCommand("ftp", sender, args).execute();
                                                    });
                                                })
                                        )
                        )

                        .then(new LiteralArgument("googleDrive")
                                .withRequirement((sender -> Config.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.checkConnection()))
                                .withPermission(Permissions.GOOGLE_DRIVE_LIST.getPermission())

                                        .executes((sender, args) -> {

                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new ListCommand("googleDrive", sender, args).execute();
                                            });
                                        })

                                        .then(new IntegerArgument("pageNumber")

                                                .executes((sender, args) -> {

                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new ListCommand("googleDrive", sender, args).execute();
                                                    });
                                                })
                                        )
                        )
                )
        ;
        backupListCommandTree.register();

        CommandTree backupReloadCommandTree = new CommandTree("backuper").withPermission(Permissions.BACKUPER.getPermission());
        backupReloadCommandTree
                .then(new LiteralArgument("config").withPermission(Permissions.CONFIG.getPermission())
                        .then(new LiteralArgument("reload").withPermission(Permissions.RELOAD.getPermission())

                                .executes((sender, args) -> {

                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                        new ReloadCommand(sender, args).execute();
                                    });
                                })
                        )
                )
        ;
        backupReloadCommandTree.register();

        CommandTree backupMenuCommandTree = new CommandTree("backuper").withPermission(Permissions.BACKUPER.getPermission());
        backupMenuCommandTree

                .then(new LiteralArgument("menu")

                        .then(new LiteralArgument("local").withRequirement((sender -> {
                                            return Config.getInstance().getLocalConfig().isEnabled();
                                        })).withPermission(Permissions.LOCAL_LIST.getPermission())

                                        .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollectionAsync((info) ->
                                                                CompletableFuture.supplyAsync(() -> {
                                            ArrayList<LocalBackup> backups = LocalBackup.getBackups();
                                            ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();

                                            for (LocalBackup backup : backups) {
                                                backupDateTimes.add(backup.getLocalDateTime());
                                            }

                                            Utils.sortLocalDateTimeDecrease(backupDateTimes);

                                            ArrayList<String> backupSuggestions = new ArrayList<>();

                                            for (LocalBackup backup : backups) {
                                                backupSuggestions.add("\"" + backup.getName() + "\"");
                                            }
                                            return backupSuggestions;
                                        })))

                                                        .executes((sender, args) -> {

                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                new MenuCommand("local", sender, args).execute();
                                                            });
                                                        })

                                                        .then(new StringArgument("action")
                                                                .replaceSuggestions(ArgumentSuggestions.stringCollection((senderSuggestionInfo) -> {

                                                                    ArrayList<String> suggestions = new ArrayList<>();

                                                                    String backupName = (String) senderSuggestionInfo.previousArgs().get("backupName");
                                                                    LocalBackup backup = LocalBackup.getInstance(backupName);

                                                                    try {
                                                                        if (Backup.BackupFileType.DIR.equals(backup.getFileType())) {
                                                                            suggestions.add("toZIP");
                                                                        }
                                                                        if (Backup.BackupFileType.ZIP.equals(backup.getFileType())) {
                                                                            suggestions.add("unZIP");
                                                                        }
                                                                        suggestions.add("delete");
                                                                        if (Config.getInstance().getFtpConfig().isEnabled()) {
                                                                            suggestions.add("copyToFtp");
                                                                        }
                                                                        if (Config.getInstance().getSftpConfig().isEnabled()) {
                                                                            suggestions.add("copyToSftp");
                                                                        }
                                                                        if (Config.getInstance().getGoogleDriveConfig().isEnabled() &&
                                                                                GoogleDriveUtils.checkConnection()) {
                                                                            suggestions.add("copyToGoogleDrive");
                                                                        }
                                                                    } catch (Exception ignored) {
                                                                    }

                                                                    return suggestions;
                                                                }))

                                                                .executes((sender, args) -> {

                                                                    if (Objects.equals(args.get("action"), "deleteConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_DELETE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new DeleteConfirmationCommand("local", sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "delete")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_DELETE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new DeleteCommand("local", sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "toZIPConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_TO_ZIP.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new ToZIPConfirmationCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "toZIP")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_TO_ZIP.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new ToZIPCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "unZIPConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_UNZIP.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new UnZIPConfirmationCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "unZIP")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_UNZIP.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new UnZIPCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToSftpConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_COPY_TO_SFTP.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToSftpConfirmationCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToSftp")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_COPY_TO_SFTP.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToSftpCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToFtpConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_COPY_TO_FTP.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToFtpConfirmationCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToFtp")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_COPY_TO_FTP.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToFtpCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToGoogleDriveConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_COPY_TO_GOOGLE_DRIVE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToGoogleDriveConfirmationCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToGoogleDrive")) {
                                                                        if (sender.hasPermission(Permissions.LOCAL_COPY_TO_GOOGLE_DRIVE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToGoogleDriveCommand(sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }
                                                                })
                                                        )
                                        )
                        )

                        .then(new LiteralArgument("sftp").withRequirement((sender -> {
                                            return Config.getInstance().getSftpConfig().isEnabled();
                                        })).withPermission(Permissions.SFTP_LIST.getPermission())

                                        .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollectionAsync((info) -> {

                                                            return CompletableFuture.supplyAsync(() -> {
                                                                ArrayList<SftpBackup> backups = SftpBackup.getBackups();
                                                                ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();

                                                                for (SftpBackup backup : backups) {
                                                                    backupDateTimes.add(backup.getLocalDateTime());
                                                                }

                                                                Utils.sortLocalDateTimeDecrease(backupDateTimes);

                                                                ArrayList<String> backupSuggestions = new ArrayList<>();

                                                                for (SftpBackup backup : backups) {
                                                                    backupSuggestions.add("\"" + backup.getName() + "\"");
                                                                }
                                                                return backupSuggestions;
                                                            });
                                                        }))

                                                        .executes((sender, args) -> {

                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                new MenuCommand("sftp", sender, args).execute();
                                                            });
                                                        })

                                                        .then(new StringArgument("action")
                                                                .replaceSuggestions(ArgumentSuggestions.strings("delete", "copyToLocal"))

                                                                .executes((sender, args) -> {

                                                                    if (Objects.equals(args.get("action"), "deleteConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.SFTP_DELETE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new DeleteConfirmationCommand("sftp", sender, args).execute();
                                                                            });

                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "delete")) {
                                                                        if (sender.hasPermission(Permissions.SFTP_DELETE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new DeleteCommand("sftp", sender, args).execute();
                                                                            });

                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToLocalConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.SFTP_COPY_TO_LOCAL.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToLocalConfirmationCommand("sftp", sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToLocal")) {
                                                                        if (sender.hasPermission(Permissions.SFTP_COPY_TO_LOCAL.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToLocalCommand("sftp", sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }
                                                                })
                                                        )
                                        )
                        )

                        .then(new LiteralArgument("ftp").withRequirement((sender -> {
                                            return Config.getInstance().getFtpConfig().isEnabled();
                                        })).withPermission(Permissions.FTP_LIST.getPermission())

                                        .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollectionAsync((info) -> {

                                                            return CompletableFuture.supplyAsync(() -> {
                                                                ArrayList<FtpBackup> backups = FtpBackup.getBackups();
                                                                ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();

                                                                for (FtpBackup backup : backups) {
                                                                    backupDateTimes.add(backup.getLocalDateTime());
                                                                }

                                                                Utils.sortLocalDateTimeDecrease(backupDateTimes);

                                                                ArrayList<String> backupSuggestions = new ArrayList<>();

                                                                for (FtpBackup backup : backups) {
                                                                    backupSuggestions.add("\"" + backup.getName() + "\"");
                                                                }
                                                                return backupSuggestions;
                                                            });
                                                        }))

                                                        .executes((sender, args) -> {

                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                new MenuCommand("ftp", sender, args).execute();
                                                            });
                                                        })

                                                        .then(new StringArgument("action")
                                                                .replaceSuggestions(ArgumentSuggestions.strings("delete", "copyToLocal"))

                                                                .executes((sender, args) -> {

                                                                    if (Objects.equals(args.get("action"), "deleteConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.FTP_DELETE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new DeleteConfirmationCommand("ftp", sender, args).execute();
                                                                            });

                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "delete")) {
                                                                        if (sender.hasPermission(Permissions.FTP_DELETE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new DeleteCommand("ftp", sender, args).execute();
                                                                            });

                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToLocalConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.FTP_COPY_TO_LOCAL.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToLocalConfirmationCommand("ftp", sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToLocal")) {
                                                                        if (sender.hasPermission(Permissions.FTP_COPY_TO_LOCAL.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToLocalCommand("ftp", sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }
                                                                })
                                                        )
                                        )
                        )

                        .then(new LiteralArgument("googleDrive")
                                .withRequirement((sender -> Config.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.checkConnection()))
                                .withPermission(Permissions.GOOGLE_DRIVE_LIST.getPermission())

                                        .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollectionAsync((info) -> {

                                                            return CompletableFuture.supplyAsync(() -> {
                                                                ArrayList<GoogleDriveBackup> backups = GoogleDriveBackup.getBackups();
                                                                ArrayList<LocalDateTime> backupDateTimes = new ArrayList<>();

                                                                for (GoogleDriveBackup backup : backups) {
                                                                    backupDateTimes.add(backup.getLocalDateTime());
                                                                }

                                                                Utils.sortLocalDateTimeDecrease(backupDateTimes);

                                                                ArrayList<String> backupSuggestions = new ArrayList<>();

                                                                for (GoogleDriveBackup backup : backups) {
                                                                    backupSuggestions.add("\"" + backup.getName() + "\"");
                                                                }
                                                                return backupSuggestions;
                                                            });
                                                        }))

                                                        .executes((sender, args) -> {

                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                new MenuCommand("googleDrive", sender, args).execute();
                                                            });
                                                        })

                                                        .then(new StringArgument("action")
                                                                .replaceSuggestions(ArgumentSuggestions.strings("delete", "copyToLocal"))

                                                                .executes((sender, args) -> {

                                                                    if (Objects.equals(args.get("action"), "deleteConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.GOOGLE_DRIVE_DELETE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new DeleteConfirmationCommand("googleDrive", sender, args).execute();
                                                                            });

                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "delete")) {
                                                                        if (sender.hasPermission(Permissions.GOOGLE_DRIVE_DELETE.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new DeleteCommand("googleDrive", sender, args).execute();
                                                                            });

                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToLocalConfirmation")) {
                                                                        if (sender.hasPermission(Permissions.GOOGLE_DRIVE_COPY_TO_LOCAL.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToLocalConfirmationCommand("googleDrive", sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }

                                                                    if (Objects.equals(args.get("action"), "copyToLocal")) {
                                                                        if (sender.hasPermission(Permissions.GOOGLE_DRIVE_COPY_TO_LOCAL.getPermission())) {
                                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                                new CopyToLocalCommand("googleDrive", sender, args).execute();
                                                                            });
                                                                        } else {
                                                                            UIUtils.returnFailure("I'm sorry, but you do not have permission to perform this command. Please contact the server administrators if you believe that this is in error.", sender);
                                                                        }
                                                                    }
                                                                })
                                                        )
                                        )
                        )
                )
        ;
        backupMenuCommandTree.register();

        CommandTree backupTaskCommandTree = new CommandTree("backuper").withPermission(Permissions.BACKUPER.getPermission());
        backupTaskCommandTree
                .then(new LiteralArgument("task")
                        .then(new StringArgument("action").replaceSuggestions(ArgumentSuggestions.strings("cancel"))
                                .executes((sender, args) -> {

                                    if (Objects.equals(args.get("action"), "cancelConfirmation")) {
                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                            new CancelConfirmationCommand(sender, args).execute();
                                        });
                                    }

                                    if (Objects.equals(args.get("action"), "cancel")) {
                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                            new CancelCommand(sender, args).execute();
                                        });
                                    }
                                })
                        )
                        .then(new LiteralArgument("status").withPermission(Permissions.STATUS.getPermission())

                                .executes((sender, args) -> {

                                    new StatusCommand(sender, args).execute();
                                })
                        )
                )
        ;
        backupTaskCommandTree.register();

        CommandTree backupAccountCommandTree = new CommandTree("backuper").withPermission(Permissions.BACKUPER.getPermission());
        backupAccountCommandTree
                .then(new LiteralArgument("account")
                        .then(new LiteralArgument("googleDrive")
                                .withRequirement((sender) -> Config.getInstance().getGoogleDriveConfig().isEnabled())
                                .withPermission(Permissions.GOOGLE_DRIVE_ACCOUNT.getPermission())

                                .then(new LiteralArgument("link")

                                        .executes((sender, args) -> {

                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new GoogleDriveLinkCommand(sender, args).execute();
                                            });
                                        })
                                )
                        )
                )
        ;
        backupAccountCommandTree.register();
    }

    public static void initEventHandlers() {

        Bukkit.getPluginManager().registerEvents(new Initialization(), Backuper.getInstance());
        Bukkit.getPluginManager().registerEvents(new WorldChangeCatcher(), Backuper.getInstance());

        boolean areWorldChangeEventsExists = true;
        for (String eventName : WorldChangeCatcherNew.eventNames) {
            try {
                Class.forName(eventName);
            } catch (Exception e) {
                areWorldChangeEventsExists = false;
            }
        }
        if (areWorldChangeEventsExists) {
            Bukkit.getPluginManager().registerEvents(new WorldChangeCatcherNew(), Backuper.getInstance());
        }
    }

    public static void checkDependencies() {

        try {
            Class.forName("io.papermc.paper.threadedregions.scheduler.EntityScheduler");
            Utils.isFolia = true;
            Backuper.getInstance().getLogManager().devLog("Folia/Paper(1.20+) has been detected!");
        } catch (Exception e) {
            Utils.isFolia = false;
            Backuper.getInstance().getLogManager().devLog("Folia/Paper(1.20+) has not been detected!");
        }
    }

    public static void checkPluginVersion() {

        if (!Config.getInstance().isCheckUpdates()) {
            return;
        }

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
                Backuper.getInstance().getLogManager().log("You are using the latest version of the BACKUPER!");
            } else {
                Utils.isUpdatedToLatest = false;
            }

        } catch (Exception e) {

            Backuper.getInstance().getLogManager().warn("Failed to check the Backuper updates!");
            Backuper.getInstance().getLogManager().warn(e);
        }
    }

    public static void sendIssueToGitHub() {

        String message = "\n" + "-".repeat(75) + "\n";
        message += "Please, if you find any issues related to the Backuper\nCreate an issue using the link: https://github.com/DVDishka/Backuper/issues\n";
        message += "-".repeat(75);

        Backuper.getInstance().getLogManager().log(message);
    }

    public static void sendGoogleAccountCheckResult(CommandSender sender) {

        if (sender.isOp() && Config.getInstance().getGoogleDriveConfig().isEnabled() && !GoogleDriveUtils.checkConnection()) {

            Component message = Component.empty();

            if (sender instanceof ConsoleCommandSender) {
                message = message.append(Component.newline());
            }

            message = message
                    .append(sender instanceof ConsoleCommandSender ? Component.text("-".repeat(75)) : Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)))
                    .append(Component.newline());

            message = message
                    .append(Component.text("Google Drive storage is enabled, but Google account is not linked!")
                            .decorate(TextDecoration.BOLD)
                            .color(NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("Use ")
                            .decorate(TextDecoration.BOLD)
                            .color(NamedTextColor.RED))
                    .append(Component.text("/backuper account googleDrive link")
                            .decorate(TextDecoration.UNDERLINED)
                            .clickEvent(ClickEvent.suggestCommand("/backuper account googleDrive link")));

            message = message
                    .append(Component.newline())
                    .append(sender instanceof ConsoleCommandSender ? Component.text("-".repeat(75)) : Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)));

            sender.sendMessage(message);
        }
    }

    public static void sendPluginVersionCheckResult(CommandSender sender) {

        if (sender.isOp() && !Utils.isUpdatedToLatest) {

            Component message = Component.empty();

            if (sender instanceof ConsoleCommandSender) {
                message = message.append(Component.newline());
            }

            message = message
                    .append(sender instanceof ConsoleCommandSender ? Component.text("-".repeat(75)) : Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)))
                    .append(Component.newline());

            message = message
                    .append(Component.text("You are using an outdated version of Backuper!\nPlease update it to the latest and check the changelist!")
                            .decorate(TextDecoration.BOLD)
                            .color(NamedTextColor.RED));

            int downloadLinkNumber = 0;
            for (String downloadLink : Utils.downloadLinks) {

                message = message.append(Component.newline());

                message = message
                        .append(Component.text("Download link: " + (sender instanceof ConsoleCommandSender ? downloadLink : Utils.downloadLinksName.get(downloadLinkNumber)))
                                .clickEvent(ClickEvent.openUrl(downloadLink))
                                .decorate(TextDecoration.UNDERLINED));

                downloadLinkNumber++;
            }

            message = message
                    .append(Component.newline())
                    .append(sender instanceof ConsoleCommandSender ? Component.text("-".repeat(75)) : Component.text("------------------------------------------")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xE3A013)));

            sender.sendMessage(message);
        }
    }

    // Send plugin version and google account warnings if player is an operator
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        sendPluginVersionCheckResult(event.getPlayer());
        sendGoogleAccountCheckResult(event.getPlayer());
    }

    @EventHandler
    public void onStartCompleted(ServerLoadEvent event) {
        Initialization.initAutoBackup();
    }

    public static void unifyBackupNameFormat(CommandSender sender) {
        Backuper.getInstance().getLogManager().log("Unifying backup names format");
        ConfigBackwardsCompatibility.unifyBackupNameFormat(sender);
        Backuper.getInstance().getLogManager().log("Backup names format unification completed");
    }

    public static void checkStorages(CommandSender sender) {
        if (Config.getInstance().getFtpConfig().isEnabled() && (FtpUtils.checkConnection())) {
            Backuper.getInstance().getLogManager().log("FTP(S) connection established successfully", sender);
        }
        if (Config.getInstance().getSftpConfig().isEnabled() && SftpUtils.checkConnection(sender)) {
            Backuper.getInstance().getLogManager().log("SFTP connection established successfully", sender);
        }
        if (Config.getInstance().getGoogleDriveConfig().isEnabled() && GoogleDriveUtils.checkConnection() && !Config.getInstance().getGoogleDriveConfig().getRawBackupFolderId().isEmpty()) {
            try {
                GoogleDriveUtils.getService().files().listLabels(Config.getInstance().getGoogleDriveConfig().getRawBackupFolderId()).execute();
            } catch (GoogleJsonResponseException e) {
                if (e.getStatusCode() == 404) {
                    Backuper.getInstance().getLogManager().warn("Wrong folder ID is defined googleDrive.backupsFolderId field in config.yml (File does not exist)", sender);
                } else {
                    Backuper.getInstance().getLogManager().warn("Failed to access Google Drive backup folder defined googleDrive.backupsFolderId field in config.yml", sender);
                    Backuper.getInstance().getLogManager().warn(e);
                }
            } catch (Exception e) {
                Backuper.getInstance().getLogManager().warn("Failed to access Google Drive backup folder defined googleDrive.backupsFolderId field in config.yml", sender);
                Backuper.getInstance().getLogManager().warn(e);
            }
        }
    }

    public static void loadSizeCache(CommandSender sender) {

        try {
            File sizeCacheFile = Config.getInstance().getSizeCacheFile();

            try {
                if (!sizeCacheFile.exists() && !sizeCacheFile.createNewFile()) {
                    Backuper.getInstance().getLogManager().warn("Unable to create %s file!".formatted(sizeCacheFile.getPath()));
                }
            } catch (Exception e) {
                Backuper.getInstance().getLogManager().warn("Unable to create %s file!".formatted(sizeCacheFile.getPath()));
            }

            FileReader reader = new FileReader(sizeCacheFile);
            StringBuilder json = new StringBuilder();
            char[] buffer = new char[1024];
            int length;
            while ((length = reader.read(buffer)) != -1) {
                json.append(new String(buffer, 0, length));
            }
            reader.close();

            Backup.loadSizeCache(json.toString());

        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to load backups size cache", sender);
            Backuper.getInstance().getLogManager().warn(e);
        }
    }
}
