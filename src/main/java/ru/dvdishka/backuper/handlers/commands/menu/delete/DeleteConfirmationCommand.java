package ru.dvdishka.backuper.handlers.commands.menu.delete;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.handlers.commands.Command;

public class DeleteConfirmationCommand extends Command {

    private String storage = "";

    public DeleteConfirmationCommand(String storage, CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
        this.storage = storage;
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (storage.equals("local") && !Config.getInstance().getLocalConfig().isEnabled() ||
                storage.equals("sftp") && !Config.getInstance().getSftpConfig().isEnabled() ||
                storage.equals("ftp") && !Config.getInstance().getFtpConfig().isEnabled() ||
                storage.equals("googleDrive") && (!Config.getInstance().getGoogleDriveConfig().isEnabled() ||
                        !GoogleDriveUtils.isAuthorized(sender))) {
            cancelSound();
            if (!storage.equals("googleDrive")) {
                returnFailure(storage + " storage is disabled!");
            } else {
                returnFailure(storage + " storage is disabled or Google account is not linked!");
            }
            return;
        }

        if (storage.equals("local") && !LocalBackup.checkBackupExistenceByName(backupName) ||
                storage.equals("sftp") && !SftpBackup.checkBackupExistenceByName(backupName) ||
                storage.equals("ftp") && !FtpBackup.checkBackupExistenceByName(backupName) ||
                storage.equals("googleDrive") && !GoogleDriveBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        Backup backup = null;

        if (storage.equals("local")) {
            backup = LocalBackup.getInstance(backupName);
        }
        if (storage.equals("sftp")) {
            backup = SftpBackup.getInstance(backupName);
        }
        if (storage.equals("ftp")) {
            backup = FtpBackup.getInstance(backupName);
        }
        if (storage.equals("googleDrive")) {
            backup = GoogleDriveBackup.getInstance(backupName);
        }
        String backupFormattedName = backup.getFormattedName();

        if (Backuper.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        long backupSize = backup.getByteSize(sender) / 1024;
        String zipFolderBackup = backup.getFileType();

        Component header = Component.empty();

        header = header
                .append(Component.text("Confirm Deletion")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)));

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text(backupFormattedName)
                        .hoverEvent(HoverEvent.showText(Component.text("(" + storage + ") " + zipFolderBackup + " " + backupSize + " MB"))))
                .append(Component.newline())
                .append(Component.newline());

        message = message
                .append(Component.text("[DELETE BACKUP]")
                        .clickEvent(ClickEvent.runCommand("/backuper menu " + storage + " \"" + backupName + "\" delete"))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD));

        sendFramedMessage(header, message, 15);
    }
}
