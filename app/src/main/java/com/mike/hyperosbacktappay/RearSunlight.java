package com.mike.hyperosbacktappay;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;
import java.lang.reflect.Method;

/** Saves the exact per-user setting and never touches main-screen sunlight mode. */
final class RearSunlight {
    private static final String KEY = "sub_display_sunlight_mode";
    private ContentResolver resolver;
    private int user;
    private String original, originalMode;
    private boolean changedSunlight, changedMode;
    private static final String MODE = "sub_display_screen_brightness_mode";

    void enable(Context context) throws Exception {
        if (resolver != null || context == null) return;
        Method current = Class.forName("android.app.ActivityManager").getDeclaredMethod("getCurrentUser");
        int id = (Integer) current.invoke(null);
        ContentResolver r = context.getContentResolver();
        String before = read(r, id);
        String mode = (String) Settings.Secure.class.getDeclaredMethod("getStringForUser",
                ContentResolver.class, String.class, int.class).invoke(null, r, MODE, id);
        resolver = r;
        user = id;
        original = before;
        originalMode = mode;
        changedMode = "1".equals(mode);
        changedSunlight = !"1".equals(before);
        if (changedMode && !writeKey(r, id, MODE, "0"))
            throw new IllegalStateException("Cannot temporarily disable rear auto brightness");
        if (changedSunlight && !write(r, id, "1")) throw new IllegalStateException("Cannot enable rear sunlight mode");
    }

    void restore() throws Exception {
        if (resolver == null) return;
        // Do not overwrite a different value changed externally during the session.
        boolean ok = true;
        if (changedSunlight && "1".equals(read(resolver, user))) ok = write(resolver, user, original);
        String mode = (String) Settings.Secure.class.getDeclaredMethod("getStringForUser",
                ContentResolver.class, String.class, int.class).invoke(null, resolver, MODE, user);
        if (changedMode && "0".equals(mode)) ok = writeKey(resolver, user, MODE, originalMode) && ok;
        if (!ok) throw new IllegalStateException("Cannot restore rear brightness settings");
        resolver = null;
        original = originalMode = null;
        changedMode = changedSunlight = false;
    }

    private static String read(ContentResolver r, int user) throws Exception {
        return (String) Settings.Secure.class.getDeclaredMethod("getStringForUser",
                ContentResolver.class, String.class, int.class).invoke(null, r, KEY, user);
    }
    private static boolean write(ContentResolver r, int user, String value) throws Exception {
        return writeKey(r, user, KEY, value);
    }
    private static boolean writeKey(ContentResolver r, int user, String key, String value) throws Exception {
        return (Boolean) Settings.Secure.class.getDeclaredMethod("putStringForUser",
                ContentResolver.class, String.class, String.class, int.class).invoke(null, r, key, value, user);
    }
}
