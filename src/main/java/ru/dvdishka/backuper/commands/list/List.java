package ru.dvdishka.backuper.commands.list;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.common.classes.CommandInterface;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.ConfigVariables;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;

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
            sender.playSound(Sound.sound(Sound.sound(Key.key("block.anvil.place"), Sound.Source.NEUTRAL, 50, 1)).build());
            return;
        }

        sender.playSound(Sound.sound(Sound.sound(Key.key("ui.button.click"), Sound.Source.NEUTRAL, 50, 1)).build());

        updateListPages();

        sender.sendMessage(createListMessage(pageNumber));
    }

    public static void updateListPages() {

        File backupsFolder = new File(ConfigVariables.backupsFolder);
        ArrayList<LocalDateTime> backups = new ArrayList<>();

        for (File file : backupsFolder.listFiles()) {

            try {
                backups.add(LocalDateTime.parse(file.getName().replace(".zip", ""),
                        Common.dateTimeFormatter));
            } catch (Exception ignored) {}
        }

        Common.sortLocalDateTimeDecrease(backups);
        ArrayList<ArrayList<TextComponent>> pages = new ArrayList<>();

        for (int i = 1; i <= backups.size(); i++) {

            if (i % 10 == 1) {
                pages.add(new ArrayList<>());
            }

            String backupFilePath;

            if (backupsFolder.toPath().resolve(backups.get(i - 1).format(Common.dateTimeFormatter)).toFile().exists()) {
                backupFilePath = backupsFolder.toPath().resolve(backups.get(i - 1).format(Common.dateTimeFormatter)).toFile().getPath();
            } else {
                backupFilePath = backupsFolder.toPath().resolve(backups.get(i - 1).format(Common.dateTimeFormatter)).toFile().getPath() + ".zip";
            }

            long backupSize = Common.getPathFileByteSize(new File(backupFilePath));

            if (backupSize != 0) {
                backupSize /= (1024 * 1024);
            }

            String backupText = backups.get(i - 1).format(Common.dateTimeFormatter);
            String backupFileType = "";

            // CHECKS IF A ZIP FILE EXISTS
            if (new File(ConfigVariables.backupsFolder).toPath().resolve(new File(backups.get(i - 1).format(Common.dateTimeFormatter) + ".zip").toPath()).toFile().exists()) {
                backupFileType = "(ZIP)";
            } else {
                backupFileType = "(Folder)";
            }

            HoverEvent<net.kyori.adventure.text.Component> hoverEvent = HoverEvent.showText(net.kyori.adventure.text.Component.text(backupFileType + " " + backupSize + " MB"));

            pages.get((i - 1) / 10).add(net.kyori.adventure.text.Component.text(backupText).hoverEvent(hoverEvent));
        }

        List.pages = pages;
    }

    public static Component createListMessage(int pageNumber) {

        Component message = Component.empty();

        message = message
                .append(Component.text("---------------")
                .decorate(TextDecoration.BOLD)
                .appendNewline());

        for (TextComponent backupComponent : pages.get(0)) {
            message = message
                    .append(backupComponent)
                    .appendNewline();
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
                .appendNewline();

        message = message
                .append(Component.text("---------------")
                .decorate(TextDecoration.BOLD));

        return message;
    }

    public static int getListPageCount() {

        updateListPages();
        return pages.size();
    }
}
