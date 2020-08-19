package com.machiav3lli.backup.items;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;

import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;

public class BackupProperties extends AppMetaInfo implements Parcelable {

    public static final String PROPERTIES_FILENAME = "backup.properties";

    @SerializedName("backupDate")
    private LocalDateTime backupDate;

    @SerializedName("hasApk")
    private boolean hasApk;

    @SerializedName("hasAppData")
    private boolean hasAppData;

    @SerializedName("hasDevicesProtectedData")
    private boolean hasDevicesProtectedData;

    @SerializedName("hasExternalData")
    private boolean hasExternalData;

    @SerializedName("hasObbData")
    private boolean hasObbData;

    @SerializedName("cipherType")
    private String cipherType;

    private Uri backupLocation;

    public BackupProperties(Uri backupLocation, Context context, PackageInfo pi, LocalDateTime backupDate,
                            boolean hasApk, boolean hasAppData, boolean hasDevicesProtectedData,
                            boolean hasExternalData, boolean hasObbData, String cipherType) {
        super(context, pi);
        this.backupLocation = backupLocation;
        this.backupDate = backupDate;
        this.hasApk = hasApk;
        this.hasAppData = hasAppData;
        this.hasDevicesProtectedData = hasDevicesProtectedData;
        this.hasExternalData = hasExternalData;
        this.hasObbData = hasObbData;
        this.cipherType = cipherType;
    }


    public BackupProperties(Uri backupLocation, AppMetaInfo base, LocalDateTime backupDate,
                            boolean hasApk, boolean hasAppData, boolean hasDevicesProtectedData,
                            boolean hasExternalData, boolean hasObbData, String cipherType) {
        super(base.getPackageName(), base.getPackageLabel(), base.getVersionName(),
                base.getVersionCode(), base.getProfileId(), base.isSystem());
        this.backupLocation = backupLocation;
        this.backupDate = backupDate;
        this.hasApk = hasApk;
        this.hasAppData = hasAppData;
        this.hasDevicesProtectedData = hasDevicesProtectedData;
        this.hasExternalData = hasExternalData;
        this.hasObbData = hasObbData;
        this.cipherType = cipherType;
    }

    public BackupProperties(Uri backupLocation, String packageName, String packageLabel, String versionName,
                            int versionCode, int profileId, boolean isSystem, LocalDateTime backupDate,
                            boolean hasApk, boolean hasAppData, boolean hasDevicesProtectedData,
                            boolean hasExternalData, boolean hasObbData, String cipherType) {
        super(packageName, packageLabel, versionName, versionCode, profileId, isSystem);
        this.backupLocation = backupLocation;
        this.backupDate = backupDate;
        this.hasApk = hasApk;
        this.hasAppData = hasAppData;
        this.hasDevicesProtectedData = hasDevicesProtectedData;
        this.hasExternalData = hasExternalData;
        this.hasObbData = hasObbData;
        this.cipherType = cipherType;
    }

    BackupProperties() {
        super();
    }

    protected BackupProperties(Parcel in) {
        this.backupLocation = in.readParcelable(Uri.class.getClassLoader());
        this.hasApk = in.readByte() != 0;
        this.hasAppData = in.readByte() != 0;
        this.hasDevicesProtectedData = in.readByte() != 0;
        this.hasExternalData = in.readByte() != 0;
        this.hasObbData = in.readByte() != 0;
        this.cipherType = in.readString();
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.backupLocation, flags);
        dest.writeByte((byte) (this.hasApk ? 1 : 0));
        dest.writeByte((byte) (this.hasAppData ? 1 : 0));
        dest.writeByte((byte) (this.hasDevicesProtectedData ? 1 : 0));
        dest.writeByte((byte) (this.hasExternalData ? 1 : 0));
        dest.writeByte((byte) (this.hasObbData ? 1 : 0));
        dest.writeString(this.cipherType);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<BackupProperties> CREATOR = new Creator<BackupProperties>() {
        @Override
        public BackupProperties createFromParcel(Parcel in) {
            return new BackupProperties(in);
        }

        @Override
        public BackupProperties[] newArray(int size) {
            return new BackupProperties[size];
        }
    };

    public static BackupProperties fromGson(Uri backupLocation, String gson) {
        BackupProperties result = new Gson().fromJson(gson, BackupProperties.class);
        result.setBackupLocation(backupLocation);
        return result;
    }

    public String toGson() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }

    public LocalDateTime getBackupDate() {
        return this.backupDate;
    }

    public boolean hasApk() {
        return this.hasApk;
    }

    public boolean hasAppData() {
        return this.hasAppData;
    }

    public boolean hasDevicesProtectedData() {
        return this.hasDevicesProtectedData;
    }

    public boolean hasExternalData() {
        return this.hasExternalData;
    }

    public boolean hasObbData() {
        return this.hasObbData;
    }

    public String getCipherType() {
        return this.cipherType;
    }

    public boolean isEncrypted(){
        return this.cipherType != null && !this.cipherType.isEmpty();
    }

    private void setBackupLocation(@NotNull Uri backupLocation){
        this.backupLocation = backupLocation;
    }

}
