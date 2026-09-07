package com.mike.hyperosbacktappay;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class HookEntry extends XposedModule {
    private static final String TAG = "HyperOSBackTapPay";
    private static final String TARGET_CLASS = "com.miui.server.input.util.ShortCutActionsUtils";
    private static final String TARGET_METHOD = "triggerFunction";
    private static final String HOOK_ID_PREFIX = "hyperos-backtap-pay:trigger:";
    private static final long TIP_DEDUP_WINDOW_MS = 800L;
    private static final long INSTALL_RETRY_DELAY_MS = 1000L;
    private static final int MAX_INSTALL_ATTEMPTS = 45;
    private static final int MAX_STATUS_ATTEMPTS = 45;

    private final Object tipDedupLock = new Object();
    private final Object installLock = new Object();
    private final ThreadLocal<Integer> triggerDepth = ThreadLocal.withInitial(() -> 0);

    private volatile SharedPreferences preferences;
    private volatile SharedPreferences.OnSharedPreferenceChangeListener preferenceListener;
    private volatile Context systemContext;
    private volatile Handler systemMainHandler;
    private volatile ClassLoader systemServerClassLoader;
    private volatile boolean hooksInstalled;
    private volatile boolean statusPublished;
    private String lastTipKey;
    private long lastTipUptime;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "Modern module loaded in " + param.getProcessName()
                + ", systemServer=" + param.isSystemServer()
                + ", API=" + getApiVersion() + ", framework=" + getFrameworkName()
                + " " + getFrameworkVersion());

        if (param.isSystemServer()) {
            try {
                initializeRemotePreferences();
            } catch (Throwable t) {
                log(Log.ERROR, TAG, "Remote Preferences init failed during module load", t);
            }
        }
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        systemServerClassLoader = param.getClassLoader();
        hooksInstalled = false;
        statusPublished = false;

        try {
            initializeRemotePreferences();
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Remote Preferences init failed", t);
        }

        rememberSystemContext(resolveSystemContext());
        installOrSchedule(param.getClassLoader(), 1);
    }

    @Override
    public boolean onHotReloading(HotReloadingParam param) {
        log(Log.INFO, TAG, "Preparing API 102 hot reload");
        Handler handler = systemMainHandler;
        if (handler != null) {
            handler.removeCallbacksAndMessages(null);
        }
        detachPreferenceListener();
        preferences = null;
        systemContext = null;
        systemMainHandler = null;
        systemServerClassLoader = null;
        hooksInstalled = false;
        statusPublished = false;
        lastTipKey = null;
        lastTipUptime = 0L;
        triggerDepth.remove();
        return true;
    }

    @Override
    public void onHotReloaded(HotReloadedParam param) {
        log(Log.INFO, TAG, "Hot reloaded in " + param.getProcessName()
                + ", systemServer=" + param.isSystemServer()
                + ", old hooks=" + param.getOldHookHandles().size());

        try {
            initializeRemotePreferences();
            rememberSystemContext(resolveSystemContext());

            ClassLoader targetClassLoader = null;
            int replacedCount = 0;
            for (XposedInterface.HookHandle oldHandle : param.getOldHookHandles()) {
                String id = oldHandle.getId();
                if (id != null && id.startsWith(HOOK_ID_PREFIX)) {
                    if (targetClassLoader == null) {
                        targetClassLoader = oldHandle.getExecutable().getDeclaringClass().getClassLoader();
                    }
                    oldHandle.replaceHook(this::interceptTrigger);
                    replacedCount++;
                } else {
                    oldHandle.unhook();
                }
            }

            if (replacedCount > 0) {
                systemServerClassLoader = targetClassLoader;
                hooksInstalled = true;
                log(Log.INFO, TAG, "Replaced " + replacedCount + " trigger hook(s) after hot reload");
                syncAllNativeActionsSafely();
                publishHookStatusWhenReady(1);
                return;
            }

            if (param.isSystemServer()) {
                targetClassLoader = findSystemServerClassLoader();
                if (targetClassLoader != null) {
                    systemServerClassLoader = targetClassLoader;
                    installOrSchedule(targetClassLoader, 1);
                } else {
                    log(Log.ERROR, TAG, "Hot reload has no old hook handles and system_server classloader recovery failed");
                }
            }
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Hot reload initialization failed", t);
        }
    }

    private void installOrSchedule(ClassLoader classLoader, int attempt) {
        if (classLoader == null || hooksInstalled) {
            return;
        }

        synchronized (installLock) {
            if (hooksInstalled) {
                return;
            }
            try {
                int count = installTriggerHooks(classLoader);
                if (count <= 0) {
                    throw new IllegalStateException("No triggerFunction overload found");
                }
                hooksInstalled = true;
                systemServerClassLoader = classLoader;
                log(Log.INFO, TAG, "Modern hook ready, installed " + count + " triggerFunction hook(s)");
                syncAllNativeActionsSafely();
                publishHookStatusWhenReady(1);
                return;
            } catch (Throwable t) {
                if (attempt == 1 || attempt % 10 == 0 || attempt >= MAX_INSTALL_ATTEMPTS) {
                    log(attempt >= MAX_INSTALL_ATTEMPTS ? Log.ERROR : Log.WARN,
                            TAG,
                            "Hook install attempt " + attempt + "/" + MAX_INSTALL_ATTEMPTS + " failed",
                            t);
                }
            }
        }

        if (attempt < MAX_INSTALL_ATTEMPTS) {
            getSystemMainHandler().postDelayed(
                    () -> installOrSchedule(classLoader, attempt + 1),
                    INSTALL_RETRY_DELAY_MS
            );
        }
    }

    private int installTriggerHooks(ClassLoader classLoader) throws Exception {
        Class<?> clazz = Class.forName(TARGET_CLASS, false, classLoader);
        int count = 0;
        for (Method method : clazz.getDeclaredMethods()) {
            if (!TARGET_METHOD.equals(method.getName())) {
                continue;
            }
            try {
                method.setAccessible(true);
                hook(method)
                        .setId(hookId(method))
                        .intercept(this::interceptTrigger);
                count++;
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Unable to hook " + method, t);
            }
        }
        return count;
    }

    private Object interceptTrigger(XposedInterface.Chain chain) throws Throwable {
        int depth = triggerDepth.get();
        triggerDepth.set(depth + 1);

        try {
            List<Object> originalArgs = chain.getArgs();
            Object[] args = originalArgs.toArray(new Object[0]);

            int functionIndex = -1;
            String function = null;
            String shortcut = null;
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (Config.FUNCTION_PAYMENT.equals(arg) || Config.FUNCTION_BUS.equals(arg)) {
                    functionIndex = i;
                    function = (String) arg;
                } else if (Config.SETTING_BACK_DOUBLE.equals(arg)
                        || Config.SETTING_BACK_TRIPLE.equals(arg)) {
                    shortcut = (String) arg;
                }
            }

            if (functionIndex < 0 || function == null || shortcut == null) {
                return chain.proceed();
            }

            rememberSystemContext(extractContext(chain.getThisObject()));
            if (!statusPublished) {
                publishHookStatusWhenReady(1);
            }

            String actionPrefKey = Config.SETTING_BACK_TRIPLE.equals(shortcut)
                    ? Config.PREF_TRIPLE_ACTION
                    : Config.PREF_DOUBLE_ACTION;
            String displayPrefKey = Config.SETTING_BACK_TRIPLE.equals(shortcut)
                    ? Config.PREF_TRIPLE_DISPLAY
                    : Config.PREF_DOUBLE_DISPLAY;

            String configuredAction = readAction(actionPrefKey, function);
            if (Config.FUNCTION_PAYMENT.equals(configuredAction)
                    || Config.FUNCTION_BUS.equals(configuredAction)) {
                if (!configuredAction.equals(function)) {
                    args[functionIndex] = configuredAction;
                    log(Log.DEBUG, TAG, "Remapped " + shortcut + " " + function
                            + " -> " + configuredAction);
                }
                function = configuredAction;
            }

            int bundleIndex = findBundleArgument(chain.getExecutable(), args);
            if (bundleIndex >= 0) {
                Bundle bundle = args[bundleIndex] instanceof Bundle
                        ? (Bundle) args[bundleIndex]
                        : new Bundle();
                args[bundleIndex] = bundle;
                int displayId = readDisplayId(displayPrefKey);
                bundle.putInt(Config.DISPLAY_BUNDLE_KEY, displayId);
                log(Log.DEBUG, TAG, "Set " + Config.DISPLAY_BUNDLE_KEY + "=" + displayId
                        + " for " + shortcut + " -> " + function);
            } else {
                log(Log.WARN, TAG, "Matched BackTap action but no Bundle argument was found");
            }

            Object result = chain.proceed(args);
            if (depth == 0 && readShowTips() && shouldShowTip(shortcut, function)) {
                String suffix = "";
                if (result instanceof Boolean) {
                    suffix = ((Boolean) result) ? " ✓" : " · 触发失败";
                }
                showTriggerTip(shortcut, function, suffix);
            }
            return result;
        } finally {
            if (depth == 0) {
                triggerDepth.remove();
            } else {
                triggerDepth.set(depth);
            }
        }
    }

    private void initializeRemotePreferences() {
        if (preferences != null) {
            return;
        }
        SharedPreferences prefs = getRemotePreferences(Config.PREFS_NAME);
        SharedPreferences.OnSharedPreferenceChangeListener listener = (sharedPreferences, key) -> {
            if (Config.PREF_DOUBLE_ACTION.equals(key) || Config.PREF_TRIPLE_ACTION.equals(key)) {
                syncNativeAction(key);
            }
        };
        prefs.registerOnSharedPreferenceChangeListener(listener);
        preferences = prefs;
        preferenceListener = listener;
        log(Log.INFO, TAG, "Remote Preferences connected");
    }

    private void detachPreferenceListener() {
        SharedPreferences prefs = preferences;
        SharedPreferences.OnSharedPreferenceChangeListener listener = preferenceListener;
        if (prefs != null && listener != null) {
            try {
                prefs.unregisterOnSharedPreferenceChangeListener(listener);
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Unable to unregister Remote Preferences listener", t);
            }
        }
        preferenceListener = null;
    }

    private void syncAllNativeActionsSafely() {
        try {
            syncNativeAction(Config.PREF_DOUBLE_ACTION);
            syncNativeAction(Config.PREF_TRIPLE_ACTION);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to sync native actions", t);
        }
    }

    private void syncNativeAction(String prefKey) {
        SharedPreferences prefs = preferences;
        if (prefs == null || !prefs.contains(prefKey)) {
            return;
        }

        String action = prefs.getString(prefKey, Config.FUNCTION_NONE);
        if (!isAllowedAction(action)) {
            return;
        }

        String settingKey = Config.PREF_TRIPLE_ACTION.equals(prefKey)
                ? Config.SETTING_BACK_TRIPLE
                : Config.SETTING_BACK_DOUBLE;
        String nativeFunction = Config.FUNCTION_NONE.equals(action)
                ? Config.FUNCTION_NONE
                : Config.FUNCTION_PAYMENT;

        Context context = ensureSystemContext();
        if (context == null) {
            log(Log.WARN, TAG, "Cannot sync " + settingKey + ": system Context unavailable");
            return;
        }

        try {
            boolean ok = Settings.System.putString(
                    context.getContentResolver(), settingKey, nativeFunction);
            log(Log.INFO, TAG, "Synced " + settingKey + "=" + nativeFunction
                    + " (configured=" + action + ") result=" + ok);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to sync native BackTap setting", t);
        }
    }

    private String readAction(String prefKey, String fallback) {
        SharedPreferences prefs = preferences;
        if (prefs == null) {
            return fallback;
        }
        String stored = prefs.getString(prefKey, fallback);
        return isAllowedAction(stored) ? stored : fallback;
    }

    private int readDisplayId(String prefKey) {
        SharedPreferences prefs = preferences;
        String display = prefs == null
                ? Config.DISPLAY_REAR
                : prefs.getString(prefKey, Config.DISPLAY_REAR);
        return Config.DISPLAY_MAIN.equals(display)
                ? Config.DISPLAY_ID_MAIN
                : Config.DISPLAY_ID_REAR;
    }

    private boolean readShowTips() {
        SharedPreferences prefs = preferences;
        return prefs != null && prefs.getBoolean(Config.PREF_SHOW_TIPS, false);
    }

    private static boolean isAllowedAction(String action) {
        return Config.FUNCTION_NONE.equals(action)
                || Config.FUNCTION_PAYMENT.equals(action)
                || Config.FUNCTION_BUS.equals(action);
    }

    private boolean shouldShowTip(String shortcut, String function) {
        String key = shortcut + "|" + function;
        long now = SystemClock.uptimeMillis();
        synchronized (tipDedupLock) {
            if (key.equals(lastTipKey) && now - lastTipUptime < TIP_DEDUP_WINDOW_MS) {
                return false;
            }
            lastTipKey = key;
            lastTipUptime = now;
            return true;
        }
    }

    private void showTriggerTip(String shortcut, String function, String resultSuffix) {
        Context context = ensureSystemContext();
        if (context == null) {
            return;
        }

        String tapText = Config.SETTING_BACK_TRIPLE.equals(shortcut) ? "背部三击" : "背部双击";
        String functionText = Config.FUNCTION_BUS.equals(function) ? "支付宝乘车码" : "支付宝付款码";
        String message = tapText + " · " + functionText + resultSuffix;

        getSystemMainHandler().post(() -> {
            try {
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
            } catch (Throwable t) {
                log(Log.WARN, TAG, "Unable to show trigger Tips", t);
            }
        });
    }

    private void publishHookStatusWhenReady(int attempt) {
        if (!hooksInstalled || statusPublished) {
            return;
        }

        Context context = ensureSystemContext();
        if (context != null) {
            try {
                ContentResolver resolver = context.getContentResolver();
                int bootCount = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, -1);
                boolean versionOk = Settings.System.putString(
                        resolver,
                        Config.STATUS_HOOK_VERSION,
                        BuildConfig.VERSION_NAME
                );
                boolean bootOk = Settings.System.putInt(
                        resolver,
                        Config.STATUS_HOOK_BOOT_COUNT,
                        bootCount
                );
                statusPublished = versionOk && bootOk;
                if (statusPublished) {
                    log(Log.INFO, TAG, "Published Modern Hook status version="
                            + BuildConfig.VERSION_NAME + ", bootCount=" + bootCount);
                    return;
                }
                log(Log.WARN, TAG, "Settings.System rejected Hook status write");
            } catch (Throwable t) {
                if (attempt == 1 || attempt % 10 == 0) {
                    log(Log.WARN, TAG, "Hook status publish attempt " + attempt + " failed", t);
                }
            }
        } else if (attempt == 1 || attempt % 10 == 0) {
            log(Log.WARN, TAG, "system Context not ready for Hook status, attempt=" + attempt);
        }

        if (!statusPublished && attempt < MAX_STATUS_ATTEMPTS) {
            getSystemMainHandler().postDelayed(
                    () -> publishHookStatusWhenReady(attempt + 1),
                    INSTALL_RETRY_DELAY_MS
            );
        }
    }

    private Handler getSystemMainHandler() {
        Handler handler = systemMainHandler;
        if (handler != null) {
            return handler;
        }
        synchronized (this) {
            handler = systemMainHandler;
            if (handler == null) {
                Looper looper = Looper.getMainLooper();
                if (looper == null) {
                    looper = Looper.myLooper();
                }
                if (looper == null) {
                    throw new IllegalStateException("No Looper available in system_server");
                }
                handler = new Handler(looper);
                systemMainHandler = handler;
            }
        }
        return handler;
    }

    private Context ensureSystemContext() {
        Context context = systemContext;
        if (context == null) {
            context = resolveSystemContext();
            rememberSystemContext(context);
        }
        return context;
    }

    private void rememberSystemContext(Context context) {
        if (context == null) {
            return;
        }
        systemContext = context;
        if (systemMainHandler == null && Looper.getMainLooper() != null) {
            systemMainHandler = new Handler(Looper.getMainLooper());
        }
    }

    private Context resolveSystemContext() {
        try {
            Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
            Method current = activityThreadClass.getDeclaredMethod("currentActivityThread");
            current.setAccessible(true);
            Object activityThread = current.invoke(null);
            if (activityThread == null) {
                return null;
            }
            Method getSystemContext = activityThreadClass.getDeclaredMethod("getSystemContext");
            getSystemContext.setAccessible(true);
            Object value = getSystemContext.invoke(activityThread);
            return value instanceof Context ? (Context) value : null;
        } catch (Throwable t) {
            log(Log.DEBUG, TAG, "ActivityThread system Context not ready", t);
            return null;
        }
    }

    private ClassLoader findSystemServerClassLoader() {
        ClassLoader[] candidates = new ClassLoader[]{
                systemServerClassLoader,
                Thread.currentThread().getContextClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            try {
                Class.forName(TARGET_CLASS, false, candidate);
                log(Log.INFO, TAG, "Recovered system_server classloader: " + candidate);
                return candidate;
            } catch (Throwable ignored) {
                // Try the next candidate.
            }
        }
        return null;
    }

    private Context extractContext(Object object) {
        if (object == null) {
            return null;
        }
        for (String fieldName : new String[]{"mContext", "mSystemContext"}) {
            try {
                Field field = findField(object.getClass(), fieldName);
                if (field == null) {
                    continue;
                }
                field.setAccessible(true);
                Object value = field.get(object);
                if (value instanceof Context) {
                    return (Context) value;
                }
            } catch (Throwable ignored) {
                // Try the next known field name.
            }
        }
        return null;
    }

    private static Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private static int findBundleArgument(Executable executable, Object[] args) {
        for (int i = 0; i < args.length; i++) {
            if (args[i] instanceof Bundle) {
                return i;
            }
        }
        Class<?>[] parameterTypes = executable.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (Bundle.class.isAssignableFrom(parameterTypes[i])) {
                return i;
            }
        }
        return -1;
    }

    private static String hookId(Method method) {
        StringBuilder id = new StringBuilder(HOOK_ID_PREFIX).append(method.getName()).append('(');
        Class<?>[] parameterTypes = method.getParameterTypes();
        for (int i = 0; i < parameterTypes.length; i++) {
            if (i > 0) {
                id.append(',');
            }
            id.append(parameterTypes[i].getName());
        }
        return id.append(')').toString();
    }
}
