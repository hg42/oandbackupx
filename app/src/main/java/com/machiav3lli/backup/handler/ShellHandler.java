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

import android.util.Log;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.utils.CommandUtils;
import com.topjohnwu.superuser.Shell;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ShellHandler {
    private static final String TAG = Constants.classTag(".ShellHandler");
    private String utilboxPath;

    public ShellHandler() throws UtilboxNotAvailableException {
        try {
            this.setUtilboxPath(Constants.UTILBOX_PATH);
        } catch (UtilboxNotAvailableException e) {
            Log.d(ShellHandler.TAG, String.format("Tried utilbox path `%s`. Not available.", Constants.UTILBOX_PATH));
        }
        if (this.utilboxPath == null) {
            Log.d(ShellHandler.TAG, "No more options for utilbox. Bailing out.");
            throw new UtilboxNotAvailableException(Constants.UTILBOX_PATH, null);
        }
    }

    public static Shell.Result runAsRoot(String... commands) throws ShellCommandFailedException {
        return ShellHandler.runShellCommand(Shell::su, commands);
    }

    public static Shell.Result runAsUser(String... commands) throws ShellCommandFailedException {
        return ShellHandler.runShellCommand(Shell::sh, commands);
    }

    private static Shell.Result runShellCommand(ShellHandler.RunnableShellCommand shell, String... commands) throws ShellCommandFailedException {
        // defining stdout and stderr on our own
        // otherwise we would have to set set the flag redirect stderr to stdout:
        // Shell.Config.setFlags(Shell.FLAG_REDIRECT_STDERR);
        // stderr is used for logging, so it's better not to call an application that does that
        // and keeps quiet
        Log.d(ShellHandler.TAG, "Running Command: " + CommandUtils.iterableToString("; ", commands));
        List<String> stdout = new ArrayList<>();
        List<String> stderr = new ArrayList<>();
        Shell.Result result = shell.runCommand(commands).to(stdout, stderr).exec();
        Log.d(ShellHandler.TAG, String.format("Command(s) '%s' ended with %d", Arrays.toString(commands), result.getCode()));
        if (!result.isSuccess()) {
            throw new ShellCommandFailedException(result);
        }
        return result;
    }

    public String[] suGetDirectoryContents(File path) throws ShellCommandFailedException {
        Shell.Result shellResult = ShellHandler.runAsRoot(String.format("%s ls \"%s\"", this.utilboxPath, path.getAbsolutePath()));
        return shellResult.getOut().toArray(new String[0]);
    }

    public List<FileInfo> suGetDetailedDirectoryContents(String path, boolean recursive) throws ShellCommandFailedException {
        return this.suGetDetailedDirectoryContents(path, recursive, "");
    }

    public List<FileInfo> suGetDetailedDirectoryContents(String path, boolean recursive, String parent) throws ShellCommandFailedException {
        // Expecting something like this (with whitespace)
        // "drwxrwx--x 3 u0_a74 u0_a74       4096 2020-08-14 13:54 files"
        // Special case:
        // "lrwxrwxrwx 1 root   root           60 2020-08-13 23:28 lib -> /data/app/org.mozilla.fenix-ddea_jq2cVLmYxBKu0ummg==/lib/x86"
        Shell.Result shellResult = ShellHandler.runAsRoot(String.format("%s ls -Al \"%s\"", this.utilboxPath, path));
        // Remove the first line with the total amount
        shellResult.getOut().remove(0);
        ArrayList<FileInfo> result = shellResult.getOut().stream()
                .filter(line -> !line.isEmpty())
                .filter(line -> !line.startsWith("total"))
                .filter(line -> ShellHandler.splitWithoutEmptyValues(line, " ", 0).length < 7 )
                .map(line -> FileInfo.fromLsOOutput(line, parent))
                .collect(Collectors.toCollection(ArrayList::new));
        if (recursive) {
            FileInfo[] directories = result.stream()
                    .filter(fileInfo -> fileInfo.filetype.equals(FileInfo.FileType.DIRECTORY))
                    .toArray(FileInfo[]::new);
            for (FileInfo dir : directories) {
                result.addAll(this.suGetDetailedDirectoryContents(
                        new File(dir.filepath).getAbsolutePath(),
                        true,
                        parent + '/' + dir.getFilename())
                );
            }
        }
        return result;
    }

    /**
     * Uses superuser permissions to retrieve uid and gid of any given directory.
     *
     * @param filepath the filepath to retrieve the information from
     * @return an array with two fields. First ist uid, second is gid:  {uid, gid}
     */
    public String[] suGetOwnerAndGroup(String filepath) throws ShellCommandFailedException, UnexpectedCommandResult {
        String command = String.format("%s stat -c '%%u %%g' \"%s\"", this.utilboxPath, filepath);
        Shell.Result shellResult = ShellHandler.runAsRoot(command);
        String[] result = shellResult.getOut().get(0).split(" ");
        if (result.length != 2) {
            throw new UnexpectedCommandResult(String.format("'%s' should have returned 2 values, but produced %d", command, result.length), shellResult);
        }
        if (result[0].isEmpty()) {
            throw new UnexpectedCommandResult(String.format("'%s' returned an empty uid", command), shellResult);
        }
        if (result[1].isEmpty()) {
            throw new UnexpectedCommandResult(String.format("'%s' returned an empty gid", command), shellResult);
        }
        return result;
    }

    public String getUtilboxPath() {
        return this.utilboxPath;
    }

    public void setUtilboxPath(String utilboxPath) throws UtilboxNotAvailableException {
        try {
            Shell.Result shellResult = ShellHandler.runAsUser(utilboxPath + " --version");
            String utilBoxVersion = "Not returned";
            if (!shellResult.getOut().isEmpty()) {
                utilBoxVersion = CommandUtils.iterableToString(shellResult.getOut());
            }
            Log.i(ShellHandler.TAG, String.format("Using Utilbox `%s`: %s", utilboxPath, utilBoxVersion));
        } catch (ShellCommandFailedException e) {
            throw new UtilboxNotAvailableException(utilboxPath, e);
        }
        this.utilboxPath = utilboxPath;
    }

    static String[] splitWithoutEmptyValues(String str, String regex, int limit){
        String[] split = Arrays.stream(str.split(regex)).filter(s -> !s.isEmpty()).toArray(String[]::new);
        int targetSize = limit > 0 ? Math.min(split.length, limit) : split.length;
        String[] result = new String[targetSize];
        System.arraycopy(split, 0, result, 0, targetSize);
        return result;
    }

    public interface RunnableShellCommand {
        Shell.Job runCommand(String... commands);
    }

    public static class ShellCommandFailedException extends Exception {
        private final transient Shell.Result shellResult;

        public ShellCommandFailedException(Shell.Result shellResult) {
            super();
            this.shellResult = shellResult;
        }

        public Shell.Result getShellResult() {
            return this.shellResult;
        }
    }

    public static class UnexpectedCommandResult extends Exception {
        protected final Shell.Result shellResult;

        public UnexpectedCommandResult(String message, Shell.Result shellResult) {
            super(message);
            this.shellResult = shellResult;
        }

        public Shell.Result getShellResult() {
            return this.shellResult;
        }
    }

    public static class UtilboxNotAvailableException extends Exception {
        private final String triedBinaries;

        public UtilboxNotAvailableException(String triedBinaries, Throwable cause) {
            super(cause);
            this.triedBinaries = triedBinaries;
        }

        public String getTriedBinaries() {
            return this.triedBinaries;
        }
    }

    public static class FileInfo {
        private static final Pattern PATTERN_LINKSPLIT = Pattern.compile(" -> ");

        public enum FileType {
            REGULAR_FILE, BLOCK_DEVICE, CHAR_DEVICE, DIRECTORY, SYMBOLIC_LINK, NAMED_PIPE, SOCKET
        }

        private final String filepath;
        private final FileType filetype;
        private final String absolutePath;
        private String linkName;

        protected FileInfo(@NotNull String filepath, @NotNull FileType filetype, @NotNull String absoluteParent) {
            this.filepath = filepath;
            this.filetype = filetype;
            this.absolutePath = absoluteParent + '/' + new File(filepath).getName();
        }

        /**
         * Create an instance of FileInfo from a line of the output from
         * `ls -AofF`
         *
         * @param lsLine single output line of `ls -Al`
         * @return an instance of FileInfo
         */
        public static FileInfo fromLsOOutput(String lsLine, String parentPath, String absoluteParent) {
            // Format
            // [0] Filemode, [1] number of directories/links inside, [2] owner [3] group [4] size
            // [5] mdate, [6] mtime, [7] filename
            String[] tokens = ShellHandler.splitWithoutEmptyValues(lsLine, " ", 7);
            FileType type;
            String linkName = null;
            String filepath = parentPath + '/' + tokens[7];
            switch (tokens[0].charAt(0)) {
                case 'd':
                    type = FileType.DIRECTORY; break;
                case 'l':
                    type = FileType.SYMBOLIC_LINK;
                    String[] nameAndLink = FileInfo.PATTERN_LINKSPLIT.split(filepath);
                    filepath = nameAndLink[0];
                    linkName = nameAndLink[1];
                    break;
                case 'p':
                    type = FileType.NAMED_PIPE; break;
                case 's':
                    type = FileType.SOCKET; break;
                case 'b':
                    type = FileType.BLOCK_DEVICE; break;
                case 'c':
                    type = FileType.CHAR_DEVICE; break;
                default:
                    type = FileType.REGULAR_FILE; break;
            }
            FileInfo result = new FileInfo(filepath, type, absoluteParent);
            result.linkName = linkName;
            return result;
        }

        public static FileInfo fromLsOOutput(String lsLine, String absoluteParent) {
            return FileInfo.fromLsOOutput(lsLine, "", absoluteParent);
        }

        public FileType getFiletype() {
            return this.filetype;
        }

        /**
         * Returns the filepath, relative to the original location
         *
         * @return relative filepath
         */
        public String getFilepath() {
            return this.filepath;
        }

        public String getFilename() {
            return new File(this.filepath).getName();
        }

        public String getAbsolutePath() {
            return this.absolutePath;
        }

        public String getLinkName() {
            return this.linkName;
        }
    }
}
