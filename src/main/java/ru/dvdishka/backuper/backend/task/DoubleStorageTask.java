package ru.dvdishka.backuper.backend.task;

import ru.dvdishka.backuper.backend.storage.Storage;

public interface DoubleStorageTask {

    Storage getSourceStorage();

    Storage getTargetStorage();
}
