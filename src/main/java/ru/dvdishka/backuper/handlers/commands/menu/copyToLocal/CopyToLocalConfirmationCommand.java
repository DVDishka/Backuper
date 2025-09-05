package ru.dvdishka.backuper.handlers.commands.menu.copyToLocal;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.FtpBackup;
import ru.dvdishka.backuper.backend.backup.GoogleDriveBackup;
import ru.dvdishka.backuper.backend.backup.SftpBackup;
import ru.dvdishka.backuper.backend.config.ConfigManager;
import ru.dvdishka.backuper.handlers.commands.Command;

public class CopyToLocalConfirmationCommand extends Command {

    private final String storage;

    public CopyToLocalConfirmationCommand(String storage, CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
        this.storage = storage;
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!ConfigManager.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled!");
            return;
        }

        if (storage.equals("sftp") && !ConfigManager.getInstance().getSftpConfig().isEnabled() ||
                storage.equals("ftp") && !ConfigManager.getInstance().getFtpConfig().isEnabled() ||
                storage.equals("googleDrive") && (!ConfigManager.getInstance().getGoogleDriveConfig().isEnabled() ||
                        !GoogleDriveUtils.checkConnection())) {
            cancelSound();
            if (!storage.equals("googleDrive")) {
                returnFailure("%s storage is disabled!".formatted(storage));
            } else {
                returnFailure("%s storage is disabled or Google account is not linked!".formatted(storage));
            }
            return;
        }

        Backup backup = null;

        if (storage.equals("sftp")) {
            backup = SftpBackup.getInstance(backupName);
        }
        if (storage.equals("ftp")) {
            backup = FtpBackup.getInstance(backupName);
        }
        if (storage.equals("googleDrive")) {
            backup = GoogleDriveBackup.getInstance(backupName);
        }

        if (backup == null) {
            cancelSound();
            returnFailure("Wrong backupName!");
            return;
        }

        String backupFormattedName = backup.getFormattedName();

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        long backupSize = backup.getMbSize();

        Component header = Component.empty();

        header = header
                .append(Component.text("Confirm copying to local")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)));

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text(backupFormattedName)
                        .hoverEvent(HoverEvent.showText(Component.text("(%s) (%s) %s MB".formatted(backup.getStorageType().name(), backup.getFileType().name(), backupSize)))))
                .append(Component.newline())
                .append(Component.newline());

        message = message
                .append(Component.text("[COPY TO LOCAL]")
                        .clickEvent(ClickEvent.runCommand("/backuper menu %s \"%s\" copyToLocal".formatted(storage, backupName)))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD));

        sendFramedMessage(header, message, 15);
    }
}
