# SFTP Storage

## Default Configuration

```yaml
storages:
  yourStorageId:
    type: sftp
    enabled: true
    autoBackup: true
    
    backupsFolder: ./
    pathSeparatorSymbol: /
    
    maxBackupsNumber: 0
    maxBackupsWeight: 0
    
    zipArchive: true
    zipCompressionLevel: 5
    
    auth:
      address: ''
      port: 22
      authType: password
      username: ''
      password: ''
      keyFilePath: ''
      useKnownHostsFile: false
      knownHostsFilePath: ''
      sshConfigFilePath: ''
```

## Configuration Options

### Basic Settings

```yaml
type: sftp
enabled: true
autoBackup: true
```

- **type**: Defines the storage implementation. Must be `sftp` for SFTP (SSH File Transfer Protocol) server storage.
- **enabled**: Controls whether this storage is active. Set to `false` to disable without removing configuration.
- **autoBackup**: When `true`, automatic backups will be saved to this storage. When `false`, only manual backups are allowed.

### Location

```yaml
backupsFolder: ./
pathSeparatorSymbol: /
```

- **backupsFolder**: Directory path on the SFTP server where backup files will be stored. Can be relative to user's home directory or absolute path.
- **pathSeparatorSymbol**: Path separator character used by the SFTP server operating system:
  - `/` - Use for most SFTP servers (Linux/Unix)
  - `\` - Use only if SFTP server is Windows and requires it
  - When in doubt, use `/`

### Backup Limits

```yaml
maxBackupsNumber: 0
maxBackupsWeight: 0
```

- **maxBackupsNumber**: Limits the number of backup files stored on SFTP server. When exceeded, oldest backups are deleted automatically. Set to `0` for unlimited backups.
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
  address: ''
  port: 22
  authType: password
  username: ''
  password: ''
  keyFilePath: ''
  useKnownHostsFile: false
  knownHostsFilePath: ''
  sshConfigFilePath: ''
```

- **address**: Hostname or IP address of the SFTP server (e.g., `sftp.example.com` or `192.168.1.100`).
- **port**: Port number for SFTP connection. Standard SSH/SFTP uses port `22`.
- **authType**: Authentication method to use:
  - `password` - Use username and password authentication
  - `key` - Use SSH key-based authentication
- **username**: Username for SFTP server authentication.
- **password**: 
  - When `authType: password` - Password for server authentication
  - When `authType: key` - Passphrase for encrypted SSH key (leave empty if key has no passphrase)
- **keyFilePath**: Path to SSH private key file (only used when `authType: key`). Can be relative or absolute path.
- **useKnownHostsFile**: Whether to verify server identity using SSH known_hosts file. Set to `true` for enhanced security.
- **knownHostsFilePath**: Path to SSH known_hosts file (only used when `useKnownHostsFile: true`).
- **sshConfigFilePath**: Path to OpenSSH configuration file to use instead of Backuper's built-in SSH configuration. Allows using advanced OpenSSH settings and configurations.

## Required Permissions

- `backuper.yourStorageId` - Required for storage to appear in command suggestions
- `backuper.yourStorageId.backup` - Create backups
- `backuper.yourStorageId.list` - View and manage backups
- `backuper.yourStorageId.list.delete` - Delete backups
- `backuper.yourStorageId.list.tozip` - Convert folder backups to ZIP archives
- `backuper.yourStorageId.list.unzip` - Extract ZIP backups to folder structures
