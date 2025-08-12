package ru.dvdishka.backuper.backend.task;

import org.bukkit.command.CommandSender;
import ru.dvdishka.backuper.backend.util.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;

public class UnpackZipTask extends BaseAsyncTask {

    private final File sourceZipDir;
    private final File targetFolderDir;

    public UnpackZipTask(File sourceZipDir, File targetFolderDir) {

        super();
        this.sourceZipDir = sourceZipDir;
        this.targetFolderDir = targetFolderDir;
    }

    @Override
    protected void run() throws IOException {

        try (ZipInputStream zipInput = new ZipInputStream(Files.newInputStream(sourceZipDir.toPath()))) {

            ZipEntry zipEntry;

            while (!cancelled && (zipEntry = zipInput.getNextEntry()) != null) {

                if (zipEntry.isDirectory()) {
                    targetFolderDir.mkdirs();
                    zipInput.closeEntry();
                    continue;
                }

                final String name = zipEntry.getName();

                try {

                    if (!targetFolderDir.toPath().resolve(name).getParent().toFile().exists()) {

                        // Sometimes it works in vain, so there is no point in checking the result
                        targetFolderDir.toPath().resolve(name).getParent().toFile().mkdirs();
                    }

                    try (FileOutputStream outputStream = new FileOutputStream(targetFolderDir.toPath().resolve(name).toFile())) {

                        int length;
                        byte[] buffer = new byte[4096];

                        while ((length = zipInput.read(buffer)) >= 0) {

                            if (cancelled) {
                                break;
                            }

                            outputStream.write(buffer, 0, length);
                            incrementCurrentProgress(length);
                        }

                        outputStream.flush();
                    }

                } catch (Exception e) {
                    warn("Something went wrong while trying to unpack file", sender);
                    warn(e);
                }

                zipInput.closeEntry();
            }
        }
    }

    @Override
    protected void prepareTask(CommandSender sender) {

        maxProgress = 0;

        try (ZipFile zipFile = new ZipFile(sourceZipDir)) {

            Enumeration<? extends ZipEntry> zipEntries = zipFile.entries();

            while (zipEntries.hasMoreElements()) {

                if (cancelled) {
                    break;
                }

                ZipEntry zipEntry = zipEntries.nextElement();
                maxProgress += zipEntry.getSize();
            }

        } catch (Exception e) {
            warn("Something went wrong while calculating UnpackZip task maxProgress", this.sender);
            warn(e);
        }

        if (maxProgress <= 0) {
            maxProgress = (long) (((double) Utils.getFileFolderByteSize(sourceZipDir)) * 1.6);
        }
    }

    @Override
    public void cancel() {
        cancelled = true;
    }
}
