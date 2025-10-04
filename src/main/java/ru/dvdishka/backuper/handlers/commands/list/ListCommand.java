package ru.dvdishka.backuper.handlers.commands.list;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.storage.Storage;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permission;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ListCommand extends Command {

    private List<List<TextComponent>> pages;
    private Storage storage;
    private final boolean sendResult;

    private final HashMap<String, Long> backupNameMbSize = new HashMap<>();
    private final HashMap<String, Backup.BackupFileType> backupNameFileType = new HashMap<>();

    public ListCommand(boolean sendResult, CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
        this.sendResult = sendResult;
    }

    @Override
    public boolean check() {
        storage = Backuper.getInstance().getStorageManager().getStorage((String) arguments.get("storage"));
        if (storage == null) {
            returnFailure("Wrong storage name %s".formatted((String) arguments.get("storage")));
            return false;
        }
        if (!storage.checkConnection()) {
            returnFailure("Failed to establish connection to %s storage".formatted(storage.getId()));
            return false;
        }
        if (!sender.hasPermission(Permission.LIST.getPermission(storage))) {
            returnFailure("Don't have enough permissions to perform this command");
            return false;
        }

        if (sendResult) sendMessage("Creating a list of backups may take some time...");
        int listPageCount = getListPageCount(); // Page update happens there
        int pageNumber = (Integer) arguments.getOrDefault("pageNumber", 1);
        if (pageNumber < 1 || pageNumber > listPageCount) {
            returnFailure("Invalid page number!");
            return false;
        }

        return true;
    }

    @Override
    public void run() {
        Component header = Component.empty();

        header = header
                .append(Component.text("Backup list")
                        .decorate(TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text("(%s)".formatted(storage.getId()))
                        .color(TextColor.fromHexString("#129c9b"))
                        .decorate(TextDecoration.BOLD));

        int pageNumber = (Integer) arguments.getOrDefault("pageNumber", 1);
        if (sendResult) {
            if (!(sender instanceof ConsoleCommandSender)) {
                sendFramedMessage(header, createListMessage(pageNumber, true), 15);
            } else {
                sendFramedMessage(header, createListMessage(pageNumber, arguments.get("pageNumber") != null), 41);
            }
        }
        buttonSound();
    }

    private void updateListPages() {
        List<Backup> backups = new ArrayList<>(storage.getBackupManager().getBackupList());
        backups.sort(Backup::compareTo);
        backups = backups.reversed();

        List<List<TextComponent>> pages = new ArrayList<>();
        for (int i = 1; i <= backups.size(); i++) {
            if (i % 10 == 1) {
                pages.add(new ArrayList<>());
            }
            Backup backup = backups.get(i - 1);
            String backupName = backup.getName();
            String backupFormattedName = backup.getFormattedName();

            long backupMbSize = backup.getMbSize();

            backupNameMbSize.put(backupFormattedName, backupMbSize);
            backupNameFileType.put(backupFormattedName, backup.getFileType());

            HoverEvent<net.kyori.adventure.text.Component> hoverEvent = HoverEvent
                    .showText(net.kyori.adventure.text.Component.text("(%s) %s %s MB".formatted(backup.getStorage().getId(), backup.getFileType().name(), backupMbSize)));
            ClickEvent clickEvent = ClickEvent.runCommand("/backuper menu %s \"%s\"".formatted(storage.getId(), backupName));

            pages.get((i - 1) / 10)
                    .add(net.kyori.adventure.text.Component.text(backupFormattedName)
                            .hoverEvent(hoverEvent)
                            .clickEvent(clickEvent));
        }
        this.pages = pages;
    }

    private Component createListMessage(int pageNumber, boolean pagedListMessage) {
        Component message = Component.empty();
        // For players
        if (!(sender instanceof ConsoleCommandSender)) {
            message = message
                    .append(Component.text("<<<<<<<<")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backuper list %s %s".formatted(storage, pageNumber - 1))))
                    .append(Component.text(String.valueOf(pageNumber))
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(">>>>>>>>")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backuper list %s %s".formatted(storage, pageNumber + 1))))
                    .append(Component.newline());

            for (TextComponent backupComponent : pages.get(pageNumber - 1)) {
                message = message
                        .append(Component.space())
                        .append(backupComponent)
                        .append(Component.newline());
            }

            message = message
                    .append(Component.text("<<<<<<<<")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backuper list %s %s".formatted(storage, pageNumber - 1))))
                    .append(Component.text(String.valueOf(pageNumber))
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(">>>>>>>>")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backuper list %s %s".formatted(storage, pageNumber + 1))));

        // For console
        } else {
            int backupIndex = 1;
            if (pagedListMessage) {
                message = message
                        .append(Component.text("<".repeat(20))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.fromHexString("#129c9b")))
                        .append(Component.text(pageNumber))
                        .append(Component.text(">".repeat(20))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.fromHexString("#129c9b")))
                        .append(Component.newline());

                for (TextComponent backupComponent : pages.get(pageNumber - 1)) {
                    if (backupIndex > 1) message = message.append(Component.newline());
                    String backupName = backupComponent.content();
                    message = message
                            .append(Component.text(backupName))
                            .append(Component.space())
                            .append(Component.text("(%s)".formatted(storage.getId())))
                            .append(Component.space())
                            .append(Component.text(backupNameFileType.get(backupName).name()))
                            .append(Component.space())
                            .append(Component.text(backupNameMbSize.get(backupName)))
                            .append(Component.space())
                            .append(Component.text("MB"));
                    backupIndex++;
                }

                message = message
                        .append(Component.newline())
                        .append(Component.text("<".repeat(20))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.fromHexString("#129c9b")))
                        .append(Component.text(pageNumber))
                        .append(Component.text(">".repeat(20))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.fromHexString("#129c9b")));
            } else {
                for (List<TextComponent> page : pages) {
                    for (TextComponent backupComponent : page) {

                        if (backupIndex > 1) {
                            message = message
                                    .append(Component.newline());
                        }

                        String backupName = backupComponent.content();

                        message = message
                                .append(Component.text(backupComponent.content()))
                                .append(Component.space())
                                .append(Component.text("(%s)".formatted(storage.getId())))
                                .append(Component.space())
                                .append(Component.text(backupNameFileType.get(backupName).name()))
                                .append(Component.space())
                                .append(Component.text(backupNameMbSize.get(backupName)))
                                .append(Component.space())
                                .append(Component.text("MB"));

                        backupIndex++;
                    }
                }
            }
        }
        return message;
    }

    private int getListPageCount() {
        updateListPages();
        return pages.size();
    }

    @Override
    protected void returnFailure(String message) {
        if (sendResult) super.returnFailure(message);
    }

    @Override
    protected void returnFailure(String message, TextColor color) {
        if (sendResult) super.returnFailure(message, color);
    }

    @Override
    protected void returnSuccess(String message) {
        if (sendResult) super.returnSuccess(message);
    }

    @Override
    protected void returnSuccess(String message, TextColor color) {
        if (sendResult) super.returnSuccess(message, color);
    }

    @Override
    protected void returnWarning(String message) {
        if (sendResult) super.returnWarning(message);
    }

    @Override
    protected void returnWarning(String message, TextColor color) {
        if (sendResult) super.returnWarning(message, color);
    }
}
