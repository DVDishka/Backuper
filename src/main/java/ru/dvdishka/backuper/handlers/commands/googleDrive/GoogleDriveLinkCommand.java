package ru.dvdishka.backuper.handlers.commands.googleDrive;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.utils.GoogleDriveUtils;
import ru.dvdishka.backuper.handlers.commands.Command;

public class GoogleDriveLinkCommand extends Command {

    public GoogleDriveLinkCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        try {

            if (Backuper.isLocked()) {
                returnFailure("You cannot link your account while some process is running");
                return;
            }

            if (GoogleDriveUtils.authorizeForced(sender) != null) {
                returnSuccess("Google account authorization completed");
            }
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to link Google account", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }
}
