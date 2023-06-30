package ru.dvdishka.backuper.handlers.commands;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.common.CommandInterface;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.ConfigVariables;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;

public class List implements CommandInterface {

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        File backupsFolder = new File(ConfigVariables.backupsFolder);

        if (backupsFolder.listFiles() == null) {
            returnFailure("Wrong backups folder in config.yml!", sender);
            return;
        }

        ArrayList<LocalDateTime> backups = new ArrayList<>();

        for (File file : backupsFolder.listFiles()) {

            try {
                backups.add(LocalDateTime.parse(file.getName().replace(".zip", ""),
                        Common.dateTimeFormatter));
            } catch (Exception ignored) {}
        }

        Common.sortLocalDateTime(backups);
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

            HoverEvent<net.kyori.adventure.text.Component> hoverEvent = HoverEvent.showText(net.kyori.adventure.text.Component.text(backupSize + " MB"));

            pages.get(i / 10).add(net.kyori.adventure.text.Component.text(backups.get(i - 1).format(Common.dateTimeFormatter)).hoverEvent(hoverEvent));
        }

        if (pages.size() == 0) {
            returnFailure("There are no backups yet!", sender);
            return;
        }

        Component message = Component.empty();

        message = message.append(Component.text("---------------")
                .decorate(TextDecoration.BOLD)
                .appendNewline());

        for (TextComponent backupComponent : pages.get(0)) {
            message = message.append(backupComponent)
                    .appendNewline();
        }

        message = message.append(Component.text("<------1------>")
                .decorate(TextDecoration.BOLD));

        sender.sendMessage(message);
    }
}
