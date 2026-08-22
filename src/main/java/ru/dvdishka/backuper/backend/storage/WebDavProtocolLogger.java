package ru.dvdishka.backuper.backend.storage;

import java.io.Closeable;

class WebDavProtocolLogger implements Closeable {

    private final StorageProtocolLogger logger;

    WebDavProtocolLogger(String storageId) {
        this.logger = new StorageProtocolLogger(storageId);
    }

    void logRequest(String method, String uri) {
        logger.log("http", "SEND", "%s %s".formatted(method, uri));
    }

    void logResponse(String method, String uri, int statusCode) {
        logger.log("http", "REPLY", "%s %s -> HTTP %d".formatted(method, uri, statusCode));
    }

    @Override
    public void close() {
        logger.close();
    }
}
