package it.moondroid.driveapi;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.android.gms.drive.DriveId;

/**
 * Created by Marco on 16/02/2015.
 */
public class Preferences {

    public static final String PREFS_NAME = "MyPrefsFile";
    public static final String KEY_DRIVE_ID = "KEY_DRIVE_ID";

    public static String getDriveId(Context context){
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return settings.getString(KEY_DRIVE_ID, "");
    }

    public static void setDriveId(Context context, String driveId){
        SharedPreferences settings = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = settings.edit();
        editor.putString(KEY_DRIVE_ID, driveId);
        editor.commit();
    }
}
