package ru.dvdishka.backuper.handlers.commands;

import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.backend.storage.UserAuthStorage;
import ru.dvdishka.backuper.handlers.commands.backup.BackupCommand;
import ru.dvdishka.backuper.handlers.commands.googleDrive.AccountLinkCommand;
import ru.dvdishka.backuper.handlers.commands.list.ListCommand;
import ru.dvdishka.backuper.handlers.commands.menu.*;
import ru.dvdishka.backuper.handlers.commands.reload.ReloadCommand;
import ru.dvdishka.backuper.handlers.commands.task.CancelCommand;
import ru.dvdishka.backuper.handlers.commands.task.StatusCommand;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class CommandManager {

    public void registerCommands() {
        CommandTree backupCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupCommandTree
                .then(new LiteralArgument("backup")
                        .then(new StringArgument("storage").includeSuggestions(ArgumentSuggestions.stringCollection((suggestionInfo) ->
                                        Backuper.getInstance().getStorageManager().getStorages().stream()
                                                .filter(storage -> suggestionInfo.sender().hasPermission(Permission.BACKUP.getPermission(storage)))
                                                .map(storage -> "%s-".formatted(storage.getId()))
                                                .filter(id -> !suggestionInfo.currentArg().contains(id)).toList()))

                                .executes((sender, args) -> {
                                    new BackupCommand(sender, args, "NOTHING").execute();
                                })
                                .then(new LongArgument("delaySeconds")
                                        .executes((sender, args) -> {
                                            new BackupCommand(sender, args, "NOTHING").execute();
                                        })
                                )
                                .then(new LiteralArgument("stop").withPermission(Permission.STOP.getPermission())
                                        .executes((sender, args) -> {
                                            new BackupCommand(sender, args, "STOP").execute();
                                        })
                                        .then(new LongArgument("delaySeconds")
                                                .executes((sender, args) -> {
                                                    new BackupCommand(sender, args, "STOP").execute();
                                                })
                                        )
                                )
                                .then(new LiteralArgument("restart").withPermission(Permission.RESTART.getPermission())
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

        CommandTree backupListCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupListCommandTree
                .then(new LiteralArgument("list")
                        .then(new StringArgument("storage")
                                .includeSuggestions(ArgumentSuggestions.stringCollection((suggestionInfo) ->
                                        Backuper.getInstance().getStorageManager().getStorages().stream()
                                                .filter(storage -> suggestionInfo.sender().hasPermission(Permission.LIST.getPermission(storage)))
                                                .filter(storage -> storage.getConfig().isEnabled() && storage.checkConnection())
                                                .map(Storage::getId).toList()))

                                .executes((sender, args) -> {
                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                        new ListCommand(true, sender, args).execute();
                                    });
                                })
                                .then(new IntegerArgument("pageNumber")
                                        .executes((sender, args) -> {
                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new ListCommand(true, sender, args).execute();
                                            });
                                        })
                                )
                        )
                );
        ;
        backupListCommandTree.register();

        CommandTree backupReloadCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupReloadCommandTree
                .then(new LiteralArgument("config").withPermission(Permission.CONFIG.getPermission())
                        .then(new LiteralArgument("reload").withPermission(Permission.CONFIG_RELOAD.getPermission())
                                .executes((sender, args) -> {
                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                        new ReloadCommand(sender, args).execute();
                                    });
                                })
                        )
                )
        ;
        backupReloadCommandTree.register();

        CommandTree backupMenuCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupMenuCommandTree
                .then(new LiteralArgument("menu")
                        .then(new StringArgument("storage").includeSuggestions(ArgumentSuggestions.stringCollection((suggestionInfo) ->
                                        Backuper.getInstance().getStorageManager().getStorages().stream()
                                                .filter(storage -> suggestionInfo.sender().hasPermission(Permission.STORAGE.getPermission(storage)))
                                                .filter(storage -> storage.getConfig().isEnabled() && storage.checkConnection())
                                                .map(Storage::getId).toList()))

                                .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollectionAsync((info) ->
                                                CompletableFuture.supplyAsync(() -> {
                                                    Storage storage = Backuper.getInstance().getStorageManager().getStorage((String) info.previousArgs().get("storage"));
                                                    if (storage == null || !info.sender().hasPermission(Permission.LIST.getPermission(storage))) return new ArrayList<>();

                                                    List<Backup> backups = storage.getBackupManager().getBackupList();
                                                    List<LocalDateTime> backupDateTimes = backups.stream().map(Backup::getLocalDateTime).sorted().toList().reversed();
                                                    ArrayList<String> backupSuggestions = new ArrayList<>();
                                                    for (Backup backup : backups) {
                                                        backupSuggestions.add("\"%s\"".formatted(backup.getName()));
                                                    }
                                                    return backupSuggestions;
                                                })))
                                        .executes((sender, args) -> {
                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new MenuCommand(sender, args).execute();
                                            });
                                        })
                                        .then(new LiteralArgument("copyTo")
                                                .then(new StringArgument("targetStorage").includeSuggestions(ArgumentSuggestions.stringCollection((suggestionInfo) ->
                                                                Backuper.getInstance().getStorageManager().getStorages().stream()
                                                                        .filter(storage -> suggestionInfo.sender().hasPermission(Permission.STORAGE.getPermission(storage)))
                                                                        .filter(storage -> storage.getConfig().isEnabled() && !storage.getId().equals((String) suggestionInfo.previousArgs().get("storage")) && storage.checkConnection())
                                                                        .map(Storage::getId).toList()))
                                                        .executes((sender, args) -> {
                                                            new CopyToCommand(sender, args).execute();
                                                        })
                                                )
                                        )
                                        .then(new StringArgument("action")
                                                .replaceSuggestions(ArgumentSuggestions.stringCollection((info) -> {
                                                    List<String> suggestions = new ArrayList<>();
                                                    Storage storage = Backuper.getInstance().getStorageManager().getStorage((String) info.previousArgs().get("storage"));
                                                    if (storage == null || !info.sender().hasPermission(Permission.STORAGE.getPermission(storage))) return suggestions;

                                                    String backupName = (String) info.previousArgs().get("backupName");
                                                    Backup backup = storage.getBackupManager().getBackup(backupName);
                                                    if (Backup.BackupFileType.DIR.equals(backup.getFileType())) {
                                                        suggestions.add("toZIP");
                                                    }
                                                    if (Backup.BackupFileType.ZIP.equals(backup.getFileType())) {
                                                        suggestions.add("unZIP");
                                                    }
                                                    suggestions.add("delete");
                                                    return suggestions;
                                                }))
                                                .executes((sender, args) -> {
                                                    Storage storage = Backuper.getInstance().getStorageManager().getStorage((String) args.get("storage"));
                                                    if (storage == null) return;

                                                    if ("deleteConfirmation".equals(args.get("action"))) {
                                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                            new DeleteCommand(sender, args).executeConfirm();
                                                        });
                                                    }
                                                    if ("delete".equals(args.get("action"))) {
                                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                            new DeleteCommand(sender, args).execute();
                                                        });
                                                    }

                                                    if ("toZIPConfirmation".equals(args.get("action"))) {
                                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                            new ToZIPCommand(sender, args).executeConfirm();
                                                        });
                                                    }
                                                    if (Objects.equals(args.get("action"), "toZIP")) {
                                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                            new ToZIPCommand(sender, args).execute();
                                                        });
                                                    }

                                                    if (("unZIPConfirmation").equals(args.get("action"))) {
                                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                            new UnZIPCommand(sender, args).executeConfirm();
                                                        });
                                                    }
                                                    if ("unZIP".equals(args.get("action"))) {
                                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                            new UnZIPCommand(sender, args).execute();
                                                        });
                                                    }
                                                })
                                        )
                                )
                        )
                )
        ;
        backupMenuCommandTree.register();

        CommandTree backupTaskCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupTaskCommandTree
                .then(new LiteralArgument("task")
                        .then(new StringArgument("action").replaceSuggestions(ArgumentSuggestions.strings("cancel"))
                                .executes((sender, args) -> {

                                    if ("cancelConfirmation".equals(args.get("action"))) {
                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                            new CancelCommand(sender, args).executeConfirm();
                                        });
                                    }
                                    if ("cancel".equals(args.get("action"))) {
                                        Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                            new CancelCommand(sender, args).execute();
                                        });
                                    }
                                })
                        )
                        .then(new LiteralArgument("status").withPermission(Permission.STATUS.getPermission())
                                .executes((sender, args) -> {
                                    new StatusCommand(sender, args).execute();
                                })
                        )
                )
        ;
        backupTaskCommandTree.register();

        CommandTree backupAccountCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupAccountCommandTree
                .then(new LiteralArgument("account")
                        .then(new StringArgument("storage")
                                .includeSuggestions(ArgumentSuggestions.stringCollection((info) ->
                                        Backuper.getInstance().getStorageManager().getStorages().stream().filter(storage -> storage instanceof UserAuthStorage).map(Storage::getId).toList()))
                                .then(new LiteralArgument("link")
                                        .executes((sender, args) -> {
                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new AccountLinkCommand(sender, args).execute();
                                            });
                                        })
                                )
                        )
                )
        ;
        backupAccountCommandTree.register();
    }
}
