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
package com.machiav3lli.backup.fragments;

import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.machiav3lli.backup.ActionListener;
import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.R;
import com.machiav3lli.backup.activities.IntroActivity;
import com.machiav3lli.backup.activities.MainActivityX;
import com.machiav3lli.backup.databinding.SheetAppBinding;
import com.machiav3lli.backup.dialogs.BackupDialogFragment;
import com.machiav3lli.backup.dialogs.RestoreDialogFragment;
import com.machiav3lli.backup.dialogs.ShareDialogFragment;
import com.machiav3lli.backup.handler.BackupRestoreHelper;
import com.machiav3lli.backup.handler.HandleMessages;
import com.machiav3lli.backup.handler.NotificationHelper;
import com.machiav3lli.backup.handler.ShellCommands;
import com.machiav3lli.backup.handler.ShellHandler;
import com.machiav3lli.backup.items.AppInfo;
import com.machiav3lli.backup.items.AppInfoV2;
import com.machiav3lli.backup.items.AppMetaInfo;
import com.machiav3lli.backup.items.BackupItem;
import com.machiav3lli.backup.items.BackupProperties;
import com.machiav3lli.backup.items.LogFile;
import com.machiav3lli.backup.items.MainItemX;
import com.machiav3lli.backup.tasks.BackupTask;
import com.machiav3lli.backup.tasks.RestoreTask;
import com.machiav3lli.backup.utils.CommandUtils;
import com.machiav3lli.backup.utils.FileUtils;
import com.machiav3lli.backup.utils.ItemUtils;
import com.machiav3lli.backup.utils.UIUtils;

import java.io.File;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class AppSheet extends BottomSheetDialogFragment implements ActionListener {
    private static final String TAG = Constants.classTag(".AppSheet");
    int notificationId = (int) System.currentTimeMillis();
    AppInfoV2 app;
    HandleMessages handleMessages;
    ArrayList<String> users;
    ShellCommands shellCommands;
    String backupDirPath;
    File backupDir;
    int position;
    private SheetAppBinding binding;

    public AppSheet(MainItemX item, Integer position) {
        this.app = item.getApp();
        this.position = position;
    }

    public int getPosition() {
        return this.position;
    }

    public String getPackageName() {
        return this.app.getPackageName();
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        BottomSheetDialog sheet = (BottomSheetDialog) super.onCreateDialog(savedInstanceState);
        sheet.setOnShowListener(d -> {
            BottomSheetDialog bottomSheetDialog = (BottomSheetDialog) d;
            FrameLayout bottomSheet = bottomSheetDialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (bottomSheet != null)
                BottomSheetBehavior.from(bottomSheet).setState(BottomSheetBehavior.STATE_EXPANDED);
        });
        handleMessages = new HandleMessages(requireContext());
        ArrayList<String> users = savedInstanceState != null ? savedInstanceState.getStringArrayList(Constants.BUNDLE_USERS) : new ArrayList<>();
        shellCommands = new ShellCommands(requireContext(), users);
        String backupDirPath = FileUtils.getBackupDirectoryPath(requireContext());
        // Todo: Query the user for the backup dir!
        backupDir = null;
        // backupDir = FileUtils.createBackupDir(getActivity(), backupDirPath);
        return sheet;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = SheetAppBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        setupOnClicks(this);
        setupChips(false);
        setupAppInfo(false);
    }

    public void updateApp(MainItemX item) {
        this.app = item.getApp();
        if (binding != null) {
            setupChips(true);
            setupAppInfo(true);
        }
    }

    private void setupChips(boolean update) {
        if (this.app.hasBackups()) {
            UIUtils.setVisibility(this.binding.delete, View.GONE, update);
            UIUtils.setVisibility(this.binding.share, View.GONE, update);
            UIUtils.setVisibility(this.binding.restore, View.GONE, update);
        } else {
            UIUtils.setVisibility(this.binding.delete, View.VISIBLE, update);
            UIUtils.setVisibility(this.binding.share, View.VISIBLE, update);
            // Todo: Verify the effect
            // UIUtils.setVisibility(this.binding.restore, app.getBackupMode() == AppInfo.MODE_UNSET ? View.GONE : View.VISIBLE, update);
            UIUtils.setVisibility(this.binding.restore, View.VISIBLE, update);
        }
        if (this.app.isInstalled()) {
            UIUtils.setVisibility(this.binding.enablePackage, this.app.isDisabled() ? View.VISIBLE : View.GONE, update);
            UIUtils.setVisibility(this.binding.disablePackage, this.app.isDisabled() ? View.GONE : View.VISIBLE, update);
            UIUtils.setVisibility(this.binding.uninstall, View.VISIBLE, update);
            UIUtils.setVisibility(this.binding.backup, View.VISIBLE, update);
        } else {
            UIUtils.setVisibility(this.binding.uninstall, View.GONE, update);
            UIUtils.setVisibility(this.binding.backup, View.GONE, update);
            UIUtils.setVisibility(this.binding.enablePackage, View.GONE, update);
            UIUtils.setVisibility(this.binding.disablePackage, View.GONE, update);
        }
        if (this.app.getAppInfo().isSystem())
            UIUtils.setVisibility(this.binding.uninstall, View.GONE, update);
    }

    private void setupAppInfo(boolean update) {
        AppMetaInfo appInfo = this.app.getAppInfo();
        if (appInfo.getApplicationIcon() != null) {
            this.binding.icon.setImageDrawable(appInfo.getApplicationIcon());
        } else {
            this.binding.icon.setImageResource(R.drawable.ic_placeholder);
        }
        this.binding.label.setText(appInfo.getPackageLabel());
        this.binding.packageName.setText(this.app.getPackageName());
        if (appInfo.isSystem()) {
            this.binding.appType.setText(R.string.systemApp);
        } else {
            this.binding.appType.setText(R.string.userApp);
        }
        // Todo: Implement Special Type
        /*if (appInfo.isSpecial()) {
            UIUtils.setVisibility(binding.appSizeLine, View.GONE, update);
            UIUtils.setVisibility(binding.dataSizeLine, View.GONE, update);
            UIUtils.setVisibility(binding.cacheSizeLine, View.GONE, update);
            UIUtils.setVisibility(binding.appSplitsLine, View.GONE, update);
        } else {
            binding.appSize.setText(Formatter.formatFileSize(requireContext(), app.getAppSize()));
            binding.dataSize.setText(Formatter.formatFileSize(requireContext(), app.getDataSize()));
            binding.cacheSize.setText(Formatter.formatFileSize(requireContext(), app.getCacheSize()));
            if (app.getCacheSize() == 0)
                UIUtils.setVisibility(binding.wipeCache, View.GONE, update);
        }*/
        // Is this really important?
        // if (app.isSplit()) binding.appSplits.setText(R.string.dialogYes);
        // else binding.appSplits.setText(R.string.dialogNo);

        // Set some values which might be overwritten
        this.binding.versionName.setText(appInfo.getVersionName());

        // Todo: Support more versions
        if (this.app.hasBackups()) {
            List<BackupItem> backupHistory = this.app.getBackupHistory();
            BackupItem backup = backupHistory.get(backupHistory.size() - 1);
            BackupProperties backupProperties = backup.getBackupProperties();

            if (this.app.isUpdated()) {
                String updatedVersionString = backupProperties.getVersionName() + " (" + this.app.getAppInfo().getVersionName() + ")";
                binding.versionName.setText(updatedVersionString);
                binding.versionName.setTextColor(ContextCompat.getColor(requireContext(), R.color.app_secondary));
            } else {
                binding.versionName.setText(this.app.getAppInfo().getVersionName());
                binding.versionName.setTextColor(binding.packageName.getTextColors());
            }

            if (backupProperties.getVersionCode() != 0
                    && appInfo.getVersionCode() > backupProperties.getVersionCode()) {
                this.binding.versionName.setText(String.format("%s -> %s", appInfo.getVersionName(), backupProperties.getVersionName()));
            }
            UIUtils.setVisibility(this.binding.lastBackupLine, View.VISIBLE, update);
            this.binding.lastBackup.setText(backupProperties.getBackupDate().toString());

            // Todo: Be more precise
            if (backupProperties.hasApk() && backupProperties.hasAppData()) {
                UIUtils.setVisibility(this.binding.backupModeLine, View.VISIBLE, update);
                this.binding.backupMode.setText(R.string.bothBackedUp);
            } else if (backupProperties.hasApk()) {
                UIUtils.setVisibility(this.binding.backupModeLine, View.VISIBLE, update);
                this.binding.backupMode.setText(R.string.onlyApkBackedUp);
            } else if (backupProperties.hasAppData()) {
                UIUtils.setVisibility(this.binding.backupModeLine, View.VISIBLE, update);
                this.binding.backupMode.setText(R.string.onlyDataBackedUp);
            } else {
                this.binding.backupMode.setText("");
            }

            UIUtils.setVisibility(this.binding.encryptedLine, View.VISIBLE, update);
            if(backupProperties.getCipherType().isEmpty()) {
                this.binding.encrypted.setText(R.string.dialogNo);
            }else {
                this.binding.encrypted.setText(backupProperties.getCipherType());
            }

        } else {
            UIUtils.setVisibility(this.binding.lastBackupLine, View.GONE, update);
            UIUtils.setVisibility(this.binding.backupModeLine, View.GONE, update);
            UIUtils.setVisibility(this.binding.encryptedLine, View.GONE, update);
        }
        ItemUtils.pickTypeColor(this.app, this.binding.appType);
    }

    private void setupOnClicks(AppSheet fragment) {
        binding.dismiss.setOnClickListener(v -> dismissAllowingStateLoss());
        binding.exodusReport.setOnClickListener(v -> requireContext().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.exodusUrl(app.getPackageName())))));
        binding.appInfo.setOnClickListener(v -> {
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", this.app.getPackageName(), null));
            this.startActivity(intent);
        });
        this.binding.wipeCache.setOnClickListener(v -> {
            try {
                Log.i(AppSheet.TAG, String.format("%s: Wiping cache", this.app));
                // Todo: Reenable Wipe Cache functionality
                String command = ShellCommands.wipeCacheCommand(this.requireContext(), this.app);
                ShellHandler.runAsRoot(command);
                requireMainActivity().refreshWithAppSheet();
            } catch (ShellHandler.ShellCommandFailedException e) {
                // Not a critical issue
                Log.w(AppSheet.TAG, "Cache couldn't be deleted: " + CommandUtils.iterableToString(e.getShellResult().getErr()));
            }
        });
        binding.backup.setOnClickListener(v -> {
            Bundle arguments = new Bundle();
            arguments.putParcelable("package", this.app.getPackageInfo());
            BackupDialogFragment dialog = new BackupDialogFragment(fragment);
            dialog.setArguments(arguments);
            dialog.show(requireActivity().getSupportFragmentManager(), "backupDialog");
        });
        binding.restore.setOnClickListener(v -> {
            BackupItem backup = this.app.getLatestBackup();
            BackupProperties properties = backup.getBackupProperties();
            if (!this.app.isInstalled() && properties.hasAppData()) {
                Toast.makeText(getContext(), getString(R.string.notInstalledModeDataWarning), Toast.LENGTH_LONG).show();
            } else {
                Bundle arguments = new Bundle();
                arguments.putParcelable("appinfo", this.app.getAppInfo());
                arguments.putParcelable("backup", properties);
                RestoreDialogFragment dialog = new RestoreDialogFragment(fragment);
                dialog.setArguments(arguments);
                dialog.show(requireActivity().getSupportFragmentManager(), "restoreDialog");
            }
        });
        binding.delete.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(app.getAppInfo().getPackageLabel())
                .setMessage(R.string.deleteBackupDialogMessage)
                .setPositiveButton(R.string.dialogYes, (dialog, which) -> {
                    Thread deleteBackupThread = new Thread(() -> {
                        handleMessages.showMessage(app.getAppInfo().getPackageLabel(), getString(R.string.deleteBackup));
                        if (backupDir != null)
                            ShellCommands.deleteBackup(new File(backupDir, app.getPackageName()));
                        handleMessages.endMessage();
                        requireMainActivity().refreshWithAppSheet();
                    });
                    deleteBackupThread.start();
                    Toast.makeText(requireContext(), R.string.deleted_backup, Toast.LENGTH_LONG).show();
                })
                .setNegativeButton(R.string.dialogNo, null)
                .show());
        binding.share.setOnClickListener(v -> {
            // Todo: Reenable this
            /*
            File backupDir = FileUtils.createBackupDir(getActivity(), FileUtils.getDefaultBackupDirPath(requireContext()));
            File apk = new File(backupDir, app.getPackageName() + "/" + app.getLogInfo().getApk());
            String dataPath = app.getLogInfo().getDataDir();
            dataPath = dataPath.substring(dataPath.lastIndexOf("/") + 1);
            File apk = new File(backupDir, app.getPackageName() + File.separator + app.getLogInfo().getApk());
            File data = new File(backupDir, app.getPackageName() + File.separator + dataPath + ".zip");
            Bundle arguments = new Bundle();
            arguments.putString("label", app.getAppInfo().getPackageLabel());
            switch (app.getBackupMode()) {
                case AppInfo.MODE_APK:
                    arguments.putSerializable("apk", apk);
                    break;
                case AppInfo.MODE_DATA:
                    arguments.putSerializable("data", data);
                    break;
                case AppInfo.MODE_BOTH:
                    arguments.putSerializable("apk", data);
                    arguments.putSerializable("data", data);
                    break;
                default:
                    break;
            }
            ShareDialogFragment shareDialog = new ShareDialogFragment();
            shareDialog.setArguments(arguments);
            shareDialog.show(requireActivity().getSupportFragmentManager(), "shareDialog");
            */
        });
        binding.enablePackage.setOnClickListener(v -> displayDialogEnableDisable(app.getPackageName(), true));
        binding.disablePackage.setOnClickListener(v -> displayDialogEnableDisable(app.getPackageName(), false));
        binding.uninstall.setOnClickListener(v -> new AlertDialog.Builder(requireContext())
                .setTitle(app.getAppInfo().getPackageLabel())
                .setMessage(R.string.uninstallDialogMessage)
                .setPositiveButton(R.string.dialogYes, (dialog, which) -> {
                    Thread uninstallThread = new Thread(() -> {
                        Log.i(TAG, "uninstalling " + app.getAppInfo().getPackageLabel());
                        handleMessages.showMessage(app.getAppInfo().getPackageLabel(), getString(R.string.uninstallProgress));
                            // Todo: Reenable Uninstalling
                        //int ret = shellCommands.uninstall(app.getPackageName(), app.getSourceDir(), app.getDataDir(), app.isSystem());
                        int ret = 0;
                            handleMessages.endMessage();
                            if (ret == 0) {
                                NotificationHelper.showNotification(getContext(), MainActivityX.class, notificationId++, app.getAppInfo().getPackageLabel(), getString(R.string.uninstallSuccess), true);
                        } else {
                            NotificationHelper.showNotification(getContext(), MainActivityX.class, notificationId++, app.getAppInfo().getPackageLabel(), getString(R.string.uninstallFailure), true);
                            UIUtils.showErrors(requireActivity());
                        }
                        requireMainActivity().refreshWithAppSheet();
                    });
                    uninstallThread.start();
                })
                .setNegativeButton(R.string.dialogNo, null)
                .show());
    }

    @Override
    public void onActionCalled(BackupRestoreHelper.ActionType actionType, int mode) {
        if (actionType == BackupRestoreHelper.ActionType.BACKUP) {
            new BackupTask(this.app, handleMessages, requireMainActivity(), backupDir, MainActivityX.getShellHandlerInstance(), mode).execute();
            requireMainActivity().refreshWithAppSheet();
        } else if (actionType == BackupRestoreHelper.ActionType.RESTORE) {
            new RestoreTask(this.app, handleMessages, requireMainActivity(), backupDir, MainActivityX.getShellHandlerInstance(), mode).execute();
            requireMainActivity().refreshWithAppSheet();
        } else
            Log.e(TAG, "unknown actionType: " + actionType);
    }

    public void displayDialogEnableDisable(final String packageName, final boolean enable) {
        String title = enable ? getString(R.string.enablePackageTitle) : getString(R.string.disablePackageTitle);
        final ArrayList<String> selectedUsers = new ArrayList<>();
        final ArrayList<String> userList = (ArrayList<String>) shellCommands.getUsers();
        CharSequence[] users = userList.toArray(new CharSequence[0]);
        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setMultiChoiceItems(users, null, (dialog, chosen, checked) -> {
                    if (checked) {
                        selectedUsers.add(userList.get(chosen));
                    } else selectedUsers.remove(userList.get(chosen));
                })
                .setPositiveButton(R.string.dialogOK, (dialog, which) -> {
                    shellCommands.enableDisablePackage(packageName, selectedUsers, enable);
                    requireMainActivity().refreshWithAppSheet();
                })
                .setNegativeButton(R.string.dialogCancel, (dialog, which) -> {
                })
                .show();
    }

    private MainActivityX requireMainActivity() {
        return (MainActivityX) requireActivity();
    }
}
