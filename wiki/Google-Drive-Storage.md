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
type: googleDrive
enabled: true
autoBackup: true
```

- **type**: Defines the storage implementation. Must be `googleDrive` for Google Drive cloud storage.
- **enabled**: Controls whether this storage is active. Set to `false` to disable without removing configuration.
- **autoBackup**: When `true`, automatic backups will be saved to this storage. When `false`, only manual backups are allowed.

### Location

```yaml
backupsFolderId: ''
createBackuperFolder: true
```

- **backupsFolderId**: Google Drive folder ID where backups will be stored. Leave empty (`''`) to use the root directory of Google Drive. To use a specific folder, provide the folder ID. 
  
  **How to get folder ID:**
  1. Open Google Drive in your web browser
  2. Open the folder you want to use for backups
  3. Look at the URL in your browser's address bar
  4. The folder ID is the long string after `/folders/` in the URL
  
  Example URL: `https://drive.google.com/drive/folders/1a2B3c4D5e6F7g8H9i0J`
  
  Folder ID: `1a2B3c4D5e6F7g8H9i0J`
- **createBackuperFolder**: When `true`, Backuper will create its own subfolder within the specified folder (or root directory if `backupsFolderId` is empty) to organize backups. When `false`, backups are stored directly in the specified folder or root directory.

### Backup Limits

```yaml
maxBackupsNumber: 0
maxBackupsWeight: 0
```

- **maxBackupsNumber**: Limits the number of backup files stored on Google Drive. When exceeded, oldest backups are deleted automatically. Set to `0` for unlimited backups.
- **maxBackupsWeight**: Limits the total size of all backups in megabytes. When exceeded, oldest backups are deleted to free space. Set to `0` for unlimited size.

### Compression

```yaml
zipArchive: true
zipCompressionLevel: 5
```

- **zipArchive**: When `true`, backups are stored as compressed ZIP files. When `false`, backups are stored as folder structures.
- **zipCompressionLevel**: Controls ZIP compression strength. Higher values create smaller files but take more time:
  - `0` - No compression (fastest, largest files)
  - `1-3` - Low compression (fast, moderate file size)
  - `4-6` - Balanced compression (moderate speed and size)
  - `7-9` - High compression (slower, smallest files)

### Authentication

```yaml
auth:
  tokenFolderPath: ./plugins/Backuper/GoogleDrive/tokens
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

- `backuper.yourStorageId` - Required for storage to appear in command suggestions and to view backups
- `backuper.yourStorageId.account` - Link/unlink Google account (required for `/backuper account yourStorageId link` command and Google OAuth authentication)
- `backuper.yourStorageId.backup` - Create backups
- `backuper.yourStorageId.list.delete` - Delete backups
- `backuper.yourStorageId.list.tozip` - Convert folder backups to ZIP archives
- `backuper.yourStorageId.list.unzip` - Extract ZIP backups to folder structures
