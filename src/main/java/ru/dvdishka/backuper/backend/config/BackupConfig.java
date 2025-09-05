package ru.dvdishka.backuper.backend.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.quartz.CronExpression;
import ru.dvdishka.backuper.Backuper;

import java.io.InputStreamReader;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
public class BackupConfig implements Config {

    private boolean autoBackup;
    private CronExpression autoBackupCron;
    private String backupFileNameFormat;
    private List<String> addDirectoryToBackup;
    private List<String> excludeDirectoryFromBackup;
    private boolean deleteBrokenBackups;
    private boolean skipDuplicateBackup;
    private String afterBackup;
    private boolean setWorldsReadOnly;

    private DateTimeFormatter dateTimeFormatter;

    public BackupConfig load(ConfigurationSection config) {
        boolean autoBackup = config.getBoolean("autoBackup", true);
        CronExpression autoBackupCron = null;
        if (autoBackup) {
            try {
                autoBackupCron = new CronExpression(config.getString("autoBackupCron", "0 0 0 1/1 * ? *"));
            } catch (ParseException e) {
                autoBackup = false;
                Backuper.getInstance().getLogManager().warn("Failed to parse backup.autoBackupCron! Disabling auto backup...");
                Backuper.getInstance().getLogManager().warn(e);
            }
        }
        this.autoBackup = autoBackup;
        this.autoBackupCron = autoBackupCron;
        String backupFileNameFormat = config.getString("backupFileNameFormat", "dd-MM-yyyy HH-mm-ss");
        this.addDirectoryToBackup = config.getStringList("addDirectoryToBackup");
        this.excludeDirectoryFromBackup = config.getStringList("excludeDirectoryFromBackup");
        this.deleteBrokenBackups = config.getBoolean("deleteBrokenBackups", true);
        this.skipDuplicateBackup = config.getBoolean("skipDuplicateBackup", true);
        this.afterBackup = config.getString("afterBackup", "NOTHING").toUpperCase();
        this.setWorldsReadOnly = config.getBoolean("setWorldsReadOnly", false);

        try {
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern(backupFileNameFormat);
            LocalDateTime localDateTime = LocalDateTime.parse(LocalDateTime.now().format(dateTimeFormatter), dateTimeFormatter);
        } catch (Exception e) {
            Backuper.getInstance().getLogManager().warn("Wrong backupFileNameFormat format: \"%s\", using default \"dd-MM-yyyy HH-mm-ss\" value...".formatted(backupFileNameFormat));
            Backuper.getInstance().getLogManager().warn(e);
            backupFileNameFormat = "dd-MM-yyyy HH-mm-ss";
        }
        this.backupFileNameFormat = backupFileNameFormat;
        this.dateTimeFormatter = DateTimeFormatter.ofPattern(backupFileNameFormat);
        return this;
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        return YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("backup_config.yml")));
    }
}
