package com.machiav3lli.backup.items;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

import java.io.File;
import java.text.ParseException;
import java.util.Objects;

public class AppMetaInfo implements Parcelable {

    @SerializedName("packageName")
    private String packageName;

    @SerializedName("packageLabel")
    private String packageLabel;

    @SerializedName("versionName")
    private String versionName;

    @SerializedName("versionCode")
    private int versionCode;

    @SerializedName("profileId")
    private int profileId;

    @SerializedName("isSystem")
    private boolean isSystem;

    @SerializedName("icon")
    Drawable applicationIcon;

    AppMetaInfo() {
    }

    public AppMetaInfo(Context context, PackageInfo pi) {
        this.packageName = pi.packageName;
        this.packageLabel = pi.applicationInfo.loadLabel(context.getPackageManager()).toString();
        this.versionName = pi.versionName;
        this.versionCode = pi.versionCode;
        // Don't have access to UserManager service; using a cheap workaround to figure out
        // who is running by parsing it from the data path: /data/user/0/org.example.app
        try {
            this.profileId = Integer.parseInt(Objects.requireNonNull(new File(pi.applicationInfo.dataDir).getParentFile()).getName());
        } catch (NumberFormatException e) {
            // Android System "App" points to /data/system
            this.profileId = -1;
        }
        // Boolean arithmetic to check if FLAG_SYSTEM is set
        this.isSystem = (pi.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != ApplicationInfo.FLAG_SYSTEM;
        this.applicationIcon = context.getPackageManager().getApplicationIcon(pi.applicationInfo);
    }

    public AppMetaInfo(String packageName, String packageLabel, String versionName, int versionCode, int profileId, boolean isSystem) {
        this.packageName = packageName;
        this.packageLabel = packageLabel;
        this.versionName = versionName;
        this.versionCode = versionCode;
        this.profileId = profileId;
        this.isSystem = isSystem;
    }

    protected AppMetaInfo(Parcel in) {
        this.packageName = in.readString();
        this.packageLabel = in.readString();
        this.versionName = in.readString();
        this.versionCode = in.readInt();
        this.profileId = in.readInt();
        this.isSystem = in.readByte() != 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.packageName);
        dest.writeString(this.packageLabel);
        dest.writeString(this.versionName);
        dest.writeInt(this.versionCode);
        dest.writeInt(this.profileId);
        dest.writeByte((byte) (this.isSystem ? 1 : 0));
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<AppMetaInfo> CREATOR = new Creator<AppMetaInfo>() {
        @Override
        public AppMetaInfo createFromParcel(Parcel in) {
            return new AppMetaInfo(in);
        }

        @Override
        public AppMetaInfo[] newArray(int size) {
            return new AppMetaInfo[size];
        }
    };

    public String getPackageName() {
        return this.packageName;
    }

    public String getPackageLabel() {
        return this.packageLabel;
    }

    public String getVersionName() {
        return this.versionName;
    }

    public int getVersionCode() {
        return this.versionCode;
    }

    public int getProfileId() {
        return this.profileId;
    }

    public boolean isSystem() {
        return this.isSystem;
    }

    public boolean isSpecial() { return false; }

    public boolean hasIcon() {
        return this.applicationIcon != null;
    }

    public Drawable getApplicationIcon() {
        return this.applicationIcon;
    }
}