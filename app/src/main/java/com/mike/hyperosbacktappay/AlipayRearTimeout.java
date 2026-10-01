package com.mike.hyperosbacktappay;

import android.app.Activity;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import io.github.libxposed.api.XposedModule;

/** Changes only the known rear-code automatic exit message on tagged module launches. */
final class AlipayRearTimeout {
    static void install(XposedModule module) throws Exception {
        Method send = Handler.class.getDeclaredMethod("sendMessageAtTime", Message.class, long.class);
        module.hook(send).setId("hyperos-backtap-pay:alipay:rear-timeout").intercept(chain -> {
            try {
                Message message = (Message) chain.getArgs().get(0);
                if (message.what != 1001) return chain.proceed();
                Handler handler = (Handler) chain.getThisObject();
                Field callbackField = Handler.class.getDeclaredField("mCallback");
                callbackField.setAccessible(true);
                Object callback = callbackField.get(handler);
                if (callback == null || !callback.getClass().getName().equals(
                        "com.alipay.mobile.onsitepay.xiaomi.XiaomiQuickpayActivity$PayCodeCallback")) return chain.proceed();
                Field ownerField = callback.getClass().getDeclaredField("this$0");
                ownerField.setAccessible(true);
                Activity owner = (Activity) ownerField.get(callback);
                if (owner.getDisplay() == null || owner.getDisplay().getDisplayId() != 1
                        || owner.isFinishing() || owner.isDestroyed()) return chain.proceed();
                long deadline = owner.getIntent().getLongExtra("hyperos_backtap_rear_deadline", 0);
                if (deadline <= SystemClock.uptimeMillis()) return chain.proceed();
                List<Object> args = new ArrayList<>(chain.getArgs());
                args.set(1, deadline);
                return chain.proceed(args.toArray());
            } catch (Throwable t) {
                module.log(Log.WARN, "HyperOSBackTapPay", "Alipay rear timeout interception failed", t);
                return chain.proceed();
            }
        });
        module.log(Log.INFO, "HyperOSBackTapPay", "Alipay rear automatic-exit deadline hook ready");
    }
}
