package com.machiav3lli.backup.handler;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.items.AppInfoV2;
import com.machiav3lli.backup.items.BackupProperties;
import com.machiav3lli.backup.utils.DocumentHelper;
import com.machiav3lli.backup.utils.FileUtils;
import com.machiav3lli.backup.utils.PrefUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class BackendController {

    public static List<AppInfoV2> getApplications(Context context){
        PackageManager pm = context.getPackageManager();
        List<ApplicationInfo> applications = pm.getInstalledApplications(0);

        return null;
    }

    public static List<AppInfoV2> getApplicationList(Context context)
            throws FileUtils.BackupLocationInAccessibleException, PrefUtils.StorageLocationNotConfiguredException {
        PackageManager pm = context.getPackageManager();
        StorageFile backupRoot = DocumentHelper.getBackupRoot(context);
        List<PackageInfo> packageInfoList = pm.getInstalledPackages(0);
        List<AppInfoV2> packageList = packageInfoList.stream()
                .map(pi -> new AppInfoV2(context, pi, backupRoot.getUri()))
                .collect(Collectors.toList());
        return packageList;
    }

    public static List<BackupProperties> getAvailableBackups(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_SHARED_PRIVATE, Context.MODE_PRIVATE);
        // Todo: Replace Default Backup Folder with something better
        String backupBaseDir = prefs.getString(Constants.PREFS_PATH_BACKUP_DIRECTORY, FileUtils.DEFAULT_BACKUP_FOLDER);
        DocumentFile documentFile = DocumentFile.fromTreeUri(context, Uri.parse(backupBaseDir));
        assert documentFile != null;
        DocumentFile[] packageDirs = documentFile.listFiles();
        // Initialize the resulting list with a maximum capacity of the potential amount of apps
        // with backups
        List<BackupProperties> backups = new ArrayList<>(packageDirs.length);
        for(DocumentFile packageDir : packageDirs){
            new AppInfoV2(context, packageDir.getUri());
        }

        //Path packageBackupDir = FileSystems.getDefault().getPath(backupBaseDir);

        return null;
    }

}
