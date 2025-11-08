package ru.dvdishka.backuper.backend.storage;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import ru.dvdishka.backuper.backend.storage.exception.StorageConnectionException;

public class GoogleDriveClientProvider {

    private final GoogleDriveStorage storage;

    private Drive driveService = null;

    private final String APPLICATION_NAME = "BACKUPER";
    private final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private final NetHttpTransport NET_HTTP_TRANSPORT = new NetHttpTransport();

    GoogleDriveClientProvider(GoogleDriveStorage storage) {
        this.storage = storage;
    }

    synchronized Drive getClient() throws StorageConnectionException {
        if (driveService != null) {
            try {
                // Test if the connection is still alive by making a simple request
                driveService.files().get("").setFields("name").execute().getName();
                return driveService;
            } catch (Exception ignored) {
                // Connection is dead, we'll create a new one
                driveService = null;
            }
        }

        try {
            Credential credential = storage.returnCredentialIfAuthorized();
            if (credential == null) {
                throw new StorageConnectionException(storage, "Not authorized in Google Drive!");
            }

            driveService = new Drive.Builder(NET_HTTP_TRANSPORT, JSON_FACTORY, credential)
                    .setApplicationName(APPLICATION_NAME)
                    .setHttpRequestInitializer(httpRequest -> {
                        credential.initialize(httpRequest);
                        httpRequest.setConnectTimeout(300 * 60000);
                        httpRequest.setReadTimeout(300 * 60000);
                    })
                    .build();

            return driveService;
        } catch (Exception e) {
            throw new StorageConnectionException(storage, "Not authorized in Google Drive!", e);
        }
    }

    void disconnect() {
        driveService = null;
    }
}
