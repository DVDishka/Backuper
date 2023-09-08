package ru.dvdishka.backuper.commands.menu.delete;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.CommandInterface;
import ru.dvdishka.backuper.commands.common.Scheduler;
import ru.dvdishka.backuper.common.Backup;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.Logger;

import java.io.File;
import java.util.Objects;

public class Delete implements CommandInterface {

    private boolean isDeleteSuccessful = true;

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        String backupName = (String) args.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelButtonSound(sender);
            returnFailure("Backup does not exist!", sender);
            return;
        }

        normalButtonSound(sender);

        Backup backup = new Backup(backupName);

        if (backup.isLocked() || Backup.isBackupBusy) {
            cancelButtonSound(sender);
            returnFailure("Backup is blocked by another operation!", sender);
            return;
        }

        File backupFile = backup.getFile();

        backup.lock();

        if (backup.zipOrFolder().equals("(ZIP)")) {

            Scheduler.getScheduler().runAsync(Common.plugin, () -> {
                if (backupFile.delete()) {
                    returnSuccess("Backup has been deleted successfully", sender);
                } else {
                    returnFailure("Backup " + backupName + " can not be deleted!", sender);
                }
                backup.unlock();
            });

        } else {

            Scheduler.getScheduler().runAsync(Common.plugin, () -> {
                deleteDir(backupFile);
                if (!isDeleteSuccessful) {
                    returnFailure("Delete task has been finished with an exception!", sender);
                } else {
                    returnSuccess("Backup has been deleted successfully", sender);
                }
                backup.unlock();
            });
        }
    }

    public void deleteDir(File dir) {

        if (dir != null && dir.listFiles() != null) {

            for (File file : Objects.requireNonNull(dir.listFiles())) {

                if (file.isDirectory()) {

                    deleteDir(file);

                } else {

                    if (!file.delete()) {

                        isDeleteSuccessful = false;
                        Logger.getLogger().devWarn(this, "Can not delete file " + file.getName());
                    }
                }
            }
            if (!dir.delete()) {

                isDeleteSuccessful = false;
                Logger.getLogger().devWarn(this, "Can not delete directory " + dir.getName());
            }
        }
    }
}
