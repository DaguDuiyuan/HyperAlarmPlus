package com.dagu.hyperalarmplus;

import android.content.Context;
import android.database.Cursor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Calendar;

public final class AlarmRuntimeHook {
    private final HookEnvironment env;

    public AlarmRuntimeHook(HookEnvironment env) {
        this.env = env;
    }

    public void install() throws Throwable {
        hookAlarmModel();
        hookDaysOfWeek();
        hookAlarmHelper();
    }

    private void hookAlarmModel() throws NoSuchMethodException {
        Constructor<?> cursorCtor = env.alarmClass.getDeclaredConstructor(Cursor.class);
        env.hook(cursorCtor, chain -> {
            Object result = chain.proceed();
            Object alarm = chain.getThisObject();
            int id = env.alarmIdField.getInt(alarm);
            Object daysOfWeek = env.alarmDaysOfWeekField.get(alarm);
            if (daysOfWeek != null
                    && env.getDays(daysOfWeek) == Constants.DAYS_LEGAL_WORKDAY
                    && env.isAlarmMarked(env.hostContext, id)) {
                env.daysField.setInt(daysOfWeek, Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
            }
            return result;
        });
    }

    private void hookDaysOfWeek() throws NoSuchMethodException {
        Method getAlarmType = env.daysOfWeekClass.getDeclaredMethod("getAlarmType");
        Method getNextAlarm = env.daysOfWeekClass.getDeclaredMethod("getNextAlarm", Context.class, Calendar.class);
        Method toString = env.daysOfWeekClass.getDeclaredMethod("toString", Context.class, boolean.class);
        Method getCoded = env.daysOfWeekClass.getDeclaredMethod("getCoded");

        env.hook(getAlarmType, chain -> {
            if (env.getDays(chain.getThisObject()) == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY) {
                return Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY;
            }
            return chain.proceed();
        });

        env.hook(getNextAlarm, chain -> {
            Object daysOfWeek = chain.getThisObject();
            if (env.getDays(daysOfWeek) != Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY) {
                return chain.proceed();
            }
            Context context = (Context) chain.getArg(0);
            Calendar calendar = (Calendar) chain.getArg(1);
            return env.getNextLegalWorkdayWithSaturday(context, calendar);
        });

        env.hook(toString, chain -> {
            if (env.getDays(chain.getThisObject()) != Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY) {
                return chain.proceed();
            }
            return env.buildRepeatLabel((Context) chain.getArg(0));
        });

        env.hook(getCoded, chain -> {
            if (Boolean.TRUE.equals(env.forceHostCoded.get())
                    && env.getDays(chain.getThisObject()) == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY) {
                return Constants.DAYS_LEGAL_WORKDAY;
            }
            return chain.proceed();
        });
    }

    private void hookAlarmHelper() throws Throwable {
        Method createContentValues = env.alarmHelperClass.getDeclaredMethod("createContentValues", Context.class, env.alarmClass);
        Method addAlarm = env.alarmHelperClass.getDeclaredMethod("addAlarm", Context.class, env.alarmClass);
        Method setAlarm = env.alarmHelperClass.getDeclaredMethod("setAlarm", Context.class, env.alarmClass);
        Method addWakeAlarm = env.alarmHelperClass.getDeclaredMethod("addWakeAlarm", Context.class, env.alarmClass);
        Method setWakeAlarm = env.alarmHelperClass.getDeclaredMethod("setWakeAlarm", Context.class, env.alarmClass);
        Method deleteAlarmInDb = env.alarmHelperClass.getDeclaredMethod("deleteAlarmInDb", Context.class, int.class);

        env.hook(createContentValues, chain -> {
            env.forceHostCoded.set(Boolean.TRUE);
            try {
                return chain.proceed();
            } finally {
                env.forceHostCoded.remove();
            }
        });

        env.hook(addAlarm, chain -> {
            Context context = (Context) chain.getArg(0);
            Object alarm = chain.getArg(1);
            env.rememberContext(context);
            boolean selected = env.isEnhancedAlarm(alarm);
            Object result = chain.proceed();
            int id = env.alarmIdField.getInt(alarm);
            env.setAlarmMarked(context, id, selected);
            env.refreshNextAlert(context);
            return result;
        });

        env.hook(setAlarm, chain -> {
            Context context = (Context) chain.getArg(0);
            Object alarm = chain.getArg(1);
            env.rememberContext(context);
            boolean selected = env.isEnhancedAlarm(alarm);
            int id = env.alarmIdField.getInt(alarm);
            env.setAlarmMarked(context, id, selected);
            Object result = chain.proceed();
            env.setAlarmMarked(context, id, selected);
            return result;
        });

        env.hook(addWakeAlarm, chain -> {
            Context context = (Context) chain.getArg(0);
            Object alarm = chain.getArg(1);
            env.rememberContext(context);
            boolean selected = env.isEnhancedAlarm(alarm);
            env.setAlarmMarked(context, Constants.WAKE_ALARM_ID, selected);
            Object result = chain.proceed();
            env.setAlarmMarked(context, Constants.WAKE_ALARM_ID, selected);
            return result;
        });

        env.hook(setWakeAlarm, chain -> {
            Context context = (Context) chain.getArg(0);
            Object alarm = chain.getArg(1);
            env.rememberContext(context);
            boolean selected = env.isEnhancedAlarm(alarm);
            env.setAlarmMarked(context, Constants.WAKE_ALARM_ID, selected);
            Object result = chain.proceed();
            env.setAlarmMarked(context, Constants.WAKE_ALARM_ID, selected);
            return result;
        });

        env.hook(deleteAlarmInDb, chain -> {
            Context context = (Context) chain.getArg(0);
            int id = (Integer) chain.getArg(1);
            Object result = chain.proceed();
            env.setAlarmMarked(context, id, false);
            return result;
        });
    }
}
