package ru.dvdishka.backuper.commands.list;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.CommandInterface;
import ru.dvdishka.backuper.common.Backup;
import ru.dvdishka.backuper.common.ConfigVariables;
import ru.dvdishka.backuper.common.Logger;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Objects;

public class List implements CommandInterface {

    public static ArrayList<ArrayList<TextComponent>> pages;

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        File backupsFolder = new File(ConfigVariables.backupsFolder);

        if (backupsFolder.listFiles() == null) {
            returnFailure("Wrong backups folder in config.yml!", sender);
            return;
        }

        if (List.getListPageCount() == 0) {
            returnFailure("There are no backups yet!", sender);
            return;
        }

        int pageNumber = (Integer) args.getOptional("pageNumber").orElse(1);

        // PAGE DOES NOT EXIST
        if (pageNumber < 1 || pageNumber > List.getListPageCount()) {
            cancelButtonSound(sender);
            return;
        }

        normalButtonSound(sender);

        updateListPages();

        sender.sendMessage(createListMessage(pageNumber));
    }

    public static void updateListPages() {

        File backupsFolder = new File(ConfigVariables.backupsFolder);
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

        List.pages = pages;
    }

    public static Component createListMessage(int pageNumber) {

        Component message = Component.empty();

        message = message
                .append(Component.text("---------------")
                        .color(TextColor.color(0xE3A013))
                        .decorate(TextDecoration.BOLD)
                        .append(Component.newline()));

        for (TextComponent backupComponent : pages.get(pageNumber - 1)) {
            message = message
                    .append(backupComponent)
                    .append(Component.newline());
        }

        message = message
                .append(Component.text("<<<<<<<<")
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backup list " + (pageNumber - 1))))
                .append(Component.text(String.valueOf(pageNumber))
                        .decorate(TextDecoration.BOLD))
                .append(Component.text(">>>>>>>>")
                        .decorate(TextDecoration.BOLD)
                        .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.RUN_COMMAND, "/backup list " + (pageNumber + 1))))
                .append(Component.newline());

        message = message
                .append(Component.text("---------------")
                        .color(TextColor.color(0xE3A013))
                        .decorate(TextDecoration.BOLD));

        return message;
    }

    public static int getListPageCount() {

        updateListPages();
        return pages.size();
    }
}
