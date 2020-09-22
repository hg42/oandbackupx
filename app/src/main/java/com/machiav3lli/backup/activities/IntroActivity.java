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
package com.machiav3lli.backup.activities;

import android.app.Activity;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.R;
import com.machiav3lli.backup.databinding.ActivityIntroBinding;
import com.machiav3lli.backup.handler.HandleMessages;
import com.machiav3lli.backup.handler.ShellHandler;
import com.machiav3lli.backup.utils.FileUtils;
import com.machiav3lli.backup.utils.LogUtils;
import com.machiav3lli.backup.utils.PrefUtils;
import com.machiav3lli.backup.utils.UIUtils;
import com.scottyab.rootbeer.RootBeer;

import java.io.File;
import java.util.Arrays;

import static android.Manifest.permission.READ_EXTERNAL_STORAGE;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

public class IntroActivity extends BaseActivity {
    private static final String TAG = Constants.classTag(".IntroActivity");
    private static final int READ_PERMISSION = 2;
    private static final int WRITE_PERMISSION = 3;
    private static final int STATS_PERMISSION = 4;
    private static final int BACKUP_DIR = 5;
    private static ShellHandler shellHandler;
    private HandleMessages handleMessages;
    private ActivityIntroBinding binding;

    public static boolean checkUsageStatsPermission(Context context) {
        AppOpsManager appOps = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        assert appOps != null;
        final int mode = Build.VERSION.SDK_INT >= 29 ?
                appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName())
                : appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.getPackageName());
        if (mode == AppOpsManager.MODE_DEFAULT) {
            return (context.checkCallingOrSelfPermission(android.Manifest.permission.PACKAGE_USAGE_STATS) == PackageManager.PERMISSION_GRANTED);
        } else {
            return (mode == AppOpsManager.MODE_ALLOWED);
        }
    }

    public static ShellHandler getShellHandlerInstance() {
        return IntroActivity.shellHandler;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setDayNightTheme(PrefUtils.getPrivateSharedPrefs(this).getString(Constants.PREFS_THEME, "system"));
        super.onCreate(savedInstanceState);
        binding = ActivityIntroBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        LogUtils.logDeviceInfo(this, TAG);
        handleMessages = new HandleMessages(this);

        if (this.ensureBackupDirectory() && checkStoragePermissions() && checkUsageStatsPermission(this)) {
            binding.permissionsButton.setVisibility(View.GONE);
            if (this.checkResources()) {
                this.launchMainActivity();
            }
        } else if (!checkUsageStatsPermission(this)) {
            binding.permissionsButton.setOnClickListener(v -> getUsageStatsPermission());
        } else {
            binding.permissionsButton.setOnClickListener(v -> getStoragePermission());
        }
    }

    private void setDayNightTheme(String theme) {
        switch (theme) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }

    private void getStoragePermission() {
        requireWriteStoragePermission();
        requireReadStoragePermission();
    }

    private void getUsageStatsPermission() {
        if (!checkUsageStatsPermission(this)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.grant_usage_access_title)
                    .setMessage(R.string.grant_usage_access_message)
                    .setPositiveButton(R.string.dialog_approve,
                            (dialog, which) -> startActivityForResult(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), STATS_PERMISSION))
                    .setNeutralButton(getString(R.string.dialog_refuse), (dialog, which) -> finish())
                    .setCancelable(false)
                    .show();
        }
    }

    private boolean checkStoragePermissions() {
        return (checkSelfPermission(READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
    }

    private void requireReadStoragePermission() {
        if (checkSelfPermission(READ_EXTERNAL_STORAGE) != PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{READ_EXTERNAL_STORAGE}, READ_PERMISSION);
    }

    private void requireWriteStoragePermission() {
        if (checkSelfPermission(WRITE_EXTERNAL_STORAGE) != PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(this, new String[]{WRITE_EXTERNAL_STORAGE}, WRITE_PERMISSION);
    }


    private boolean ensureBackupDirectory(){
        // Check if a backup directory is defined, otherwise ask the user to define it
        // This also grants access to it, otherwise Android will tell the app, the directory would
        // not exist and nothing would work

        try{
            Uri backupDir = FileUtils.getBackupDir(this);
            Log.d(IntroActivity.TAG, "Using backup location: " + backupDir);
            return true;
        } catch (FileUtils.BackupLocationNotAccessibleException | FileUtils.BackupLocationNotSetException e) {
            final String message;
            if(e instanceof FileUtils.BackupLocationNotSetException){
                message = "Backup location not configured. Please select a location where a backup directory will be created.";
            }else{
                message = "Backup location is not accessible. Please select a new location where a backup directory will be created.";
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();

            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            this.startActivityForResult(intent, IntroActivity.BACKUP_DIR);
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        if (requestCode == WRITE_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                if (!canAccessExternalStorage()) {
                    Log.w(TAG, String.format("Permissions were granted: %s -> %s",
                            Arrays.toString(permissions), Arrays.toString(grantResults)));
                    Toast.makeText(this, "Permissions were granted but because of an android bug you have to restart your phone",
                            Toast.LENGTH_LONG).show();
                }
                if (this.checkResources()) {
                    this.launchMainActivity();
                } else {
                    this.finishAffinity();
                }
            } else {
                Log.w(TAG, String.format("Permissions were not granted: %s -> %s",
                        Arrays.toString(permissions), Arrays.toString(grantResults)));
                Toast.makeText(this, getString(
                        R.string.permission_not_granted), Toast.LENGTH_LONG).show();
            }
        } else {
            Log.w(TAG, String.format("Unknown permissions request code: %s",
                    requestCode));
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == STATS_PERMISSION || requestCode == BACKUP_DIR) {
            if (requestCode == BACKUP_DIR) {
                Uri uri = data.getData();
                if (resultCode == Activity.RESULT_OK) {
                    PrefUtils.setStorageRootDir(this, uri.toString());
                }
                this.ensureBackupDirectory();
            }
            if (checkUsageStatsPermission(this)) {
                if (checkStoragePermissions()) {
                    if (this.checkResources()) {
                        this.launchMainActivity();
                    } else {
                        // idea: Lead user to PrefsActivity to adjust the settings
                        // but it's rather unlikely that a user deviates from the standard and
                        // knows what to change
                        this.finishAffinity();
                    }
                } else {
                    getStoragePermission();
                }
            } else if(requestCode == STATS_PERMISSION) {
                // On initial setup, the app asks for the backup directory first
                // This causes the app to go through this path if it's not processing the permission update
                // Super dirty, but works for now
                finishAffinity();
            }
        }
    }

    private void showFatalUiWarning(String message) {
        UIUtils.showWarning(this, IntroActivity.TAG, message, (dialog, id) -> this.finishAffinity());
    }

    private boolean canAccessExternalStorage() {
        final File externalStorage = this.getExternalFilesDir(null).getParentFile();
        return externalStorage != null && externalStorage.canRead() && externalStorage.canWrite();
    }

    private boolean checkResources() {
        this.handleMessages.showMessage(IntroActivity.TAG, getString(R.string.suCheck));
        boolean goodToGo = true;

        // Initialize the ShellHandler for further root checks
        if (!this.initShellHandler(this)) {
            this.showFatalUiWarning(this.getString(R.string.busyboxProblem));
            goodToGo = false;
        }

        RootBeer rootBeer = new RootBeer(this);
        if (!rootBeer.isRooted()) {
            this.showFatalUiWarning(this.getString(R.string.noSu));
            goodToGo = false;
        }
        if (goodToGo) {
            try {
                ShellHandler.runAsRoot("id");
            } catch (ShellHandler.ShellCommandFailedException e) {
                this.showFatalUiWarning(this.getString(R.string.noSu));
                goodToGo = false;
            }
        }
        this.handleMessages.endMessage();
        return goodToGo;
    }

    public boolean initShellHandler(Context context) {
        try {
            IntroActivity.shellHandler = new ShellHandler(context);
        } catch (ShellHandler.UtilboxNotAvailableException e) {
            Log.e(IntroActivity.TAG, "Could initialize ShellHandler: " + e.getMessage());
            return false;
        }
        return true;
    }

    private void launchMainActivity() {
        if (PrefUtils.isBiometricLockAvailable(this) && PrefUtils.isLockEnabled(this)) {
            BiometricPrompt biometricPrompt = createBeometricPrompt(this);
            BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.prefs_biometriclock))
                    .setConfirmationRequired(true)
                    .setDeviceCredentialAllowed(true)
                    .build();
            biometricPrompt.authenticate(promptInfo);
        } else {binding.permissionsButton.setVisibility(View.GONE);
        // Todo: Replace this with a user query to set the backup path!
        //backupDir = FileUtils.createBackupDir(this, backupDirPath);
        startActivity(new Intent(this, MainActivityX.class));
    }
}

    private BiometricPrompt createBeometricPrompt(Activity activity) {
        return new BiometricPrompt(this, ContextCompat.getMainExecutor(this), new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                binding.permissionsButton.setVisibility(View.GONE);
                startActivity(new Intent(activity, MainActivityX.class));
            }
        });
    }
}
