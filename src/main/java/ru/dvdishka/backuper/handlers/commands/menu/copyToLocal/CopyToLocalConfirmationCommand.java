package ru.dvdishka.backuper.handlers.commands.menu.copyToLocal;

import dev.jorel.commandapi.executors.CommandArguments;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.classes.SftpBackup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.handlers.commands.Command;

public class CopyToLocalConfirmationCommand extends Command {

    public CopyToLocalConfirmationCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled");
            return;
        }

        if (!SftpBackup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        SftpBackup backup = SftpBackup.getInstance(backupName);

        if (Backuper.isLocked()) {
            cancelSound();
            returnFailure("Backup is blocked by another operation!");
            return;
        }

        buttonSound();

        long backupSize = backup.getMbSize(sender);
        String zipFolderBackup = backup.getFileType();

        Component header = Component.empty();

        header = header
                .append(Component.text("Confirm copying to local")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)));

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text(backupName)
                        .hoverEvent(HoverEvent.showText(Component.text("(sftp) " + zipFolderBackup + " " + backupSize + " MB"))))
                .append(Component.newline())
                .append(Component.newline());

        message = message
                .append(Component.text("[COPY TO LOCAL]")
                        .clickEvent(ClickEvent.runCommand("/backuper menu sftp " + "\"" + backupName + "\" copyToLocal"))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD));

        sendFramedMessage(header, message, 15);
    }
}
