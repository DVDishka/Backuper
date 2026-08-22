package ru.dvdishka.backuper.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.BaseTest;
import ru.dvdishka.backuper.backend.config.ConfigBackwardsCompatibility;
import ru.dvdishka.backuper.backend.storage.StorageType;
import ru.dvdishka.backuper.backend.storage.WebDavStorage;

import java.io.IOException;
import java.io.InputStreamReader;

public class ConfigTest extends BaseTest {

    private FileConfiguration defaultConfig;

    @BeforeEach
    public void setUp() {
        defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("config.yml")));
    }

    @Test
    public void testConfigReloading() throws IOException {
        config.set("backup.autoBackup", false);
        reload();
        assert !Backuper.getInstance().getConfigManager().getBackupConfig().isAutoBackup();
    }

    @Test
    public void testConfigReparation() throws IOException {
        config.set("backup.autoBackup", null);
        config.set("server.alertTimeBeforeRestart", null);
        config.set("storages.local.enabled", null);
        reload();

        assert defaultConfig.getBoolean("backup.autoBackup") == Backuper.getInstance().getConfigManager().getBackupConfig().isAutoBackup();
        assert defaultConfig.getInt("server.alertTimeBeforeRestart") == Backuper.getInstance().getConfigManager().getServerConfig().getAlertTimeBeforeRestart();
        assert defaultConfig.getBoolean("storages.local.enabled") == Backuper.getInstance().getStorageManager().getStorage("local").getConfig().isEnabled();
    }

    @Test
    public void testStorageDeletion() throws IOException {
        config.set("storages.local", null);
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
        reload();

        assert config.get("configVersion").equals(defaultConfig.get("configVersion"));
        assert config.getInt("backup.autoBackupPeriod") == 600;
        assert config.get("backup.autoBackupCron").equals("");
        assert !config.getBoolean("backup.autoBackup");
        assert config.get("storages.local.backupsFolder").equals("testFolder");
        assert config.getInt("server.alertTimeBeforeRestart") == 10;
    }

    @Test
    public void testConfigBackwardsCompatibilityWithStorageProtocolLogging() {
        FileConfiguration legacyConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(Backuper.getInstance().getResource("config.yml")));
        legacyConfig.set("configVersion", 13.0);
        legacyConfig.set("storages.local.debug.protocolLogging", null);
        legacyConfig.set("storages.ftp.debug.protocolLogging", null);
        legacyConfig.set("storages.sftp.debug.protocolLogging", null);
        legacyConfig.set("storages.googleDrive.debug.protocolLogging", null);

        ConfigBackwardsCompatibility.configBelow14(legacyConfig);

        assert legacyConfig.getBoolean("storages.local.debug.protocolLogging");
        assert legacyConfig.getBoolean("storages.ftp.debug.protocolLogging");
        assert legacyConfig.getBoolean("storages.sftp.debug.protocolLogging");
        assert legacyConfig.getBoolean("storages.googleDrive.debug.protocolLogging");
    }

    @Test
    public void testWebDavConfigLoading() throws IOException {
        config.set("storages.webdav.enabled", true);
        config.set("storages.webdav.autoBackup", false);
        config.set("storages.webdav.auth.url", "http://127.0.0.1:9/dav/");
        config.set("storages.webdav.auth.allowInsecureHttp", true);
        config.set("storages.webdav.http.requestTimeoutSeconds", 120);
        config.set("storages.webdav.http.deleteConfirmationTimeoutSeconds", 45);
        config.set("storages.webdav.http.bufferUploadsToDisk", true);
        config.set("storages.webdav.maxBackupsWeight", Long.MAX_VALUE);
        config.set("storages.webdav.debug.protocolLogging", false);

        reload();

        WebDavStorage storage = (WebDavStorage) Backuper.getInstance().getStorageManager().getStorage("webdav");
        assert storage != null;
        assert storage.getType() == StorageType.WEBDAV;
        assert storage.getConfig().getRequestTimeoutSeconds() == 120;
        assert storage.getConfig().getDeleteConfirmationTimeoutSeconds() == 45;
        assert storage.getConfig().isBufferUploadsToDisk();
        assert storage.getConfig().getBackupsWeight() == Long.MAX_VALUE;
    }

    @Test
    public void testWebDavInvalidTimeoutValuesUseDefaults() throws IOException {
        config.set("storages.webdav.enabled", true);
        config.set("storages.webdav.auth.url", "http://127.0.0.1:9/dav/");
        config.set("storages.webdav.auth.allowInsecureHttp", true);
        config.set("storages.webdav.backupsFolder", 123);
        config.set("storages.webdav.autoBackup", "invalid");
        config.set("storages.webdav.maxBackupsNumber", "invalid");
        config.set("storages.webdav.maxBackupsWeight", 1.5);
        config.set("storages.webdav.zipArchive", "invalid");
        config.set("storages.webdav.zipCompressionLevel", "invalid");
        config.set("storages.webdav.http.requestTimeoutSeconds", "invalid");
        config.set("storages.webdav.http.deleteConfirmationTimeoutSeconds", 1.5);
        config.set("storages.webdav.http.bufferUploadsToDisk", "invalid");
        config.set("storages.webdav.debug.protocolLogging", "invalid");

        reload();

        WebDavStorage storage = (WebDavStorage) Backuper.getInstance().getStorageManager().getStorage("webdav");
        assert storage != null;
        assert storage.getConfig().getBackupsFolder().equals("./");
        assert storage.getConfig().isAutoBackup();
        assert storage.getConfig().getBackupsNumber() == 0;
        assert storage.getConfig().getBackupsWeight() == 0;
        assert storage.getConfig().isZipArchive();
        assert storage.getConfig().getZipCompressionLevel() == 5;
        assert storage.getConfig().getRequestTimeoutSeconds() == 3600;
        assert storage.getConfig().getDeleteConfirmationTimeoutSeconds() == 60;
        assert !storage.getConfig().isBufferUploadsToDisk();
        assert storage.getConfig().isProtocolLogging();
    }

    @Test
    public void testConfigBackwardsCompatibilityWithFixedBackupTime() throws IOException {
        // Below 4
        config.set("configVersion", 3.0);
        config.set("fixedBackupTime", true);
        config.set("firstBackupTime", 10);
        reload();

        assert config.get("backup.autoBackupCron").equals("0 0 10 1/1 * ? *");
    }
}
