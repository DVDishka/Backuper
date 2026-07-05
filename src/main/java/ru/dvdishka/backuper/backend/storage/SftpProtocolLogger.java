package ru.dvdishka.backuper.backend.storage;

import com.jcraft.jsch.Logger;

import java.io.Closeable;

class SftpProtocolLogger implements Closeable {

    private final StorageProtocolLogger logger;

    SftpProtocolLogger(String storageId) {
        this.logger = new StorageProtocolLogger(storageId);
    }

    void logLifecycle(String clientRole, String message) {
        logger.log(clientRole, "INFO", message);
    }

    void logOperation(String operation, String message) {
        logger.logOperation(operation, message);
    }

    Logger createJSchLogger(String clientRole) {
        return new Logger() {
            @Override
            public boolean isEnabled(int level) {
                return true;
            }

            @Override
            public void log(int level, String message) {
                logger.log(clientRole, "JSCH-%s".formatted(levelName(level)), message);
            }
        };
    }

    private String levelName(int level) {
        return switch (level) {
            case Logger.DEBUG -> "DEBUG";
            case Logger.INFO -> "INFO";
            case Logger.WARN -> "WARN";
            case Logger.ERROR -> "ERROR";
            case Logger.FATAL -> "FATAL";
            default -> String.valueOf(level);
        };
    }

    @Override
    public void close() {
        logger.close();
    }
}
