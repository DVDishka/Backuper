package ru.dvdishka.backuper.handlers.commands.googleDrive;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.handlers.commands.Command;

public class GoogleDriveLinkCommand extends Command {

    public GoogleDriveLinkCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        try {
            if (Backuper.getInstance().getTaskManager().isLocked()) {
                returnFailure("You cannot link your account while some process is running");
                return;
            }

            if (GoogleDriveUtils.authorizeForced(sender) != null) {
                returnSuccess("Google account authorization completed");
            }
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Failed to link Google account", sender);
            Backuper.getInstance().getLogManager().warn(e);
        }
    }
}
