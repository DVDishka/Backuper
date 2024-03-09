package ru.dvdishka.backuper.handlers.commands.menu.toZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.backend.utils.Backup;
import ru.dvdishka.backuper.handlers.commands.Permissions;

public class ToZIPConfirmationCommand extends Command {

    public ToZIPConfirmationCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelButtonSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        normalButtonSound();

        Backup backup = new Backup(backupName);

        long backupSize = backup.getMBSize();
        String zipFolderBackup = backup.zipOrFolder();

        if (zipFolderBackup.equals("(ZIP)")) {
            cancelButtonSound();
            returnFailure("Backup is already ZIP!");
            return;
        }

        if (Backup.isLocked() || Backup.isLocked()) {
            cancelButtonSound();
            returnFailure("Backup is blocked by another operation!");
            return;
        }

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text("---------------")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xE3A013)))
                .append(Component.newline());

        message = message
                .append(Component.text("Are you sure")
                        .append(Component.newline())
                        .append(Component.text("You want to convert this backup to ZIP?"))
                        .color(TextColor.color(0xB02100)))
                .append(Component.newline());

        message = message
                .append(Component.text(backupName)
                        .hoverEvent(HoverEvent.showText(Component.text(zipFolderBackup + " " + backupSize + " MB"))))
                .append(Component.newline());

        message = message
                .append(Component.text("[CONVERT BACKUP]")
                        .clickEvent(ClickEvent.runCommand("/backup menu \"" + backupName + "\" toZIP"))
                        .color(TextColor.color(0x4974B))
                        .decorate(TextDecoration.BOLD));

        if (sender.hasPermission(Permissions.STATUS.getPermission())) {
            message = message
                    .append(Component.space())
                    .append(Component.text("[STATUS]")
                            .clickEvent(ClickEvent.runCommand("/backup status"))
                            .color(TextColor.color(17, 102, 212))
                            .decorate(TextDecoration.BOLD));
        }

        message = message.append(Component.newline());

        message = message
                .append(Component.text("---------------")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xE3A013)));

        sender.sendMessage(message);
    }
}
