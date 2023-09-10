package ru.dvdishka.backuper.commands.menu.unZIP;

import dev.jorel.commandapi.executors.CommandArguments;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.commands.common.CommandInterface;
import ru.dvdishka.backuper.commands.common.Scheduler;
import ru.dvdishka.backuper.common.Backup;
import ru.dvdishka.backuper.common.Common;
import ru.dvdishka.backuper.common.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnZIP implements CommandInterface {

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

        if (backup.zipOrFolder().equals("(Folder)")) {
            cancelButtonSound(sender);
            returnFailure("Backup is already Folder!", sender);
            return;
        }

        if (backup.isLocked() || Backup.isBackupBusy) {
            cancelButtonSound(sender);
            returnFailure("Blocked by another operation!", sender);
            return;
        }

        backup.lock();

        Scheduler.getScheduler().runAsync(Common.plugin, () -> {
            try {
                Logger.getLogger().log("The Convert Backup To Folder process has been started, it may take a long time...");
                sendMessage("The UnZIP process has been started, it may take a long time...", sender);

                Logger.getLogger().log("The Unpack task has been stared");
                sendMessage("The Unpack task has been stared", sender);
                unPack(backup, sender);

                Logger.getLogger().log("The Delete Old Backup ZIP task has been started");
                sendMessage("The Delete Old Backup ZIP task has been started", sender);
                if (!backup.getZIPFile().delete()) {
                    Logger.getLogger().warn("Something went wrong while trying to delete old backup ZIP");
                    returnFailure("Something went wrong while trying to delete old backup ZIP", sender);
                }
                Logger.getLogger().log("The Delete Old Backup ZIP task has been finished");
                sendMessage("The Delete Old Backup ZIP task has been finished", sender);

                backup.unlock();

                Logger.getLogger().log("The Convert Backup To Folder process has been finished successfully");
                returnSuccess("The UnZIP process has been finished successfully", sender);

            } catch (Exception e) {
                returnFailure("The Convert Backup To Folder process has been finished with an exception!", sender);
                Logger.getLogger().warn("The Convert Backup To Folder process has been finished with an exception!");
                Logger.getLogger().devWarn(this, e);
            }
        });
    }

    public void unPack(Backup backup, CommandSender sender) {

        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(backup.getFile().toPath()))) {

            ZipEntry zipEntry;
            String name;

            while ((zipEntry = zipInput.getNextEntry()) != null) {

                name = zipEntry.getName();

                if (!new File(backup.getFile().getPath().replace(".zip", "")).toPath().resolve(name).getParent().toFile().exists() &&
                        !new File(backup.getFile().getPath().replace(".zip", "")).toPath().resolve(name).getParent().toFile().mkdirs()) {
                    returnFailure("Can not create directory " + new File(backup.getFile().getPath().replace(".zip", "")).toPath().resolve(name).getParent(), sender);
                }

                FileOutputStream outputStream = new FileOutputStream(new File(backup.getFile().getPath().replace(".zip", "")).toPath().resolve(name).toFile());
                for (int c = zipInput.read(); c != -1; c = zipInput.read()) {
                    outputStream.write(c);
                }
                outputStream.flush();
                zipInput.closeEntry();
                outputStream.close();
            }
            Logger.getLogger().log("The Unpack task has been finished");
            sendMessage("The Unpack task has been finished", sender);
        } catch (Exception e) {
            Logger.getLogger().warn("The Unpack task has been finished with an exception!");
            Logger.getLogger().devWarn(this, e.getMessage());
            returnFailure("The Unpack task has been finished with an exception!", sender);
        }
    }
}
