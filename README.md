<div align='center'>

# Backuper

<img height="128" src="images/backuper_logo.png" width="128" alt=""/>

## _Simple localBackup plugin for Paper/Folia_

---

[![bStats Graph Data](https://bstats.org/signatures/bukkit/Backuper.svg)](https://bstats.org/plugin/bukkit/Backuper)

</div align='center'>

## Pages

* [<img width="20px" src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"></img>](https://github.com/DVDishka/Backuper)[ GitHub](https://github.com/DVDishka/Backuper)
* [<img width="20px" src="https://i.imgur.com/o104U27.png"></img>](https://modrinth.com/plugin/backuper)[ Modrinth](https://modrinth.com/plugin/backuper)
* [<img width="20px" src="https://i.imgur.com/QJnHi37.png"></img>](https://hangar.papermc.io/Collagen/Backuper)[ Hangar](https://hangar.papermc.io/Collagen/Backuper)

---

## Version numbers changing

### Backuper-X.Y.Z(A)

* **X** - Changing **X** number means some breaking changes without backward compatibility. This means that before switching to this version you should check the `Incompatible version changes` paragraph
* **Y** - Changing **Y** number means that the version contains significant changes with backward compatibility
* **Z** - Changing **Z** number means that the version contains minor changes or improvements with backward compatibility
* **A** - Changing **A** number means that the version contains bugfixes or security/stability improvements. Usually these are hotfixes

---

## Incompatible version changes

### 1.x.x - 2.x.x

* Fully changed permission system. Check the `Permissions` section to see the changes
* Fully changed command naming. Check the `Commands` section to see changes

---

## How to use

1. Install into your plugins folder
2. Start the server
3. Change config.yml (plugins/Backuper/config.yml)
4. Restart the server

**By default, backups will be stored in (plugins/Backuper/Backups)**

---

## How does automatic backup work?

- **If `backupTime` option in config.yml is not set to -1**
    
  - Backups will occur every day at the time specified in `backupTime`, regardless of `backupPeriod`



- **If `backupTime` option in config.yml is set to -1**
    
  - First backup will happen at the server start
  - Next backups will happen after `backupPeriod`

---

## Configuration

* `Auto backup` - **(true/false)** - Enables automatic backups once at a specified interval **(when disabled, backups will only run on the `/backuper backup` command)**  



* `Backups folder` - **(Path)** - Full path to folder, where backups will be stored
* `Add directory to backup` - **(List of paths)** - Full directory paths to folders/files that you want to be backed up. World folders will be backed up automatically, so you do not need to specify world folders there (For example you can specify "plugins", "config")
* `Exclude Directory From Backup` - **(List of paths)** - Full directory paths to folders/files that you want to be excluded from backup. If you want to backup everything from the **folder1** except some **folder1/file1** you can specify **folder1** in `addDirectoryToBackup` and **folder1/file1** in `excludeDirectoryFromBackup`. (The `backupsFolder` directory will be excluded automatically to prevent the loop)



* `After backup` - **(NOTHING/STOP/RESTART)** - What will the server do after an **automatic** backup
* `backup time` - **(0 - 23 or -1)** - Fixed backup time. Automatic backups will be made at this time every day. (`backupPeriod` will be automatically set to 24 hours). **-1 to disable backup time fixation**
* `Backup period` - **(>= 1 Minutes or -1)** - The period after which the server will make automatic backups **(To change this value you need to set `backupTime` to -1 and `autoBackup` to true)**



* `Skip duplicate backup` - **(true/false)** - Backup will only occur if the world has been changed since the last backup. If the world has not been changed, this backup cycle will be skipped. `AfterBackup` **will be executed anyway**
* `Max backup number` - **(>= 1 or 0)** - Maximum number of backups to be kept **(0 - unlimited)**
* `Max backup weight` - **(>= 1 or 0)** - Maximum weight of backups that will be stored **(MB)** **(0 - unlimited)**
* `Zip archive` - **(true/false)** - Do you want to store backups in ZIP archives?



* `Alert time before restart` - **(>= 1 Seconds or -1)** - A notification about the server restart will be sent to all players on the server `alertTimeBeforeRestart` seconds before the restart. **-1 to disable notifications**
* `Alert only server restart` - **(true/false)** - Notifications will be sent only if the server will be restarted or stopped after the backup



* `Better logging` - **(true/false)** - Enable logging of additional information **(used for debugging, you probably don't need it)**
* `Set worlds read only` - **(true/false)** - **(True recommended)** The backuper will mark all world folders as **Read Only** to prevent folder changing that may cause the backup crash. **True** value may cause **access denied** errors during the backup **(you should just ignore that)**
* `Check updates` - **(true/false)** - Backuper will tell you if a new version is available

---

## Commands

* `/backuper backup <stopRestartServer>` - Command to backup the server manually, argument means what the server will do after restart. The `stopRestartServer` argument can be `STOP` or `RESTART`, also you can use it **without an argument**
* `/backuper backup <delay> <stopRestartServer>` - Command to backup the server manually with a delay **(delay in seconds > 0)**. `stopRestartServer` argument is the same as in command above and it is also **optional**



* `/backuper list` - Command to view the list of backups **(click on a backup to open its menu)**
* `/backuper menu <backupName>` - Command to open the menu of the specified backup **(can be opened by clicking on the specified backup in `/backuper list`)**
* `/backuper menu <backupName> toZIP` - Command to convert the specified backup to a ZIP archive **(can be used by clicking on the `[TO ZIP]` option in `/backuper menu <backupName>`)**
* `/backuper menu <backupName> unZIP` - Command to convert the specified backup from a ZIP archive to a folder **(can be used by clicking on the `[UNZIP]` option in `/backuper menu <backupName>`)**
* `/backuper menu <backupName> delete` - Command to delete the specified backup **(can be used by clicking on the `[DELETE]` option in `/backuper menu <backupName>`)**



* `/backuper config reload` - Command to reload the config file



* `/backuper status` - Command to get the current progress of a task

---

## Permissions

* `backuper.backup` - Permission to use `/backuper backup` command without the `stopRestartServer` argument
* `backuper.backup.stop` - Permission to use `/backuper backup` command with the **STOP** argument (`backuper.backup` permission required)
* `backuper.backup.restart` - Permission to use `/backuper backup` command with the **RESTART** argument (`backuper.backup` permission required)



* `backuper.list` - Permission to use `/backuper list` and `/backuper menu` commands
* `backuper.list.tozip` - Permission to convert backups to a ZIP archive (`backuper.list` permission required)
* `backuper.list.unzip` - Permission to convert backups from a ZIP archive to a folder (`backuper.list` permission required)
* `backuper.list.delete` - Permission to delete backups (`backuper.list` permission required)



* `backuper.config.reload` - Permission to use `/backuper config reload` command (`backuper.config` permission required)



* `backuper.status` - Permission to use `/backuper status` command

---

## Notes

* **Please report any issues to** [GitHub](https://github.com/DVDishka/Backuper/issues)
* RESTART option may not work well, so it's better to use STOP with a loop in your start script ([start script](https://flags.sh/) auto restart ON)
* You can reset the backup time if it is broken **and your `backupTime` is set to -1** by changing `lastBackup` to 0. Then the next backup will happen at the server start and the next ones will happen after `backupPeriod`
