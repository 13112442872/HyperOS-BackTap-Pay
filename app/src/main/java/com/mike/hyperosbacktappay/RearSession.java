package com.mike.hyperosbacktappay;

import android.content.SharedPreferences;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Supplier;
import io.github.libxposed.api.XposedModule;

/** Overrides only the windows belonging to a BackTap-started rear code session. */
final class RearSession {
    private final XposedModule module;
    private final Handler handler;
    private final Supplier<SharedPreferences> preferences;
    private final Map<Object, SavedWindow> windows = new IdentityHashMap<>();
    private boolean active;
    private long generation, deadline, launchDeadline, handoffDeadline;
    private float brightness;
    private Object display, activity, service;
    private Runnable expiration;
    private final RearSunlight sunlight = new RearSunlight();
    private boolean sunlightRequested, sunlightAttempted;

    RearSession(XposedModule module, Handler handler, Supplier<SharedPreferences> preferences) {
        this.module = module;
        this.handler = handler;
        this.preferences = preferences;
    }

    void install(ClassLoader loader) throws Exception {
        Class<?> powerImpl = Class.forName("com.android.server.display.DisplayPowerControllerImpl", false, loader);
        for (Method method : powerImpl.getDeclaredMethods()) {
            if (!method.getName().equals("limitQRCodeBrightnessMax") || method.getParameterCount() != 3) continue;
            method.setAccessible(true);
            module.hook(method).setId("hyperos-backtap-pay:rear:qr-brightness").intercept(chain -> {
                boolean controlled;
                synchronized (this) {
                    controlled = active && activity != null && !windows.isEmpty()
                            && SystemClock.uptimeMillis() < deadline;
                }
                if (controlled && ((Number) get(chain.getThisObject(), "mDisplayId")).intValue() == 1) {
                    float max = ((Number) chain.getArgs().get(0)).floatValue();
                    float requested = ((Number) chain.getArgs().get(1)).floatValue();
                    if (Float.isFinite(max) && Float.isFinite(requested)) return Math.max(0f, Math.min(max, requested));
                }
                return chain.proceed();
            });
        }
        Class<?> dc = Class.forName("com.android.server.wm.DisplayContent", false, loader);
        Method surface = dc.getDeclaredMethod("applySurfaceChangesTransaction");
        surface.setAccessible(true);
        module.hook(surface).setId("hyperos-backtap-pay:rear:surface").intercept(chain -> {
            Object target = chain.getThisObject();
            try {
                if (((Number) get(target, "mDisplayId")).intValue() == 1) updateWindow(target);
            } catch (Throwable t) {
                end();
                module.log(Log.ERROR, "HyperOSBackTapPay", "Rear window control failed", t);
            }
            return chain.proceed();
        });
        Class<?> pg = Class.forName("com.android.server.power.PowerGroup", false, loader);
        int count = 0;
        for (Method method : pg.getDeclaredMethods()) {
            if (!method.getName().equals("setWakefulnessLocked")) continue;
            method.setAccessible(true);
            module.hook(method).setId("hyperos-backtap-pay:rear:power:" + count++).intercept(chain -> {
                // Both main-screen locking and rear-screen sleep end the temporary overrides.
                if (!chain.getArgs().isEmpty() && chain.getArgs().get(0) instanceof Integer
                        && ((Integer) chain.getArgs().get(0)) != 1) end();
                return chain.proceed();
            });
        }
        if (count == 0) throw new NoSuchMethodException("PowerGroup.setWakefulnessLocked");
        Class<?> starter = Class.forName("com.android.server.wm.ActivityStarter", false, loader);
        for (Method method : starter.getDeclaredMethods()) {
            if (!method.getName().equals("executeRequest")) continue;
            method.setAccessible(true);
            module.hook(method).setId("hyperos-backtap-pay:rear:intent").intercept(chain -> {
                try {
                    Object request = chain.getArgs().get(0);
                    android.content.Intent intent = (android.content.Intent) get(request, "intent");
                    synchronized (this) {
                        android.content.pm.ActivityInfo info = (android.content.pm.ActivityInfo) get(request, "activityInfo");
                        android.content.ComponentName component = intent == null ? null : intent.getComponent();
                        String pkg = component != null ? component.getPackageName() : info == null ? null : info.packageName;
                        String name = component != null ? component.getClassName() : info == null ? "" : info.name;
                        if (active && intent != null && SystemClock.uptimeMillis() <= launchDeadline
                                && "com.eg.android.AlipayGphone".equals(pkg) && name.contains("xiaomi")) {
                            intent.putExtra("hyperos_backtap_rear_deadline", deadline);
                        }
                    }
                } catch (Throwable t) {
                    module.log(Log.WARN, "HyperOSBackTapPay", "Rear deadline tagging failed", t);
                }
                return chain.proceed();
            });
        }
    }

    synchronized long begin() {
        SharedPreferences p = preferences.get();
        int seconds = p == null ? 30 : p.getInt(Config.PREF_REAR_SECONDS, 30);
        int percent = p == null ? 100 : p.getInt(Config.PREF_REAR_BRIGHTNESS, 100);
        brightness = Math.max(1, Math.min(100, percent)) / 100f;
        sunlightRequested = p != null && p.getBoolean(Config.PREF_REAR_SUNLIGHT, false);
        sunlightAttempted = false;
        if (!sunlightRequested) restoreSunlight();
        long now = SystemClock.uptimeMillis();
        deadline = now + Math.max(1, Math.min(300, seconds)) * 1000L;
        launchDeadline = now + 5000L;
        handoffDeadline = active ? now + 2000L : 0L;
        active = true;
        long token = ++generation;
        if (expiration != null) handler.removeCallbacks(expiration);
        expiration = () -> expire(token);
        handler.postDelayed(expiration, deadline - now);
        if (handoffDeadline > now) handler.postDelayed(() -> verifyHandoff(token), handoffDeadline - now);
        module.log(Log.DEBUG, "HyperOSBackTapPay", "Rear session begin token=" + token + " repeated=" + (handoffDeadline > now));
        return token;
    }

    synchronized void failed(long token) { if (generation == token) end(); }

    private synchronized void updateWindow(Object dc) throws Exception {
        if (!active) return;
        Object focused = get(dc, "mFocusedApp");
        Object window = get(dc, "mCurrentFocus");
        boolean alipay = focused != null && "com.eg.android.AlipayGphone".equals(get(focused, "packageName"));
        if (!alipay) {
            if ((activity != null && SystemClock.uptimeMillis() >= handoffDeadline)
                    || SystemClock.uptimeMillis() >= launchDeadline) end();
            return;
        }
        if (window == null) {
            Method mainWindow = focused.getClass().getDeclaredMethod("findMainWindow", boolean.class);
            mainWindow.setAccessible(true);
            window = mainWindow.invoke(focused, false);
        }
        if (window == null) {
            if ((activity != null && SystemClock.uptimeMillis() >= handoffDeadline)
                    || SystemClock.uptimeMillis() >= launchDeadline) end();
            return;
        }
        if (activity != null && activity != focused && SystemClock.uptimeMillis() >= handoffDeadline) {
            end(); return;
        }
        // A SystemUI/wallpaper window may take focus while mFocusedApp is still stale.
        WindowManager.LayoutParams attrs = (WindowManager.LayoutParams) get(window, "mAttrs");
        if (!"com.eg.android.AlipayGphone".equals(attrs.packageName)) {
            if (activity != null && SystemClock.uptimeMillis() >= handoffDeadline) end();
            return;
        }
        display = dc;
        service = get(dc, "mWmService");
        if (activity != null && activity != focused) {
            for (SavedWindow saved : windows.values()) saved.restore();
            windows.clear();
            module.log(Log.DEBUG, "HyperOSBackTapPay", "Rear code window handed over token=" + generation);
        }
        activity = focused;
        if (sunlightRequested && !sunlightAttempted) {
            sunlightAttempted = true;
            long token = generation;
            Object wm = service;
            handler.post(() -> {
                synchronized (RearSession.this) {
                    if (!active || token != generation) return;
                    try { sunlight.enable((android.content.Context) get(wm, "mContext")); }
                    catch (Throwable t) { module.log(Log.ERROR, "HyperOSBackTapPay", "Rear sunlight enable failed", t); }
                }
            });
        }
        if (!windows.containsKey(window)) windows.put(window, new SavedWindow(attrs));
        attrs.screenBrightness = brightness;
        attrs.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        // RootWindowContainer aggregates userActivityTimeout globally; do not change it.
        // FLAG_KEEP_SCREEN_ON is display-scoped; the session timer closes only this code page.
    }

    private void verifyHandoff(long token) {
        Object wm, dc;
        synchronized (this) {
            if (!active || token != generation) return;
            wm = service; dc = display;
        }
        if (wm == null || dc == null) { failed(token); return; }
        try {
            synchronized (get(wm, "mGlobalLock")) {
                synchronized (this) {
                    if (!active || token != generation) return;
                    updateWindow(dc);
                }
            }
        } catch (Throwable t) {
            failed(token);
            module.log(Log.ERROR, "HyperOSBackTapPay", "Rear handoff verification failed", t);
        }
    }

    private void expire(long token) {
        Object wm;
        synchronized (this) { if (!active || token != generation) return; wm = service; }
        if (wm == null) { failed(token); return; }
        try {
            synchronized (get(wm, "mGlobalLock")) {
                synchronized (this) {
                    if (!active || token != generation) return;
                    Object focused = display == null ? null : get(display, "mFocusedApp");
                    Object finish = activity;
                    boolean stillCode = finish != null && finish == focused;
                    end();
                    if (stillCode) {
                        Method method = finish.getClass().getDeclaredMethod("finishIfPossible", String.class, boolean.class);
                        method.setAccessible(true);
                        method.invoke(finish, "backtap-rear-timeout", true);
                    }
                }
            }
        } catch (Throwable t) {
            end();
            module.log(Log.ERROR, "HyperOSBackTapPay", "Rear timeout cleanup failed", t);
        }
    }

    synchronized void end() {
        if (active) module.log(Log.DEBUG, "HyperOSBackTapPay", "Rear session end token=" + generation);
        active = false;
        handoffDeadline = 0L;
        ++generation;
        if (expiration != null) handler.removeCallbacks(expiration);
        expiration = null;
        restoreSunlight();
        for (SavedWindow saved : windows.values()) saved.restore();
        windows.clear();
        Object wm = service;
        display = activity = service = null;
        if (wm != null) handler.post(() -> {
            try {
                Method request = wm.getClass().getDeclaredMethod("requestTraversal");
                request.setAccessible(true);
                request.invoke(wm);
            } catch (Throwable t) {
                module.log(Log.WARN, "HyperOSBackTapPay", "Rear restore traversal failed", t);
            }
        });
    }

    private void restoreSunlight() {
        try { sunlight.restore(); }
        catch (Throwable t) { module.log(Log.ERROR, "HyperOSBackTapPay", "Rear sunlight restore failed", t); }
    }

    private static Object get(Object object, String name) throws Exception {
        for (Class<?> c = object.getClass(); c != null; c = c.getSuperclass()) {
            try { Field f = c.getDeclaredField(name); f.setAccessible(true); return f.get(object); }
            catch (NoSuchFieldException ignored) { }
        }
        throw new NoSuchFieldException(name);
    }

    private static final class SavedWindow {
        final WindowManager.LayoutParams attrs;
        final float brightness;
        final long timeout;
        final boolean keepScreenOn;
        SavedWindow(WindowManager.LayoutParams attrs) {
            this.attrs = attrs;
            brightness = attrs.screenBrightness;
            timeout = attrs.userActivityTimeout;
            keepScreenOn = (attrs.flags & WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0;
        }
        void restore() {
            attrs.screenBrightness = brightness;
            attrs.userActivityTimeout = timeout;
            if (!keepScreenOn) attrs.flags &= ~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        }
    }
}
