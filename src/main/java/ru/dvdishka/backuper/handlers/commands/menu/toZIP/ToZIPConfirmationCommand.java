package ru.dvdishka.backuper.handlers.commands.menu.toZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.handlers.commands.Command;

public class ToZIPConfirmationCommand extends Command {

    public ToZIPConfirmationCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!ConfigManager.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("local storage is disabled!");
            return;
        }

        if (!LocalBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        LocalBackup localBackup = LocalBackup.getInstance(backupName);
        String backupFormattedName = localBackup.getFormattedName();

        long backupSize = localBackup.getMbSize();

        if (Backup.BackupFileType.ZIP.equals(localBackup.getFileType())) {
            cancelSound();
            returnFailure("Backup is already ZIP!");
            return;
        }

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Backup is blocked by another operation!");
            return;
        }

        buttonSound();

        Component header = Component.empty();

        header = header
                .append(Component.text("Confirm ToZIP")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)));

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text(backupFormattedName)
                        .hoverEvent(HoverEvent.showText(Component.text("(%s) (%s) %s MB".formatted(localBackup.getStorageType().name(), localBackup.getFileType().name(), backupSize)))))
                .append(Component.newline())
                .append(Component.newline());

        message = message
                .append(Component.text("[CONVERT BACKUP]")
                        .clickEvent(ClickEvent.runCommand("/backuper menu local \"%s\" toZIP".formatted(backupName)))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD));

        sendFramedMessage(header, message, 15);
    }
}
