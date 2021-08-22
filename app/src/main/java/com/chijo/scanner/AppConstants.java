package com.chijo.scanner;

import android.os.Environment;

public class AppConstants {

    public static String DOCUMENT_SAVE_LOCATION = Environment.getExternalStorageDirectory() + "/Document Scanner/";
    public static String DEV_SAVE_LOCATION = Environment.getExternalStorageDirectory() + "/Document Scanner Dev/";
    public static int DOCUMENT_DELETE_CODE = 10001;
    public static int DOCUMENT_REORDERED_CODE = 10001;

    public static int TRANSFORMER_TYPE_ZOOM_OUT = 0;
    public static int TRANSFORMER_TYPE_DEPTH = 1;

}
