package com.dagu.hyperalarmplus;

final class Constants {
    static final String TARGET_PACKAGE = "com.android.deskclock";
    static final String TAG = "HyperAlarmPlus";

    static final int TYPE_ONLY_ONCE = 0;
    static final int TYPE_EVERY_DAY = 1;
    static final int TYPE_LEGAL_WORKDAY = 2;
    static final int TYPE_LEGAL_OFF_DAY = 3;
    static final int TYPE_MON_TO_FRI = 4;
    static final int TYPE_CUSTOM = 5;
    static final int TYPE_SHIFT_WORK = 6;
    static final int TYPE_SELF_DEFINE = 7;
    static final int TYPE_LEGAL_WORKDAY_WITH_SATURDAY = 8;

    static final int DAYS_ONLY_ONCE = 0;
    static final int DAYS_MON_TO_FRI = 31;
    static final int DAYS_EVERY_DAY = 127;
    static final int DAYS_LEGAL_WORKDAY = 128;
    static final int DAYS_LEGAL_OFF_DAY = 256;
    static final int DAYS_SHIFT_WORK = 512;
    static final int DAYS_LEGAL_WORKDAY_WITH_SATURDAY = 1024;
    static final int WAKE_ALARM_ID = Integer.MIN_VALUE;

    static final String PREF_NAME = "hyper_alarm_plus";
    static final String PREF_ALARM_IDS = "legal_workday_with_saturday_ids";
    static final String PREF_MIGRATED = "migrated_to_device_protected";

    private Constants() {
    }
}
