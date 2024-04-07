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
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.Logger;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;

public class ListCommand extends Command {

    public static ArrayList<ArrayList<TextComponent>> pages;

    public ListCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        File backupsFolder = new File(Config.getInstance().getBackupsFolder());

        if (backupsFolder.listFiles() == null) {
            returnFailure("Wrong backups folder in config.yml!");
            return;
        }

        if (ListCommand.getListPageCount() == 0) {
            returnFailure("There are no backups yet!");
            return;
        }

        int pageNumber = (Integer) arguments.getOrDefault("pageNumber", 1);

        // PAGE DOES NOT EXIST
        if (pageNumber < 1 || pageNumber > ListCommand.getListPageCount()) {
            cancelButtonSound();
            return;
        }

        normalButtonSound();

        updateListPages();

        Component header = Component.empty();

        header = header
                .append(Component.text("Backup List")
                        .decorate(TextDecoration.BOLD));

        if (!(sender instanceof ConsoleCommandSender)) {
            sendFramedMessage(header, createListMessage(pageNumber, true), 15);
        }
        else {
            sendFramedMessage(header, createListMessage(pageNumber, arguments.get("pageNumber") != null), 41);
        }
    }

    public static void updateListPages() {

        File backupsFolder = new File(Config.getInstance().getBackupsFolder());
        ArrayList<LocalDateTime> backups = new ArrayList<>();

        if (backupsFolder.listFiles() == null) {
            Logger.getLogger().warn("Wrong backupsFolder path!");
            return;
        }

        for (File file : Objects.requireNonNull(backupsFolder.listFiles())) {

            try {
                backups.add(LocalDateTime.parse(file.getName().replace(".zip", ""),
                        Backup.dateTimeFormatter));
            } catch (Exception ignored) {}
        }

        Backup.sortLocalDateTimeDecrease(backups);
        ArrayList<ArrayList<TextComponent>> pages = new ArrayList<>();

        for (int i = 1; i <= backups.size(); i++) {

            if (i % 10 == 1) {
                pages.add(new ArrayList<>());
            }

            String backupName = backups.get(i - 1).format(Backup.dateTimeFormatter);
            Backup backup = new Backup(backupName);
            String backupFileType = backup.zipOrFolder();
            long backupSize = backup.getMBSize();

            HoverEvent<net.kyori.adventure.text.Component> hoverEvent = HoverEvent
                    .showText(net.kyori.adventure.text.Component.text(backupFileType + " " + backupSize + " MB"));
            ClickEvent clickEvent = ClickEvent.runCommand("/backup menu \"" + backupName + "\"");

            pages.get((i - 1) / 10)
                    .add(net.kyori.adventure.text.Component.text(backupName)
                    .hoverEvent(hoverEvent)
                    .clickEvent(clickEvent));
        }

        ListCommand.pages = pages;
    }

    public Component createListMessage(int pageNumber, boolean pagedListMessage) {

        Component message = Component.empty();

        if (!(sender instanceof ConsoleCommandSender)) {

            message = message
                    .append(Component.text("<<<<<<<<")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backup list " + (pageNumber - 1))))
                    .append(Component.text(String.valueOf(pageNumber))
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(">>>>>>>>")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backup list " + (pageNumber + 1))))
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
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backup list " + (pageNumber - 1))))
                    .append(Component.text(String.valueOf(pageNumber))
                            .decorate(TextDecoration.BOLD))
                    .append(Component.text(">>>>>>>>")
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.fromHexString("#129c9b"))
                            .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backup list " + (pageNumber + 1))));

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

                    message = message
                            .append(Component.text(backupComponent.content()))
                            .append(Component.space())
                            .append(Component.text(new Backup(backupComponent.content()).zipOrFolder()))
                            .append(Component.space())
                            .append(Component.text(new Backup(backupComponent.content()).getMBSize()))
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

                        message = message
                                .append(Component.text(backupComponent.content()))
                                .append(Component.space())
                                .append(Component.text(new Backup(backupComponent.content()).zipOrFolder()))
                                .append(Component.space())
                                .append(Component.text(new Backup(backupComponent.content()).getMBSize()))
                                .append(Component.space())
                                .append(Component.text(" MB"));

                        backupIndex++;
                    }
                }
            }
        }
        return message;
    }

    public static int getListPageCount() {

        updateListPages();
        return pages.size();
    }
}
