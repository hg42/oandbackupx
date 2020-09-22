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
import com.machiav3lli.backup.utils.PrefUtils.StorageLocationNotConfiguredException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class FileUtils {
    // TODO replace the usage of Environment.getExternalStorageDirectory()
    public static final String DEFAULT_BACKUP_FOLDER = "content://com.android.externalstorage.documents/tree/primary%3A" + "OABXNG";
    public static final String BACKUP_SUBDIR_NAME = "OABXNG";
    public static final String LOG_FILE_NAME = "OAndBackupX.log";
    private static Uri backupLocation;

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

    // Todo: Remove this. Only used in Scheduling
    public static String getBackupDirectoryPath(Context context) {
        return PrefUtils.getPrivateSharedPrefs(context).getString(Constants.PREFS_PATH_BACKUP_DIRECTORY, null);
    }

    public static File getDefaultLogFilePath(Context context) {
        return new File(context.getExternalFilesDir(null), FileUtils.LOG_FILE_NAME);
        //return PrefUtils.getPrivateSharedPrefs(context).getString(Constants.PREFS_PATH_BACKUP_DIRECTORY, PrefUtils.getBackupDir(context) + "/OAndBackupX.log");
    }

    /*public static Uri getOrCreateBackupSubdirectory(DocumentFile storageRoot){
        DocumentFile backupRoot = storageRoot.findFile(backupDirUri);
        if (backupRoot == null || !backupRoot.exists()) {
            throw new BackupLocationNotAccessibleException("Cannot access backup directory");
        }
        DocumentFile backupDir = backupRoot.findFile(FileUtils.BACKUP_SUBDIR_NAME);
        if (backupDir == null) {
            Log.i(FileUtils.TAG, "Backup directory does not exist. Creating it");
            backupDir = backupRoot.createDirectory(FileUtils.BACKUP_SUBDIR_NAME);
            assert backupDir != null;
        }
        return backupDir.getUri();
    }*/

    /**
     * Returns the backup directory URI. It's not the root path but the subdirectory, because
     * user tend to just select their storage's root directory and expect the app to create a
     * directory in it.
     *
     * @return URI to OABX storage directory
     */
    public static Uri getBackupDir(Context context)
            throws StorageLocationNotConfiguredException, BackupLocationInAccessibleException {
        if (FileUtils.backupLocation == null) {
            String storageRoot = PrefUtils.getStorageRootDir(context);
            if (storageRoot.isEmpty()) {
                throw new StorageLocationNotConfiguredException();
            }
            DocumentFile storageRootDoc = DocumentFile.fromTreeUri(context, Uri.parse(storageRoot));
            if(storageRootDoc == null || !storageRootDoc.exists()){
                throw new BackupLocationInAccessibleException("Cannot access the root location.");
            }
            DocumentFile backupLocationDoc = storageRootDoc.findFile(FileUtils.BACKUP_SUBDIR_NAME);
            if(backupLocationDoc == null || !backupLocationDoc.exists()){
                Log.i(FileUtils.TAG, "Backup directory does not exist. Creating it");
                backupLocationDoc = storageRootDoc.createDirectory(FileUtils.BACKUP_SUBDIR_NAME);
                assert backupLocationDoc != null;
            }
            FileUtils.backupLocation = Uri.parse(DocumentsContract.getTreeDocumentId(backupLocationDoc.getUri()));
        }
        return FileUtils.backupLocation;
    }

    /**
     * Invalidates the cached value for the backup location URI so that the next call to
     * `getBackupDir` will set it again.
     */
    public static void invalidateBackupLocation(){
        FileUtils.backupLocation = null;
    }

    public static String getName(String path) {
        if (path.endsWith(File.separator))
            path = path.substring(0, path.length() - 1);
        return path.substring(path.lastIndexOf(File.separator) + 1);
    }

    public static class BackupLocationInAccessibleException extends Exception {
        public BackupLocationInAccessibleException() {
            super();
        }

        public BackupLocationInAccessibleException(String message) {
            super(message);
        }

        public BackupLocationInAccessibleException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
