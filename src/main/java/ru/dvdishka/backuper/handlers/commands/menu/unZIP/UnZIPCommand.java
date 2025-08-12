package ru.dvdishka.backuper.handlers.commands.menu.unZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

public class UnZIPCommand extends Command {

    public UnZIPCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Config.getInstance().getLocalConfig().isEnabled()) {
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

        if (Backup.BackupFileType.DIR.equals(localBackup.getFileType())) {
            cancelSound();
            returnFailure("Backup is already unzipped!");
            return;
        }

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        Backuper.getInstance().getTaskManager().startTaskAsync(localBackup.getUnZipTask(), sender, List.of(Permissions.LOCAL_UNZIP));
        Backuper.getInstance().getScheduleManager().runAsync(() -> {
            localBackup.getUnZipTask();
        });
    }
}
