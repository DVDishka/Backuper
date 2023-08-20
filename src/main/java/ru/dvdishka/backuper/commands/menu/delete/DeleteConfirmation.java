package ru.dvdishka.backuper.commands.menu.delete;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.CommandInterface;
import ru.dvdishka.backuper.common.Common;

public class DeleteConfirmation implements CommandInterface {

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        String backupName = (String) args.get("backupName");

        if (!Common.checkBackupExistanceByName(backupName)) {
            cancelButtonSound(sender);
            returnFailure("Backup does not exist!", sender);
            return;
        }

        normalButtonSound(sender);

        long backupSize = Common.getBackupMBSizeByName(backupName);
        String zipFolderBackup = Common.zipOrFolderBackupByName(backupName);

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text("---------------")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xE3A013)))
                .appendNewline();

        message = message
                .append(Component.text("Are you sure")
                        .appendNewline()
                        .append(Component.text("You want to delete the backup?"))
                        .color(TextColor.color(0xB02100)))
                .appendNewline();

        message = message
                .append(Component.text(backupName)
                        .hoverEvent(HoverEvent.showText(Component.text(zipFolderBackup + " " + backupSize + " MB"))))
                .appendNewline();

        message = message
                .append(Component.text("[DELETE BACKUP]")
                        .clickEvent(ClickEvent.runCommand("/backup menu \"" + backupName + "\" delete"))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD))
                .appendNewline();

        message = message
                .append(Component.text("---------------")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xE3A013)));

        sender.sendMessage(message);
    }
}
