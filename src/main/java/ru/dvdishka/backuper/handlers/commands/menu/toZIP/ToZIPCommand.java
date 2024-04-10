package ru.dvdishka.backuper.handlers.commands.menu.toZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.utils.*;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ToZIPCommand extends Command implements Task {

    private String taskName = "ToZIP";
    private long maxProgress = 0;
    private volatile long currentProgress = 0;

    private final long zipProgressMultiplier = 5;
    private final long deleteProgressMultiplier = 1;

    public ToZIPCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelButtonSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        normalButtonSound();

        Backup backup = new Backup(backupName);

        if (backup.zipOrFolder().equals("(ZIP)")) {
            cancelButtonSound();
            returnFailure("Backup is already ZIP!");
            return;
        }

        if (Backup.isLocked() || Backup.isLocked()) {
            cancelButtonSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        Backup.lock(this);

        long backupFolderByteSize = backup.getByteSize();

        maxProgress = backupFolderByteSize * zipProgressMultiplier;
        maxProgress += backupFolderByteSize * deleteProgressMultiplier;

        StatusCommand.sendTaskStartedMessage("ToZIP", sender);

        try {

            ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(Paths.get(backup.getFile().getPath() + " in progress" + ".zip")));

            Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

                try {

                    Logger.getLogger().log("The Convert Backup To ZIP process has been started, it may take some time...", sender);

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
                        Backup.unlock();
                        throw new RuntimeException();
                    }
                    Logger.getLogger().devLog("The Rename \"in progress\" Folder task has been finished");

                    Backup.unlock();

                    Logger.getLogger().success("The Convert Backup To ZIP process has been finished successfully", sender);

                } catch (Exception e) {

                    Backup.unlock();

                    Logger.getLogger().warn("The Convert Backup To ZIP process has been finished with an exception!", sender);
                    Logger.getLogger().warn(this, e);
                }
            });
        } catch (Exception e) {

            Backup.unlock();
        }
    }

    public void packToZIP(File sourceDir, ZipOutputStream zip, Path folderPath, CommandSender sender) {

        if (sourceDir.isFile()) {

            try {

                String relativeFilePath = folderPath.toAbsolutePath().relativize(sourceDir.toPath().toAbsolutePath()).toString();

                zip.putNextEntry(new ZipEntry(relativeFilePath));
                FileInputStream fileInputStream = new FileInputStream(sourceDir);
                byte[] buffer = new byte[4048];
                int length;

                while ((length = fileInputStream.read(buffer)) > 0) {
                    zip.write(buffer, 0, length);
                }
                zip.closeEntry();
                fileInputStream.close();

                incrementCurrentProgress(Utils.getFolderOrFileByteSize(sourceDir) * zipProgressMultiplier);

            } catch (Exception e) {

                Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + sourceDir.getName(), sender);
                Logger.getLogger().warn(this, e);
            }
        }


        if (sourceDir.isDirectory()) {

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

                        incrementCurrentProgress(Utils.getFolderOrFileByteSize(file) * zipProgressMultiplier);

                    } catch (Exception e) {

                        Logger.getLogger().warn("Something went wrong while trying to put file in ZIP! " + file.getName(), sender);
                        Logger.getLogger().warn(this, e);
                    }
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

                    incrementCurrentProgress(Utils.getFolderOrFileByteSize(file) * deleteProgressMultiplier);

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

    private synchronized void incrementCurrentProgress(long progress) {
        currentProgress += progress;
    }

    @Override
    public String getTaskName() {
        return taskName;
    }

    @Override
    public long getTaskProgress() {
        return (long) (((double) currentProgress) / ((double) maxProgress) * 100.0);
    }
}
