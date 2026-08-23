package com.mike.hyperosbacktappay;

import android.os.Bundle;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public final class HookEntry implements IXposedHookLoadPackage {
    private static final String TAG = "HyperOSBackTapPay";
    private static final String SYSTEM_PACKAGE = "android";
    private static final String TARGET_CLASS = "com.miui.server.input.util.ShortCutActionsUtils";
    private static final String TARGET_METHOD = "triggerFunction";
    private static final String TARGET_FUNCTION = "launch_alipay_payment_code";
    private static final String TARGET_SHORTCUT = "back_double_tap";
    private static final String DISPLAY_KEY = "show_code_display";
    private static final int SUB_SCREEN_DISPLAY_ID = 1;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!SYSTEM_PACKAGE.equals(lpparam.packageName)) {
            return;
        }

        try {
            Class<?> clazz = XposedHelpers.findClass(TARGET_CLASS, lpparam.classLoader);
            XposedBridge.hookAllMethods(clazz, TARGET_METHOD, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    boolean isPaymentCode = false;
                    boolean isBackDoubleTap = false;

                    for (Object arg : param.args) {
                        if (TARGET_FUNCTION.equals(arg)) {
                            isPaymentCode = true;
                        } else if (TARGET_SHORTCUT.equals(arg)) {
                            isBackDoubleTap = true;
                        }
                    }

                    if (!isPaymentCode || !isBackDoubleTap) {
                        return;
                    }

                    int bundleIndex = findBundleArgument(param);
                    if (bundleIndex < 0) {
                        XposedBridge.log(TAG + ": matched BackTap payment action, but no Bundle parameter was found");
                        return;
                    }

                    Bundle bundle = (Bundle) param.args[bundleIndex];
                    if (bundle == null) {
                        bundle = new Bundle();
                        param.args[bundleIndex] = bundle;
                    }

                    bundle.putInt(DISPLAY_KEY, SUB_SCREEN_DISPLAY_ID);
                    XposedBridge.log(TAG + ": forced " + DISPLAY_KEY + "=" + SUB_SCREEN_DISPLAY_ID
                            + " for " + TARGET_SHORTCUT + " -> " + TARGET_FUNCTION);
                }
            });

            XposedBridge.log(TAG + ": hooked " + TARGET_CLASS + "#" + TARGET_METHOD);
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook failed");
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
