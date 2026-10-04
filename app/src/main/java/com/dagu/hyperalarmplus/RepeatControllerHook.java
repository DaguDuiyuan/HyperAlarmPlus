package com.dagu.hyperalarmplus;

import java.lang.reflect.Method;

public final class RepeatControllerHook {
    private final HookEnvironment env;

    public RepeatControllerHook(HookEnvironment env) {
        this.env = env;
    }

    public void install() throws Throwable {
        hookRepeatController();
        hookSetAlarmController();
    }

    private void hookRepeatController() throws Throwable {
        Method setLastCheckedItem = env.repeatControllerClass.getDeclaredMethod("setLastCheckedItem", String.class);
        Method getDays = env.repeatControllerClass.getDeclaredMethod("getDays");

        env.hook(setLastCheckedItem, chain -> {
            String label = (String) chain.getArg(0);
            if (label != null && label.contains("\u542b\u5468\u516d")) {
                Object controller = chain.getThisObject();
                env.setControllerDays(Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
                Object adapter = env.getObjectField(controller, "mAlarmRepeatAdapter");
                if (adapter != null) {
                    env.setAdapterChecked(adapter, Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY);
                }
                return null;
            }
            return chain.proceed();
        });

        env.hook(getDays, chain -> {
            Object daysOfWeek = env.getControllerDaysOfWeek();
            if (daysOfWeek != null && env.getDays(daysOfWeek) == Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY) {
                return Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY;
            }
            return chain.proceed();
        });
    }

    private void hookSetAlarmController() throws Throwable {
        Class<?> setAlarmControllerClass = Class.forName("com.android.deskclock.alarm.SetAlarmController", false, env.classLoader);
        Method setAlarmRepeatValue = setAlarmControllerClass.getDeclaredMethod("setAlarmRepeatValue");
        Method buildAlarmFromUi = setAlarmControllerClass.getDeclaredMethod("buildAlarmFromUi");

        env.hook(setAlarmRepeatValue, chain -> {
            Object controller = chain.getThisObject();
            if (env.isEnhancedRepeatSelected(controller)) {
                env.setControllerDays(Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
            }
            Object result = chain.proceed();
            if (env.isEnhancedRepeatSelected(controller)) {
                env.applyEnhancedRepeatSummary(controller);
            }
            return result;
        });

        env.hook(buildAlarmFromUi, chain -> {
            Object controller = chain.getThisObject();
            boolean enhanced = env.isEnhancedRepeatSelected(controller);
            Object alarm = chain.proceed();
            if (enhanced || env.isEnhancedRepeatSummary(controller)) {
                Object daysOfWeek = env.alarmDaysOfWeekField.get(alarm);
                env.setRawDays(daysOfWeek, Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
            }
            return alarm;
        });
    }
}
