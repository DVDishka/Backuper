package ru.dvdishka.backuper.commands.menu.toZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.Bukkit;
import org.bukkit.World;
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

            ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(Paths.get(backup.getFile().getPath() + " in progress" + ".zip")));

            Scheduler.getScheduler().runAsync(Common.plugin, () -> {

                try {

                    Logger.getLogger().log("The Convert Backup To ZIP process has been started, it may take a some time...", sender);

                    Logger.getLogger().devLog("The Pack To Zip task has been started");
                    for (File file : backup.getFile().listFiles()) {
                        Logger.getLogger().devLog("The Pack World " + file.getName() + " To ZIP task has been started");
                        packToZIP(file, zip, file.getParentFile().toPath(), sender);
                        Logger.getLogger().devLog("The Pack World " + file.getName() + " To ZIP task has been finished");
                    }
                    Logger.getLogger().devLog("The Pack To Zip task has been finished");

                    Logger.getLogger().devLog("The Delete Old Backup Folder task has been started");
                    deleteDir(backup.getFile(), sender);
                    Logger.getLogger().devLog("The Delete Old Backup Folder task has been finished");

                    Logger.getLogger().devLog("The Rename \"in progress\" ZIP task has been started");
                    if (!new File(backup.getFile().getPath().replace(".zip", "") + " in progress" + ".zip")
                            .renameTo(new File(backup.getFile().getPath().replace(".zip", "") + ".zip"))) {
                        Logger.getLogger().warn("The Rename \"in progress\" ZIP task has been finished with an exception!", sender);
                        backup.unlock();
                        throw new RuntimeException();
                    }
                    Logger.getLogger().devLog("The Rename \"in progress\" Folder task has been finished");

                    backup.unlock();

                    Logger.getLogger().success("The Convert Backup To ZIP process has been finished successfully", sender);

                } catch (Exception e) {

                    backup.unlock();
                    Logger.getLogger().warn("The Convert Backup To ZIP process has been finished with an exception!", sender);
                    Logger.getLogger().devWarn(this, e);
                }
            });
        } catch (Exception e) {

            backup.unlock();
        }
    }

    public void packToZIP(File sourceDir, ZipOutputStream zip, Path folderPath, CommandSender sender) {

        for (File file : Objects.requireNonNull(sourceDir.listFiles())) {

            if (file.isDirectory()) {

                packToZIP(file, zip, folderPath, sender);

            } else if (!file.getName().equals("session.lock")) {

                try {

                    String relativeFilePath = folderPath.toAbsolutePath().relativize(file.toPath().toAbsolutePath()).toString();

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

                    Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + file.getName(), sender);
                    Logger.getLogger().devWarn(this, e);
                    throw new RuntimeException();
                }
            }
        }
    }

    public void deleteDir(File dir, CommandSender sender) {

        if (dir != null && dir.listFiles() != null) {

            for (File file : Objects.requireNonNull(dir.listFiles())) {

                if (file.isDirectory()) {

                    deleteDir(file, sender);

                } else {

                    if (!file.delete()) {

                        Logger.getLogger().warn("Can not delete file " + file.getName(), sender);
                    }
                }
            }
            if (!dir.delete()) {

                Logger.getLogger().warn("Can not delete directory " + dir.getName(), sender);
            }
        }
    }
}
