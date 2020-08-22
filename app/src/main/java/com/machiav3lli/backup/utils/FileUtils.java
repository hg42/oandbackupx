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
package com.machiav3lli.backup.utils;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.machiav3lli.backup.Constants;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

public final class FileUtils {
    // TODO replace the usage of Environment.getExternalStorageDirectory()
    public static final String DEFAULT_BACKUP_ROOT = "content://com.android.externalstorage.documents/tree/primary%3A";
    public static final String DEFAULT_BACKUP_FOLDER = "content://com.android.externalstorage.documents/tree/primary%3A" + "OABXNG";
    public static final String BACKUP_SUBDIR_NAME = "OABXNG";

    private static final String TAG = Constants.classTag(".FileUtils");

    public static BufferedReader openFileForReading(Context context, Uri uri) throws FileNotFoundException {
        return new BufferedReader(
                new InputStreamReader(context.getContentResolver().openInputStream(uri), StandardCharsets.UTF_8)
        );
    }

    public static BufferedWriter openFileForWriting(Context context, Uri uri) throws FileNotFoundException {
        return FileUtils.openFileForWriting(context, uri, "w");
    }

    public static BufferedWriter openFileForWriting(Context context, Uri uri, String mode) throws FileNotFoundException {
        return new BufferedWriter(
                new OutputStreamWriter(context.getContentResolver().openOutputStream(uri, mode), StandardCharsets.UTF_8)
        );
    }

    // OLD: To check if still needed or valid

    public static File getExternalStorageDirectory(Context context) {
        return context.getExternalFilesDir(null).getParentFile().getParentFile().getParentFile().getParentFile();
    }

    public static File getExternalStoragePublicDirectory(Context context, String directory) {
        return new File(getExternalStorageDirectory(context), directory);
    }

    public static String getBackupDirectoryPath(Context context) {
        return PrefUtils.getPrivateSharedPrefs(context).getString(Constants.PREFS_PATH_BACKUP_DIRECTORY, null);
    }

    public static String getDefaultLogFilePath(Context context) {
        return PrefUtils.getPrivateSharedPrefs(context).getString(Constants.PREFS_PATH_BACKUP_DIRECTORY, PrefUtils.getBackupDir(context) + "/OAndBackupX.log");
    }

    public static Uri getBackupDir(final Context context) throws BackupLocationNotSetException, BackupLocationNotAccessibleException {
        Uri backupDirUri = PrefUtils.getBackupDir(context);
        if (backupDirUri == null) {
            throw new BackupLocationNotSetException();
        }
        DocumentFile backupRoot = DocumentFile.fromTreeUri(context, backupDirUri);
        if (backupRoot == null || !backupRoot.exists()) {
            // Todo: Replace with real Exception
            throw new BackupLocationNotAccessibleException("Cannot access backup directory");
        }
        DocumentFile backupDir = backupRoot.findFile(FileUtils.BACKUP_SUBDIR_NAME);
        if (backupDir == null) {
            Log.i(FileUtils.TAG, "Backup directory does not exist. Creating it");
            backupDir = backupRoot.createDirectory(FileUtils.BACKUP_SUBDIR_NAME);
            assert backupDir != null;
        }
        return backupDir.getUri();
    }

    public static String getName(String path) {
        if (path.endsWith(File.separator))
            path = path.substring(0, path.length() - 1);
        return path.substring(path.lastIndexOf(File.separator) + 1);
    }

    public static class BackupLocationNotSetException extends Exception {

        public BackupLocationNotSetException() {
            super("Backup Location has not been configured");
        }

    }

    public static class BackupLocationNotAccessibleException extends Exception {
        public BackupLocationNotAccessibleException() {
            super();
        }

        public BackupLocationNotAccessibleException(String message) {
            super(message);
        }

        public BackupLocationNotAccessibleException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
