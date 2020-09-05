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
package com.machiav3lli.backup.utils;

import android.graphics.Color;

import androidx.appcompat.widget.AppCompatTextView;

import com.machiav3lli.backup.Constants;
import com.machiav3lli.backup.items.AppInfo;
import com.machiav3lli.backup.schedules.db.Schedule;

import java.text.DateFormat;
import java.util.Calendar;
import java.util.Date;
import com.machiav3lli.backup.items.AppInfoV2;

public final class ItemUtils {
    private static final String TAG = Constants.classTag(".ItemUtils");

    public static long calculateID(AppInfo app) {
        return app.getPackageName().hashCode()
                + app.getBackupMode()
                + (app.isDisabled() ? 0 : 1)
                + (app.isInstalled() ? 1 : 0)
                + (app.getLogInfo() != null ? app.getLogInfo().getLastBackupMillis() : 0)
                + app.getCacheSize();
    }

    public static long calculateScheduleID(Schedule sched) {
        return sched.getId()
                + sched.getInterval() * 24
                + sched.getHour()
                + sched.getMode().getValue()
                + sched.getSubmode().getValue()
                + (sched.isEnabled() ? 1 : 0);
    }

    public static String getFormattedDate(long lastUpdate, boolean withTime) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(lastUpdate);
        Date date = calendar.getTime();
        DateFormat dateFormat = withTime ? DateFormat.getDateTimeInstance() : DateFormat.getDateInstance();
        return dateFormat.format(date);
    }

    public static long calculateID(AppInfoV2 app) {
        return app.getPackageName().hashCode();
    }

    public static void pickTypeColor(AppInfoV2 app, AppCompatTextView text) {
        int color;
        if (app.isInstalled()) {
            color = app.getAppInfo().isSystem() ? Color.rgb(64, 158, 172) : Color.rgb(172, 64, 158);
            // int color = app.isSystem() ? app.isSpecial() ? Color.rgb(158, 172, 64) : Color.rgb(64, 158, 172) : Color.rgb(172, 64, 158);
            // if (app.isDisabled()) color = Color.DKGRAY;
            text.setTextColor(color);
        } else {
            text.setTextColor(Color.GRAY);
        }
    }
}
