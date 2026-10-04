package com.dagu.hyperalarmplus;

import android.content.Context;
import android.content.SharedPreferences;
import android.view.View;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

public final class BedtimeHook {
    private final HookEnvironment env;
    private final Set<Object> patchedBedtimePickers = Collections.newSetFromMap(new IdentityHashMap<>());

    public BedtimeHook(HookEnvironment env) {
        this.env = env;
    }

    public void install() throws Throwable {
        Throwable failure = null;
        try {
            hookBedtimeUtil();
        } catch (Throwable t) {
            env.logError("Failed to install bedtime util hooks", t);
            failure = t;
        }
        try {
            hookBedtimeGuide();
        } catch (Throwable t) {
            env.logError("Failed to install bedtime guide hooks", t);
            if (failure == null) {
                failure = t;
            }
        }
        try {
            hookBedtimeSettings();
        } catch (Throwable t) {
            env.logError("Failed to install bedtime settings hooks", t);
            if (failure == null) {
                failure = t;
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void hookBedtimeUtil() throws Throwable {
        Class<?> bedtimeUtilClass = Class.forName("com.android.deskclock.alarm.bedtime.BedtimeUtil", false, env.classLoader);
        Method getWakeDaysOfWeek = bedtimeUtilClass.getDeclaredMethod("getWakeDaysOfWeek", SharedPreferences.class);
        Method getTempWakeRepeat = bedtimeUtilClass.getDeclaredMethod("getTempWakeRepeat", Context.class);
        Method saveTempWakeAlarm = bedtimeUtilClass.getDeclaredMethod("saveTempWakeAlarm", Context.class, env.alarmClass);
        Method queryWakeAlarm = bedtimeUtilClass.getDeclaredMethod("queryWakeAlarm", Context.class);
        Method queryWakeAlarmForAddition = bedtimeUtilClass.getDeclaredMethod("queryWakeAlarmForAddition", Context.class);
        Method queryBedtimeForAddition = bedtimeUtilClass.getDeclaredMethod("queryBedtimeForAddition", Context.class);

        env.hook(queryWakeAlarm, chain -> {
            env.rememberContext((Context) chain.getArg(0));
            return chain.proceed();
        });

        env.hook(queryWakeAlarmForAddition, chain -> {
            env.rememberContext((Context) chain.getArg(0));
            return chain.proceed();
        });

        env.hook(queryBedtimeForAddition, chain -> {
            env.rememberContext((Context) chain.getArg(0));
            return chain.proceed();
        });

        env.hook(getWakeDaysOfWeek, chain -> {
            int result = (Integer) chain.proceed();
            if (result == Constants.DAYS_LEGAL_WORKDAY && env.isAlarmMarked(env.hostContext, Constants.WAKE_ALARM_ID)) {
                return Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY;
            }
            return result;
        });

        env.hook(getTempWakeRepeat, chain -> {
            Context context = (Context) chain.getArg(0);
            env.rememberContext(context);
            int result = (Integer) chain.proceed();
            if (result == Constants.DAYS_LEGAL_WORKDAY && env.isAlarmMarked(context, Constants.WAKE_ALARM_ID)) {
                return Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY;
            }
            return result;
        });

        env.hook(saveTempWakeAlarm, chain -> {
            Context context = (Context) chain.getArg(0);
            Object alarm = chain.getArg(1);
            env.rememberContext(context);
            Object daysOfWeek = alarm == null ? null : env.alarmDaysOfWeekField.get(alarm);
            boolean selected = daysOfWeek != null && env.getDays(daysOfWeek) == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY;
            env.forceHostCoded.set(Boolean.TRUE);
            try {
                Object result = chain.proceed();
                env.setAlarmMarked(context, Constants.WAKE_ALARM_ID, selected);
                return result;
            } finally {
                env.forceHostCoded.remove();
            }
        });
    }

    private void hookBedtimeGuide() throws Throwable {
        Class<?> guideClass = Class.forName("com.android.deskclock.alarm.bedtime.BedtimeGuideActivity", false, env.classLoader);
        Method transRepeatTypeToIndex = guideClass.getDeclaredMethod("transRepeatTypeToIndex", env.daysOfWeekClass);
        Method initData = guideClass.getDeclaredMethod("initData");

        env.hook(initData, chain -> {
            Object result = chain.proceed();
            installBedtimeGuidePickerHooks(chain.getThisObject());
            return result;
        });

        env.hook(transRepeatTypeToIndex, chain -> {
            Object daysOfWeek = chain.getArg(0);
            if (env.getDays(daysOfWeek) == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY) {
                return 3;
            }
            return chain.proceed();
        });
    }

    private void hookBedtimeSettings() throws Throwable {
        Class<?> fragmentClass = Class.forName("com.android.deskclock.settings.BedtimeSettingsFragment", false, env.classLoader);
        Method commitRepeat = env.findDeclaredMethodOrNull(fragmentClass, "handleBedtimeRepeatResult", 0);
        if (commitRepeat == null) {
            commitRepeat = env.findDeclaredMethodOrNull(fragmentClass, "commitRepeatResult", 0);
        }
        if (commitRepeat == null) {
            commitRepeat = env.findDeclaredMethodOrNull(fragmentClass, "handleRepeatDialogDismiss", 0);
        }
        if (commitRepeat == null) {
            commitRepeat = env.findDeclaredMethodOrNull(fragmentClass, "commitAndDismissRepeatDialog", 0);
        }
        Method onCreatePreferences = fragmentClass.getDeclaredMethod("onCreatePreferences", android.os.Bundle.class, String.class);

        env.hook(onCreatePreferences, chain -> {
            Object result = chain.proceed();
            Object fragment = chain.getThisObject();
            Context context = env.getObjectField(fragment, "mAppContext");
            env.rememberContext(context);
            if (env.isAlarmMarked(context, Constants.WAKE_ALARM_ID)) {
                Object wakeAlarm = env.getObjectField(fragment, "mWakeAlarm");
                Object daysOfWeek = wakeAlarm == null ? null : env.alarmDaysOfWeekField.get(wakeAlarm);
                if (daysOfWeek != null && env.getDays(daysOfWeek) == Constants.DAYS_LEGAL_WORKDAY) {
                    env.setRawDays(daysOfWeek, Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
                    env.applyEnhancedBedtimeRepeat(fragment);
                }
            }
            return result;
        });

        if (commitRepeat == null) {
            env.logInfo("Bedtime settings repeat-commit method not found; skipping restore hook");
            return;
        }
        env.hook(commitRepeat, chain -> {
            Object fragment = chain.getThisObject();
            boolean enhanced = env.isControllerEnhanced();
            Object result = chain.proceed();
            if (enhanced) {
                restoreBedtimeSettingsSelection(fragment);
            }
            return result;
        });
    }

    private void restoreBedtimeSettingsSelection(Object fragment) {
        try {
            Context context = env.getObjectField(fragment, "mAppContext");
            if (context == null) {
                context = env.getObjectField(fragment, "mActivity");
            }
            env.setControllerDays(Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
            env.applyEnhancedBedtimeRepeat(fragment);
            Object wakeAlarm = env.getObjectField(fragment, "mWakeAlarm");
            if (wakeAlarm != null) {
                env.saveWakeAlarm(context, wakeAlarm);
            } else {
                env.setAlarmMarked(context, Constants.WAKE_ALARM_ID, true);
            }
        } catch (Throwable t) {
            env.logError("Failed to restore bedtime settings selection", t);
        }
    }

    private void installBedtimeGuidePickerHooks(Object guide) {
        try {
            env.rememberContext(guide instanceof Context ? (Context) guide : null);
            Object picker = env.getObjectField(guide, "numberPicker");
            if (picker == null || patchedBedtimePickers.contains(picker)) {
                return;
            }
            patchedBedtimePickers.add(picker);

            Method setDisplayedValues = picker.getClass().getMethod("setDisplayedValues", String[].class);
            Method setMaxValue = picker.getClass().getMethod("setMaxValue", int.class);
            Method setOnValueChangedListener = env.findMethodByName(picker.getClass(), "setOnValueChangedListener", 1);
            Method getDisplayedValues = env.findMethodByName(picker.getClass(), "getDisplayedValues", 0);
            Method setValue = picker.getClass().getMethod("setValue", int.class);

            String[] values = (String[]) getDisplayedValues.invoke(picker);
            if (values == null || values.length < 3 || env.containsLabel(values, "\u542b\u5468\u516d")) {
                return;
            }

            String[] patched = new String[]{values[0], values[1], values[2], env.buildRepeatLabel((Context) guide)};
            setDisplayedValues.invoke(picker, (Object) patched);
            setMaxValue.invoke(picker, 3);
            Object wakeAlarm = env.getObjectField(guide, "wakeUpAlarm");
            Object wakeDays = wakeAlarm == null ? null : env.alarmDaysOfWeekField.get(wakeAlarm);
            if (wakeDays != null && env.getDays(wakeDays) == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY) {
                setValue.invoke(picker, 3);
            }

            Object originalListener = env.getObjectField(picker, "mOnValueChangeListener");
            if (originalListener != null && setOnValueChangedListener != null) {
                Class<?> listenerType = setOnValueChangedListener.getParameterTypes()[0];
                Object proxy = Proxy.newProxyInstance(
                        listenerType.getClassLoader(),
                        new Class<?>[]{listenerType},
                        new BedtimeGuidePickerHandler(guide, originalListener)
                );
                setOnValueChangedListener.invoke(picker, proxy);
            }
        } catch (Throwable t) {
            env.logError("Failed to patch bedtime guide picker", t);
        }
    }

    private final class BedtimeGuidePickerHandler implements InvocationHandler {
        private final Object guide;
        private final Object original;

        private BedtimeGuidePickerHandler(Object guide, Object original) {
            this.guide = guide;
            this.original = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("onValueChange".equals(method.getName()) && args != null && args.length == 3
                    && args[2] instanceof Integer newValue && newValue == 3) {
                Object wakeAlarm = env.getObjectField(guide, "wakeUpAlarm");
                Object daysOfWeek = wakeAlarm == null ? null : env.alarmDaysOfWeekField.get(wakeAlarm);
                if (daysOfWeek != null) {
                    env.daysField.setInt(daysOfWeek, Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
                }
                if (args[0] instanceof View view) {
                    view.sendAccessibilityEvent(4);
                }
                return null;
            }
            return method.invoke(original, args);
        }
    }
}
