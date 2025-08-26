package ru.dvdishka.backuper.handlers.commands.menu;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import ru.dvdishka.backuper.backend.backup.*;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;
import ru.dvdishka.backuper.handlers.commands.Command;

public class MenuCommand extends Command {

    private String storage = "";

    public MenuCommand(String storage, CommandSender sender, CommandArguments arguments) {
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
                        !GoogleDriveUtils.checkConnection())) {
            cancelSound();
            if (!storage.equals("googleDrive")) {
                returnFailure("%s storage is disabled!".formatted(storage));
            } else {
                returnFailure("%s storage is disabled or Google account is not linked!".formatted(storage));
            }
            return;
        }

        if (storage.equals("local") && !LocalBackup.checkBackupExistenceByName(backupName) ||
                storage.equals("sftp") && !SftpBackup.checkBackupExistenceByName(backupName) ||
                storage.equals("ftp") && !FtpBackup.checkBackupExistenceByName(backupName)
                || storage.equals("googleDrive") && !GoogleDriveBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        buttonSound();

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

        long backupMbSize = backup.getMbSize();

        Component header = Component.empty();

        header = header
                .append(Component.text("Backup menu")
                        .decorate(TextDecoration.BOLD))
                .append(Component.space())
                .append(Component.text("(%s)".formatted(storage))
                        .color(TextColor.fromHexString("#129c9b"))
                        .decorate(TextDecoration.BOLD));
        ;

        Component message = Component.empty();

        if (!(sender instanceof ConsoleCommandSender)) {

            message = message
                    .append(Component.text(backupFormattedName)
                            .hoverEvent(HoverEvent.showText(Component.text("(%s) (%s) %s MB".formatted(backup.getStorageType().name(), backup.getFileType().name(), backup.getMbSize())))))
                    .append(Component.newline())
                    .append(Component.newline());

            if (storage.equals("local") && Backup.BackupFileType.DIR.equals(backup.getFileType())) {
                message = message
                        .append(Component.text("[TO ZIP]")
                                .clickEvent(ClickEvent.runCommand("/backuper menu %s \"%s\" toZIPConfirmation".formatted(storage, backupName)))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0x4974B)))
                        .append(Component.space());
            }

            if (storage.equals("local") && Backup.BackupFileType.ZIP.equals(backup.getFileType())) {
                message = message
                        .append(Component.text("[UNZIP]")
                                .clickEvent(ClickEvent.runCommand("/backuper menu %s \"%s\" unZIPConfirmation".formatted(storage, backupName)))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(0x4974B)))
                        .append(Component.space());
            }

            if (storage.equals("local") && Config.getInstance().getFtpConfig().isEnabled()) {
                message = message
                        .append(Component.text("[COPY TO FTP]")
                                .clickEvent(ClickEvent.runCommand("/backuper menu %s \"%s\" copyToFtpConfirmation".formatted(storage, backupName)))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(17, 102, 212)))
                        .append(Component.space());
            }

            if (storage.equals("local") && Config.getInstance().getSftpConfig().isEnabled()) {
                message = message
                        .append(Component.text("[COPY TO SFTP]")
                                .clickEvent(ClickEvent.runCommand("/backuper menu %s \"%s\" copyToSftpConfirmation".formatted(storage, backupName)))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(17, 102, 212)))
                        .append(Component.space());
            }

            if (storage.equals("sftp") && Config.getInstance().getLocalConfig().isEnabled() ||
                    storage.equals("ftp") && Config.getInstance().getFtpConfig().isEnabled() ||
                    storage.equals("googleDrive") && Config.getInstance().getGoogleDriveConfig().isEnabled()) {
                message = message
                        .append(Component.text("[COPY TO LOCAL]")
                                .clickEvent(ClickEvent.runCommand("/backuper menu %s \"%s\" copyToLocalConfirmation".formatted(storage, backupName)))
                                .decorate(TextDecoration.BOLD)
                                .color(TextColor.color(17, 102, 212)))
                        .append(Component.space());
            }

            message = message
                    .append(Component.text("[DELETE]")
                            .clickEvent(ClickEvent.runCommand("/backuper menu %s \"%s\" deleteConfirmation".formatted(storage, backupName)))
                            .decorate(TextDecoration.BOLD)
                            .color(TextColor.color(0xB02100)));

            sendFramedMessage(header, message, 15);

        } else {

            message = message
                    .append(Component.text(backupFormattedName))
                    .append(Component.space())
                    .append(Component.text("(%s)".formatted(backup.getStorageType().name())))
                    .append(Component.space())
                    .append(Component.text(backup.getFileType().name()))
                    .append(Component.space())
                    .append(Component.text(backupMbSize))
                    .append(Component.space())
                    .append(Component.text("MB"));

            sendFramedMessage(header, message);
        }
    }
}
