<div align='center'>

# Backuper

<img height="128" src="https://raw.githubusercontent.com/DVDishka/Backuper/refs/heads/master/images/backuper_logo.svg" width="128" alt=""/>

Simple backup plugin for Paper/Folia with **FTP/SFTP/Google Drive** support.

![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/DVDishka/Backuper/total?label=GitHub%20Downloads)
![Modrinth Downloads](https://img.shields.io/modrinth/dt/Backuper?label=Modrinth%20Downloads)
![Hangar Downloads](https://img.shields.io/hangar/dt/Backuper?label=Hangar%20Downloads)

[![bStats Graph Data](https://bstats.org/signatures/bukkit/Backuper.svg)](https://bstats.org/plugin/bukkit/Backuper)

</div>

---

## 🔗 Pages 🔗

* [<img width="20px" src="https://raw.githubusercontent.com/DVDishka/Backuper/refs/heads/master/images/backuper_logo.svg"></img>](https://backuper-mc.com)[ Backuper](https://backuper-mc.com)
* [<img width="20px" src="https://github.githubassets.com/images/modules/logos_page/GitHub-Mark.png"></img>](https://github.com/DVDishka/Backuper)[ GitHub](https://github.com/DVDishka/Backuper)
* [<img width="20px" src="https://i.imgur.com/o104U27.png"></img>](https://modrinth.com/plugin/backuper)[ Modrinth](https://modrinth.com/plugin/backuper)
* [<img width="20px" src="https://i.imgur.com/QJnHi37.png"></img>](https://hangar.papermc.io/Collagen/Backuper)[ Hangar](https://hangar.papermc.io/Collagen/Backuper)

---

## ⭐ Key Features ⭐

- **Multiple Storage Types (Local/SFTP/FTP/Google Drive)**
- **Automatic backups with customizable schedule**
- **Backups Management**
- **Archieving or storing backup as a folder**
- **Flexible Configuration**
- **Async to server thread and multithreaded**
- **Player Notifications**

---

## 🚀 Quick Start 🚀

1. Install the plugin in your `plugins` folder
2. Configure storage settings in `plugins/Backuper/config.yml`
3. Set backup schedule and preferences
4. For cloud storage (Google Drive), link your account using commands

---

## 💾 Storage Types 💾

- **Local** - Store backups on the same server
- **FTP** - Upload backups to FTP servers
- **SFTP** - Secure file transfer via SSH
- **Google Drive** - Cloud storage with OAuth authentication

---

## 📚 Documentation 📚

### ⚙️ Configuration ⚙️
See [[Configuration]]

### 💻 Commands 💻
See [[Commands]]

### 🔐 Permissions 🔐
See [[Permissions System|Permissions-System]]

---

## 🔄 Version Compatibility 🔄

### Version Numbering (X.Y.Z)
- **X** - Breaking changes without backward compatibility
- **Y** - Significant changes with backward compatibility  
- **Z** - Minor changes and improvements

### Major Version Changes
- **1.x.x → 2.x.x**: Complete permission and command system overhaul
- **2.x.x → 3.x.x**: Storage-specific permissions and required storage specification in commands
- **3.x.x → 4.x.x**: Storage Type replaced with Storage Id, support for multiple storages of the same type, and reworked permissions system

---

## 📝 Notes 📝

* **Please report any issues to** [GitHub](https://github.com/DVDishka/Backuper/issues)
* RESTART option may not work well, so it's better to use STOP with a loop in your start script ([start script](https://flags.sh/) auto restart ON)
