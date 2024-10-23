package ru.dvdishka.backuper.handlers.commands.googleDrive;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
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
            GoogleDriveUtils.authorizeForced(sender);
            returnSuccess("Google account authorization completed");
        } catch (Exception e) {
            Logger.getLogger().warn("Failed to link Google account", sender);
            Logger.getLogger().warn(this.getClass(), e);
        }
    }
}
