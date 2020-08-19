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
package com.machiav3lli.backup.handler.action;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.handler.Crypto;
import com.machiav3lli.backup.handler.ShellHandler;
import com.machiav3lli.backup.handler.TarUtils;
import com.machiav3lli.backup.items.ActionResult;
import com.machiav3lli.backup.items.AppInfo;
import com.machiav3lli.backup.items.AppInfoV2;
import com.machiav3lli.backup.items.BackupProperties;
import com.machiav3lli.backup.utils.BackupBuilder;
import com.machiav3lli.backup.utils.DocumentHelper;
import com.machiav3lli.backup.utils.PrefUtils;

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class BackupAppAction extends BaseAppAction {
    private static final String TAG = Constants.classTag(".BackupAppAction");

    public BackupAppAction(Context context, ShellHandler shell) {
        super(context, shell);
    }

    public ActionResult run(AppInfoV2 app, int backupMode) {
        Log.i(BackupAppAction.TAG, String.format("Backing up: %s [%s]", app.getPackageName(), app.getAppInfo().getPackageLabel()));
        DocumentFile appBackupRoot = app.getBackupDir(true);
        BackupBuilder backupBuilder = new BackupBuilder(this.getContext(), app.getAppInfo(), appBackupRoot);
        DocumentFile backupDir = backupBuilder.getBackupPath();

        try {
            if ((backupMode & AppInfo.MODE_APK) == AppInfo.MODE_APK) {
                Log.i(BackupAppAction.TAG, String.format("%s: Backing up package", app));
                this.backupPackage(app, backupDir);
                backupBuilder.setHasApk(true);
            }
            if ((backupMode & AppInfo.MODE_DATA) == AppInfo.MODE_DATA) {
                Log.i(BackupAppAction.TAG, String.format("%s: Backing up data", app));
                this.backupData(app, backupDir);
                backupBuilder.setHasAppData(true);
                if (PrefUtils.getDefaultSharedPreferences(this.getContext()).getBoolean(Constants.PREFS_EXTERNALDATA, true)) {
                    this.backupExternalData(app, backupDir);
                    backupBuilder.setHasExternalData(true);
                    this.backupObbData(app, backupDir);
                    backupBuilder.setHasObbData(true);
                }
                if (PrefUtils.getDefaultSharedPreferences(this.getContext()).getBoolean(Constants.PREFS_DEVICEPROTECTEDDATA, true)) {
                    this.backupDeviceProtectedData(app, backupDir);
                    backupBuilder.setHasDevicesProtectedData(true);
                }
            }
            if (PrefUtils.isEncryptionEnabled(this.getContext())) {
                backupBuilder.setCipherType(Crypto.getCipherAlgorithm(this.getContext()));
            }
        } catch (BackupFailedException | Crypto.CryptoSetupException e) {
            return new ActionResult(app,
                    null,
                    String.format("%s: %s", e.getClass().getSimpleName(), e.getMessage()),
                    false
            );
        }
        BackupProperties backupProperties = backupBuilder.createBackupProperties();
        Log.i(BackupAppAction.TAG, String.format("%s: Backup done: %s", app, backupProperties));
        return new ActionResult(app, backupProperties, "", true);
    }

    protected void createBackupArchive(Uri backupInstanceDir, String what, List<ShellHandler.FileInfo> allFilesToBackup) throws IOException, Crypto.CryptoSetupException {
        DocumentFile backupDir = DocumentFile.fromTreeUri(this.getContext(), backupInstanceDir);
        Uri backupFileUri = this.getBackupArchive(backupInstanceDir, what, PrefUtils.isEncryptionEnabled(this.getContext()));
        DocumentFile backupFile = backupDir.createFile("application/gzip", backupFileUri.getLastPathSegment());
        String password = PrefUtils.getDefaultSharedPreferences(this.getContext()).getString(Constants.PREFS_PASSWORD, "");
        OutputStream outStream = new BufferedOutputStream(this.getContext().getContentResolver().openOutputStream(backupFile.getUri(), "w"));
        if (!password.isEmpty()) {
            outStream = Crypto.encryptStream(outStream, password, PrefUtils.getCryptoSalt(this.getContext()));
        }
        try (TarArchiveOutputStream archive = new TarArchiveOutputStream(new GzipCompressorOutputStream(outStream))) {
            archive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarUtils.suAddFiles(archive, allFilesToBackup);
        } finally {
            Log.d(BackupAppAction.TAG, "Done compressing. Closing " + backupFileUri.getLastPathSegment());
            outStream.close();
        }
    }

    protected void copyToBackupArchive(Uri backupInstanceDir, String what, List<ShellHandler.FileInfo> allFilesToBackup) throws IOException {
        DocumentFile backupInstance = DocumentFile.fromTreeUri(this.getContext(), backupInstanceDir);
        DocumentFile backupDir = backupInstance.createDirectory(what);
        DocumentHelper.suRecursiveCopyFileToDocument(this.getContext(), allFilesToBackup, backupDir.getUri());
    }

    protected void backupPackage(AppInfoV2 app, DocumentFile backupInstanceDir) throws BackupAppAction.BackupFailedException {
        Log.i(BackupAppAction.TAG, String.format("%s: Backup package apks", app));
        String[] apksToBackup;
        if (app.getApkSplits() == null) {
            apksToBackup = new String[]{app.getApkPath()};
        } else {
            apksToBackup = new String[1 + app.getApkSplits().length];
            apksToBackup[0] = app.getApkPath();
            System.arraycopy(app.getApkSplits(), 0, apksToBackup, 1, app.getApkSplits().length);
            Log.d(BackupAppAction.TAG, String.format("Package is splitted into %d apks", apksToBackup.length));
        }

        Log.d(BackupAppAction.TAG, String.format(
                "%s: Backing up package (%d apks: %s)",
                app,
                apksToBackup.length,
                Arrays.stream(apksToBackup).map(s -> new File(s).getName()).collect(Collectors.joining(" "))
        ));

        try {
            for (String apk : apksToBackup) {
                DocumentHelper.suCopyFileToDocument(this.getContext().getContentResolver(), apk, backupInstanceDir);
            }
        } catch (IOException e) {
            Log.e(BackupAppAction.TAG, String.format("%s: Backup APKs failed: %s", app, e));
            throw new BackupFailedException("Could not backup apk", e);
        }
    }

    protected void genericBackupData(final String backupType, final Uri backupInstanceDir, List<ShellHandler.FileInfo> filesToBackup, boolean compress) throws BackupFailedException, Crypto.CryptoSetupException {
        Log.i(BackupAppAction.TAG, "Backing up " + backupType);

        if (filesToBackup.isEmpty()) {
            Log.i(BackupAppAction.TAG, String.format("Nothing to backup for %s. Skipping", backupType));
            return;
        }
        try {
            if (compress) {
                this.createBackupArchive(backupInstanceDir, backupType, filesToBackup);
            } else {
                this.copyToBackupArchive(backupInstanceDir, backupType, filesToBackup);
            }
        } catch (IOException e) {
            final String message = String.format("%s occurred on %s backup: %s", e.getClass().getCanonicalName(), backupType, e);
            Log.e(BackupAppAction.TAG, message);
            throw new BackupFailedException(message, e);
        }
    }

    private List<ShellHandler.FileInfo> assembleFileList(String sourceDirectory)
            throws BackupFailedException {

        // Check what are the contents to backup. No need to start working, if the directory does not exist
        try {
            // Get a list of directories in the directory to backup
            List<String> dirsInSource = new ArrayList<>(Arrays.asList(this.getShell().suGetDirectoryContents(new File(sourceDirectory))));
            // Excludes cache and libs, when we don't want to backup'em
            if (PrefUtils.getDefaultSharedPreferences(this.getContext()).getBoolean(Constants.PREFS_EXCLUDECACHE, true)) {
                dirsInSource.removeAll(BaseAppAction.DATA_EXCLUDED_DIRS);
            }

            // calculate a list what should be part of the backup
            String[] dirsToBackup = dirsInSource.stream()
                    .map(s -> '"' + new File(sourceDirectory, s).getAbsolutePath() + '"')
                    .toArray(String[]::new);
            // if the list is empty, there is nothing to do
            List<ShellHandler.FileInfo> allFilesToBackup = new ArrayList<>();
            if (dirsToBackup.length == 0) {
                return allFilesToBackup;
            }
            for (String dir : dirsToBackup) {
                allFilesToBackup.addAll(this.getShell().suGetDetailedDirectoryContents(dir, true));
            }
            return allFilesToBackup;
        } catch (ShellHandler.ShellCommandFailedException e) {
            throw new BackupFailedException("Could not list contents of " + sourceDirectory, e);
        }
    }

    protected void backupData(AppInfoV2 app, DocumentFile backupInstanceDir) throws BackupFailedException, Crypto.CryptoSetupException {
        List<ShellHandler.FileInfo> filesToBackup = this.assembleFileList(app.getDataDir());
        this.genericBackupData(BaseAppAction.BACKUP_DIR_DATA, backupInstanceDir.getUri(), filesToBackup, true);
    }

    protected void backupExternalData(AppInfoV2 app, DocumentFile backupInstanceDir) throws BackupFailedException, Crypto.CryptoSetupException {
        List<ShellHandler.FileInfo> filesToBackup = this.assembleFileList(app.getExternalDataDir());
        this.genericBackupData(BaseAppAction.BACKUP_DIR_EXTERNAL_FILES, backupInstanceDir.getUri(), filesToBackup, true);
    }

    protected void backupObbData(AppInfoV2 app, DocumentFile backupInstanceDir) throws BackupFailedException, Crypto.CryptoSetupException {
        List<ShellHandler.FileInfo> filesToBackup = this.assembleFileList(app.getObbFilesDir());
        this.genericBackupData(BaseAppAction.BACKUP_DIR_OBB_FILES, backupInstanceDir.getUri(), filesToBackup, false);
    }

    protected void backupDeviceProtectedData(AppInfoV2 app, DocumentFile backupInstanceDir) throws BackupFailedException, Crypto.CryptoSetupException {
        List<ShellHandler.FileInfo> filesToBackup = this.assembleFileList(app.getDeviceProtectedDataDir());
        this.genericBackupData(BaseAppAction.BACKUP_DIR_DEVICE_PROTECTED_FILES, backupInstanceDir.getUri(), filesToBackup, false);
    }

    public static class BackupFailedException extends AppActionFailedException {
        public BackupFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
