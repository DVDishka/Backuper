package ru.dvdishka.backuper.handlers.commands.menu.copyToSftp;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.handlers.commands.Command;

public class CopyToSftpConfirmationCommand extends Command {

    public CopyToSftpConfirmationCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Config.getInstance().getSftpConfig().isEnabled()) {
            cancelSound();
            returnFailure("SFTP storage is disabled");
            return;
        }

        if (!LocalBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        LocalBackup backup = LocalBackup.getInstance(backupName);
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
                .append(Component.text("Confirm copying to SFTP")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)));

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text(backupFormattedName)
                        .hoverEvent(HoverEvent.showText(Component.text("(local) " + zipFolderBackup + " " + backupSize + " MB"))))
                .append(Component.newline())
                .append(Component.newline());

        message = message
                .append(Component.text("[COPY TO SFTP]")
                        .clickEvent(ClickEvent.runCommand("/backuper menu local " + "\"" + backupName + "\" copyToSftp"))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD));

        sendFramedMessage(header, message, 15);
    }
}
