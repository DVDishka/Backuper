package ru.dvdishka.backuper.backend.storage;

import ru.dvdishka.backuper.backend.config.PathStorageConfig;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;

public interface PathStorage extends Storage {

    PathStorageConfig getConfig();

    @Override
    default String getFileNameFromPath(String path) throws StorageMethodException, StorageConnectionException {
        return path.substring(path.lastIndexOf(getConfig().getPathSeparatorSymbol()) + 1);
    }

    @Override
    default String getParentPath(String path) throws StorageMethodException, StorageConnectionException {
        return path.substring(0, path.lastIndexOf(getConfig().getPathSeparatorSymbol()) == -1 ? 0 : path.lastIndexOf(getConfig().getPathSeparatorSymbol()));
    }

    @Override
    default String resolve(String path, String fileName) {
        if (!path.endsWith(getConfig().getPathSeparatorSymbol())) {
            path = "%s%s".formatted(path, getConfig().getPathSeparatorSymbol());
        }
        return "%s%s".formatted(path, fileName);
    }
}
