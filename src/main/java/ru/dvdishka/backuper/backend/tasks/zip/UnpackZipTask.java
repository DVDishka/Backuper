package ru.dvdishka.backuper.backend.tasks.zip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnpackZipTask extends Task {

    private static final String taskName = "UnpackZip";

    private final File sourceZipDir;
    private final File targetFolderDir;

    private volatile long completedUnZIPTasksCount = 0;

    public UnpackZipTask(File targetFolderDir, File sourceZipDir, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.sourceZipDir = sourceZipDir;
        this.targetFolderDir = targetFolderDir;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backup.lock(this);
        }

        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(sourceZipDir.toPath()))) {

            Logger.getLogger().devLog("UnpackZip task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            ZipEntry zipEntry;

            int unZIPTasksCount = 0;

            while ((zipEntry = zipInput.getNextEntry()) != null) {

                unZIPTasksCount++;

                String name = zipEntry.getName();
                ArrayList<Integer> content = new ArrayList<>();

                for (int c = zipInput.read(); c != -1; c = zipInput.read()) {
                    content.add(c);
                }

                Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

                    try {

                        if (!targetFolderDir.toPath().resolve(name).getParent().toFile().exists()) {

                            // Sometimes it works in vain, so there is no point in checking the result
                            targetFolderDir.toPath().resolve(name).getParent().toFile().mkdirs();
                        }

                        FileOutputStream outputStream = new FileOutputStream(targetFolderDir.toPath().resolve(name).toFile());
                        for (int c : content) {
                            outputStream.write(c);
                            incrementCurrentProgress(1);
                        }
                        outputStream.flush();
                        outputStream.close();

                        taskCompleted();

                    } catch (Exception e) {

                        Logger.getLogger().warn("Something went wrong while trying to unpack file", sender);
                        Logger.getLogger().warn(this, e);

                        taskCompleted();
                    }
                });

                zipInput.closeEntry();
            }

            // Waiting for all files being unZipped
            while (unZIPTasksCount != completedUnZIPTasksCount) {}

            Logger.getLogger().devLog("UnpackZip task has been finished");

            if (setLocked) {
                UIUtils.successSound(sender);
                Backup.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backup.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running UnpackZip task", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    private synchronized void taskCompleted() {
        completedUnZIPTasksCount++;
    }

    @Override
    public void prepareTask() {

        this.isTaskPrepared = true;

        maxProgress = (long) ((double) Utils.getFolderOrFileByteSize(sourceZipDir) * Backup.zipCompressValue);
    }
}
