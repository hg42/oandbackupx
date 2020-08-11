package com.machiav3lli.backup.items;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.annotations.SerializedName;

public class SpecialAppMetaInfo extends AppMetaInfo implements Parcelable {

    @SerializedName("specialFiles")
    String[] files;

    public SpecialAppMetaInfo(String packageName, String label, String versionName, int versionCode, String[] fileList){
        super(packageName, label, versionName, versionCode, 0, true);
        this.files = fileList;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public String[] getFileList() {
        return this.files;
    }

    protected SpecialAppMetaInfo(Parcel in){
        super(in);
        this.files = new String[in.readInt()];
        in.readStringArray(this.files);
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.files.length);
        dest.writeStringArray(this.files);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    public static final Creator<SpecialAppMetaInfo> CREATOR = new Creator<SpecialAppMetaInfo>() {
        @Override
        public SpecialAppMetaInfo createFromParcel(Parcel in) {
            return new SpecialAppMetaInfo(in);
        }

        @Override
        public SpecialAppMetaInfo[] newArray(int size) {
            return new SpecialAppMetaInfo[size];
        }
    };
}
