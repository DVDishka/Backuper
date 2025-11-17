## Backup Commands

### Manual Backup
```
/backuper backup <storageId>
/backuper backup <storageId> <stop|restart>
/backuper backup <storageId> <delay> <stop|restart>
```
Create backup to specified storage. Optionally stop or restart server after backup with optional delay in seconds.

## Backup Management

### Backups List
```
/backuper list <storageId>
```
Display list of all backups in specified storage.

### Backup Menu
```
/backuper menu <storageId> <backup_name>
/backuper menu <storageId> <backup_name> <action>
```
Open interactive menu for backup management or execute specific action on backup.

**Actions:**
- `delete` - Delete backup
- `tozip` - Convert backup to ZIP archive
- `unzip` - Extract ZIP archive to folder structure
- `copyTo <storageId>` - Copy backup to another storage

## Account Management

### Link Accounts
```
/backuper account <storageId> link
```
Link cloud storage account for authentication. Required for Google Drive and other cloud storages.

## Reloading

### Reload Plugin
```
/backuper reload
```
Reload entire plugin including configuration and all components.

## Task Management

### Check Status
```
/backuper task status
```
Display current task status and progress information.

### Cancel Task
```
/backuper task cancel
```
Cancel currently running task.

## Required Permissions

See [[Permissions System|Permissions-System]] for detailed permission requirements.
