package com.dagu.hyperalarmplus;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;

public final class XposedInitEntry extends XposedModule {
    private XposedInterface base;

    @Override
    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {
        base = this;
    }

    @Override
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {
        super.onPackageReady(param);
        if (!Constants.TARGET_PACKAGE.equals(param.getPackageName())) {
            return;
        }

        HookEnvironment env = new HookEnvironment(base, param.getClassLoader());
        try {
            env.initReflection();
        } catch (Throwable t) {
            env.logError("Failed to init reflection", t);
            return;
        }

        boolean complete = true;
        complete &= install(env, "AppContextHook", () -> new AppContextHook(env).install());
        complete &= install(env, "AlarmRuntimeHook", () -> new AlarmRuntimeHook(env).install());
        complete &= install(env, "RepeatAdapterHook", () -> new RepeatAdapterHook(env).install());
        complete &= install(env, "RepeatControllerHook", () -> new RepeatControllerHook(env).install());
        complete &= install(env, "BedtimeHook", () -> new BedtimeHook(env).install());
        env.logInfo(complete ? "Hooks installed" : "Hooks installed with missing parts");
    }

    private boolean install(HookEnvironment env, String name, InstallAction action) {
        try {
            action.run();
            return true;
        } catch (Throwable t) {
            env.logError("Failed to install " + name, t);
            return false;
        }
    }

    private interface InstallAction {
        void run() throws Throwable;
    }
}
