package com.dagu.hyperalarmplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.libxposed.api.XposedInterface;

final class HookEnvironment {
    final XposedInterface base;
    final ClassLoader classLoader;
    volatile Context hostContext;

    Class<?> alarmClass;
    Class<?> daysOfWeekClass;
    Class<?> holidayHelperClass;
    Class<?> holidayInstanceClass;
    Class<?> dataBeanClass;
    Class<?> repeatControllerClass;
    Class<?> alarmHelperClass;

    Field alarmIdField;
    Field alarmDaysOfWeekField;
    Field daysField;
    Field dataBeanGroupIdField;
    Field dataBeanRepeatTypeField;
    Field repeatControllerDaysOfWeekField;
    Field repeatControllerNewDaysOfWeekField;

    Method isHolidayMethod;
    Method isHolidayDataInvalidMethod;
    Method getHolidayInstanceMethod;
    Method isFreedayMethod;
    Method setNextAlertMethod;
    Method setWakeAlarmMethod;

    final ThreadLocal<Boolean> forceHostCoded = new ThreadLocal<>();

    HookEnvironment(XposedInterface base, ClassLoader classLoader) {
        this.base = base;
        this.classLoader = classLoader;
    }

    void initReflection() throws Throwable {
        alarmClass = Class.forName("com.android.deskclock.Alarm", false, classLoader);
        daysOfWeekClass = Class.forName("com.android.deskclock.Alarm$DaysOfWeek", false, classLoader);
        holidayHelperClass = Class.forName("com.android.deskclock.addition.holiday.HolidayHelper", false, classLoader);
        holidayInstanceClass = Class.forName("com.android.deskclock.addition.holiday.HolidayInstance", false, classLoader);
        dataBeanClass = Class.forName("com.android.deskclock.alarm.DataBean", false, classLoader);
        repeatControllerClass = Class.forName("com.android.deskclock.alarm.RepeatAlarmController", false, classLoader);
        alarmHelperClass = Class.forName("com.android.deskclock.util.AlarmHelper", false, classLoader);

        alarmIdField = declaredField(alarmClass, "id");
        alarmDaysOfWeekField = declaredField(alarmClass, "daysOfWeek");
        daysField = declaredField(daysOfWeekClass, "mDays");
        dataBeanGroupIdField = declaredField(dataBeanClass, "groupId");
        dataBeanRepeatTypeField = declaredField(dataBeanClass, "repeatType");
        repeatControllerDaysOfWeekField = declaredField(repeatControllerClass, "mDaysOfWeek");
        repeatControllerNewDaysOfWeekField = declaredField(repeatControllerClass, "mNewDaysOfWeek");

        isHolidayMethod = holidayHelperClass.getDeclaredMethod("isHoliday", Context.class, Calendar.class);
        isHolidayMethod.setAccessible(true);
        isHolidayDataInvalidMethod = holidayHelperClass.getDeclaredMethod("isHolidayDataInvalid", Context.class);
        isHolidayDataInvalidMethod.setAccessible(true);
        getHolidayInstanceMethod = holidayInstanceClass.getDeclaredMethod("getInstance", Context.class);
        getHolidayInstanceMethod.setAccessible(true);
        isFreedayMethod = holidayInstanceClass.getDeclaredMethod("isFreeday", int.class, int.class);
        isFreedayMethod.setAccessible(true);
        setNextAlertMethod = alarmHelperClass.getDeclaredMethod("setNextAlert", Context.class);
        setNextAlertMethod.setAccessible(true);
        setWakeAlarmMethod = alarmHelperClass.getDeclaredMethod("setWakeAlarm", Context.class, alarmClass);
        setWakeAlarmMethod.setAccessible(true);
    }

    void hook(Executable executable, XposedInterface.Hooker hooker) {
        executable.setAccessible(true);
        base.hook(executable)
                .setPriority(XposedInterface.PRIORITY_DEFAULT)
                .intercept(hooker);
    }

    void rememberContext(Context context) {
        if (context != null) {
            hostContext = context.getApplicationContext();
        }
    }

    void logInfo(String message) {
        base.log(4, Constants.TAG, message);
    }

    void logError(String message, Throwable throwable) {
        base.log(6, Constants.TAG, message, throwable);
    }

    @SuppressWarnings("unchecked")
    <T> T getObjectField(Object target, String name) {
        if (target == null) {
            return null;
        }
        try {
            Field field = declaredField(target.getClass(), name);
            return (T) field.get(target);
        } catch (Throwable ignored) {
            return null;
        }
    }

    Object getStaticObjectField(Class<?> clazz, String name) throws Throwable {
        Field field = declaredField(clazz, name);
        return field.get(null);
    }

    void setStaticIntField(Class<?> clazz, String name, int value) {
        try {
            Field field = declaredField(clazz, name);
            field.setInt(null, value);
        } catch (Throwable t) {
            logError("Failed to set " + clazz.getName() + "#" + name, t);
        }
    }

    int getStaticIntField(Class<?> clazz, String name) {
        try {
            Field field = declaredField(clazz, name);
            return field.getInt(null);
        } catch (Throwable t) {
            logError("Failed to get " + clazz.getName() + "#" + name, t);
            return -1;
        }
    }

    Method findDeclaredMethod(Class<?> clazz, String name, int parameterCount) throws NoSuchMethodException {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(clazz.getName() + "#" + name + " with " + parameterCount + " params");
    }
    Method findDeclaredMethodOrNull(Class<?> clazz, String name, int parameterCount) {
        try {
            return findDeclaredMethod(clazz, name, parameterCount);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    Method findMethodByName(Class<?> clazz, String name, int parameterCount) throws NoSuchMethodException {
        Class<?> cursor = clazz;
        while (cursor != null) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.getName().equals(name) && method.getParameterTypes().length == parameterCount) {
                    method.setAccessible(true);
                    return method;
                }
            }
            cursor = cursor.getSuperclass();
        }
        throw new NoSuchMethodException(clazz.getName() + "#" + name + " with " + parameterCount + " params");
    }

    Field declaredField(Class<?> clazz, String name) throws NoSuchFieldException {
        Class<?> cursor = clazz;
        while (cursor != null) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                cursor = cursor.getSuperclass();
            }
        }
        throw new NoSuchFieldException(clazz.getName() + "#" + name);
    }

    int getDays(Object daysOfWeek) throws IllegalAccessException {
        return daysField.getInt(daysOfWeek);
    }

    void setRawDays(Object daysOfWeek, int days) throws IllegalAccessException {
        if (daysOfWeek != null) {
            daysField.setInt(daysOfWeek, days);
        }
    }

    int alarmTypeForDays(int days) {
        if (days == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY) {
            return Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY;
        }
        if (days == Constants.DAYS_ONLY_ONCE) {
            return Constants.TYPE_ONLY_ONCE;
        }
        if (days == Constants.DAYS_EVERY_DAY) {
            return Constants.TYPE_EVERY_DAY;
        }
        if (days == Constants.DAYS_LEGAL_WORKDAY) {
            return Constants.TYPE_LEGAL_WORKDAY;
        }
        if (days == Constants.DAYS_LEGAL_OFF_DAY) {
            return Constants.TYPE_LEGAL_OFF_DAY;
        }
        if (days == Constants.DAYS_MON_TO_FRI) {
            return Constants.TYPE_MON_TO_FRI;
        }
        if (days == Constants.DAYS_SHIFT_WORK) {
            return Constants.TYPE_SHIFT_WORK;
        }
        return Constants.TYPE_CUSTOM;
    }

    void setControllerDays(int days) throws Throwable {
        Object mDaysOfWeek = repeatControllerDaysOfWeekField.get(null);
        Object mNewDaysOfWeek = repeatControllerNewDaysOfWeekField.get(null);
        setRawDays(mDaysOfWeek, days);
        setRawDays(mNewDaysOfWeek, days);
    }

    boolean isControllerEnhanced() {
        try {
            Object daysOfWeek = getControllerDaysOfWeek();
            return daysOfWeek != null && getDays(daysOfWeek) == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY;
        } catch (Throwable ignored) {
            return false;
        }
    }

    Object getControllerDaysOfWeek() {
        try {
            return repeatControllerDaysOfWeekField.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    int getRepeatType(Object dataBean) throws IllegalAccessException {
        return dataBeanRepeatTypeField.getInt(dataBean);
    }

    Object getDataBeanAt(Object adapter, int position) {
        try {
            Object data = getObjectField(adapter, "dataList");
            if (data instanceof List<?> list && position >= 0 && position < list.size()) {
                return list.get(position);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    int indexOfRepeatType(List<Object> data, int repeatType) throws IllegalAccessException {
        if (data == null) {
            return -1;
        }
        for (int i = 0; i < data.size(); i++) {
            if (getRepeatType(data.get(i)) == repeatType) {
                return i;
            }
        }
        return -1;
    }

    int indexOfRepeatType(Object adapter, int repeatType) throws IllegalAccessException {
        Object data = getObjectField(adapter, "dataList");
        if (data instanceof List<?> list) {
            @SuppressWarnings("unchecked")
            List<Object> typed = (List<Object>) list;
            return indexOfRepeatType(typed, repeatType);
        }
        return -1;
    }

    boolean containsRepeatType(List<Object> data, int repeatType) throws IllegalAccessException {
        return indexOfRepeatType(data, repeatType) >= 0;
    }

    boolean isEnhancedAlarm(Object alarm) {
        try {
            Object daysOfWeek = alarm == null ? null : alarmDaysOfWeekField.get(alarm);
            return daysOfWeek != null && getDays(daysOfWeek) == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private volatile SharedPreferences alarmPrefs;
    private volatile boolean alarmPrefsMigrated;

    void setAlarmMarked(Context context, int id, boolean marked) {
        if (context == null || (id < 0 && id != Constants.WAKE_ALARM_ID)) {
            return;
        }
        try {
            SharedPreferences prefs = getAlarmPrefs(context);
            Set<String> ids = new HashSet<>(prefs.getStringSet(Constants.PREF_ALARM_IDS, new HashSet<>()));
            String key = String.valueOf(id);
            if (ids.contains(key) == marked) {
                return;
            }
            if (marked) {
                ids.add(key);
            } else {
                ids.remove(key);
            }
            prefs.edit().putStringSet(Constants.PREF_ALARM_IDS, ids).apply();
        } catch (Throwable t) {
            logError("Failed to update alarm mark", t);
        }
    }

    boolean isAlarmMarked(Context context, int id) {
        if (context == null || (id < 0 && id != Constants.WAKE_ALARM_ID)) {
            return false;
        }
        try {
            SharedPreferences prefs = getAlarmPrefs(context);
            Set<String> ids = prefs.getStringSet(Constants.PREF_ALARM_IDS, null);
            return ids != null && ids.contains(String.valueOf(id));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private SharedPreferences getAlarmPrefs(Context context) {
        SharedPreferences prefs = alarmPrefs;
        if (prefs == null) {
            synchronized (this) {
                prefs = alarmPrefs;
                if (prefs == null) {
                    prefs = createAlarmPrefs(context);
                    alarmPrefs = prefs;
                }
            }
        }
        if (!alarmPrefsMigrated) {
            migrateAlarmPrefs(context, prefs);
        }
        return prefs;
    }

    private SharedPreferences createAlarmPrefs(Context context) {
        Context storage = context;
        try {
            Context deviceProtected = context.createDeviceProtectedStorageContext();
            if (deviceProtected != null) {
                storage = deviceProtected;
            }
        } catch (Throwable ignored) {
        }
        return storage.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
    }

    private void migrateAlarmPrefs(Context context, SharedPreferences target) {
        if (target.getBoolean(Constants.PREF_MIGRATED, false)) {
            alarmPrefsMigrated = true;
            return;
        }
        try {
            SharedPreferences source = context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE);
            Set<String> ids = source.getStringSet(Constants.PREF_ALARM_IDS, null);
            if (ids != null && !ids.isEmpty()) {
                Set<String> merged = new HashSet<>(target.getStringSet(Constants.PREF_ALARM_IDS, new HashSet<>()));
                merged.addAll(ids);
                target.edit().putStringSet(Constants.PREF_ALARM_IDS, merged).apply();
            }
            target.edit().putBoolean(Constants.PREF_MIGRATED, true).apply();
            alarmPrefsMigrated = true;
        } catch (Throwable ignored) {
            // Credential-encrypted storage is still locked; retry after unlock.
        }
    }

    void refreshNextAlert(Context context) {
        if (context == null) {
            return;
        }
        try {
            setNextAlertMethod.invoke(null, context);
        } catch (Throwable t) {
            logError("Failed to refresh next alert", t);
        }
    }

    void saveWakeAlarm(Context context, Object wakeAlarm) throws Throwable {
        if (context != null && wakeAlarm != null) {
            setWakeAlarmMethod.invoke(null, context, wakeAlarm);
        }
    }

    int getNextLegalWorkdayWithSaturday(Context context, Calendar start) throws Throwable {
        Calendar cursor = (Calendar) start.clone();
        for (int offset = 0; offset < 10; offset++) {
            if (shouldRingOnLegalWorkdayWithSaturday(context, cursor)) {
                return offset;
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1);
        }

        int offset = 10;
        while (offset < 370) {
            if (shouldRingOnLegalWorkdayWithSaturday(context, cursor)) {
                return offset;
            }
            cursor.add(Calendar.DAY_OF_YEAR, 1);
            offset++;
        }
        return -1;
    }

    private boolean shouldRingOnLegalWorkdayWithSaturday(Context context, Calendar calendar) throws Throwable {
        boolean holiday = (Boolean) isHolidayMethod.invoke(null, context, calendar);
        if (!holiday) {
            return true;
        }
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        if (dayOfWeek != Calendar.SATURDAY) {
            return false;
        }
        return !isLegalFreeday(context, calendar);
    }

    private boolean isLegalFreeday(Context context, Calendar calendar) throws Throwable {
        Object instance = getHolidayInstanceMethod.invoke(null, context);
        return (Boolean) isFreedayMethod.invoke(
                instance,
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.DAY_OF_YEAR)
        );
    }

    boolean isHolidayDataInvalid(Context context) {
        try {
            return (Boolean) isHolidayDataInvalidMethod.invoke(null, context);
        } catch (Throwable ignored) {
            return false;
        }
    }

    String buildRepeatLabel(Context context) {
        String label = getHostString(context, "legal_workday", "\u6cd5\u5b9a\u5de5\u4f5c\u65e5");
        if (context != null && isHolidayDataInvalid(context)) {
            return label + "\uff08\u6570\u636e\u8fc7\u671f\uff0c\u542b\u5468\u516d\uff09";
        }
        return label + "\uff08\u542b\u5468\u516d\uff09";
    }

    String getHostString(Context context, String name, String fallback) {
        try {
            int id = context.getResources().getIdentifier(name, "string", Constants.TARGET_PACKAGE);
            if (id != 0) {
                return context.getString(id);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    int resolveHostColor(Context context, String name, int fallback) {
        try {
            int id = context.getResources().getIdentifier(name, "color", Constants.TARGET_PACKAGE);
            if (id != 0) {
                return context.getColor(id);
            }
        } catch (Throwable ignored) {
        }
        return fallback;
    }

    boolean containsLabel(String[] values, String labelPart) {
        if (values == null) {
            return false;
        }
        for (String value : values) {
            if (value != null && value.contains(labelPart)) {
                return true;
            }
        }
        return false;
    }

    void setAdapterChecked(Object adapter, int type) throws Throwable {
        Method setRepeatItemChecked = adapter.getClass().getDeclaredMethod("setRepeatItemChecked", int.class);
        setRepeatItemChecked.setAccessible(true);
        setRepeatItemChecked.invoke(adapter, type);
    }

    void applyEnhancedRepeatSummary(Object setAlarmController) {
        try {
            TextView repeatValue = getObjectField(setAlarmController, "mRepeatValueTv");
            Context context = getObjectField(setAlarmController, "mActivity");
            if (repeatValue != null) {
                repeatValue.setText(buildRepeatLabel(context));
            }
        } catch (Throwable t) {
            logError("Failed to apply enhanced repeat summary", t);
        }
    }

    boolean isEnhancedRepeatSummary(Object setAlarmController) {
        try {
            TextView repeatValue = getObjectField(setAlarmController, "mRepeatValueTv");
            CharSequence text = repeatValue == null ? null : repeatValue.getText();
            return text != null && text.toString().contains("\u542b\u5468\u516d");
        } catch (Throwable ignored) {
            return false;
        }
    }

    boolean isEnhancedRepeatSelected(Object setAlarmController) {
        try {
            Object repeatController = getObjectField(setAlarmController, "mRepeatAlarmController");
            Object adapter = repeatController == null ? null : getObjectField(repeatController, "mAlarmRepeatAdapter");
            if (adapter == null) {
                return isEnhancedRepeatSummary(setAlarmController);
            }
            int checked = getStaticIntField(adapter.getClass(), "mCheckedItem");
            Object dataBean = getDataBeanAt(adapter, checked);
            return dataBean != null && getRepeatType(dataBean) == Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY;
        } catch (Throwable ignored) {
            return isEnhancedRepeatSummary(setAlarmController);
        }
    }

    void applyEnhancedBedtimeRepeat(Object fragment) {
        try {
            Context context = getObjectField(fragment, "mActivity");
            if (context == null) {
                context = getObjectField(fragment, "mAppContext");
            }
            Object preference = getObjectField(fragment, "mRepeatTypePreference");
            if (preference != null) {
                Method setPrefValue = preference.getClass().getMethod("setPrefValue", String.class);
                setPrefValue.invoke(preference, buildRepeatLabel(context));
            }

            Object wakeAlarm = getObjectField(fragment, "mWakeAlarm");
            Object daysOfWeek = wakeAlarm == null ? null : alarmDaysOfWeekField.get(wakeAlarm);
            if (daysOfWeek != null) {
                setRawDays(daysOfWeek, Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
            }
        } catch (Throwable t) {
            logError("Failed to apply enhanced bedtime repeat", t);
        }
    }
}
