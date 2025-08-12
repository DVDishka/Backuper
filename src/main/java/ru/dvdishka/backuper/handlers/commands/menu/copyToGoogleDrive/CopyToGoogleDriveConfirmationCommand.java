package ru.dvdishka.backuper.handlers.commands.menu.copyToGoogleDrive;

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
import ru.dvdishka.backuper.backend.util.GoogleDriveUtils;
import ru.dvdishka.backuper.handlers.commands.Command;

public class CopyToGoogleDriveConfirmationCommand extends Command {

    public CopyToGoogleDriveConfirmationCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
            cancelSound();
            returnFailure("Local storage is disabled");
            return;
        }

        if (!Config.getInstance().getGoogleDriveConfig().isEnabled() || !GoogleDriveUtils.checkConnection()) {
            cancelSound();
            returnFailure("Google Drive storage is disabled or Google account is not linked!");
            return;
        }

        LocalBackup localBackup = LocalBackup.getInstance((String) arguments.get("backupName"));

        if (localBackup == null) {
            cancelSound();
            returnFailure("Wrong backup name");
            return;
        }

        String backupName = localBackup.getName();
        String backupFormattedName = localBackup.getFormattedName();

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        long backupSize = localBackup.getMbSize();

        Component header = Component.empty();

        header = header
                .append(Component.text("Confirm copying to GoogleDrive")
                        .decorate(TextDecoration.BOLD)
                        .color(TextColor.color(0xB02100)));

        Component message = net.kyori.adventure.text.Component.empty();

        message = message
                .append(Component.text(backupFormattedName)
                        .hoverEvent(HoverEvent.showText(Component.text("(%s) (%s) %s MB".formatted(localBackup.getFileType().name(), localBackup.getStorageType().name(), backupSize)))))
                .append(Component.newline())
                .append(Component.newline());

        message = message
                .append(Component.text("[COPY TO GoogleDrive]")
                        .clickEvent(ClickEvent.runCommand("/backuper menu local \"%s\" copyToGoogleDrive".formatted(backupName)))
                        .color(TextColor.color(0xB02100))
                        .decorate(TextDecoration.BOLD));

        sendFramedMessage(header, message, 15);
    }
}
