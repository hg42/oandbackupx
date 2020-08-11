package com.machiav3lli.backup.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import androidx.documentfile.provider.DocumentFile;

import com.machiav3lli.backup.Constants;

import java.io.File;

public final class DocumentHelper {

    public static DocumentFile getDocumentDir(Context context, String uri){
        // assume the stored path is an URI and it can be used with Storage Access Framework
        // if it fails to parse it as URI, it's likely just a legacy plain path

        // Todo: Remove this maybe
        try{
            return DocumentFile.fromTreeUri(context, Uri.parse(uri));
        } catch (IllegalArgumentException e) {
            return DocumentFile.fromFile(new File(uri));
        }
    }

    public static DocumentFile getBackupRoot(Context context){
        SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_SHARED_PRIVATE, Context.MODE_PRIVATE);
        String backupBaseDir = prefs.getString(Constants.PREFS_PATH_BACKUP_DIRECTORY, FileUtils.DEFAULT_BACKUP_FOLDER);
        return DocumentHelper.getDocumentDir(context, backupBaseDir);
    }

    public static DocumentFile getDocumentFile(Context context, String uri){
        try{
            return DocumentFile.fromSingleUri(context, Uri.parse(uri));
        } catch (IllegalArgumentException e) {
            return DocumentFile.fromFile(new File(uri));
        }
    }

    public static DocumentFile getDirFromBackupRoot(Context context, String relativePath){
        Uri newUri = DocumentHelper.getBackupRoot(context)
                .getUri()
                .buildUpon()
                .appendPath(relativePath)
                .build();
        return DocumentFile.fromTreeUri(context, newUri);
    }
    public static DocumentFile ensureDirectory(DocumentFile base, String dirName){
        DocumentFile dir = base.findFile(dirName);
        if(dir == null){
            dir = base.createDirectory(dirName);
        }
        return dir;
    }

    public static void copyFile()
}
