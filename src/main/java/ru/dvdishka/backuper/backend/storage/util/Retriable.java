package ru.dvdishka.backuper.backend.storage.util;

import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;
import ru.dvdishka.backuper.backend.storage.exception.StorageLimitException;
import ru.dvdishka.backuper.backend.storage.exception.StorageMethodException;
import ru.dvdishka.backuper.backend.storage.exception.StorageQuotaExceededException;

@FunctionalInterface
public interface Retriable<T> {

    int DEFAULT_RETRIES = 5;
    int DEFAULT_RETRY_DELAY_MILLIS = 3000;

    /***
     * Define the main logic to be retried here
     */
    T run() throws Exception;

    /***
     * Doesn't handle Storage exceptions
     * @throws StorageMethodException Something went wrong while executing some operation even after retries
     * @throws StorageConnectionException Failed to connect to storage even after retries
     * @throws StorageLimitException Storage limit exceeded
     * @throws StorageQuotaExceededException Storage quota exceeded even after retries
     */
    default T retry(RetriableExceptionHandler exceptionHandler, int retries, int retryDelayMillis) throws StorageMethodException, StorageConnectionException, StorageLimitException, StorageQuotaExceededException {
        int completedRetries = 0;

        while (completedRetries < retries) {
            try {
                return run();
            } catch (Exception e) {
                completedRetries++;

                if (completedRetries == retries) {
                    switch (e) {
                        case StorageConnectionException storageConnectionException -> throw storageConnectionException;
                        case StorageLimitException storageLimitException -> throw storageLimitException;
                        case StorageQuotaExceededException storageQuotaExceededException -> throw storageQuotaExceededException;
                        case StorageMethodException storageMethodException -> throw storageMethodException;
                        default -> throw exceptionHandler.handleFinalException(e);
                    }
                } else {
                    Backuper.getInstance().getLogManager().devWarn("Operation failed, retrying in " + (retryDelayMillis / 1000) + " seconds... (" + completedRetries + "/" + retries + ")");
                    Backuper.getInstance().getLogManager().devWarn(e);
                    if (!(e instanceof StorageLimitException || e instanceof StorageQuotaExceededException || e instanceof StorageConnectionException || e instanceof StorageMethodException)) {
                        exceptionHandler.handleRegularException(e);
                    }
                }

                try {
                    Thread.sleep(retryDelayMillis);
                } catch (InterruptedException ignored) {}
            }
        }

        // Unreachable code
        throw new RuntimeException("Unexpected error in Retriable logic");
    }

    /***
     * Doesn't handle Storage exceptions
     * @throws StorageMethodException Something went wrong while executing some operation even after retries
     * @throws StorageConnectionException Failed to connect to storage even after retries
     * @throws StorageLimitException Storage limit exceeded
     * @throws StorageQuotaExceededException Storage quota exceeded even after retries
     */
    default T retry(RetriableExceptionHandler exceptionHandler) throws StorageMethodException, StorageConnectionException, StorageLimitException, StorageQuotaExceededException {
        return retry(exceptionHandler, DEFAULT_RETRIES, DEFAULT_RETRY_DELAY_MILLIS);
    }

    interface RetriableExceptionHandler {

        /***
         * Handle exceptions but don't throw anything. It isn't the final exception, there will be some retries
         */
        void handleRegularException(Exception e);

        /***
         * Handle exceptions and return one that'll be thrown. It is the final exception, there will be no more retries
         */
        RuntimeException handleFinalException(Exception e);
    }
}