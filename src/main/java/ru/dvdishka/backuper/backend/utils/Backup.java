package ru.dvdishka.backuper.backend.utils;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.handlers.commands.Permissions;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

public class Backup {

    private String backupName;
    private LocalDateTime backupLocalDateTime;

    public static DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    public static volatile boolean isBackupBusy = false;

    public Backup(String backupName) {

        if (!checkBackupExistenceByName(backupName)) {
            return;
        }
        this.backupName = backupName;
        this.backupLocalDateTime = LocalDateTime.parse(backupName, dateTimeFormatter);
    }

    public String getName() {
        return backupName;
    }

    public LocalDateTime getLocalDateTime() {
        return backupLocalDateTime;
    }

    public long getMBSize() {

        File backupsFolder = new File(Config.getInstance().getBackupsFolder());
        String backupFilePath;

        if (backupsFolder.toPath().resolve(backupName).toFile().exists()) {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath();
        } else {
            backupFilePath = backupsFolder.toPath().resolve(backupName).toFile().getPath() + ".zip";
        }

        long backupSize = Common.getPathOrFileByteSize(new File(backupFilePath));

        if (backupSize != 0) {
            backupSize /= (1024 * 1024);
        }

        return backupSize;
    }

    public String zipOrFolder() {

        File backupsFolder = new File(Config.getInstance().getBackupsFolder());
        String zipOrFolder = "(ZIP)";

        if (backupsFolder.toPath().resolve(backupName).toFile().exists()) {
            zipOrFolder = "(Folder)";
        }

        return zipOrFolder;
    }

    public File getFile() {

        File backupsFolder = new File(Config.getInstance().getBackupsFolder());

        if (this.zipOrFolder().equals("(ZIP)")) {
            return backupsFolder.toPath().resolve(backupName + ".zip").toFile();
        } else {
            return backupsFolder.toPath().resolve(backupName).toFile();
        }
    }

    public File getZIPFile() {

        File backupsFolder = new File(Config.getInstance().getBackupsFolder());

        if (backupsFolder.toPath().resolve(backupName + ".zip").toFile().exists()) {
            return backupsFolder.toPath().resolve(backupName + ".zip").toFile();
        }
        return null;
    }

    public void lock() {

        isBackupBusy = true;
    }

    public void unlock() {

        isBackupBusy = false;
    }

    public boolean isLocked() {

        return isBackupBusy;
    }

    @SuppressWarnings({"BooleanMethodIsAlwaysInverted", "ResultOfMethodCallIgnored"})
    public static boolean checkBackupExistenceByName(String backupName) {

        try {
            LocalDateTime.parse(backupName, dateTimeFormatter);
        } catch (Exception e) {
            return false;
        }

        File backupsFolder = new File(Config.getInstance().getBackupsFolder());

        return backupsFolder.toPath().resolve(backupName).toFile().exists() ||
                backupsFolder.toPath().resolve(backupName + ".zip").toFile().exists();
    }

    public static void sortLocalDateTime(ArrayList<LocalDateTime> backups) {
        for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

            for (int secondBackupsIndex = firstBackupsIndex; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                if (backups.get(firstBackupsIndex).isAfter(backups.get(secondBackupsIndex))) {

                    LocalDateTime saveDate = backups.get(firstBackupsIndex);

                    backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                    backups.set(secondBackupsIndex, saveDate);
                }
            }
        }
    }

    public static void sortLocalDateTimeDecrease(ArrayList<LocalDateTime> backups) {
        for (int firstBackupsIndex = 0; firstBackupsIndex < backups.size(); firstBackupsIndex++) {

            for (int secondBackupsIndex = firstBackupsIndex; secondBackupsIndex < backups.size(); secondBackupsIndex++) {

                if (backups.get(firstBackupsIndex).isBefore(backups.get(secondBackupsIndex))) {

                    LocalDateTime saveDate = backups.get(firstBackupsIndex);

                    backups.set(firstBackupsIndex, backups.get(secondBackupsIndex));
                    backups.set(secondBackupsIndex, saveDate);
                }
            }
        }
    }

    public static void sendBackupAlert(long timeSeconds) {

        for (Player player : Bukkit.getOnlinePlayers()) {

            if (!player.hasPermission(Permissions.ALERT.getPermission())) {
                continue;
            }

            Component message = Component.empty();

            message = message.append(Component.text("---------"))
                    .append(Component.newline());

            message = message.append(Component.text("Server will be restarted in ~ " +
                            timeSeconds + " seconds"))
                    .append(Component.newline());

            message = message.append(Component.text("---------"));

            player.sendMessage(message);
        }
    }
}
