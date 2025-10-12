# Google Drive Storage

## Default Configuration

```yaml
storages:
  yourStorageId:
    type: googleDrive
    enabled: true
    autoBackup: true
    
    backupsFolderId: ''
    createBackuperFolder: true
    
    maxBackupsNumber: 0
    maxBackupsWeight: 0
    
    zipArchive: true
    zipCompressionLevel: 5
    
    auth:
      tokenFolderPath: ./plugins/Backuper/GoogleDrive/tokens
```

## Configuration Options

### Basic Settings

```yaml
type: googleDrive            # Storage type - must be 'googleDrive' for Google Drive storage
enabled: true                # Enable/disable this storage (true/false)
autoBackup: true             # Include this storage in automatic backups (true/false)
```

- **type**: Defines the storage implementation. Must be `googleDrive` for Google Drive cloud storage.
- **enabled**: Controls whether this storage is active. Set to `false` to disable without removing configuration.
- **autoBackup**: When `true`, automatic backups will be saved to this storage. When `false`, only manual backups are allowed.

### Location

```yaml
backupsFolderId: ''          # Google Drive folder ID where backups will be stored
createBackuperFolder: true   # Whether to create a dedicated Backuper folder
```

- **backupsFolderId**: Google Drive folder ID where backups will be stored. Leave empty (`''`) to use the root directory of Google Drive. To use a specific folder, provide the folder ID (found in the Google Drive URL).
- **createBackuperFolder**: When `true`, Backuper will create its own subfolder within the specified folder (or root directory if `backupsFolderId` is empty) to organize backups. When `false`, backups are stored directly in the specified folder or root directory.

### Backup Limits

```yaml
maxBackupsNumber: 0          # Maximum number of backups to keep (0 = unlimited)
maxBackupsWeight: 0          # Maximum total size of all backups in MB (0 = unlimited)
```

- **maxBackupsNumber**: Limits the number of backup files stored on Google Drive. When exceeded, oldest backups are deleted automatically. Set to `0` for unlimited backups.
- **maxBackupsWeight**: Limits the total size of all backups in megabytes. When exceeded, oldest backups are deleted to free space. Set to `0` for unlimited size.

### Compression

```yaml
zipArchive: true             # Store backups as ZIP files (true/false)
zipCompressionLevel: 5       # ZIP compression level (0-9)
```

- **zipArchive**: When `true`, backups are stored as compressed ZIP files. When `false`, backups are stored as folder structures (slower upload and larger storage usage).
- **zipCompressionLevel**: Controls ZIP compression strength. For Google Drive, lower values are recommended for faster uploads:
  - `0` - No compression (fastest upload, largest files)
  - `1-3` - Low compression (fast upload, moderate file size) - **Recommended for Google Drive**
  - `4-6` - Balanced compression (moderate speed and size)
  - `7-9` - High compression (slower upload, smallest files)

### Authentication

```yaml
auth:
  tokenFolderPath: ./plugins/Backuper/GoogleDrive/tokens  # Directory for storing authentication tokens
```

- **tokenFolderPath**: Directory where Google Drive authentication tokens will be stored. These tokens are used to maintain access to your Google Drive account without repeated login prompts.

## Link Google Account

Run the following command in-game or console using your storage ID:
```
/backuper account yourStorageId link
```

This will:
1. Open a web browser with Google OAuth authentication
2. Ask you to sign in to your Google account
3. Request permission to access Google Drive
4. Save authentication tokens to the specified `tokenFolderPath`

## Required Permissions

- `backuper.yourStorageId` - Required for storage to appear in command suggestions
- `backuper.yourStorageId.account` - Link/unlink Google account (required for `/backuper account yourStorageId link` command and Google OAuth authentication)
- `backuper.yourStorageId.backup` - Create backups
- `backuper.yourStorageId.list` - View and manage backups
- `backuper.yourStorageId.list.delete` - Delete backups
- `backuper.yourStorageId.list.tozip` - Convert folder backups to ZIP archives
- `backuper.yourStorageId.list.unzip` - Extract ZIP backups to folder structures
