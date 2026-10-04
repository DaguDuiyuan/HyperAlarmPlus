package com.dagu.hyperalarmplus;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

public final class RepeatAdapterHook {
    private final HookEnvironment env;

    public RepeatAdapterHook(HookEnvironment env) {
        this.env = env;
    }

    public void install() throws Throwable {
        Class<?> adapterClass = Class.forName("com.android.deskclock.alarm.AlarmRepeatAdapter", false, env.classLoader);
        Class<?> listenerClass = Class.forName(
                "com.android.deskclock.alarm.AlarmRepeatAdapter$OnOtherViewItemClickListener",
                false,
                env.classLoader
        );
        Method setData = adapterClass.getDeclaredMethod("setData", java.util.ArrayList.class, boolean.class);
        Method bindOtherViewHolder = env.findDeclaredMethod(adapterClass, "bindOtherViewHolder", 2);
        Method setRepeatItemChecked = adapterClass.getDeclaredMethod("setRepeatItemChecked", int.class);
        Method getItemViewType = adapterClass.getDeclaredMethod("getItemViewType", int.class);
        Method setOtherListener = adapterClass.getDeclaredMethod("setOnOtherViewItemClickListener", listenerClass);

        env.hook(setData, chain -> {
            @SuppressWarnings("unchecked")
            List<Object> data = (List<Object>) chain.getArg(0);
            insertExtraRepeatItem(data);
            Object result = chain.proceed();
            Object adapter = chain.getThisObject();
            env.rememberContext(env.getObjectField(adapter, "mContext"));
            expandAdapterRepeatTypeLabels(adapter);
            Object daysOfWeek = env.getControllerDaysOfWeek();
            if (daysOfWeek != null) {
                env.setAdapterChecked(adapter, env.alarmTypeForDays(env.getDays(daysOfWeek)));
            }
            return result;
        });

        env.hook(bindOtherViewHolder, chain -> {
            Object adapter = chain.getThisObject();
            int position = (Integer) chain.getArg(1);
            Object dataBean = env.getDataBeanAt(adapter, position);
            if (dataBean != null && env.getRepeatType(dataBean) == Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY) {
                bindExtraRepeatItem(adapter, chain.getArg(0), position);
                return null;
            }
            return chain.proceed();
        });

        env.hook(setRepeatItemChecked, chain -> {
            int type = (Integer) chain.getArg(0);
            Object adapter = chain.getThisObject();
            if (env.indexOfRepeatType(adapter, Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY) < 0) {
                return chain.proceed();
            }
            int index = env.indexOfRepeatType(adapter, dataBeanTypeForAlarmType(type));
            if (index < 0) {
                return chain.proceed();
            }
            env.setStaticIntField(adapter.getClass(), "mCheckedItem", index);
            Method notifyDataSetChanged = adapter.getClass().getMethod("notifyDataSetChanged");
            notifyDataSetChanged.invoke(adapter);
            return null;
        });

        env.hook(getItemViewType, chain -> {
            Object adapter = chain.getThisObject();
            int position = (Integer) chain.getArg(0);
            Object dataBean = env.getDataBeanAt(adapter, position);
            if (dataBean != null && env.getRepeatType(dataBean) == Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY) {
                return 0;
            }
            return chain.proceed();
        });

        env.hook(setOtherListener, chain -> {
            Object adapter = chain.getThisObject();
            Object originalListener = chain.getArg(0);
            if (originalListener == null || Proxy.isProxyClass(originalListener.getClass())) {
                return chain.proceed();
            }
            Object proxy = Proxy.newProxyInstance(
                    listenerClass.getClassLoader(),
                    new Class<?>[]{listenerClass},
                    new RepeatClickHandler(adapter, originalListener)
            );
            return chain.proceed(new Object[]{proxy});
        });
    }

    private static int dataBeanTypeForAlarmType(int alarmType) {
        return alarmType == Constants.TYPE_CUSTOM ? Constants.TYPE_SELF_DEFINE : alarmType;
    }

    private void insertExtraRepeatItem(List<Object> data) throws Throwable {
        if (data == null || env.containsRepeatType(data, Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY)) {
            return;
        }

        int legalWorkdayIndex = env.indexOfRepeatType(data, Constants.TYPE_LEGAL_WORKDAY);
        if (legalWorkdayIndex < 0) {
            return;
        }

        Object reference = data.get(legalWorkdayIndex);
        Constructor<?> constructor = env.dataBeanClass.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object item = constructor.newInstance();
        env.dataBeanGroupIdField.setInt(item, env.dataBeanGroupIdField.getInt(reference));
        env.dataBeanRepeatTypeField.setInt(item, Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY);
        data.add(legalWorkdayIndex + 1, item);
    }

    private void bindExtraRepeatItem(Object adapter, Object holder, int position) throws Throwable {
        Context context = env.getObjectField(adapter, "mContext");
        env.rememberContext(context);
        env.declaredField(holder.getClass(), "position").setInt(holder, position);

        TextView descView = env.getObjectField(holder, "mOtherDescView");
        View checkedView = env.getObjectField(holder, "mOtherCheckedView");
        if (descView == null) {
            return;
        }
        descView.setText(env.buildRepeatLabel(context));
        boolean checked = env.getStaticIntField(adapter.getClass(), "mCheckedItem") == position;
        descView.setTextColor(env.resolveHostColor(context,
                checked ? "repeat_checked_visible" : "alarm_repeat_text_normal_color",
                checked ? 0xff277af7 : 0xffffffff));
        if (checkedView != null) {
            checkedView.setVisibility(checked ? View.VISIBLE : View.GONE);
        }
    }

    private void expandAdapterRepeatTypeLabels(Object adapter) {
        try {
            Object data = env.getObjectField(adapter, "dataList");
            String[] repeatType = env.getObjectField(adapter, "repeatType");
            if (!(data instanceof List<?> list) || repeatType == null || repeatType.length >= list.size()) {
                return;
            }
            Context context = env.getObjectField(adapter, "mContext");
            String[] patched = new String[list.size()];
            for (int i = 0; i < patched.length; i++) {
                Object dataBean = list.get(i);
                int type = dataBean == null ? -1 : env.getRepeatType(dataBean);
                patched[i] = getRepeatLabelForType(adapter, repeatType, type, context);
            }
            env.declaredField(adapter.getClass(), "repeatType").set(adapter, patched);
        } catch (Throwable t) {
            env.logError("Failed to expand repeat labels", t);
        }
    }

    private String getRepeatLabelForType(Object adapter, String[] repeatType, int type, Context context) {
        if (type == Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY) {
            return env.buildRepeatLabel(context);
        }
        boolean wakeAlarmRepeat = Boolean.TRUE.equals(env.getObjectField(adapter, "mIsWakeAlarmRepeat"));
        int index = wakeAlarmRepeat ? type - 1 : type;
        if (index >= 0 && index < repeatType.length) {
            return repeatType[index];
        }
        return "";
    }

    private final class RepeatClickHandler implements InvocationHandler {
        private final Object adapter;
        private final Object original;

        private RepeatClickHandler(Object adapter, Object original) {
            this.adapter = adapter;
            this.original = original;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("onOtherViewItemClick".equals(method.getName()) && args != null && args.length == 2
                    && args[0] instanceof List<?> data && args[1] instanceof Integer position
                    && position >= 0 && position < data.size()
                    && env.getRepeatType(data.get(position)) == Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY) {
                env.setControllerDays(Constants.DAYS_LEGAL_WORKDAY_WITH_SATURDAY);
                env.setAdapterChecked(adapter, Constants.TYPE_LEGAL_WORKDAY_WITH_SATURDAY);
                return null;
            }
            return method.invoke(original, args);
        }
    }
}
