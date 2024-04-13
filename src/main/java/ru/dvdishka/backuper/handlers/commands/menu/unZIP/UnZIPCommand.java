package ru.dvdishka.backuper.handlers.commands.menu.unZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.classes.Backup;
import ru.dvdishka.backuper.backend.classes.Task;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.common.Scheduler;
import ru.dvdishka.backuper.backend.utils.*;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.handlers.commands.status.StatusCommand;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static java.lang.Long.min;

public class UnZIPCommand extends Command implements Task {

    private String taskName = "UnZIP";
    private long maxProgress = 0;
    private volatile long currentProgress = 0;

    private volatile ArrayList<Integer> completedUnZIPTasks = new ArrayList<>();

    private final long unZIPProgressMultiplier = 10;
    private final long deleteProgressMultiplier = 1;

    public UnZIPCommand(CommandSender sender, CommandArguments arguments) {
        super(sender, arguments);
    }

    @Override
    public void execute() {

        String backupName = (String) arguments.get("backupName");

        if (!Backup.checkBackupExistenceByName(backupName)) {
            cancelSound();
            returnFailure("Backup does not exist!");
            return;
        }

        assert backupName != null;

        Backup backup = new Backup(backupName);

        if (backup.zipOrFolder().equals("(Folder)")) {
            cancelSound();
            returnFailure("Backup is already Folder!");
            return;
        }

        if (Backup.isLocked() || Backup.isLocked()) {
            cancelSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        buttonSound();

        Backup.lock(this);

        long backupZIPByteSize = backup.getByteSize();

        maxProgress = (long) (backupZIPByteSize * Backup.zipCompressValue) * unZIPProgressMultiplier;
        maxProgress += backupZIPByteSize * deleteProgressMultiplier;

        StatusCommand.sendTaskStartedMessage("UnZIP", sender);

        Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

            try {

                Logger.getLogger().log("The Convert Backup To Folder process has been started, it may take some time...", sender);

                Logger.getLogger().devLog("The Unpack task has been started");
                unPack(backup, sender);

                Logger.getLogger().devLog("The Delete Old Backup ZIP task has been started");
                if (!backup.getZIPFile().delete()) {
                    Logger.getLogger().warn("The Delete Old Backup ZIP task has been finished with an exception", sender);
                    Backup.unlock();
                    throw new RuntimeException();
                }
                incrementCurrentProgress(backupZIPByteSize * deleteProgressMultiplier);

                Logger.getLogger().devLog("The Delete Old Backup ZIP task has been finished");

                Logger.getLogger().devLog("The Rename \"in progress\" Folder task has been started");
                if (!new File(backup.getFile().getPath().replace(".zip", "") + " in progress")
                        .renameTo(new File(backup.getFile().getPath().replace(".zip", "")))) {
                    Logger.getLogger().warn("The Rename \"in progress\" Folder task has been finished with an exception!", sender);
                    Backup.unlock();
                    throw new RuntimeException();
                }
                Logger.getLogger().devLog("The Rename \"in progress\" Folder task has been finished");

                Backup.unlock();

                Logger.getLogger().success("The Convert Backup To Folder process has been finished successfully", sender);

                successSound();

            } catch (Exception e) {

                Backup.unlock();

                Logger.getLogger().warn("The Convert Backup To Folder process has been finished with an exception!", sender);
                Logger.getLogger().warn(this, e);

                cancelSound();
            }
        });
    }

    public void unPack(Backup backup, CommandSender sender) {

        completedUnZIPTasks.clear();

        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(backup.getFile().toPath()))) {

            ZipEntry zipEntry;

            int iterationNumber = 0;

            while ((zipEntry = zipInput.getNextEntry()) != null) {

                iterationNumber++;

                String name = zipEntry.getName();
                ArrayList<Integer> content = new ArrayList<>();

                for (int c = zipInput.read(); c != -1; c = zipInput.read()) {
                    content.add(c);
                }

                final int taskID = iterationNumber;

                Scheduler.getScheduler().runAsync(Utils.plugin, () -> {

                    try {

                        if (!new File(backup.getFile().getPath().replace(".zip", "") + " in progress").toPath().resolve(name).getParent().toFile().exists()) {
                            new File(backup.getFile().getPath().replace(".zip", "") + " in progress").toPath().resolve(name).getParent().toFile().mkdirs();
                        }

                        FileOutputStream outputStream = new FileOutputStream(new File(backup.getFile().getPath().replace(".zip", "") + " in progress").toPath().resolve(name).toFile());
                        for (int c : content) {
                            outputStream.write(c);
                            incrementCurrentProgress(unZIPProgressMultiplier);
                        }
                        outputStream.flush();
                        outputStream.close();

                        completedUnZIPTasks.add(taskID);

                    } catch (Exception e) {

                        Backup.unlock();
                        Logger.getLogger().warn("Something went wrong while trying to unpack file", sender);
                        Logger.getLogger().warn(this, e);

                        completedUnZIPTasks.add(taskID);

                        throw new RuntimeException();
                    }
                });

                zipInput.closeEntry();
            }

            // Waiting for all files being unZipped
            while (iterationNumber != completedUnZIPTasks.size()) {}

            Logger.getLogger().devLog("The Unpack task has been finished");

        } catch (Exception e) {

            Logger.getLogger().warn("The Unpack task has been finished with an exception!", sender);
            Logger.getLogger().warn(this, e);
            throw new RuntimeException();
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
        return min((long) (((double) currentProgress) / ((double) maxProgress) * 100.0), 100);
    }
}
