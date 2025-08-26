package ru.dvdishka.backuper.handlers.commands.menu.toZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.backup.Backup;
import ru.dvdishka.backuper.backend.backup.LocalBackup;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.util.List;

public class ToZIPCommand extends Command {

    public ToZIPCommand(CommandSender sender, CommandArguments arguments) {
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

        if (Backup.BackupFileType.ZIP.equals(localBackup.getFileType())) {
            cancelSound();
            returnFailure("Backup is already ZIP!");
            return;
        }

        if (Backuper.getInstance().getTaskManager().isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        AsyncTask task = localBackup.getToZipTask();
        Backuper.getInstance().getTaskManager().startTaskAsync(task, sender, List.of(Permissions.LOCAL_TO_ZIP));
    }
}
