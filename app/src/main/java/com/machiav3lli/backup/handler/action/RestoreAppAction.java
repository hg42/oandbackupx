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

import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
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
import com.machiav3lli.backup.utils.DocumentHelper;
import com.machiav3lli.backup.utils.PrefUtils;

import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class RestoreAppAction extends BaseAppAction {
    private static final String TAG = Constants.classTag(".RestoreAppAction");
    private static final String BASEAPKFILENAME = "base.apk";
    private static final File PACKAGE_STAGING_DIRECTORY = new File("/data/local/tmp");

    public RestoreAppAction(Context context, ShellHandler shell) {
        super(context, shell);
    }

    public ActionResult run(AppInfoV2 app, BackupProperties backupProperties, Uri backupLocation, int backupMode) {
        Log.i(RestoreAppAction.TAG, String.format("Restoring up: %s [%s]", app.getPackageName(), app.getAppInfo().getPackageLabel()));
        try {
            this.killPackage(app.getPackageName());
            if ((backupMode & AppInfo.MODE_APK) == AppInfo.MODE_APK) {
                this.restorePackage(backupLocation, backupProperties);
            }

            if ((backupMode & AppInfo.MODE_DATA) == AppInfo.MODE_DATA) {
                this.restoreAllData(app, backupProperties, backupLocation);
            }
        } catch (RestoreFailedException | Crypto.CryptoSetupException e) {
            return new ActionResult(app,
                    null,
                    String.format("%s: %s", e.getClass().getSimpleName(), e.getMessage()),
                    false
            );
        }
        Log.i(RestoreAppAction.TAG, String.format("%s: Backup done: %s", app, backupProperties));
        return new ActionResult(app, backupProperties, "", true);
    }

    protected void restoreAllData(AppInfoV2 app, BackupProperties backupProperties, Uri backupLocation) throws Crypto.CryptoSetupException, RestoreFailedException {
        this.restoreData(app, backupProperties, backupLocation);
        SharedPreferences prefs = PrefUtils.getDefaultSharedPreferences(this.getContext());
        if (prefs.getBoolean(Constants.PREFS_EXTERNALDATA, true)) {
            this.restoreExternalData(app, backupProperties, backupLocation);
        }
        if (prefs.getBoolean(Constants.PREFS_EXTERNALDATA, true)) {
            this.restoreObbData(app, backupProperties, backupLocation);
        }
        if (prefs.getBoolean(Constants.PREFS_DEVICEPROTECTEDDATA, true)) {
            this.restoreDeviceProtectedData(app, backupProperties, backupLocation);
        }
    }

    protected void uncompress(File filepath, File targetDir) throws IOException, Crypto.CryptoSetupException {
        String inputFilename = filepath.getAbsolutePath();
        Log.d(RestoreAppAction.TAG, "Opening file for expansion: " + inputFilename);
        String password = PrefUtils.getDefaultSharedPreferences(this.getContext()).getString(Constants.PREFS_PASSWORD, "");
        InputStream in = new BufferedInputStream(new FileInputStream(inputFilename));
        if (!password.isEmpty()) {
            Log.d(RestoreAppAction.TAG, "Encryption enabled");
            in = Crypto.decryptStream(in, password, PrefUtils.getCryptoSalt(this.getContext()));
        }
        TarUtils.uncompressTo(new TarArchiveInputStream(new GzipCompressorInputStream(in)), targetDir);
        Log.d(RestoreAppAction.TAG, "Done expansion. Closing " + inputFilename);
        in.close();
    }

    public void restorePackage(Uri backupLocation, BackupProperties backupProperties) throws RestoreFailedException {
        DocumentFile backupDir = DocumentFile.fromTreeUri(this.getContext(), backupLocation);

        DocumentFile baseApk = backupDir.findFile(RestoreAppAction.BASEAPKFILENAME);
        if (baseApk == null) {
            throw new RestoreFailedException(RestoreAppAction.BASEAPKFILENAME + " is missing in backup", null);
        }

        DocumentFile[] splitApksInBackup = Arrays.stream(backupDir.listFiles())
                .filter(dir -> !dir.isDirectory()) // Forget about dictionaries immediately
                .filter(dir -> !dir.getName().endsWith(".apk")) // Only apks are relevant
                .filter(dir -> !dir.getName().equals(RestoreAppAction.BASEAPKFILENAME)) // Base apk is a special case
                .toArray(DocumentFile[]::new);

        // Todo: Remove this
        DocumentFile[] apksToRestore;
        if (splitApksInBackup.length == 0) {
            apksToRestore = new DocumentFile[]{baseApk};
        } else {
            apksToRestore = new DocumentFile[1 + splitApksInBackup.length];
            apksToRestore[0] = baseApk;
            System.arraycopy(splitApksInBackup, 0, apksToRestore, 1, splitApksInBackup.length);
            Log.i(RestoreAppAction.TAG, String.format("Package is splitted into %d apks", apksToRestore.length));
        }
        // --- REMOVE END
        /* in newer android versions selinux rules prevent system_server
         * from accessing many directories. in android 9 this prevents pm
         * install from installing from other directories that the package
         * staging directory (/data/local/tmp).
         * you can also pipe the apk data to the install command providing
         * it with a -S $apk_size value. but judging from this answer
         * https://issuetracker.google.com/issues/80270303#comment14 this
         * could potentially be unwise to use.
         */
        File stagingApkPath = null;
        if (RestoreAppAction.PACKAGE_STAGING_DIRECTORY.exists()) {
            // It's expected, that all SDK 24+ version of Android go this way.
            stagingApkPath = RestoreAppAction.PACKAGE_STAGING_DIRECTORY;
        } else {
            /*
             * pm cannot install from a file on the data partition
             * Failure [INSTALL_FAILED_INVALID_URI] is reported
             * therefore, if the backup directory is oab's own data
             * directory a temporary directory on the external storage
             * is created where the apk is then copied to.
             *
             * @Tiefkuehlpizze 2020-06-28: When does this occur? Checked it with emulator image with SDK 24. This is
             *                             a very old piece of code. Maybe it's obsolete.
             * @machiav3lli 2020-08-09: In some oem ROMs the access to data/local/tmp is not allowed, I don't know how
             *                              this has changed in the last couple of years.
             */
            stagingApkPath = new File(this.getContext().getExternalFilesDir(null), "apkTmp");
            Log.w(RestoreAppAction.TAG, "Weird configuration. Expecting that the system does not allow " +
                    "installing from oabxs own data directory. Copying the apk to " + stagingApkPath);
        }

        try {
            String command;
            // Try it with a staging path. This is usually the way to go.
            // copy apks to staging dir
            for (DocumentFile apkDoc : apksToRestore) {
                DocumentHelper.suCopyFileFromDocument(
                        this.getContext().getContentResolver(),
                        apkDoc.getUri(),
                        new File(stagingApkPath, apkDoc.getName()).getAbsolutePath()
                );
            }
            StringBuilder sb = new StringBuilder();
            // Install main package
            sb.append(this.getPackageInstallCommand(new File(stagingApkPath, baseApk.getName())));
            // If split apk resources exist, install them afterwards (order does not matter)
            if (splitApksInBackup.length > 0) {
                for (DocumentFile apk : splitApksInBackup) {
                    sb.append(" && ").append(
                            this.getPackageInstallCommand(new File(stagingApkPath, apk.getName()), backupProperties.getPackageName()));
                }
            }

            // append cleanup command
            final File finalStagingApkPath = stagingApkPath;
            sb.append(String.format(" && %s rm %s", this.getShell().getUtilboxPath(),
                    Arrays.stream(apksToRestore).map(s -> '"' + finalStagingApkPath.getAbsolutePath() + '/' + s.getName() + '"').collect(Collectors.joining(" "))
            ));
            command = sb.toString();
            ShellHandler.runAsRoot(command);
            // Todo: Reload package data; Implement function for it
        } catch (ShellHandler.ShellCommandFailedException e) {
            String error = BaseAppAction.extractErrorMessage(e.getShellResult());
            Log.e(RestoreAppAction.TAG, String.format("Restore APKs failed: %s", error));
            throw new RestoreFailedException(error, e);
        } catch (IOException e) {
            throw new RestoreFailedException("Could not copy apk to staging directory", e);
        }
    }

    private void genericRestoreDataByCopying(final String targetPath, final Uri backupDir, final String what) throws RestoreFailedException {
        try {
            final Uri backupDirFile = backupDir.buildUpon().appendPath(what).build();
            DocumentHelper.suRecursiveCopyFileFromDocument(this.getContext(), backupDirFile, targetPath);
        } catch (IOException e) {
            throw new RestoreFailedException("Could not read the input file due to IOException", e);
        } catch (ShellHandler.ShellCommandFailedException e) {
            String error = BaseAppAction.extractErrorMessage(e.getShellResult());
            throw new RestoreFailedException("Could not restore a file due to a failed root command: " + error, e);
        }
    }

    protected TarArchiveInputStream openArchiveFile(Uri archiveUri, boolean isEncrypted) throws Crypto.CryptoSetupException, IOException, FileNotFoundException {
        InputStream inputStream = new BufferedInputStream(this.getContext().getContentResolver().openInputStream(archiveUri));
        if (isEncrypted) {
            String password = PrefUtils.getDefaultSharedPreferences(this.getContext()).getString(Constants.PREFS_PASSWORD, "");
            if (!password.isEmpty()) {
                Log.d(RestoreAppAction.TAG, "Decryption enabled");
                inputStream = Crypto.decryptStream(inputStream, password, PrefUtils.getCryptoSalt(this.getContext()));
            }
        }
        return new TarArchiveInputStream(new GzipCompressorInputStream(inputStream));
    }

    private void genericRestoreFromArchive(final Uri archiveUri, final String targetDir, boolean isEncrypted) throws RestoreFailedException, Crypto.CryptoSetupException {
        try (TarArchiveInputStream inputStream = this.openArchiveFile(archiveUri, isEncrypted)) {
            TarUtils.suUncompressTo(inputStream, targetDir);
        } catch (FileNotFoundException e) {
            throw new RestoreFailedException("Backup archive at " + archiveUri + " is missing", e);
        } catch (IOException e) {
            throw new RestoreFailedException("Could not read the input file or write an output file due to IOException", e);
        } catch (ShellHandler.ShellCommandFailedException e) {
            String error = BaseAppAction.extractErrorMessage(e.getShellResult());
            throw new RestoreFailedException("Could not restore a file due to a failed root command: " + error, e);
        }
    }

    private void genericRestorePermissions(String type, File targetDir) throws RestoreFailedException {

        try {
            // retrieve the assigned uid and gid from the data directory Android created
            String[] uidgid = this.getShell().suGetOwnerAndGroup(targetDir.getAbsolutePath());
            // get the contents. lib for example must be owned by root
            List<String> dataContents = new ArrayList<>(Arrays.asList(this.getShell().suGetDirectoryContents(targetDir)));
            // Maybe dirty: Remove what we don't wanted to have in the backup. Just don't touch it
            dataContents.removeAll(BaseAppAction.DATA_EXCLUDED_DIRS);
            // calculate a list what must be updated
            String[] chownTargets = dataContents.stream().map(s -> '"' + new File(targetDir, s).getAbsolutePath() + '"').toArray(String[]::new);
            if (chownTargets.length == 0) {
                // surprise. No data?
                Log.i(RestoreAppAction.TAG, String.format("No chown targets. Is this an app without any %s ? Doing nothing.", type));
                return;
            }
            String command = this.prependUtilbox(String.format(
                    "chown -R %s:%s %s", uidgid[0], uidgid[1],
                    String.join(" ", chownTargets)));
            ShellHandler.runAsRoot(command);
        } catch (ShellHandler.ShellCommandFailedException e) {
            String errorMessage = "Could not update permissions for " + type;
            Log.e(RestoreAppAction.TAG, errorMessage);
            throw new RestoreFailedException(errorMessage, e);
        } catch (ShellHandler.UnexpectedCommandResult e) {
            String errorMessage = String.format("Could not extract user and group information from %s directory", type);
            Log.e(RestoreAppAction.TAG, errorMessage);
            throw new RestoreFailedException(errorMessage, e);
        }
    }

    public void restoreData(AppInfoV2 app, BackupProperties backupProperties, Uri backupLocation) throws RestoreFailedException, Crypto.CryptoSetupException {

        this.genericRestoreFromArchive(
                this.getBackupArchive(backupLocation, BaseAppAction.BACKUP_DIR_DATA, backupProperties.isEncrypted()),
                app.getDataDir(), backupProperties.isEncrypted());
        this.genericRestorePermissions(BaseAppAction.BACKUP_DIR_DATA, new File(app.getDataDir()));
    }

    public void restoreExternalData(AppInfoV2 app, BackupProperties backupProperties, Uri backupLocation) throws RestoreFailedException, Crypto.CryptoSetupException {
        this.genericRestoreFromArchive(
                this.getBackupArchive(backupLocation, BaseAppAction.BACKUP_DIR_EXTERNAL_FILES, backupProperties.isEncrypted()),
                app.getExternalDataDir(), backupProperties.isEncrypted()
        );
    }

    public void restoreObbData(AppInfoV2 app, BackupProperties backupProperties, Uri backupLocation) throws RestoreFailedException {
        this.genericRestoreDataByCopying(app.getObbFilesDir(), backupLocation, BaseAppAction.BACKUP_DIR_OBB_FILES);
    }

    public void restoreDeviceProtectedData(AppInfoV2 app, BackupProperties backupProperties, Uri backupLocation) throws RestoreFailedException, Crypto.CryptoSetupException {
        this.genericRestoreFromArchive(
                this.getBackupArchive(backupLocation, BaseAppAction.BACKUP_DIR_DEVICE_PROTECTED_FILES, backupProperties.isEncrypted()),
                app.getDeviceProtectedDataDir(), backupProperties.isEncrypted()
        );
        this.genericRestorePermissions(
                BaseAppAction.BACKUP_DIR_DEVICE_PROTECTED_FILES,
                new File(app.getDeviceProtectedDataDir())
        );
    }

    /**
     * Returns an installation command for abd/shell installation.
     * Supports base packages and additional packages (split apk addons)
     *
     * @param apkPath path to the apk to be installed (should be in the staging dir)
     * @return a complete shell command
     */
    public String getPackageInstallCommand(File apkPath) {
        return this.getPackageInstallCommand(apkPath, null);
    }

    /**
     * Returns an installation command for abd/shell installation.
     * Supports base packages and additional packages (split apk addons)
     *
     * @param apkPath         path to the apk to be installed (should be in the staging dir)
     * @param basePackageName null, if it's a base package otherwise the name of the base package
     * @return a complete shell command
     */
    public String getPackageInstallCommand(File apkPath, String basePackageName) {
        return String.format("%s%s -r \"%s\"",
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ? "cmd package install" : "pm install",
                basePackageName != null ? " -p " + basePackageName : "",
                apkPath);
    }

    public void killPackage(String packageName) {
        ActivityManager manager = (ActivityManager) this.getContext().getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> runningList = manager.getRunningAppProcesses();
        for (ActivityManager.RunningAppProcessInfo process : runningList) {
            if (process.processName.equals(packageName) && process.pid != android.os.Process.myPid()) {
                Log.d(RestoreAppAction.TAG, String.format("Killing pid %d of package %s", process.pid, packageName));
                try {
                    ShellHandler.runAsRoot("kill " + process.pid);
                } catch (ShellHandler.ShellCommandFailedException e) {
                    Log.e(RestoreAppAction.TAG, BaseAppAction.extractErrorMessage(e.getShellResult()));
                }
            }
        }
    }

    public enum RestoreCommand {
        MOVE("mv"),
        COPY("cp -r");

        final String command;

        RestoreCommand(String command) {
            this.command = command;
        }

        @NotNull
        @Override
        public String toString() {
            return this.command;
        }
    }

    public static class RestoreFailedException extends AppActionFailedException {
        public RestoreFailedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
