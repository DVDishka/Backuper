package ru.dvdishka.backuper.handlers.commands;

import dev.jorel.commandapi.CommandTree;
import dev.jorel.commandapi.arguments.*;
import org.bukkit.command.CommandSender;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

public class CommandManager {

    public void init() {
        CommandTree backupCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupCommandTree
                .then(new LiteralArgument("backup")
                        .then(new StringArgument("storage").includeSuggestions(getMultiStorageSuggestion())

                                .executes((sender, args) -> {
                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                        new BackupCommand(sender, args, "NOTHING").execute();
                                    });
                                })
                                .then(new LongArgument("delaySeconds")
                                        .executes((sender, args) -> {
                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new BackupCommand(sender, args, "NOTHING").execute();
                                            });
                                        })
                                )
                                .then(new LiteralArgument("stop").withPermission(Permission.STOP.getPermission())
                                        .executes((sender, args) -> {
                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new BackupCommand(sender, args, "STOP").execute();
                                            });
                                        })
                                        .then(new LongArgument("delaySeconds")
                                                .executes((sender, args) -> {
                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new BackupCommand(sender, args, "STOP").execute();
                                                    });
                                                })
                                        )
                                )
                                .then(new LiteralArgument("restart").withPermission(Permission.RESTART.getPermission())
                                        .executes((sender, args) -> {
                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new BackupCommand(sender, args, "RESTART").execute();
                                            });
                                        })
                                        .then(new LongArgument("delaySeconds")
                                                .executes((sender, args) -> {
                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new BackupCommand(sender, args, "RESTART").execute();
                                                    });
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
                                .includeSuggestions(getSingleStorageSuggestion())

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
                .then(new LiteralArgument("reload").withPermission(Permission.CONFIG_RELOAD.getPermission())
                        .executes((sender, args) -> {
                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                new ReloadCommand(sender, args).execute();
                            });
                        })
                )
        ;
        backupReloadCommandTree.register();

        CommandTree backupMenuCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupMenuCommandTree
                .then(new LiteralArgument("menu")
                        .then(new StringArgument("storage").includeSuggestions(getSingleStorageSuggestion())

                                .then(new TextArgument("backupName").includeSuggestions(ArgumentSuggestions.stringCollectionAsync((info) ->
                                                    CompletableFuture.supplyAsync(() -> {
                                                            Storage storage = Backuper.getInstance().getStorageManager().getStorage((String) info.previousArgs().get("storage"));
                                                        if (storage == null || !info.sender().hasPermission(Permission.STORAGE.getPermission(storage))) return new ArrayList<>();

                                                        return storage.getBackupManager().getBackupList().stream()
                                                                .sorted(Backup::compareTo)
                                                                .map(backup -> "\"%s\"".formatted(backup.getName()))
                                                                .toList();
                                                })))
                                        .executes((sender, args) -> {
                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                new MenuCommand(sender, args).execute();
                                            });
                                        })
                                        .then(new LiteralArgument("copyto")
                                                .then(new StringArgument("targetStorage").includeSuggestions(ArgumentSuggestions.stringCollectionAsync((suggestionInfo) ->
                                                                CompletableFuture.supplyAsync(() -> Backuper.getInstance().getStorageManager().getStorages().stream()
                                                                        .filter(storage -> suggestionInfo.sender().hasPermission(Permission.STORAGE.getPermission(storage)))
                                                                        .map(Storage::getId)
                                                                        .filter(id -> !id.equals(suggestionInfo.previousArgs().get("storage"))).toList())))
                                                        .executes((sender, args) -> {
                                                            Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                                new CopyToCommand(sender, args).execute();
                                                            });
                                                        })
                                                )
                                        )
                                        .then(new LiteralArgument("delete")
                                                .executes((sender, args) -> {
                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new DeleteCommand(sender, args).execute();
                                                    });
                                                })
                                        )
                                        .then(new LiteralArgument("unzip")
                                                .executes((sender, args) -> {
                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new UnZIPCommand(sender, args).execute();
                                                    });
                                                })
                                        )
                                        .then(new LiteralArgument("tozip")
                                                .executes((sender, args) -> {
                                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                                        new ToZIPCommand(sender, args).execute();
                                                    });
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
                        .then(new LiteralArgument("cancel")
                                .executes((sender, args) -> {
                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                        new CancelCommand(sender, args).execute();
                                    });
                                })
                        )
                        .then(new LiteralArgument("status").withPermission(Permission.STATUS.getPermission())
                                .executes((sender, args) -> {
                                    Backuper.getInstance().getScheduleManager().runAsync(() -> {
                                        new StatusCommand(sender, args).execute();
                                    });
                                })
                        )
                )
        ;
        backupTaskCommandTree.register();

        CommandTree backupAccountCommandTree = new CommandTree("backuper").withPermission(Permission.BACKUPER.getPermission());
        backupAccountCommandTree
                .then(new LiteralArgument("account")
                        .then(new StringArgument("storage")
                                .includeSuggestions(ArgumentSuggestions.stringCollectionAsync((info) -> CompletableFuture.supplyAsync(() ->
                                        Backuper.getInstance().getStorageManager().getStorages().stream()
                                                .filter(storage -> info.sender().hasPermission(Permission.ACCOUNT.getPermission(storage)))
                                                .filter(storage -> storage instanceof UserAuthStorage)
                                                .map(Storage::getId)
                                                .toList())))
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

    private ArgumentSuggestions<CommandSender> getSingleStorageSuggestion() {
        return ArgumentSuggestions.stringCollectionAsync((suggestionInfo) ->
                CompletableFuture.supplyAsync(() -> Backuper.getInstance().getStorageManager().getStorages().stream()
                        .filter(storage -> suggestionInfo.sender().hasPermission(Permission.STORAGE.getPermission(storage)))
                        .map(Storage::getId).toList()));
    }

    private ArgumentSuggestions<CommandSender> getMultiStorageSuggestion() {
        return ArgumentSuggestions.stringCollectionAsync(suggestionInfo -> CompletableFuture.supplyAsync(() -> Backuper.getInstance().getStorageManager().getStorages().stream()
                .filter(storage ->suggestionInfo.sender().hasPermission(Permission.BACKUP.getPermission(storage))) // Filter for player permissions
                .filter(storage -> { // Filter for storageId starts with current argument
                    String lastStorageString = suggestionInfo.currentArg().substring(!suggestionInfo.currentArg().contains("-") ? 0 : suggestionInfo.currentArg().lastIndexOf("-") + 1);
                    if (Backuper.getInstance().getStorageManager().getStorage(lastStorageString) != null) return true; // If last argument is finished we should skip this filter
                    return storage.getId().startsWith(lastStorageString);
                })
                .filter(storage -> Arrays.stream(suggestionInfo.currentArg().split("-")).noneMatch(currentArgumentStorage -> currentArgumentStorage.equals(storage.getId()))) // Filter if storage is already in arguments
                .map(Storage::getId) // Map storages to ids
                .map(id -> { // Finish suggestion strings
                    String currentArg = suggestionInfo.currentArg();
                    String lastStorageString = suggestionInfo.currentArg().substring(!suggestionInfo.currentArg().contains("-") ? 0 : suggestionInfo.currentArg().lastIndexOf("-") + 1);
                    if (Backuper.getInstance().getStorageManager().getStorage(lastStorageString) != null) currentArg += "-"; // Add "-" if last argument is completed

                    int lastIndex = currentArg.lastIndexOf("-") == -1 ? 0 : currentArg.lastIndexOf("-") + 1;
                    return "%s%s".formatted(currentArg.substring(0, lastIndex), id);
                })
                .toList())
        );
    }
}
