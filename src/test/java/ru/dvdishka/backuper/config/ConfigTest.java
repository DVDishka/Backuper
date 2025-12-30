package ru.dvdishka.backuper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.BaseTest;

import java.io.IOException;
import java.io.InputStreamReader;

public class ConfigTest extends BaseTest {

    private FileConfiguration defaultConfig;

    @BeforeEach
    public void setUp() {
        defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("config.yml")));
    }

    @Test
    public void testConfigReloading() {
        Backuper.getInstance().getConfigManager().setConfigField("backup.autoBackup", false);
        reload();
        assert !Backuper.getInstance().getConfigManager().getBackupConfig().isAutoBackup();
    }

    @Test
    public void testConfigReparation() throws IOException {
        config.set("backup.autoBackup", null);
        config.set("server.alertTimeBeforeRestart", null);
        config.set("storages.local.enabled", null);
        config.save(configFile);

        reload();

        assert defaultConfig.getBoolean("backup.autoBackup") == Backuper.getInstance().getConfigManager().getBackupConfig().isAutoBackup();
        assert defaultConfig.getInt("server.alertTimeBeforeRestart") == Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart();
        assert defaultConfig.getBoolean("storages.local.enabled") == Backuper.getInstance().getStorageManager().getStorage("local").getConfig().isEnabled();
    }

    @Test
    public void testStorageDeletion() throws IOException {
        config.set("storages.local", null);
        config.save(configFile);

        reload();

        assert Backuper.getInstance().getStorageManager().getStorage("local") == null; // There must be no such storage because it must be not repaired
    }

    @Test
    public void testConfigBackwardCompatibilityWithPeriodicalBackupTime() throws IOException {
        // Below 4
        config.set("configVersion", 3.0);
        config.set("backupPeriod", 10);
        config.set("fixedBackupTime", false);

        // Below 8
        config.set("backupsFolder", "testFolder");
        config.set("autoBackup", false);
        config.set("alertTimeBeforeRestart", 10);

        // Below 13 is checked in some of the Below 4 and Below 8 fields

        config.save(configFile);
        reload();

        assert config.get("configVersion").equals(defaultConfig.get("configVersion"));
        assert config.getInt("backup.autoBackupPeriod") == 600;
        assert config.get("backup.autoBackupCron").equals("");
        assert !config.getBoolean("backup.autoBackup");
        assert config.get("storages.local.backupsFolder").equals("testFolder");
        assert config.getInt("server.alertTimeBeforeRestart") == 10;
    }

    @Test
    public void testConfigBackwardsCompatibilityWithFixedBackupTime() throws IOException {
        // Below 4
        config.set("configVersion", 3.0);
        config.set("fixedBackupTime", true);
        config.set("firstBackupTime", 10);

        config.save(configFile);
        reload();

        assert config.get("backup.autoBackupCron").equals("0 0 10 1/1 * ? *");
    }
}
