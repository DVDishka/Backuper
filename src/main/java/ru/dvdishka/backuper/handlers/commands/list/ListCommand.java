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
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.classes.FtpBackup;
import ru.dvdishka.backuper.backend.classes.LocalBackup;
import ru.dvdishka.backuper.backend.classes.SftpBackup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.handlers.commands.Command;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class ListCommand extends Command {

    public static ArrayList<ArrayList<TextComponent>> pages;
    private String storage;

    private HashMap<String, Long> backupNameMbSize = new HashMap<>();
    private HashMap<String, String> backupNameFileType = new HashMap<>();

    public ListCommand(String storage, CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);

        this.storage = storage;
    }

    @Override
    public void execute() {

        if (storage.equals("local") && !Config.getInstance().getLocalConfig().isEnabled() ||
                storage.equals("sftp") && !Config.getInstance().getSftpConfig().isEnabled() ||
                storage.equals("ftp") && !Config.getInstance().getFtpConfig().isEnabled()) {
            cancelSound();
            returnFailure(storage + " storage is disabled!");
            return;
        }

        if (storage.equals("local")) {
            File backupsFolder = new File(Config.getInstance().getLocalConfig().getBackupsFolder());
            if (backupsFolder.listFiles() == null) {
                returnFailure("Wrong local backups folder in config.yml!");
                cancelSound();
                return;
            }
        }

        sendMessage("Creating a list of backups may take some time...");
        buttonSound();

        // PAGE UPDATING HAPPENS THERE
        int listPageCount = getListPageCount();

        if (listPageCount == 0) {
            returnFailure("There are no backups yet!");
            cancelSound();
            return;
        }

        int pageNumber = (Integer) arguments.getOrDefault("pageNumber", 1);

        if (pageNumber < 1 || pageNumber > listPageCount) {
            returnFailure("Invalid page number!");
            cancelSound();
            return;
        }

        Component header = Component.empty();

        header = header
                .append(Component.text("Backup list")
                        .decorate(TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text("(" + storage + ")")
                        .color(TextColor.fromHexString("#129c9b"))
                        .decorate(TextDecoration.BOLD));

        if (!(sender instanceof ConsoleCommandSender)) {
            sendFramedMessage(header, createListMessage(pageNumber, true), 15);
        }
        else {
            sendFramedMessage(header, createListMessage(pageNumber, arguments.get("pageNumber") != null), 41);
        }
        buttonSound();
    }

    private void updateListPages() {

        ArrayList<Backup> backups = null;

        if (storage.equals("local")) {
            backups = getSortedDecreaseLocalBackupList();
        }
        if (storage.equals("sftp")) {
            backups = getSortedDecreaseSftpBackupList();
        }
        if (storage.equals("ftp")) {
            backups = getSortedDecreaseFtpBackupList();
        }

        if (backups == null) {
            Logger.getLogger().warn("Something went wrong while trying to get backup list!", sender);
            return;
        }

        ArrayList<ArrayList<TextComponent>> pages = new ArrayList<>();

        for (int i = 1; i <= backups.size(); i++) {

            if (i % 10 == 1) {
                pages.add(new ArrayList<>());
            }

            Backup backup = backups.get(i - 1);
            String backupName = backup.getName();
            String backupFormattedName = backup.getFormattedName();

            String backupFileType = backup.getFileType();
            long backupMbSize = backup.getMbSize(sender);

            backupNameMbSize.put(backupFormattedName, backupMbSize);
            backupNameFileType.put(backupFormattedName, backupFileType);

            HoverEvent<net.kyori.adventure.text.Component> hoverEvent = HoverEvent
                    .showText(net.kyori.adventure.text.Component.text("(" + storage + ") " + backupFileType + " " + backupMbSize + " MB"));
            ClickEvent clickEvent = ClickEvent.runCommand("/backuper menu " + storage + " \"" + backupName + "\"");

            pages.get((i - 1) / 10)
                    .add(net.kyori.adventure.text.Component.text(backupFormattedName)
                    .hoverEvent(hoverEvent)
                    .clickEvent(clickEvent));
        }

        ListCommand.pages = pages;
    }

    private Component createListMessage(int pageNumber, boolean pagedListMessage) {

        Component message = Component.empty();

        if (!(sender instanceof ConsoleCommandSender)) {

            message = message
                    .append(Component.text("<<<<<<<<")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backuper list " + storage + " " + (pageNumber - 1))))
                    .append(Component.text(String.valueOf(pageNumber))
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(">>>>>>>>")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backuper list " + storage + " " + (pageNumber + 1))))
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
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backuper list " + storage + " " + (pageNumber - 1))))
                    .append(Component.text(String.valueOf(pageNumber))
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(">>>>>>>>")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backuper list " + storage + " " + (pageNumber + 1))));

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

                    if (backupIndex > 1) {
                        message = message
                                .append(Component.newline());
                    }

                    String backupName = backupComponent.content();

                    message = message
                            .append(Component.text(backupName))
                            .append(Component.space())
                            .append(Component.text(storage))
                            .append(Component.space())
                            .append(Component.text(backupNameFileType.get(backupName)))
                            .append(Component.space())
                            .append(Component.text(backupNameMbSize.get(backupName)))
                            .append(Component.space())
                            .append(Component.text(" MB"));

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
            }
            else {
                for (ArrayList<TextComponent> page : pages) {
                    for (TextComponent backupComponent : page) {

                        if (backupIndex > 1) {
                            message = message
                                    .append(Component.newline());
                        }

                        String backupName = backupComponent.content();

                        message = message
                                .append(Component.text(backupComponent.content()))
                                .append(Component.space())
                                .append(Component.text(storage))
                                .append(Component.space())
                                .append(Component.text(backupNameFileType.get(backupName)))
                                .append(Component.space())
                                .append(Component.text(backupNameMbSize.get(backupName)))
                                .append(Component.space())
                                .append(Component.text(" MB"));

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

    private ArrayList<Backup> getSortedDecreaseLocalBackupList() {

        ArrayList<Backup> backups = new ArrayList<>(LocalBackup.getBackups());

        for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

            for (int secondBackupsIndex = firstBackupsIndex; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                if (backups.get(firstBackupsIndex).getLocalDateTime().isBefore(backups.get(secondBackupsIndex).getLocalDateTime())) {

                    Backup save = backups.get(firstBackupsIndex);

                    backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                    backups.set(secondBackupsIndex, save);
                }
            }
        }

        return backups;
    }

    private ArrayList<Backup> getSortedDecreaseFtpBackupList() {

        ArrayList<Backup> backups = new ArrayList<>(FtpBackup.getBackups());

        for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

            for (int secondBackupsIndex = firstBackupsIndex; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                if (backups.get(firstBackupsIndex).getLocalDateTime().isBefore(backups.get(secondBackupsIndex).getLocalDateTime())) {

                    Backup save = backups.get(firstBackupsIndex);

                    backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                    backups.set(secondBackupsIndex, save);
                }
            }
        }

        return backups;
    }

    private ArrayList<Backup> getSortedDecreaseSftpBackupList() {

        ArrayList<Backup> backups = new ArrayList<>(SftpBackup.getBackups());

        for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

            for (int secondBackupsIndex = firstBackupsIndex; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                if (backups.get(firstBackupsIndex).getLocalDateTime().isBefore(backups.get(secondBackupsIndex).getLocalDateTime())) {

                    Backup save = backups.get(firstBackupsIndex);

                    backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                    backups.set(secondBackupsIndex, save);
                }
            }
        }

        return backups;
    }
}
