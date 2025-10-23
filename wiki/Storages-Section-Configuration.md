## Storage Types

- `local` - Local file system storage
- `ftp` - FTP server storage
- `sftp` - SFTP server storage
- `googleDrive` - Google Drive cloud storage

## Storage IDs

Storage IDs are customizable and defined by you in config.yml. You can use any ID you want instead of default names.

**Important:**
- Storage IDs must be unique across all storages
- Storage IDs cannot contain spaces
- Do not use `backuper` as Storage ID - it's reserved for Backuper's internal server backup operations

### Multiple Storages

```yaml
storages:
  myLocalBackup:        # <- Storage ID
    type: local
    # ... other settings
    
  productionFtp:         # <- Storage ID
    type: ftp
    # ... other settings
    
  backupServerSftp:     # <- Storage ID
    type: sftp
    # ... other settings
    
  companyDrive:          # <- Storage ID
    type: googleDrive
    # ... other settings
```

### Multiple Storages of the Same Type

You can create multiple storages of the same type:

```yaml
storages:
  localMain:             # <- Storage ID
    type: local
    backupsFolder: ./backups/main
    # ... other settings
    
  localEmergency:        # <- Storage ID
    type: local
    backupsFolder: ./backups/emergency
    # ... other settings
```

## Storage Configuration Guides

- [[Local Storage|Local-Storage]]
- [[FTP Storage|FTP-Storage]]
- [[SFTP Storage|SFTP-Storage]]
- [[Google Drive Storage|Google-Drive-Storage]]
