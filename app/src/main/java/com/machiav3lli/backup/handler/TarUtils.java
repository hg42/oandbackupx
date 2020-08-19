/*
 * OAndBackupX: open-source apps backup and restore app.
 * Copyright (C) 2020  Antonios Hazim
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.machiav3lli.backup.handler;

import android.system.ErrnoException;
import android.system.Os;

import com.topjohnwu.superuser.io.SuFileInputStream;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.archivers.tar.TarConstants;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import kotlin.NotImplementedError;

public final class TarUtils {

    /**
     * Adds a filepath to the given archive.
     * If it's a directory, it'll be added cursively
     *
     * @param archive       an opened tar archive to write to
     * @param inputFilepath the filepath to add to the archive
     * @param parent        the parent directory in the archive, use "" to add it to the root directory
     * @throws IOException on IO related errors such as out of disk space or missing files
     */
    public static void addFilepath(TarArchiveOutputStream archive, File inputFilepath, String parent) throws IOException {
        String entryName = parent + inputFilepath.getName();
        TarArchiveEntry archiveEntry = new TarArchiveEntry(inputFilepath, entryName);
        // Interject for symlinks
        if (FileUtils.isSymlink(inputFilepath)) {
            archiveEntry.setLinkName(inputFilepath.getCanonicalPath());
        }
        archive.putArchiveEntry(archiveEntry);

        if (inputFilepath.isFile() && !FileUtils.isSymlink(inputFilepath)) {
            BufferedInputStream bis = new BufferedInputStream(new FileInputStream(inputFilepath));
            IOUtils.copy(bis, archive);
            bis.close();
            archive.closeArchiveEntry();
        } else if (inputFilepath.isDirectory()) {
            archive.closeArchiveEntry();
            for (File nextFile : Objects.requireNonNull(inputFilepath.listFiles(), "Directory listing returned null!")) {
                TarUtils.addFilepath(archive, nextFile, entryName + File.separator);
            }
        } else {
            // in case of a symlink
            archive.closeArchiveEntry();
        }
    }

    public static void suAddFiles(TarArchiveOutputStream archive, List<ShellHandler.FileInfo> allFiles) throws IOException {
        for (ShellHandler.FileInfo file : allFiles) {
            TarArchiveEntry entry;
            switch (file.getFiletype()) {
                case REGULAR_FILE:
                    entry = new TarArchiveEntry(new File(file.getFilepath()));
                    archive.putArchiveEntry(entry);
                    try (SuFileInputStream in = new SuFileInputStream(file.getAbsolutePath())) {
                        IOUtils.copy(in, archive);
                    }
                    archive.closeArchiveEntry();
                    break;
                case BLOCK_DEVICE:
                    throw new NotImplementedError("Block devices should not occur");
                case CHAR_DEVICE:
                    throw new NotImplementedError("Char devices should not occur");
                case DIRECTORY:
                    entry = new TarArchiveEntry(file.getFilepath(), TarConstants.LF_DIR);
                    archive.putArchiveEntry(entry);
                    archive.closeArchiveEntry();
                    break;
                case SYMBOLIC_LINK:
                    entry = new TarArchiveEntry(file.getFilepath(), TarConstants.LF_LINK);
                    entry.setLinkName(file.getLinkName());
                    archive.putArchiveEntry(entry);
                    archive.closeArchiveEntry();
                    break;
                case NAMED_PIPE:
                    entry = new TarArchiveEntry(file.getFilepath(), TarConstants.LF_FIFO);
                    archive.putArchiveEntry(entry);
                    archive.closeArchiveEntry();
                    break;
                case SOCKET:
                    throw new NotImplementedError("It does not make sense to backup sockets");
            }
        }
    }

    public static void suUncompressTo(TarArchiveInputStream archive, String targetDir) throws IOException, ShellHandler.ShellCommandFailedException {
        TarArchiveEntry tarEntry;
        while ((tarEntry = archive.getNextTarEntry()) != null) {
            final File file = new File(targetDir, tarEntry.getName());
            if (tarEntry.isDirectory()) {
                ShellHandler.runAsRoot(String.format("mkdir \"%s\"", file.getAbsolutePath()));
                TarUtils.suUncompressTo(archive, new File(targetDir, file.getName()).getAbsolutePath());
            } else if (tarEntry.isFile()){
                try (SuFileOutputStream fos = new SuFileOutputStream(new File(targetDir, tarEntry.getName()))) {
                    IOUtils.copy(archive, fos);
                }
            } else if (tarEntry.isSymbolicLink()) {
                ShellHandler.runAsRoot(
                        String.format(
                                "cd \"%s\" && ln -s \"%s\" \"%s\"; cd -", targetDir, file.getAbsolutePath(), tarEntry.getLinkName()
                        ));
            } else if(tarEntry.isFIFO()){
                ShellHandler.runAsRoot(
                        String.format(
                                "cd \"%s\" && mkfifo \"%s\"; cd -", targetDir, file.getAbsolutePath()
                        ));
            }else{
                throw new NotImplementedError("Cannot restore file type");
            }
        }
    }

    public static void uncompressTo(TarArchiveInputStream archive, File targetDir) throws IOException {
        TarArchiveEntry tarEntry;
        while ((tarEntry = archive.getNextTarEntry()) != null) {
            final File file = new File(targetDir, tarEntry.getName());
            if (tarEntry.isDirectory()) {
                if (!file.mkdirs()) {
                    throw new IOException("Unable to create folder " + file.getAbsolutePath());
                }
            } else if (tarEntry.isSymbolicLink()) {
                try {
                    Os.symlink(tarEntry.getLinkName(), targetDir + tarEntry.getFile().getPath());
                } catch (ErrnoException e) {
                    e.printStackTrace();
                }
            } else {
                final File parent = file.getParentFile();
                if (!parent.exists() && !parent.mkdirs()) {
                    throw new IOException("Unable to create folder " + parent.getAbsolutePath());
                }
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    IOUtils.copy(archive, fos);
                }
            }
        }
    }
}
