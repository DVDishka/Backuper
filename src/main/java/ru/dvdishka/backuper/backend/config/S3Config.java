package ru.dvdishka.backuper.backend.config;

import lombok.Getter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.dvdishka.backuper.Backuper;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

@Getter
public class S3Config implements PathStorageConfig {

    private String id;

    private boolean enabled;
    private boolean autoBackup;
    private String bucket;
    private String region;
    private String endpoint;
    private boolean pathStyleAccess;
    private String backupsFolder;
    private int backupsNumber;
    private long backupsWeight;
    private boolean zipArchive;
    private int zipCompressionLevel;
    private String storageClass;
    private String accessKeyId;
    private String secretAccessKey;
    private String sessionToken;
    private int partSizeMb;
    private boolean bufferUploadsToDisk;
    private int requestTimeoutSeconds;
    private boolean protocolLogging;

    private ConfigurationSection config;

    @Override
    public S3Config load(ConfigurationSection config, String name) {
        this.config = config;
        this.id = name;
        this.enabled = getBoolean(config, "enabled", false);
        this.autoBackup = getBoolean(config, "autoBackup", true);
        this.bucket = getString(config, "bucket", "", true);
        this.region = getString(config, "region", "us-east-1", false);
        this.endpoint = getString(config, "endpoint", "", true);
        this.pathStyleAccess = getBoolean(config, "pathStyleAccess", false);
        this.backupsFolder = getString(config, "backupsFolder", "backups/", false);
        int backupsNumber = getInteger(config, "maxBackupsNumber", 0);
        long backupsWeightMb = getLong(config, "maxBackupsWeight", 0);
        this.zipArchive = getBoolean(config, "zipArchive", true);
        int zipCompressionLevel = getInteger(config, "zipCompressionLevel", 5);
        this.storageClass = getString(config, "storageClass", "", true);
        this.accessKeyId = getString(config, "auth.accessKeyId", "", true);
        this.secretAccessKey = getString(config, "auth.secretAccessKey", "", true);
        this.sessionToken = getString(config, "auth.sessionToken", "", true);
        int partSizeMb = getInteger(config, "transfer.partSizeMb", 8);
        this.bufferUploadsToDisk = getBoolean(config, "transfer.bufferUploadsToDisk", false);
        int requestTimeoutSeconds = getInteger(config, "transfer.requestTimeoutSeconds", 3600);
        this.protocolLogging = getBoolean(config, "debug.protocolLogging", true);

        if (backupsNumber < 0) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            Backuper.getInstance().getLogManager().warn("%s.maxBackupsNumber must be >= 0, using default 0 value...".formatted(config.getCurrentPath()));
            backupsNumber = 0;
        }
        this.backupsNumber = backupsNumber;

        if (backupsWeightMb < 0) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            Backuper.getInstance().getLogManager().warn("%s.maxBackupsWeight must be >= 0, using default 0 value...".formatted(config.getCurrentPath()));
            backupsWeightMb = 0;
        }
        if (backupsWeightMb > Long.MAX_VALUE / 1_048_576L) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            Backuper.getInstance().getLogManager().warn("%s.maxBackupsWeight is too large, using the maximum supported value...".formatted(config.getCurrentPath()));
            this.backupsWeight = Long.MAX_VALUE;
        } else {
            this.backupsWeight = backupsWeightMb * 1_048_576L;
        }

        if (partSizeMb < 5) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            Backuper.getInstance().getLogManager().warn("%s.transfer.partSizeMb must be >= 5 (S3 minimum part size), using 5 MB...".formatted(config.getCurrentPath()));
            partSizeMb = 5;
        }
        this.partSizeMb = partSizeMb;

        if (requestTimeoutSeconds < 0) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            Backuper.getInstance().getLogManager().warn("%s.transfer.requestTimeoutSeconds must be >= 0, using default 3600 value...".formatted(config.getCurrentPath()));
            requestTimeoutSeconds = 3600;
        }
        this.requestTimeoutSeconds = requestTimeoutSeconds;

        if (zipCompressionLevel < 0 || zipCompressionLevel > 9) {
            Backuper.getInstance().getLogManager().warn("Failed to load config value!");
            if (zipCompressionLevel < 0) {
                Backuper.getInstance().getLogManager().warn("%s.zipCompressionLevel must be >= 0, using 0 value...".formatted(config.getCurrentPath()));
                zipCompressionLevel = 0;
            } else {
                Backuper.getInstance().getLogManager().warn("%s.zipCompressionLevel must be <= 9, using 9 value...".formatted(config.getCurrentPath()));
                zipCompressionLevel = 9;
            }
        }
        this.zipCompressionLevel = zipCompressionLevel;

        return this;
    }

    private int getInteger(ConfigurationSection config, String path, int defaultValue) {
        Long value = parseLong(config.get(path));
        if (value != null && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            return value.intValue();
        }

        warnInvalidValue(config, path, "an integer", String.valueOf(defaultValue));
        return defaultValue;
    }

    private long getLong(ConfigurationSection config, String path, long defaultValue) {
        Long value = parseLong(config.get(path));
        if (value != null) {
            return value;
        }

        warnInvalidValue(config, path, "an integer", String.valueOf(defaultValue));
        return defaultValue;
    }

    private Long parseLong(Object configuredValue) {
        if (!(configuredValue instanceof Number number)) {
            return null;
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            return null;
        }
    }

    private boolean getBoolean(ConfigurationSection config, String path, boolean defaultValue) {
        Object configuredValue = config.get(path);
        if (configuredValue instanceof Boolean value) {
            return value;
        }

        warnInvalidValue(config, path, "true or false", String.valueOf(defaultValue));
        return defaultValue;
    }

    private String getString(ConfigurationSection config, String path, String defaultValue, boolean allowBlank) {
        Object configuredValue = config.get(path);
        if (configuredValue instanceof String value && (allowBlank || !value.isBlank())) {
            return value;
        }

        warnInvalidValue(config, path, allowBlank ? "a string" : "a nonblank string", defaultValue);
        return defaultValue;
    }

    private void warnInvalidValue(ConfigurationSection config, String path, String expectedValue, String defaultValue) {
        Backuper.getInstance().getLogManager().warn("Failed to load config value!");
        Backuper.getInstance().getLogManager().warn(
                "%s.%s must be %s, using default %s value..."
                        .formatted(config.getCurrentPath(), path, expectedValue, defaultValue));
    }

    @Override
    public String getPathSeparatorSymbol() {
        return "/";
    }

    @Override
    public ConfigurationSection getDefaultConfig() {
        try (InputStream resource = Backuper.getInstance().getResource("s3_config.yml")) {
            if (resource == null) {
                throw new IllegalStateException("Missing bundled resource: s3_config.yml");
            }
            return YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to close s3_config.yml", e);
        }
    }
}
