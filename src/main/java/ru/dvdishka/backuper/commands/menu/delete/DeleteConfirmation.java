package ru.dvdishka.backuper.commands.menu.delete;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.CommandInterface;
import ru.dvdishka.backuper.common.Backup;

public class DeleteConfirmation implements CommandInterface {

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        String backupName = (String) args.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelButtonSound(sender);
            returnFailure("Backup does not exist!", sender);
            return;
        }

        assert backupName != null;

        normalButtonSound(sender);

        Backup backup = new Backup(backupName);

        if (backup.isLocked() || Backup.isBackupBusy) {
            cancelButtonSound(sender);
            returnFailure("Backup is blocked by another operation!", sender);
            return;
        }

        long backupSize = backup.getMBSize();
        String zipFolderBackup = backup.zipOrFolder();

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text("---------------")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xE3A013)))
                .append(Component.newline());

        message = message
                .append(Component.text("Are you sure")
                        .append(Component.newline())
                        .append(Component.text("You want to delete the backup?"))
                        .color(TextColor.color(0xB02100)))
                .append(Component.newline());

        message = message
                .append(Component.text(backupName)
                        .hoverEvent(HoverEvent.showText(Component.text(zipFolderBackup + " " + backupSize + " MB"))))
                .append(Component.newline());

        message = message
                .append(Component.text("[DELETE BACKUP]")
                        .clickEvent(ClickEvent.runCommand("/backup menu \"" + backupName + "\" delete"))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD))
                .append(Component.newline());

        message = message
                .append(Component.text("---------------")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xE3A013)));

        sender.sendMessage(message);
    }
}
