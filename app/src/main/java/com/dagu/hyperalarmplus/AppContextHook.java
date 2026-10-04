package com.dagu.hyperalarmplus;

import android.content.Context;

import java.lang.reflect.Method;

public final class AppContextHook {
    private final HookEnvironment env;

    public AppContextHook(HookEnvironment env) {
        this.env = env;
    }

    public void install() throws Throwable {
        Class<?> appClass = Class.forName("com.android.deskclock.DeskClockApp", false, env.classLoader);
        Method onCreate = appClass.getDeclaredMethod("onCreate");
        env.hook(onCreate, chain -> {
            Object result = chain.proceed();
            if (chain.getThisObject() instanceof Context context) {
                env.rememberContext(context);
            }
            return result;
        });
    }
}
