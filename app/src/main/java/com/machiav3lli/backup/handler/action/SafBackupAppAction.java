package com.machiav3lli.backup.handler.action;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.handler.Crypto;
import com.machiav3lli.backup.handler.ShellHandler;
import com.machiav3lli.backup.handler.TarUtils;
import com.machiav3lli.backup.items.ActionResult;
import com.machiav3lli.backup.items.AppInfo;
import com.machiav3lli.backup.items.LogFile;
import com.machiav3lli.backup.utils.DocumentHelper;
import com.machiav3lli.backup.utils.PrefUtils;
import com.topjohnwu.superuser.io.SuFileInputStream;

import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.apache.commons.io.FileUtils;
import org.json.JSONException;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class SafBackupAppAction extends BackupAppAction {
    public static final String TAG = Constants.classTag(".BackupAppAction");

    public SafBackupAppAction(Context context, ShellHandler shell) {
        super(context, shell);
    }

    @Override
    public ActionResult run(AppInfo app, int backupMode) {
        Log.i(SafBackupAppAction.TAG, String.format("Backing up: %s [%s]", app.getPackageName(), app.getLabel()));
        try {
            if ((backupMode & AppInfo.MODE_APK) == AppInfo.MODE_APK) {
                this.backupPackage(app);
            }
            if ((backupMode & AppInfo.MODE_DATA) == AppInfo.MODE_DATA) {
                this.backupData(app);
                if (PrefUtils.getDefaultSharedPreferences(this.getContext()).getBoolean(Constants.PREFS_EXTERNALDATA, true)) {
                    this.backupExternalData(app);
                    this.backupObbData(app);
                }
                if (PrefUtils.getDefaultSharedPreferences(this.getContext()).getBoolean(Constants.PREFS_DEVICEPROTECTEDDATA, true)) {
                    this.backupDeviceProtectedData(app);
                }
            }
            boolean encrypted = !PrefUtils.getDefaultSharedPreferences(this.getContext()).getString(Constants.PREFS_PASSWORD, "").isEmpty();
            app.setBackupMode(backupMode);
            LogFile.writeLogFile(this.getAppBackupFolder(app), app, backupMode, encrypted);
        } catch (BackupFailedException | Crypto.CryptoSetupException | JSONException | IOException e) {
            return new ActionResult(
                    app,
                    String.format("%s: %s", e.getClass().getSimpleName(), e.getMessage()),
                    false
            );
        }
        return new ActionResult(app, "", true);
    }

    private TarArchiveOutputStream openBackupArchive(Uri outputFilename) throws IOException, Crypto.CryptoSetupException {
        Log.d(SafBackupAppAction.TAG, "Opening output file for compression: " + outputFilename);
        String password = PrefUtils.getDefaultSharedPreferences(this.getContext()).getString(Constants.PREFS_PASSWORD, "");

        DocumentHelper.getBackupRoot(this.getContext()).createFile("application/octet-stream", "test");
        /*OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFilename, false));
        if (!password.isEmpty()) {
            out = Crypto.encryptStream(out, password, PrefUtils.getCryptoSalt(this.getContext()));
        }
        TarArchiveOutputStream archive = new TarArchiveOutputStream(new GzipCompressorOutputStream(out));
        archive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
        return archive;*/
        return null;
    }

    protected void compress(File filepath, File outputFilename) throws IOException, Crypto.CryptoSetupException {
        Log.d(SafBackupAppAction.TAG, "Opening output file for compression: " + outputFilename);
        String password = PrefUtils.getDefaultSharedPreferences(this.getContext()).getString(Constants.PREFS_PASSWORD, "");

        OutputStream out = new BufferedOutputStream(new FileOutputStream(outputFilename, false));
        if (!password.isEmpty()) {
            out = Crypto.encryptStream(out, password, PrefUtils.getCryptoSalt(this.getContext()));
        }
        try (TarArchiveOutputStream archive = new TarArchiveOutputStream(new GzipCompressorOutputStream(out))) {
            archive.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);
            TarUtils.addFilepath(archive, filepath, "");
        } finally {
            Log.d(SafBackupAppAction.TAG, "Done compressing. Closing " + outputFilename);
            out.close();
        }
    }

    protected void backupPackage(AppInfo app) throws BackupFailedException {
        Log.i(SafBackupAppAction.TAG, String.format("%s: Backup package apks with Storage Access Framework", app));
        String[] apksToBackup;
        if (app.getSplitSourceDirs() == null) {
            apksToBackup = new String[]{app.getSourceDir()};
        } else {
            apksToBackup = new String[1 + app.getSplitSourceDirs().length];
            apksToBackup[0] = app.getSourceDir();
            System.arraycopy(app.getSplitSourceDirs(), 0, apksToBackup, 1, app.getSplitSourceDirs().length);
            Log.i(SafBackupAppAction.TAG, String.format("Package is splitted into %d apks", apksToBackup.length));
        }

        Log.d(SafBackupAppAction.TAG, String.format(
                "%s: Backing up package (%d apks: %s)",
                app,
                apksToBackup.length,
                Arrays.stream(apksToBackup).map(s -> new File(s).getName()).collect(Collectors.joining(" "))
        ));
        String command = this.prependUtilbox(
                String.format(
                        "cp %s \"%s\"",
                        Arrays.stream(apksToBackup).map(s -> '"' + s + '"').collect(Collectors.joining(" ")),
                        this.getAppBackupFolder(app)
                ));
        try {
            for(String apkPath : apksToBackup){
                try (SuFileInputStream inputStream = new SuFileInputStream(apkPath)) {

                }
            }

            ShellHandler.runAsRoot(command);
        } catch (ShellHandler.ShellCommandFailedException e) {
            String error = BaseAppAction.extractErrorMessage(e.getShellResult());
            Log.e(SafBackupAppAction.TAG, String.format("%s: Backup APKs failed: %s", app, error));
            throw new BackupFailedException(error, e);
        } catch (FileNotFoundException e) {
            Log.e(SafBackupAppAction.TAG, String.format("%s: Backup APKs failed: %s", app, e));
            throw new BackupFailedException("Could not backup apk file", e);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void genericBackupData(
            String type, AppInfo app, File backupDirectory, File sourceDirectory, boolean compress)
            throws BackupFailedException, Crypto.CryptoSetupException {
        Log.i(SafBackupAppAction.TAG, String.format("%s: Backup up %s", app, type));

        String[] dirsToBackup;
        // Check what are the contents to backup. No need to start working, if the directory does not exist
        try {
            // Get a list of directories in the directory to backup
            List<String> dirsInSource = new ArrayList<>(Arrays.asList(this.getShell().suGetDirectoryContents(sourceDirectory)));
            // Excludes cache and libs, when we don't want to backup'em
            if (PrefUtils.getDefaultSharedPreferences(this.getContext()).getBoolean(Constants.PREFS_EXCLUDECACHE, true))
                dirsInSource.removeAll(BaseAppAction.DATA_EXCLUDED_DIRS);

            // calculate a list what should be part of the backup
            dirsToBackup = dirsInSource.stream().map(s -> '"' + new File(sourceDirectory, s).getAbsolutePath() + '"').toArray(String[]::new);
            // if the list is empty, there is nothing to do
            if (dirsToBackup.length == 0) {
                Log.i(SafBackupAppAction.TAG, String.format("%s: Nothing to backup for %s. Skipping", app, type));
                return;
            }
        } catch (ShellHandler.ShellCommandFailedException e) {
            String errorMessage = e.getShellResult().getErr().toString();
            // It's okay, if the directory does not exist.
            if (errorMessage.contains("No such file or directory")) {
                Log.i(SafBackupAppAction.TAG, String.format("%s: '%s'. Fine. Skipping", app, errorMessage));
                return;
            }
            throw new BackupFailedException("Could not list contents of " + sourceDirectory, e);
        }
        // Create the (temporary) directory for the backup data
        if (!(backupDirectory.exists() || backupDirectory.mkdir())) {
            String errorMessage = String.format("Could not create %s backup directory: %s", type, backupDirectory);
            Log.e(SafBackupAppAction.TAG, String.format("%s: %s", app, errorMessage));
            throw new BackupFailedException(errorMessage, null);
        }
        try {
            // cp -RL follows links and copies their contents
            // cp -Rd would be right, but the external storage is formatted as FAT where
            // symbolic links are not supported
            String command = this.prependUtilbox(String.format(
                    "cp -RLp  %s \"%s\"",
                    Arrays.stream(dirsToBackup).collect(Collectors.joining(" ")),
                    backupDirectory
            ));
            ShellHandler.runAsRoot(command);
            if (compress) {
                this.compress(
                        backupDirectory,
                        this.getBackupArchive(app, type, PrefUtils.isEncryptionEnabled(this.getContext()))
                );
            }
        } catch (ShellHandler.ShellCommandFailedException e) {
            String error = BaseAppAction.extractErrorMessage(e.getShellResult());
            Log.e(SafBackupAppAction.TAG, String.format("%s: Backup App Data failed: %s", app, error));
            throw new BackupFailedException(error, e);
        } catch (IOException e) {
            Log.e(SafBackupAppAction.TAG, String.format("%s: Backup App Data failed with IOException: %s", app, e));
            throw new BackupFailedException("IOException", e);
        } finally {
            // if the backup is compressed, clean up in any case
            if (compress) {
                boolean backupDeleted = FileUtils.deleteQuietly(backupDirectory);
                Log.d(SafBackupAppAction.TAG, "Uncompressed Data Backup was deleted: " + backupDeleted);
            }
        }
    }


    @Override
    protected void backupData(AppInfo app) throws BackupFailedException, Crypto.CryptoSetupException {
        this.genericBackupData(
                BaseAppAction.BACKUP_DIR_DATA,
                app,
                this.getDataBackupFolder(app),
                new File(app.getDataDir()),
                true
        );
    }

    @Override
    protected void backupExternalData(AppInfo app) throws BackupFailedException, Crypto.CryptoSetupException {
        this.genericBackupData(
                BaseAppAction.BACKUP_DIR_EXTERNAL_FILES,
                app,
                this.getExternalFilesBackupFolder(app),
                app.getExternalFilesPath(this.getContext()),
                true   // to be discussed. Sometimes it makes sense, sometimes huge amounts of compressed data is stored here
        );
    }

    @Override
    protected void backupObbData(AppInfo app) throws BackupFailedException, Crypto.CryptoSetupException {
        this.genericBackupData(
                BaseAppAction.BACKUP_DIR_OBB_FILES,
                app,
                this.getObbBackupFolder(app),
                app.getObbFilesPath(this.getContext()),
                false   // no compression, obb is already compressed
        );
    }

    @Override
    protected void backupDeviceProtectedData(AppInfo app) throws BackupFailedException, Crypto.CryptoSetupException {
        this.genericBackupData(
                BaseAppAction.BACKUP_DIR_DEVICE_PROTECTED_FILES,
                app,
                this.getDeviceProtectedFolder(app),
                new File(app.getDeviceProtectedDataDir()),
                true
        );
    }

    @Override
    protected boolean removePackageBackup(AppInfo app) {
        Log.i(SafBackupAppAction.TAG, String.format("%s: Removing existing package apks", app));
        List<String> apksToDelete = new ArrayList<>();
        if (app.getLogInfo().getSplitApks() != null) {
            apksToDelete.addAll(Arrays.asList(app.getLogInfo().getSplitApks()));
        }
        apksToDelete.add(app.getLogInfo().getApk());
        boolean successFlag = true;
        for (String originalPath : apksToDelete) {
            File backupApkPath = new File(this.getAppBackupFolder(app), new File(originalPath).getName());
            Log.i(SafBackupAppAction.TAG, String.format("%s: Removing apk from backup: %s", app, backupApkPath));
            successFlag = backupApkPath.delete();
            if (!successFlag) {
                Log.e(SafBackupAppAction.TAG, String.format("%s: Could not delete path: %s", app, backupApkPath));
                break;
            }
        }
        return successFlag;
    }
}
