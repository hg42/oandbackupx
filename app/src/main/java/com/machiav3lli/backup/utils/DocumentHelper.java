package com.machiav3lli.backup.utils;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.handler.ShellCommands;
import com.machiav3lli.backup.handler.ShellHandler;
import com.topjohnwu.superuser.io.SuFileInputStream;
import com.topjohnwu.superuser.io.SuFileOutputStream;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

public final class DocumentHelper {
    public static final String TAG = Constants.classTag(".DocumentHelper");

    public static DocumentFile getDocumentDir(Context context, String uri) {
        // assume the stored path is an URI and it can be used with Storage Access Framework
        // if it fails to parse it as URI, it's likely just a legacy plain path

        // Todo: Remove this maybe
        try {
            return DocumentFile.fromTreeUri(context, Uri.parse(uri));
        } catch (IllegalArgumentException e) {
            return DocumentFile.fromFile(new File(uri));
        }
    }

    public static DocumentFile getBackupRoot(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_SHARED_PRIVATE, Context.MODE_PRIVATE);
        String backupBaseDir = prefs.getString(Constants.PREFS_PATH_BACKUP_DIRECTORY, FileUtils.DEFAULT_BACKUP_FOLDER);
        return DocumentHelper.getDocumentDir(context, backupBaseDir);
    }

    public static DocumentFile getDocumentFile(Context context, Uri uri) {
        try {
            return DocumentFile.fromSingleUri(context, uri);
        } catch (IllegalArgumentException e) {
            return DocumentFile.fromFile(new File(uri.toString()));
        }
    }

    public static DocumentFile getDirFromBackupRoot(Context context, String relativePath) {
        Uri newUri = DocumentHelper.getBackupRoot(context)
                .getUri()
                .buildUpon()
                .appendPath(relativePath)
                .build();
        return DocumentFile.fromTreeUri(context, newUri);
    }

    public static DocumentFile ensureDirectory(DocumentFile base, String dirName) {
        DocumentFile dir = base.findFile(dirName);
        if (dir == null) {
            dir = base.createDirectory(dirName);
            assert dir != null;
        }
        return dir;
    }

    public static void deleteRecursive(Context context, Uri uri) throws FileNotFoundException {
        DocumentFile target = DocumentHelper.getDocumentFile(context, uri);
        if (target == null) {
            throw new FileNotFoundException("File does not exist: " + uri);
        }
        DocumentHelper.deleteRecursive(target);
    }

    public static void deleteRecursive(DocumentFile target) {
        if (target.isFile()) {
            target.delete();
        } else if (target.isDirectory()) {
            DocumentFile[] contents = target.listFiles();
            for (DocumentFile file : contents) {
                DocumentHelper.deleteRecursive(file);
            }
        }
    }

    public static void suRecursiveCopyFileToDocument(Context context, List<ShellHandler.FileInfo> filesToBackup, Uri targetUri) throws IOException {
        final ContentResolver resolver = context.getContentResolver();
        for (ShellHandler.FileInfo file : filesToBackup) {
            Uri parentUri = targetUri.buildUpon().appendEncodedPath(new File(file.getFilepath()).getParent()).build();
            DocumentFile parentFile = DocumentFile.fromTreeUri(context, parentUri);
            switch (file.getFiletype()) {
                case REGULAR_FILE:
                    DocumentHelper.suCopyFileToDocument(resolver, file.getAbsolutePath(), DocumentFile.fromTreeUri(context, parentUri));
                    break;
                case DIRECTORY:
                    parentFile.createDirectory(file.getFilename());
                    break;
                default:
                    Log.e(DocumentHelper.TAG, "SAF does not support " + file.getFiletype());
                    break;
            }
        }
    }

    public static void suCopyFileToDocument(ContentResolver resolver, String sourcePath, DocumentFile targetDir) throws IOException {
        try (SuFileInputStream inputFile = new SuFileInputStream(sourcePath)) {
            DocumentFile newFile = targetDir.createFile("application/octet-stream", new File(sourcePath).getName());
            assert newFile != null;
            try (OutputStream outputFile = resolver.openOutputStream(newFile.getUri())) {
                IOUtils.copy(inputFile, outputFile);
            }
        }
    }

    public static void suRecursiveCopyFileFromDocument(Context context, Uri sourceDir, String targetPath) throws IOException, ShellHandler.ShellCommandFailedException {
        final ContentResolver resolver = context.getContentResolver();
        DocumentFile rootDir = DocumentFile.fromTreeUri(context, sourceDir);
        for (DocumentFile sourceDoc : rootDir.listFiles()) {
            if (sourceDoc.isDirectory()) {
                ShellHandler.runAsRoot(String.format("mkdir \"%s\"", new File(targetPath, sourceDoc.getName())));
            } else if (sourceDoc.isFile()) {
                DocumentHelper.suCopyFileFromDocument(
                        resolver, sourceDoc.getUri(), new File(targetPath, sourceDoc.getName()).getAbsolutePath());
            }
        }
    }

    public static void suCopyFileFromDocument(ContentResolver resolver, Uri sourceUri, String targetPath) throws IOException {
        try (SuFileOutputStream outputFile = new SuFileOutputStream(targetPath)) {
            try (InputStream inputFile = resolver.openInputStream(sourceUri)) {
                IOUtils.copy(inputFile, outputFile);
            }
        }
    }
}
