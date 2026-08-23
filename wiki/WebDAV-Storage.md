> **Important:** `WebDavStorage` supports only `Basic` authentication method.

## Default Configuration

```yaml
storages:
  yourStorageId:
    type: webdav
    enabled: true
    autoBackup: true

    backupsFolder: ./

    maxBackupsNumber: 0
    maxBackupsWeight: 0

    zipArchive: true
    zipCompressionLevel: 5

    auth:
      url: 'https://cloud.example.com/remote.php/dav/files/username/'
      username: 'username'
      password: 'app-password'
      allowInsecureHttp: false

    http:
      requestTimeoutSeconds: 3600
      deleteConfirmationTimeoutSeconds: 60
      bufferUploadsToDisk: false
```

## Configuration Options

### Basic Settings

```yaml
type: webdav
enabled: true
autoBackup: true
```

- **type**: Must be `webdav` for WebDAV storage.
- **enabled**: Controls whether this storage is active.
- **autoBackup**: When `true`, automatic backups are saved to this storage.

### Location

```yaml
backupsFolder: ./
```

- **backupsFolder**: Directory relative to `auth.url`. The directory must already exist. Backuper creates backup subdirectories and files inside it.
- `auth.url` is the WebDAV collection used as the storage root. It may include a path, for example a Nextcloud user's DAV endpoint.
- Paths are encoded per URI segment, so spaces and non-ASCII file names are supported.

### Authentication

```yaml
auth:
  url: 'https://cloud.example.com/remote.php/dav/files/username/'
  username: 'username'
  password: 'app-password'
  allowInsecureHttp: false
```

- **url**: Full HTTP(S) URL of the WebDAV root. Do not include credentials, query parameters, fragments, `.` or `..` path segments, or percent-encoded path separators.
- **username** and **password**: Credentials for HTTP Basic authentication. The username cannot contain `:`. Use an app password when the provider supports one. Leave `username` empty for anonymous WebDAV.
- **allowInsecureHttp**: Allows Basic credentials over unencrypted HTTP. This is disabled by default because Basic credentials can be recovered from intercepted traffic. Prefer HTTPS. Enable it only when another trusted transport, such as a private tunnel, protects the connection.

### HTTP Compatibility

```yaml
http:
  requestTimeoutSeconds: 3600
  deleteConfirmationTimeoutSeconds: 60
  bufferUploadsToDisk: false
```

- **requestTimeoutSeconds**: Timeout for each HTTP attempt. Increase it for large files or slow connections. Set to `0` to disable it. Backuper makes at most two attempts when a request times out.
- **deleteConfirmationTimeoutSeconds**: How long Backuper waits for deletion to finish after receiving `202 Accepted`. Set to `0` to accept the response without checking that deletion finished.
- **bufferUploadsToDisk**: Saves each upload to a temporary file and sends it with a fixed `Content-Length`. Enable this if the server rejects chunked uploads with `411 Length Required`, or if uploads need to be retryable. The temporary directory must have enough free space for the file being uploaded. Streaming uploads are not retried.
- Redirects must keep the same scheme, host, and port and remain under the path configured by `auth.url`. Credentials are never sent to another origin.

Backuper makes up to five attempts for temporary network failures and HTTP `408`, `425`, `429`, `500`, `502`, `503`, and `504` responses. It follows `Retry-After` delays of up to 60 seconds. A longer delay stops the operation. Authentication, certificate, TLS protocol, HTTP protocol, and storage limit errors are not retried.

If a buffered upload fails after the server may have saved the file, Backuper downloads the remote file and compares it with the temporary copy. A strong ETag is required before Backuper can retry `DELETE` or `MOVE`, which prevents it from modifying a resource that another client replaced.

### Backup Limits and Compression

```yaml
maxBackupsNumber: 0
maxBackupsWeight: 0
zipArchive: true
zipCompressionLevel: 5
```

- **maxBackupsNumber**: Maximum number of backups to keep. Set to `0` for unlimited backups.
- **maxBackupsWeight**: Maximum total backup size in megabytes. Set to `0` for unlimited size.
- **zipArchive**: Stores backups as ZIP files when enabled, or as directory trees when disabled.
- **zipCompressionLevel**: ZIP compression level from `0` to `9`.

## Required Permissions

- `backuper.yourStorageId` - View this storage and its backups
- `backuper.yourStorageId.backup` - Create backups
- `backuper.yourStorageId.list.delete` - Delete backups
- `backuper.yourStorageId.list.tozip` - Convert folder backups to ZIP archives
- `backuper.yourStorageId.list.unzip` - Extract ZIP backups to folder structures

## Provider Notes

- **Nextcloud**: A typical URL is `https://host/remote.php/dav/files/USERNAME/`. An app password is recommended.
- **ownCloud**: Use the DAV files endpoint shown by the account's WebDAV settings.
- **Reverse proxies**: Allow the WebDAV methods `PROPFIND`, `MKCOL`, `PUT`, `GET`, `DELETE`, and `MOVE`, and permit request bodies large enough for backups.
