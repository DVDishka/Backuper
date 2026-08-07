package ru.dvdishka.backuper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.IOException;
import java.util.UUID;

@EnabledIfEnvironmentVariable(named = "storages.webdav.backupsFolder", matches = ".*\\S.*")
@EnabledIfEnvironmentVariable(named = "storages.webdav.auth.url", matches = ".*\\S.*")
public class WebDavBackupIT extends BaseBackupIT {

    private String testRunFolder;

    @BeforeEach
    public void setUp() throws IOException {
        String backupsFolder = System.getenv("storages.webdav.backupsFolder");
        String url = System.getenv("storages.webdav.auth.url");

        config.set("storages.webdav.enabled", true);
        config.set("storages.webdav.type", "webdav");
        config.set("storages.webdav.backupsFolder", backupsFolder);

        config.set("storages.webdav.auth.url", url);
        setFromEnvironmentIfPresent("storages.webdav.auth.username");
        setFromEnvironmentIfPresent("storages.webdav.auth.password");
        setBooleanFromEnvironmentIfPresent("storages.webdav.auth.allowInsecureHttp");
        setBooleanFromEnvironmentIfPresent("storages.webdav.http.bufferUploadsToDisk");

        reload();

        storage = Backuper.getInstance().getStorageManager().getStorage("webdav");
        String testRunFolderName = "backuper-it-%s".formatted(UUID.randomUUID());
        storage.createDir(testRunFolderName, backupsFolder);
        testRunFolder = storage.resolve(backupsFolder, testRunFolderName);

        config.set("storages.webdav.backupsFolder", testRunFolder);
        reload();
        storage = Backuper.getInstance().getStorageManager().getStorage("webdav");
    }

    @AfterEach
    public void cleanUpTestRunFolder() {
        if (storage != null && testRunFolder != null && storage.exists(testRunFolder)) {
            storage.delete(testRunFolder);
        }
    }

    private void setFromEnvironmentIfPresent(String path) {
        String value = System.getenv(path);
        if (value != null) {
            config.set(path, value);
        }
    }

    private void setBooleanFromEnvironmentIfPresent(String path) {
        String value = System.getenv(path);
        if (value != null) {
            if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
                throw new IllegalArgumentException(
                        "Invalid boolean environment variable %s: expected true or false".formatted(path));
            }
            config.set(path, Boolean.parseBoolean(value));
        }
    }

    @Test
    @Override
    public void smallFolderTest() throws IOException, InterruptedException {
        super.smallFolderTest();
    }

    @Test
    @Override
    public void smallZipBackupTest() throws IOException, InterruptedException {
        super.smallZipBackupTest();
    }

    @Test
    @Override
    public void bigFolderBackupTest() throws IOException, InterruptedException {
        super.bigFolderBackupTest();
    }

    @Test
    @Override
    public void bigZipBackupTest() throws IOException, InterruptedException {
        super.bigZipBackupTest();
    }
}
