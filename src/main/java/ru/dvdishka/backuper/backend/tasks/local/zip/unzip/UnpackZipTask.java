package ru.dvdishka.backuper.backend.tasks.local.zip.unzip;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.tasks.Task;
import ru.dvdishka.backuper.backend.utils.UIUtils;
import ru.dvdishka.backuper.backend.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class UnpackZipTask extends Task {

    private static final String taskName = "UnpackZip";

    private final File sourceZipDir;
    private final File targetFolderDir;

    private final ArrayList<CompletableFuture<Void>> unPackTasks = new ArrayList<>();

    public UnpackZipTask(File sourceZipDir, File targetFolderDir, boolean setLocked, CommandSender sender) {

        super(taskName, setLocked, sender);
        this.sourceZipDir = sourceZipDir;
        this.targetFolderDir = targetFolderDir;
    }

    @Override
    public void run() {

        if (setLocked) {
            Backuper.lock(this);
        }

        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(sourceZipDir.toPath()))) {

            Logger.getLogger().devLog("UnpackZip task has been started");

            if (!isTaskPrepared) {
                prepareTask();
            }

            ZipEntry zipEntry;

            while ((zipEntry = zipInput.getNextEntry()) != null) {

                final String name = zipEntry.getName();
                final ArrayList<Integer> content = new ArrayList<>();

                for (int c = zipInput.read(); c != -1; c = zipInput.read()) {
                    content.add(c);
                }

                CompletableFuture<Void> unPackTask = CompletableFuture.runAsync(() -> {

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

                    } catch (Exception e) {

                        Logger.getLogger().warn("Something went wrong while trying to unpack file", sender);
                        Logger.getLogger().warn(this, e);
                    }
                });

                unPackTasks.add(unPackTask);

                zipInput.closeEntry();
            }

            CompletableFuture.allOf(unPackTasks.toArray(new CompletableFuture[0])).join();

            Logger.getLogger().devLog("UnpackZip task has been finished");

            if (setLocked) {
                UIUtils.successSound(sender);
                Backuper.unlock();
            }

        } catch (Exception e) {

            if (setLocked) {
                UIUtils.cancelSound(sender);
                Backuper.unlock();
            }

            Logger.getLogger().warn("Something went wrong while running UnpackZip task", sender);
            Logger.getLogger().warn(this, e);
        }
    }

    @Override
    public void prepareTask() {

        this.isTaskPrepared = true;
        maxProgress = 0;

        try (ZipFile zipFile = new ZipFile(sourceZipDir)) {

            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

            while (zipEntries.hasMoreElements()) {
                ZipEntry zipEntry = zipEntries.nextElement();
                maxProgress += zipEntry.getSize();
            }

        } catch (Exception e) {

            Logger.getLogger().warn("Something went wrong while calculating UnpackZip task maxProgress", sender);
            Logger.getLogger().warn(this, e);
        }

        if (maxProgress <= 0) {
            maxProgress = (long) (((double) Utils.getFileFolderByteSize(sourceZipDir)) * 1.6);
        }
    }
}
