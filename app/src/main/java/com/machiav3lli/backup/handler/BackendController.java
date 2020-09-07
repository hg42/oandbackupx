package com.machiav3lli.backup.handler;

import android.app.usage.StorageStats;
import android.app.usage.StorageStatsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Process;
import android.util.Log;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.items.AppInfoV2;
import com.machiav3lli.backup.utils.DocumentHelper;
import com.machiav3lli.backup.utils.FileUtils;
import com.machiav3lli.backup.utils.PrefUtils;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public final class BackendController {
    private static final String TAG = Constants.classTag(".BackendController");

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

    public static StorageStats getPackageStorageStats(Context context, String packageName) throws PackageManager.NameNotFoundException {
        UUID storageUuid = context.getPackageManager().getApplicationInfo(packageName, 0).storageUuid;
        return BackendController.getPackageStorageStats(context, packageName, storageUuid);
    }

    public static StorageStats getPackageStorageStats(Context context, String packageName, UUID storageUuid) throws PackageManager.NameNotFoundException {
        StorageStatsManager storageStatsManager = (StorageStatsManager) context.getSystemService(Context.STORAGE_STATS_SERVICE);
        try {
            return storageStatsManager.queryStatsForPackage(storageUuid, packageName, Process.myUserHandle());
        }catch(IOException e){
            Log.e(BackendController.TAG, String.format("Could not retrieve storage stats of %s: %s", packageName, e));
            return null;
        }
    }

}
