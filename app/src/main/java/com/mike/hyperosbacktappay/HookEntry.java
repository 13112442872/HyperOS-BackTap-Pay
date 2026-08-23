package com.mike.hyperosbacktappay;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "HyperOSBackTapPay";
    private static final String SYSTEM_PACKAGE = "android";
    private static final String TARGET_CLASS = "com.miui.server.input.util.ShortCutActionsUtils";
    private static final String TARGET_METHOD = "triggerFunction";

    private volatile XSharedPreferences preferences;
    private volatile boolean statusPublished;
    private volatile boolean controlReceiverRegistered;
    private BroadcastReceiver controlReceiver;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        preferences = new XSharedPreferences(Config.MODULE_PACKAGE, Config.PREFS_NAME);
        hookShortcutActions(lpparam.classLoader);
        hookSystemStatus(lpparam.classLoader);
    }

    private void hookShortcutActions(ClassLoader classLoader) {
        try {
            Class<?> clazz = XposedHelpers.findClass(TARGET_CLASS, classLoader);
            XposedBridge.hookAllMethods(clazz, TARGET_METHOD, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    String function = null;
                    String shortcut = null;

                    for (Object arg : param.args) {
                        if (Config.FUNCTION_PAYMENT.equals(arg) || Config.FUNCTION_BUS.equals(arg)) {
                            function = (String) arg;
                        } else if (Config.SETTING_BACK_DOUBLE.equals(arg)
                                || Config.SETTING_BACK_TRIPLE.equals(arg)) {
                            shortcut = (String) arg;
                        }
                    }

                    if (function == null || shortcut == null) {
                        return;
                    }

                    publishStatusFromShortcutObject(param.thisObject);

                    int bundleIndex = findBundleArgument(param);
                    if (bundleIndex < 0) {
                        XposedBridge.log(TAG + ": matched BackTap Alipay action, but no Bundle parameter was found");
                        return;
                    }

                    Bundle bundle = (Bundle) param.args[bundleIndex];
                    if (bundle == null) {
                        bundle = new Bundle();
                        param.args[bundleIndex] = bundle;
                    }

                    String prefKey = Config.SETTING_BACK_TRIPLE.equals(shortcut)
                            ? Config.PREF_TRIPLE_DISPLAY
                            : Config.PREF_DOUBLE_DISPLAY;
                    int displayId = readDisplayId(prefKey);
                    bundle.putInt(Config.DISPLAY_BUNDLE_KEY, displayId);

                    XposedBridge.log(TAG + ": set " + Config.DISPLAY_BUNDLE_KEY + "=" + displayId
                            + " for " + shortcut + " -> " + function);
                }
            });

            XposedBridge.log(TAG + ": hooked " + TARGET_CLASS + "#" + TARGET_METHOD);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": shortcut hook failed");
            XposedBridge.log(t);
        }
    }

    private void hookSystemStatus(ClassLoader classLoader) {
        try {
            Class<?> systemServer = XposedHelpers.findClass("com.android.server.SystemServer", classLoader);
            XposedBridge.hookAllMethods(systemServer, "startOtherServices", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object value = XposedHelpers.getObjectField(param.thisObject, "mSystemContext");
                        if (value instanceof Context) {
                            Context context = (Context) value;
                            registerControlReceiver(context);
                            publishHookStatus(context);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + ": unable to initialize system-server bridge");
                        XposedBridge.log(t);
                    }
                }
            });
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": status hook unavailable; status will be published on first shortcut trigger");
            XposedBridge.log(t);
        }
    }

    private synchronized void registerControlReceiver(Context context) {
        if (controlReceiverRegistered || context == null) {
            return;
        }

        controlReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context receiverContext, Intent intent) {
                if (intent == null || !Config.ACTION_SET_GESTURE.equals(intent.getAction())) {
                    return;
                }

                String settingKey = intent.getStringExtra(Config.EXTRA_SETTING_KEY);
                String function = intent.getStringExtra(Config.EXTRA_FUNCTION);
                if (!isAllowedSetting(settingKey) || !isAllowedFunction(function)) {
                    XposedBridge.log(TAG + ": rejected invalid control request");
                    return;
                }

                try {
                    boolean ok = Settings.System.putString(
                            receiverContext.getContentResolver(),
                            settingKey,
                            function
                    );
                    XposedBridge.log(TAG + ": system_server write " + settingKey + "=" + function
                            + " result=" + ok);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + ": system_server gesture write failed");
                    XposedBridge.log(t);
                }
            }
        };

        IntentFilter filter = new IntentFilter(Config.ACTION_SET_GESTURE);
        if (Build.VERSION.SDK_INT >= 33) {
            context.registerReceiver(
                    controlReceiver,
                    filter,
                    Config.CONTROL_PERMISSION,
                    null,
                    Context.RECEIVER_EXPORTED
            );
        } else {
            context.registerReceiver(
                    controlReceiver,
                    filter,
                    Config.CONTROL_PERMISSION,
                    null
            );
        }
        controlReceiverRegistered = true;
        XposedBridge.log(TAG + ": system-server control receiver registered");
    }

    private static boolean isAllowedSetting(String settingKey) {
        return Config.SETTING_BACK_DOUBLE.equals(settingKey)
                || Config.SETTING_BACK_TRIPLE.equals(settingKey);
    }

    private static boolean isAllowedFunction(String function) {
        return Config.FUNCTION_NONE.equals(function)
                || Config.FUNCTION_PAYMENT.equals(function)
                || Config.FUNCTION_BUS.equals(function);
    }

    private int readDisplayId(String prefKey) {
        String display = Config.DISPLAY_REAR;
        try {
            XSharedPreferences prefs = preferences;
            if (prefs != null) {
                prefs.reload();
                display = prefs.getString(prefKey, Config.DISPLAY_REAR);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": preference reload failed, using rear display fallback");
            XposedBridge.log(t);
        }
        return Config.DISPLAY_MAIN.equals(display) ? Config.DISPLAY_ID_MAIN : Config.DISPLAY_ID_REAR;
    }

    private void publishStatusFromShortcutObject(Object object) {
        if (statusPublished || object == null) {
            return;
        }

        String[] fieldNames = {"mContext", "mSystemContext"};
        for (String fieldName : fieldNames) {
            try {
                Object value = XposedHelpers.getObjectField(object, fieldName);
                if (value instanceof Context) {
                    Context context = (Context) value;
                    registerControlReceiver(context);
                    publishHookStatus(context);
                    return;
                }
            } catch (Throwable ignored) {
                // Try the next common field name.
            }
        }
    }

    private void publishHookStatus(Context context) {
        if (context == null) {
            return;
        }
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
                XposedBridge.log(TAG + ": published Hook status version=" + BuildConfig.VERSION_NAME
                        + ", bootCount=" + bootCount);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": failed to publish Hook status");
            XposedBridge.log(t);
        }
    }

    private static int findBundleArgument(XC_MethodHook.MethodHookParam param) {
        for (int i = 0; i < param.args.length; i++) {
            if (param.args[i] instanceof Bundle) {
                return i;
            }
        }

        if (param.method instanceof Method) {
            Class<?>[] parameterTypes = ((Method) param.method).getParameterTypes();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (Bundle.class.isAssignableFrom(parameterTypes[i])) {
                    return i;
                }
            }
        }

        return -1;
    }
}
