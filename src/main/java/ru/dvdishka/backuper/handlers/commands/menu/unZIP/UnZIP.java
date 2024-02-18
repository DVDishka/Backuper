package ru.dvdishka.backuper.handlers.commands.menu.unZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.handlers.commands.Command;
import ru.dvdishka.backuper.back.common.Scheduler;
import ru.dvdishka.backuper.back.common.Backup;
import ru.dvdishka.backuper.back.common.Common;
import ru.dvdishka.backuper.back.common.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnZIP extends Command {

    private volatile ArrayList<Integer> completedUnZIPTasks = new ArrayList<>();

    public UnZIP(CommandSender sender, CommandArguments arguments) {
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

        if (backup.zipOrFolder().equals("(Folder)")) {
            cancelButtonSound();
            returnFailure("Backup is already Folder!");
            return;
        }

        if (backup.isLocked() || Backup.isBackupBusy) {
            cancelButtonSound();
            returnFailure("Blocked by another operation!");
            return;
        }

        backup.lock();

        Scheduler.getScheduler().runAsync(Common.plugin, () -> {

            try {

                Logger.getLogger().log("The Convert Backup To Folder process has been started, it may take a some time...", sender);

                Logger.getLogger().devLog("The Unpack task has been started");
                unPack(backup, sender);

                Logger.getLogger().devLog("The Delete Old Backup ZIP task has been started");
                if (!backup.getZIPFile().delete()) {
                    Logger.getLogger().warn("The Delete Old Backup ZIP task has been finished with an exception", sender);
                    backup.unlock();
                    throw new RuntimeException();
                }
                Logger.getLogger().devLog("The Delete Old Backup ZIP task has been finished");

                Logger.getLogger().devLog("The Rename \"in progress\" Folder task has been started");
                if (!new File(backup.getFile().getPath().replace(".zip", "") + " in progress")
                        .renameTo(new File(backup.getFile().getPath().replace(".zip", "")))) {
                    Logger.getLogger().warn("The Rename \"in progress\" Folder task has been finished with an exception!", sender);
                    backup.unlock();
                    throw new RuntimeException();
                }
                Logger.getLogger().devLog("The Rename \"in progress\" Folder task has been finished");

                backup.unlock();

                Logger.getLogger().success("The Convert Backup To Folder process has been finished successfully", sender);

            } catch (Exception e) {

                backup.unlock();
                Logger.getLogger().warn("The Convert Backup To Folder process has been finished with an exception!", sender);
                Logger.getLogger().devWarn(this, e);
            }
        });
    }

    public void unPack(Backup backup, CommandSender sender) {

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

                Scheduler.getScheduler().runAsync(Common.plugin, () -> {

                    try {

                        if (!new File(backup.getFile().getPath().replace(".zip", "") + " in progress").toPath().resolve(name).getParent().toFile().exists() &&
                                !new File(backup.getFile().getPath().replace(".zip", "") + " in progress").toPath().resolve(name).getParent().toFile().mkdirs()) {
                            Logger.getLogger().warn("Can not create directory " + new File(backup.getFile().getPath().replace(".zip", "") + " in progress").toPath().resolve(name).getParent(), sender);
                        }

                        FileOutputStream outputStream = new FileOutputStream(new File(backup.getFile().getPath().replace(".zip", "") + " in progress").toPath().resolve(name).toFile());
                        for (int c : content) {
                                outputStream.write(c);
                        }
                        outputStream.flush();
                        outputStream.close();

                        completedUnZIPTasks.add(taskID);

                    } catch (Exception e) {

                        backup.unlock();
                        Logger.getLogger().warn("Something went wrong while trying to unpack file", sender);
                        Logger.getLogger().devWarn(this, e);
                        throw new RuntimeException();
                    }
                });

                zipInput.closeEntry();
            }

            while (iterationNumber != completedUnZIPTasks.size()) {}

            Logger.getLogger().devLog("The Unpack task has been finished");

        } catch (Exception e) {

            Logger.getLogger().warn("The Unpack task has been finished with an exception!", sender);
            Logger.getLogger().devWarn(this, e);
            throw new RuntimeException();
        }
    }
}
