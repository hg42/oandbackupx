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

import android.content.Context;
import android.util.Log;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.items.AppInfoV2;
import com.machiav3lli.backup.utils.FileUtils;
import com.machiav3lli.backup.utils.LogUtils;
import com.topjohnwu.superuser.Shell;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import static com.machiav3lli.backup.utils.CommandUtils.iterableToString;
import static com.machiav3lli.backup.utils.FileUtils.getName;

public class ShellCommands {
    private static final String TAG = Constants.classTag(".ShellCommands");
    private static String errors = "";
    private final Context context;
    boolean multiuserEnabled;
    private ArrayList<String> users;

    public ShellCommands(Context context, List<String> users) {
        this.users = (ArrayList<String>) users;
        this.context = context;
        this.users = (ArrayList<String>) getUsers();
        multiuserEnabled = this.users != null && this.users.size() > 1;
    }

    public static void deleteBackup(File file) {
        if (file.exists()) {
            if (file.isDirectory() && Objects.requireNonNull(file.list()).length > 0)
                for (File child : Objects.requireNonNull(file.listFiles()))
                    deleteBackup(child);
            file.delete();
        }
    }

    public static void writeErrorLog(Context context, String packageName, String err) {
        errors += String.format("%s: %s%n", packageName, err);
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd - HH:mm:ss", Locale.getDefault());
        String dateFormated = dateFormat.format(date);
        try {
            File outFile = new LogUtils().createLogFile(context, FileUtils.getDefaultLogFilePath(context).toString());
            if (outFile != null) {
                try (FileWriter fw = new FileWriter(outFile.getAbsoluteFile(),
                        true);
                     BufferedWriter bw = new BufferedWriter(fw)) {
                    bw.write(String.format("%s: %s [%s]%n", dateFormated, err, packageName));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, e.toString());
        }
    }

    public static int getCurrentUser() {
        try {
            // using reflection to get id of calling user since method getCallingUserId of UserHandle is hidden
            // https://github.com/android/platform_frameworks_base/blob/master/core/java/android/os/UserHandle.java#L123
            Class userHandle = Class.forName("android.os.UserHandle");
            boolean muEnabled = userHandle.getField("MU_ENABLED").getBoolean(null);
            int range = userHandle.getField("PER_USER_RANGE").getInt(null);
            if (muEnabled) return android.os.Binder.getCallingUid() / range;
        } catch (ClassNotFoundException | NoSuchFieldException | IllegalAccessException ignored) {
        }
        return 0;
    }

    public static List<String> getDisabledPackages() {
        Shell.Result shellResult = ShellCommands.runAsUser("pm list packages -d");
        ArrayList<String> packages = new ArrayList<>();
        for (String line : shellResult.getOut()) {
            if (line.contains(":")) {
                packages.add(line.substring(line.indexOf(":") + 1).trim());
            }
        }
        if (shellResult.isSuccess() && !packages.isEmpty()) {
            return packages;
        }
        return new ArrayList<>();
    }

    protected static Shell.Result runAsRoot(String... commands) {
        return ShellCommands.runShellCommand(Shell::su, null, commands);
    }

    protected static Shell.Result runAsUser(String... commands) {
        return ShellCommands.runShellCommand(Shell::sh, null, commands);
    }

    protected static Shell.Result runAsRoot(Collection<String> errors, String... commands) {
        return ShellCommands.runShellCommand(Shell::su, errors, commands);
    }

    protected static Shell.Result runAsUser(Collection<String> errors, String... commands) {
        return ShellCommands.runShellCommand(Shell::sh, errors, commands);
    }

    private static Shell.Result runShellCommand(RunnableShellCommand c, Collection<String> errors, String... commands) {
        // defining stdout and stderr on our own
        // otherwise we would have to set set the flag redirect stderr to stdout:
        // Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        // stderr is used for logging, so it's better not to call an application that does that
        // and keeps quiet
        List<String> stdout = new ArrayList<>();
        List<String> stderr = new ArrayList<>();
        Log.d(TAG, "Running Command: " + iterableToString("; ", commands));
        Shell.Result result = c.runCommand(commands).to(stdout, stderr).exec();
        Log.d(TAG, String.format("Command(s) '%s' ended with %d", Arrays.toString(commands), result.getCode()));
        if (!result.isSuccess() && errors != null) {
            errors.addAll(stderr);
        }
        return result;
    }

    public static void wipeCache(Context context, AppInfoV2 app) throws ShellActionFailedException {
        final String conditionalDeleteTemplate = "\\\n && if [ -d \"%s\" ]; then rm -rf \"%s/\"* ; fi";
        Log.i(TAG, String.format("%s: Wiping cache", app.getPackageName()));
        StringBuilder commandBuilder = new StringBuilder();
        // Normal app cache always exists
        commandBuilder.append(String.format("rm -rf \"%s/cache/\"* \"%s/code_cache/\"*", app.getDataDir(), app.getDataDir()));

        // device protected data cache, might exist or not
        if (!app.getDeviceProtectedDataDir().isEmpty()) {
            String cacheDir = new File(app.getDeviceProtectedDataDir(), "cache").getAbsolutePath();
            String codeCacheDir = new File(app.getDeviceProtectedDataDir(), "code_cache").getAbsolutePath();
            commandBuilder.append(String.format(conditionalDeleteTemplate, cacheDir, cacheDir));
            commandBuilder.append(String.format(conditionalDeleteTemplate, codeCacheDir, codeCacheDir));
        }

        // external cache dirs are added dynamically, the bash if-else will handle the logic
        for (File myCacheDir : context.getExternalCacheDirs()) {
            String cacheDirName = myCacheDir.getName();
            File appsCacheDir = new File(new File(myCacheDir.getParentFile().getParentFile(), app.getPackageName()), cacheDirName);
            commandBuilder.append(String.format(conditionalDeleteTemplate, appsCacheDir, appsCacheDir));
        }

        String command = commandBuilder.toString();
        try {
            ShellHandler.runAsRoot(command);
        } catch (ShellHandler.ShellCommandFailedException e) {
            throw new ShellActionFailedException(command, String.join("\n", e.getShellResult().getErr()), e);
        }
    }

    public void uninstall(String packageName, String sourceDir, String dataDir, boolean isSystem) throws ShellActionFailedException {
        String command;
        if (!isSystem) {
            // Uninstalling while user app
            command = String.format("pm uninstall %s", packageName);
            try {
                ShellHandler.runAsRoot(command);
            } catch (ShellHandler.ShellCommandFailedException e) {
                throw new ShellActionFailedException(command, String.join("\n", e.getShellResult().getErr()), e);
            }
            // don't care for the result here, it likely fails due to file not found
            try {
                command = String.format("%s rm -r /data/lib/%s/*", Constants.UTILBOX_PATH, packageName);
                ShellHandler.runAsRoot(command);
            } catch (ShellHandler.ShellCommandFailedException e) {
                Log.d(TAG, "Command '" + command + "' failed: " + String.join(" ", e.getShellResult().getErr()));
            }
        } else {
            // Deleting while system app
            // it seems that busybox mount sometimes fails silently so use toolbox instead
            String apkSubDir = getName(sourceDir);
            apkSubDir = apkSubDir.substring(0, apkSubDir.lastIndexOf('.'));
            command = "(mount -o remount,rw /system" + " && " +
                    String.format("%s rm %s", Constants.UTILBOX_PATH, sourceDir) + " ; " +
                    String.format("rm -r /system/app/%s", apkSubDir) + " ; " +
                    String.format("%s rm -r %s", Constants.UTILBOX_PATH, dataDir) + " ; " +
                    String.format("%s rm -r /data/app-lib/%s*", Constants.UTILBOX_PATH, packageName) + "); " +
                    "mount -o remount,ro /system";
            try {
                ShellHandler.runAsRoot(command);
            } catch (ShellHandler.ShellCommandFailedException e) {
                throw new ShellActionFailedException(command, String.join("\n", e.getShellResult().getErr()), e);
            }
        }
    }

    public void enableDisablePackage(String packageName, List<String> users, boolean enable) {
        String option = enable ? "enable" : "disable";
        if (users != null && !users.isEmpty()) {
            List<String> commands = new ArrayList<>();
            for (String user : users) {
                commands.add(String.format("pm %s --user %s %s", option, user, packageName));
            }
            Shell.Result shellResult = ShellCommands.runAsRoot(String.join(" && ", commands));
            if (!shellResult.isSuccess()) {
                for (String line : shellResult.getErr()) {
                    ShellCommands.writeErrorLog(context, packageName, line);
                }
            }
        }
    }

    public List<String> getUsers() {
        if (users != null && !users.isEmpty()) {
            return users;
        } else {
            Shell.Result shellResult = ShellCommands.runAsRoot(String.format("pm list users | %s sed -nr 's/.*\\{([0-9]+):.*/\\1/p'", Constants.UTILBOX_PATH));
            ArrayList<String> usersNew = new ArrayList<>();
            for (String line : shellResult.getOut()) {
                if (line.trim().length() != 0)
                    usersNew.add(line.trim());
            }
            return shellResult.isSuccess() ? usersNew : new ArrayList<>();
        }
    }

    public void quickReboot() {
        Shell.Result shellResult = ShellCommands.runAsRoot(String.format("%s pkill system_server", Constants.UTILBOX_PATH));
        if (!shellResult.isSuccess()) {
            for (String line : shellResult.getErr()) {
                ShellCommands.writeErrorLog(this.context, "", line);
            }
        }
    }

    protected interface RunnableShellCommand {
        Shell.Job runCommand(String... commands);
    }

    public static class ShellActionFailedException extends Exception {
        final String command;

        public ShellActionFailedException(String command, String message, Throwable cause) {
            super(message, cause);
            this.command = command;
        }

        public String getCommand() {
            return this.command;
        }
    }
}
