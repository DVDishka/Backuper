package ru.dvdishka.backuper.commands.menu.makeZip;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.CommandInterface;
import ru.dvdishka.backuper.commands.common.Scheduler;
import ru.dvdishka.backuper.common.Backup;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ToZIP implements CommandInterface {

    private boolean isDeleteSuccessful = true;

    @Override
    public void execute(CommandSender sender, CommandArguments args) {

        String backupName = (String) args.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelButtonSound(sender);
            returnFailure("Backup does not exist!", sender);
            return;
        }

        assert backupName != null;

        normalButtonSound(sender);

        Backup backup = new Backup(backupName);

        if (backup.zipOrFolder().equals("(ZIP)")) {
            cancelButtonSound(sender);
            returnFailure("Backup is already ZIP!", sender);
            return;
        }

        if (backup.isLocked() || Backup.isBackupBusy) {
            cancelButtonSound(sender);
            returnFailure("Blocked by another operation!", sender);
            return;
        }

        backup.lock();

        try {

            ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(Paths.get(backup.getFile().getPath() + ".zip")));

            Scheduler.getScheduler().runAsync(Common.plugin, () -> {

                Logger.getLogger().log("Convert Backup To ZIP process has been started");
                sendMessage("Convert Backup To ZIP process has been started", sender);
                Logger.getLogger().log("Pack To Zip task has been started...");
                sendMessage("Pack To Zip task has been started...", sender);

                packToZIP(backup.getFile(), zip, new File(backup.getFile().getPath() + ".zip").toPath());

                Logger.getLogger().log("Pack To Zip task has been finished");
                sendMessage("Pack To Zip task has been finished", sender);
                Logger.getLogger().log("Delete Old Backup Folder task has been started...");
                sendMessage("Delete Old Backup Folder task has been started...", sender);

                deleteDir(backup.getFile());

                Logger.getLogger().log("Delete Old Backup Folder task has been finished");
                sendMessage("Delete Old Backup Folder task has been finished", sender);
                Logger.getLogger().log("Convert Backup To ZIP process has been finished successfully");
                returnSuccess("Convert Backup To ZIP process has been finished successfully", sender);

                backup.unlock();
            });

        } catch (Exception e) {

            backup.unlock();
            returnFailure("Something went wrong when trying to convert the backup to zip!", sender);
            Logger.getLogger().warn("Something went wrong when trying to convert the backup to zip!");
            Logger.getLogger().devWarn(this, e);
        }
    }

    public void packToZIP(File sourceDir, ZipOutputStream zip, Path zipFilePath) {

        for (File file : Objects.requireNonNull(sourceDir.listFiles())) {

            if (file.isDirectory()) {

                packToZIP(file, zip, zipFilePath);

            } else if (!file.getName().equals("session.lock")) {

                try {

                    String relativeFilePath = zipFilePath.relativize(file.toPath()).toFile().getPath();
                    relativeFilePath = relativeFilePath.replace("./", "");
                    relativeFilePath = relativeFilePath.replace("..\\", "");
                    while (!relativeFilePath.isEmpty() && relativeFilePath.charAt(0) == '.') {

                        relativeFilePath = relativeFilePath.replaceFirst(".", "");
                    }

                    zip.putNextEntry(new ZipEntry(relativeFilePath));
                    FileInputStream fileInputStream = new FileInputStream(file);
                    byte[] buffer = new byte[4048];
                    int length;

                    while ((length = fileInputStream.read(buffer)) > 0) {

                        zip.write(buffer, 0, length);
                    }
                    zip.closeEntry();
                    fileInputStream.close();

                } catch (Exception e) {

                    Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + file.getName());
                    Logger.getLogger().devWarn(this, e);
                }
            }
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
