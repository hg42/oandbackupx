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
package com.machiav3lli.backup.items;

import android.os.Parcel;
import android.os.Parcelable;
import android.view.View;

import androidx.appcompat.widget.AppCompatCheckBox;
import androidx.appcompat.widget.AppCompatTextView;

import com.google.android.material.chip.Chip;
import com.machiav3lli.backup.R;
import com.machiav3lli.backup.utils.ItemUtils;
import com.mikepenz.fastadapter.FastAdapter;
import com.mikepenz.fastadapter.items.AbstractItem;

import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.machiav3lli.backup.utils.ItemUtils.calculateID;

public class BatchItemX extends AbstractItem<BatchItemX.ViewHolder> implements Parcelable {
    public static final Creator<BatchItemX> CREATOR = new Creator<BatchItemX>() {
        @Override
        public BatchItemX createFromParcel(Parcel in) {
            return new BatchItemX(in);
        }

        @Override
        public BatchItemX[] newArray(int size) {
            return new BatchItemX[size];
        }
    };
    AppInfo app;

    public BatchItemX(AppInfo app) {
        this.app = app;
    }

    protected BatchItemX(Parcel in) {
        app = in.readParcelable(AppInfo.class.getClassLoader());
    }

    public AppInfo getApp() {
        return app;
    }

    @Override
    public long getIdentifier() {
        return calculateID(app);
    }

    @Override
    public int getLayoutRes() {
        return R.layout.item_batch_x;
    }

    @NotNull
    @Override
    public ViewHolder getViewHolder(@NotNull View view) {
        return new ViewHolder(view);
    }

    @Override
    public int getType() {
        return R.id.fastadapter_item;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(app.getLogInfo(), flags);
        dest.writeString(app.getLabel());
        dest.writeString(app.getPackageName());
        dest.writeString(app.getVersionName());
        dest.writeString(app.getSourceDir());
        dest.writeString(app.getDataDir());
        dest.writeInt(app.getVersionCode());
        dest.writeInt(app.getBackupMode());
        dest.writeBooleanArray(new boolean[]{app.isSystem(), app.isInstalled(), app.isChecked()});
        dest.writeParcelable(app.getIcon(), flags);
    }

    protected static class ViewHolder extends FastAdapter.ViewHolder<BatchItemX> {
        AppCompatCheckBox checkbox = itemView.findViewById(R.id.enableCheckbox);
        AppCompatTextView label = itemView.findViewById(R.id.label);
        AppCompatTextView packageName = itemView.findViewById(R.id.packageName);
        AppCompatTextView lastBackup = itemView.findViewById(R.id.lastBackup);
        Chip backupMode = itemView.findViewById(R.id.backupMode);
        Chip appType = itemView.findViewById(R.id.appType);
        Chip update = itemView.findViewById(R.id.update);

        public ViewHolder(View view) {
            super(view);
        }

        @Override
        public void bindView(@NotNull BatchItemX item, @NotNull List<?> list) {
            final AppInfo app = item.getApp();

            checkbox.setChecked(app.isChecked());
            label.setText(app.getLabel());
            packageName.setText(app.getPackageName());
            if (app.getLogInfo() != null) {
                lastBackup.setVisibility(View.VISIBLE);
                lastBackup.setText(ItemUtils.getFormattedDate(app.getLogInfo().getLastBackupMillis(),false));
            } else lastBackup.setVisibility(View.GONE);
            if (app.isUpdated()) {
                update.setVisibility(View.VISIBLE);
            } else update.setVisibility(View.GONE);
            ItemUtils.pickBackupMode(app.getBackupMode(), backupMode);
            ItemUtils.pickAppType(app, appType);
        }

        @Override
        public void unbindView(@NotNull BatchItemX item) {
            checkbox.setText(null);
            label.setText(null);
            packageName.setText(null);
            lastBackup.setText(null);
            backupMode.setText(null);
            appType.setText(null);
        }
    }
}
