package ru.dvdishka.backuper.backend.utils;

import com.google.api.client.auth.oauth2.AuthorizationCodeFlow;
import com.google.api.client.auth.oauth2.AuthorizationCodeRequestUrl;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.VerificationCodeReceiver;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpRequestInitializer;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Preconditions;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.common.Logger;
import ru.dvdishka.backuper.backend.config.Config;
import ru.dvdishka.backuper.backend.exceptions.NotAuthorizedException;

import java.io.*;
import java.util.List;
import java.util.Objects;

public class GoogleDriveUtils {

    private static File credentialsFile;
    private static File tokensFolder;

    private static final String APPLICATION_NAME = "BACKUPER";
    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final List<String> DRIVE_SCOPES = List.of(DriveScopes.DRIVE_FILE);
    private static final NetHttpTransport NET_HTTP_TRANSPORT = new NetHttpTransport();
    private static final AuthorizationCodeInstalledApp.Browser BROWSER = new AuthorizationCodeInstalledApp.DefaultBrowser();

    private static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

    public static void init() {
        credentialsFile = Config.getInstance().getGoogleDriveConfig().getCredentialsFile();
        tokensFolder = Config.getInstance().getGoogleDriveConfig().getTokensFolder();
    }

    public static Credential returnCredentialIfAuthorized(CommandSender sender) {

        try {
            GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new FileReader(credentialsFile));

            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    NET_HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, DRIVE_SCOPES)
                    .setDataStoreFactory(new FileDataStoreFactory(tokensFolder))
                    .setAccessType("offline")
                    .build();

            Credential credential = flow.loadCredential("user");

            if (credential != null
                    && (credential.getRefreshToken() != null
                    || credential.getExpiresInSeconds() == null
                    || credential.getExpiresInSeconds() > 60)) {
                return credential;
            }
            return null;

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to authorize user in Google Drive");
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static boolean isAuthorized(CommandSender sender) {
        return returnCredentialIfAuthorized(sender) != null;
    }

    public static Credential authorizeForced(CommandSender sender) throws IOException {

        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new FileReader(credentialsFile));

        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                NET_HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, DRIVE_SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(tokensFolder))
                .setAccessType("offline")
                .build();
        // PORT MUST BE UNLOCKED
        LocalServerReceiver receiver = new LocalServerReceiver.Builder().setPort(8888).build();
        Credential credential = new MyAuthorizationCodeInstalledApp(flow, receiver).authorizeForced("user", sender);

        return credential;
    }

    public static Drive getService(CommandSender sender) throws NotAuthorizedException {

        Credential credential = returnCredentialIfAuthorized(sender);
        if (credential == null) {
            throw new NotAuthorizedException("googleDrive");
        }

        return new Drive.Builder(NET_HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .setHttpRequestInitializer(new HttpRequestInitializer() {
                    @Override
                    public void initialize(HttpRequest httpRequest) throws IOException {
                        credential.initialize(httpRequest);
                        httpRequest.setConnectTimeout(300 * 60000);
                        httpRequest.setReadTimeout(300 * 60000);
                    }
                })
                .build();
    }

    public static String uploadFile(File file, String parentFolderId, CommandSender sender) {

        if (!file.exists()) {
            Logger.getLogger().warn("File does not exist: " + file.getAbsolutePath(), sender);
            return null;
        }

        try {
            Drive service = getService(sender);

            com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
            driveFileMeta.setName(file.getName());
            if (!Objects.equals(parentFolderId, "")) {
                driveFileMeta.setParents(List.of(parentFolderId));
            }
            FileContent driveFileContent = new FileContent("", file);

            com.google.api.services.drive.model.File driveFile = service.files()
                    .create(driveFileMeta, driveFileContent)
                    .setFields("id, parents")
                    .execute();
            return driveFile.getId();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to upload file to Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static String createFolder(String folderName, String parentFolderId, CommandSender sender) {

        try {
            Drive service = getService(sender);

            com.google.api.services.drive.model.File driveFileMeta = new com.google.api.services.drive.model.File();
            driveFileMeta.setName(folderName);
            driveFileMeta.setParents(List.of(parentFolderId));
            driveFileMeta.setMimeType(FOLDER_MIME_TYPE);

            return service.files().create(driveFileMeta).setFields("id, parents").execute().getId();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to create folder in Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static InputStream downloadFileStream(String fileId, CommandSender sender) {

        try {
            Drive service = getService(sender);

            return service.files().get(fileId).executeMediaAsInputStream();

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to download file from Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
            return null;
        }
    }

    public static void downloadFile(String fileId, File targetFile, CommandSender sender) {

        try (InputStream inputStream = downloadFileStream(fileId, sender); OutputStream outputStream = new FileOutputStream(targetFile)) {
            assert inputStream != null;

            byte[] buffer = new byte[2048];
            int length;

            while ((length = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, length);
            }

        } catch (Exception e) {
            Logger.getLogger().warn("Failed to download file from Google Drive", sender);
            Logger.getLogger().warn(GoogleDriveUtils.class, e);
        }
    }

    public static void test(CommandSender sender) {

        Logger.getLogger().log("Google Drive: Test started", sender);

        Logger.getLogger().log("Google Drive: File uploading test started", sender);
        String id = uploadFile(new File("plugins/Backuper/29-06-2024 07-26-40.zip"), "", sender);
        Logger.getLogger().log("Google Drive: File uploaded", sender);

        Logger.getLogger().log("Google Drive: File downloading test started", sender);
        downloadFile(id, new File("plugins/Backuper/downloaded-29-06-2024 07-26-40.zip"), sender);
        Logger.getLogger().log("Google Drive: File downloaded", sender);

        Logger.getLogger().log("Google Drive: Test finished", sender);
    }
}

class MyAuthorizationCodeInstalledApp extends AuthorizationCodeInstalledApp {

    public MyAuthorizationCodeInstalledApp(AuthorizationCodeFlow flow, VerificationCodeReceiver receiver) {
        super(flow, receiver);
    }

    protected void onAuthorization(AuthorizationCodeRequestUrl authorizationUrl, CommandSender sender) throws IOException {
        String url = authorizationUrl.build();
        Preconditions.checkNotNull(url);

        Component message = Component.empty()
                .append(Component.text("Login using this link:")
                        .color(TextColor.fromHexString("#129c9b")))
                .append(Component.text(url)
                        .clickEvent(ClickEvent.clickEvent(ClickEvent.Action.OPEN_URL, url))
                        .decorate(TextDecoration.UNDERLINED));

        sender.sendMessage(message);
    }

    public Credential authorizeForced(String userId, CommandSender sender) throws IOException {
        try {
            Credential credential = getFlow().loadCredential(userId);
            if (credential != null
                    && (credential.getRefreshToken() != null
                    || credential.getExpiresInSeconds() == null
                    || credential.getExpiresInSeconds() > 60)) {
                return credential;
            }

            String redirectUri = getReceiver().getRedirectUri();
            AuthorizationCodeRequestUrl authorizationUrl =
                    getFlow().newAuthorizationUrl().setRedirectUri(redirectUri);
            onAuthorization(authorizationUrl, sender);

            String code = getReceiver().waitForCode();
            TokenResponse response = getFlow().newTokenRequest(code).setRedirectUri(redirectUri).execute();

            return getFlow().createAndStoreCredential(response, userId);
        } finally {
            getReceiver().stop();
        }
    }

    public Credential authorize(String userId, CommandSender sender) throws IOException {
        try {
            String redirectUri = getReceiver().getRedirectUri();
            AuthorizationCodeRequestUrl authorizationUrl =
                    getFlow().newAuthorizationUrl().setRedirectUri(redirectUri);
            onAuthorization(authorizationUrl, sender);

            String code = getReceiver().waitForCode();
            TokenResponse response = getFlow().newTokenRequest(code).setRedirectUri(redirectUri).execute();

            return getFlow().createAndStoreCredential(response, userId);
        } finally {
            getReceiver().stop();
        }
    }
}
