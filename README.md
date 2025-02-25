<div align='center'>

# Backuper

<img height="128" src="https://raw.githubusercontent.com/DVDishka/Backuper/refs/heads/master/images/backuper_logo.svg" width="128" alt=""/>

## Simple backup plugin for Paper/Folia with **FTP/GOOGLE DRIVE/SFTP** support

---

[![bStats Graph Data](https://bstats.org/signatures/bukkit/Backuper.svg)](https://bstats.org/plugin/bukkit/Backuper)

</div>

## Pages

* [<img width="20px" src="https://raw.githubusercontent.com/DVDishka/Backuper/refs/heads/master/images/backuper_logo.svg"></img>](https://backuper-mc.com)[ Backuper](https://backuper-mc.com)
* [<img width="20px" src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"></img>](https://github.com/DVDishka/Backuper)[ GitHub](https://github.com/DVDishka/Backuper)
* [<img width="20px" src="https://i.imgur.com/o104U27.png"></img>](https://modrinth.com/plugin/backuper)[ Modrinth](https://modrinth.com/plugin/backuper)
* [<img width="20px" src="https://i.imgur.com/QJnHi37.png"></img>](https://hangar.papermc.io/Collagen/Backuper)[ Hangar](https://hangar.papermc.io/Collagen/Backuper)

---

## Version numbers changing

### Backuper-X.Y.Z(A)

* **X** - Changing **X** number means some breaking changes without backward compatibility. This means that before switching to this version you should check the `Incompatible version changes` paragraph
* **Y** - Changing **Y** number means that the version contains significant changes with backward compatibility
* **Z** - Changing **Z** number means that the version contains minor changes or improvements with backward compatibility
* **A** - Changing **A** letter means that the version contains bugfixes or security/stability improvements. Usually these are hotfixes

---

## Incompatible version changes

### 1.x.x - 2.x.x

* Fully changed permission system. Check the `Permissions` section to see the changes
* Fully changed command naming. Check the `Commands` section to see changes

### 2.x.x - 3.x.x

* Changed permission system. Now there are different permissions for different storages
* You need to specify the storage when using the `/backuper backup` command (`/backuper backup <storage>`)

---

## Basic setup

#### Use plugins/Backuper/config.yml to configure plugin

1. Automatic backups are enabled by default. To change backup interval change `backup.backupPeriod` option. To disable automatic backups change `backup.autoBackup` option
2. If you want to make your backups one time a day at specific time change `backup.backupTime` option
3. If you want to restart your server after automatic backup use `backup.afterBackup` option
4. To set the maximum number of backups to store change `local.maxBackupsNumber`/`ftp.maxBackupsNumber`/`sftp.maxBackupsNumber`/`googleDrive.maxBackupsNumber`
5. To set the maximum weight of backups to store change `local.maxBackupsWeight`/`ftp.maxBackupsWeight`/`sftp.maxBackupsWeight`/`googleDrive.maxBackupsWeight`

**To set up FTP/GOOGLE DRIVE/SFTP storage check the `Configuration` section below**

**To configure the plugin, it is best to look at the full `Configuration` section, there are many useful options there**

---

## Google Drive setup

1. Enable Google Drive storage and customize settings in **config.yml**. (Check `Configuration/GOOGLE DRIVE storage settings` section)
2. Link your Google Account to the Backuper using `/backuper account googleDrive link` command

---

## Configuration

### Backup settings

* `Auto backup` - **(true/false)** - Enables automatic backups once at a specified interval **(when disabled, backups will only run on the `/backuper backup` command)**



* `Backup File Name Format` - **(Date Time format)** - Set a format for backup file names **(see java.time docs)**. It must contain information about both time and date



* `Add directory to backup` - **(List of paths)** - Full directory paths to folders/files that you want to be backed up. World folders will be backed up automatically, so you do not need to specify world folders there (For example you can specify "plugins", "config"). You can also specify all files with "*".
* `Exclude Directory From Backup` - **(List of paths)** - Full directory paths to folders/files that you want to be excluded from backup. If you want to backup everything from the **folder1** except some **folder1/file1** you can specify **folder1** in `addDirectoryToBackup` and **folder1/file1** in `excludeDirectoryFromBackup`. (The `backupsFolder` directory will be excluded automatically to prevent the loop)



* `Backup period` - **(>= 1 Minutes or -1)** - The period after which the server will make automatic backups **(To change this value you need to set `backupTime` to -1 and `autoBackup` to true)**
* `backup time` - **(0 - 23 or -1)** - Fixed backup time. Automatic backups will be made at this time every day. (`backupPeriod` will be automatically set to 24 hours). **-1 to disable backup time fixation**



* `Delete broken backups` - **(true/false)** - Sometimes errors may occur while creating a backup. When this option is enabled, such backups will be deleted
* `Skip duplicate backup` - **(true/false)** - Backup will only occur if the world has been changed since the last backup. If the world has not been changed, this backup cycle will be skipped. `AfterBackup` **will be executed anyway**
* `After backup` - **(NOTHING/STOP/RESTART)** - What will the server do after an **automatic** backup



* `Set worlds read only` - **(true/false)** - **(True recommended)** The backuper will mark all world folders as **Read Only** to prevent folder changing that may cause the backup crash. **True** value may cause **access denied** errors during the backup **(you should just ignore that)**

### Local storage settings

* `Enabled` - **(true/false)** - Enable local storage to use it via the Backuper
* `Auto Backup` - **(true/false)** - With automatic backup, backups will be saved to specified local storage. Works only if `local.enabled: true` and `backup.autoBackup: true`



* `Backups folder` - **(Path)** - Full directory where backups will be stored



* `Max backups number` - **(>= 1 or 0)** - Maximum number of backups to be kept in local storage **(0 - unlimited)**
* `Max backup weight` - **(>= 1 or 0)** - Maximum weight of backups that will be stored in local storage **(MB)** **(0 - unlimited)**



* `Zip archive` - **(true/false)** - Do you want to store backups in ZIP archives in local storage?
* `Zip compression level` - **(0 - 9)** - archive compression level. A higher value may reduce file size, but may also increase the time required to archive and decompress

### GOOGLE DRIVE storage settings

* `Enabled` - **(true/false)** - Enable GOOGLE DRIVE storage to use it via the Backuper
* `Auto Backup` - **(true/false)** - With automatic backup, backups will be saved to specified local storage. Works only if `googleDrive.enabled: true` and `backup.autoBackup: true`



* `Backups folder ID` - **(ID)** - GOOGLE DRIVE folder ID where backups will be stored
* `Create Backuper Folder` - **(true/false)** - Do you want the Backuper to create its own folder in specified in `backupsFolderId` directory to store backups there



* `Max backups number` - **(>= 1 or 0)** - Maximum number of backups to be kept in GOOGLE DRIVE storage **(0 - unlimited)**
* `Max backup weight` - **(>= 1 or 0)** - Maximum weight of backups that will be stored in GOOGLE DRIVE storage **(MB)** **(0 - unlimited)**



#### Authentication

* `Token Folder Path` - **(Path)** - Directory where you want to store your Google authentication tokens



### FTP storage settings

* `Enabled` - **(true/false)** - Enable FTP storage to use it via the Backuper
* `Auto Backup` - **(true/false)** - With automatic backup, backups will be saved to specified FTP server. Works only if `ftp.enabled: true` and `backup.autoBackup: true`



* `Backups folder` - **(Path)** - FTP server directory where backups will be stored
* `Path separator symbol` - **(Symbol)** - Path separator symbol used on FTP SERVER. For example `/` on UNIX systems and `\` on windows



* `Max backups number` - **(>= 1 or 0)** - Maximum number of backups to be kept on FTP server **(0 - unlimited)**
* `Max backup weight` - **(>= 1 or 0)** - Maximum weight of backups that will be stored on FTP server **(MB)** **(0 - unlimited)**



* `ZIP archive` - **(true/false)** - Do you want to store backups in ZIP archives on FTP server?
* `ZIP compression level` - **(0 - 9)** - archive compression level. A higher value may reduce file size, but may also increase the time required to archive and decompress



#### Authentication

* `Address` - **(Address)** - FTP server address
* `Port` - **(Port)** - FTP server port
* `Username` - **(Username)** - FTP server username to use for authentication
* `Password` - **(Password)** - FTP server password to use fot authentication



### SFTP storage settings

* `Enabled` - **(true/false)** - Enable SFTP storage to use it via the Backuper
* `Auto Backup` - **(true/false)** - With automatic backup, backups will be saved to specified SFTP server. Works only if `sftp.enabled: true` and `backup.autoBackup: true`



* `Backups folder` - **(Path)** - SFTP server directory where backups will be stored
* `Path separator symbol` - **(Symbol)** - Path separator symbol used on SFTP SERVER. For example `/` on UNIX systems and `\` on windows



* `Max backups number` - **(>= 1 or 0)** - Maximum number of backups to be kept on SFTP server **(0 - unlimited)**
* `Max backup weight` - **(>= 1 or 0)** - Maximum weight of backups that will be stored on SFTP server **(MB)** **(0 - unlimited)**



#### Authentication

* `Address` - **(Address)** - SFTP server address
* `Port` - **(Port)** - SFTP server port
* `Auth type` - **(password/key)** - SFTP server authentication type
* `Username` - **(Username)** - SFTP server username to use for authentication
* `Password` - **(Password)** - SFTP server password to use fot authentication
* `Key file path` - **(Path)** - Local path to key file if `authType: key`
* `Use known hosts file` - **(true/false)** - Do you want to specify local knownHostsFile?
* `Known hosts file path` - **(Path)** - Path to local knownHostsFile if `useKnownHostsFile: true`



### Server settings

* `Alert time before restart` - **(> 0 or -1)** - A notification about the server restart will be sent to all players on the server `alertTimeBeforeRestart` seconds before the restart. -1 to disable notifications
* `Alert only server restart` - **(true/false)** - Notifications will be sent only if the server will be restarted or stopped after the backup



* `Check updates` - **(true/false)** - Check for new versions of the Backuper to stay up to date



* `Better logging` - **(true/false)** - Better logging (Some statistic and other information for debugging, you probably don't need it)

---

## Commands

#### The `storage` argument is responsible for the storages such as **local**, **GOOGLE DRIVE**, **FTP**, **SFTP**. To use these arguments storages must be enabled. The separator sign to use multiple storages at one command is `-`. **(Example: `local`, `ftp-googleDrive`, `local-ftp-sftp`)**

#### The `service` argument is responsible for the storage provider services such as **GOOGLE DRIVE**. To use these arguments corresponding storages must be enabled

* `/backuper backup <storage> <stopRestartServer>` - Command to backup the server manually. The `stopRestartServer` argument means what the server will do after restart. The `stopRestartServer` argument can be `stop` or `restart`, also you can use it **without an argument**
* `/backuper backup <storage> <delay> <stopRestartServer>` - Command to backup the server manually with a delay **(delay in seconds > 0)**. `stopRestartServer` and `storage` arguments are the same as in command above and it is also **optional**



* `/backuper list <storage>` - Command to view the list of backups **(click on a backup to open its menu)**
* `/backuper menu <storage> <backupName>` - Command to open the menu of the specified backup
* `/backuper menu <storage> <backupName> delete` - Command to delete the specified backup
* `/backuper menu <storage> <backupName> toZIP` - **only for LOCAL storage** - Command to convert the specified backup to a ZIP archive
* `/backuper menu <storage> <backupName> unZIP` - **only for LOCAL storage** - Command to convert the specified backup from a ZIP archive to a folder
* `/backuper menu <storage> <backupName> copyToFtp` - **only for LOCAL storage** - Command to copy the specified backup to **FTP** storage from **local** storage
* `/backuper menu <storage> <backupName> copyToSftp` - **only for LOCAL storage** - Command to copy the specified backup to **SFTP** storage from **local** storage
* `/backuper menu <storage> <backupName> copyToGoogleDrive` - **only for LOCAL storage** - Command to copy the specified backup to **GOOGLE DRIVE** storage from **local** storage
* `/backuper menu <storage> <backupName> copyToLocal` - **only for FTP/GOOGLE DRIVE/SFTP storage** - Command to copy the specified backup to **LOCAL** storage from **FTP/GOOGLE DRIVE/SFTP** storage



* `/backuper account <service> link` - Command to link the `service` account to use this storage provider via the Backuper



* `/backuper config reload` - Command to reload the config file



* `/backuper task status` - Command to get the current progress of a task
* `/backuper task cancel` - Command to cancel the current task

---

## Permissions

### Basic permissions

* `backuper.backup` - Permission to use `/backuper backup <storage>` command without the `stopRestartServer` argument
* `backuper.backup.stop` - Permission to use `/backuper backup <storage>` command with the **STOP** argument (`backuper.backup` permission required)
* `backuper.backup.restart` - Permission to use `/backuper backup <storage>` command with the **RESTART** argument (`backuper.backup` permission required)



### Local storage permissions

* `backuper.local.list` - Permission to use `/backuper list local` and `/backuper menu local` commands
* `backuper.local.list.tozip` - Permission to convert backups to a ZIP archive in local storage (`backuper.local.list` permission required)
* `backuper.local.list.unzip` - Permission to convert backups from a ZIP archive to a folder in local storage (`backuper.local.list` permission required)
* `backuper.local.list.delete` - Permission to delete backups in local storage (`backuper.local.list` permission required)
* `backuper.local.list.copytoftp` - Permission to copy backups from local to FTP storage (`backuper.local.list` permission required)
* `backuper.local.list.copytosftp` - Permission to copy backups from local to SFTP storage (`backuper.local.list` permission required)



### GOOGLE DRIVE storage permissions

* `backuper.googledrive.account` - Permission to manage linked Google account (use link command)



* `backuper.googledrive.list` - Permission to use `/backuper list googleDrive` and `/backuper menu googleDrive` commands
* `backuper.googledrive.list.delete` - Permission to delete backups in GOOGLE DRIVE storage (`backuper.googleDrive.list` permission required)
* `backuper.googledrive.list.copytolocal` - Permission to copy backups from GOOGLE DRIVE to local storage (`backuper.googleDrive.list` permission required)



### SFTP storage permissions

* `backuper.sftp.list` - Permission to use `/backuper list sftp` and `/backuper menu sftp` commands
* `backuper.sftp.list.delete` - Permission to delete backups in SFTP storage (`backuper.sftp.list` permission required)
* `backuper.sftp.list.copytolocal` - Permission to copy backups from SFTP to local storage (`backuper.sftp.list` permission required)



### FTP storage permissions

* `backuper.ftp.list` - Permission to use `/backuper list ftp` and `/backuper menu ftp` commands
* `backuper.ftp.list.delete` - Permission to delete backups in FTP storage (`backuper.ftp.list` permission required)
* `backuper.ftp.list.copytolocal` - Permission to copy backups from FTP to local storage (`backuper.ftp.list` permission required)



### Other permissions

* `backuper.config.reload` - Permission to use `/backuper config reload` command (`backuper.config` permission required)
* `backuper.status` - Permission to use `/backuper status` command

---

## Notes

* **Please report any issues to** [GitHub](https://github.com/DVDishka/Backuper/issues)
* RESTART option may not work well, so it's better to use STOP with a loop in your start script ([start script](https://flags.sh/) auto restart ON)
* You can reset the backup time if it is broken **and your `backup.backupTime` is set to -1** by changing `lastBackup` to 0. Then the next backup will happen at the server start and the next ones will happen after `backup.backupPeriod`
