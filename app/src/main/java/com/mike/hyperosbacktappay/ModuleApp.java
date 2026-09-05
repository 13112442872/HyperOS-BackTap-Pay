package com.mike.hyperosbacktappay;

import android.app.Application;

import java.util.concurrent.CopyOnWriteArraySet;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModuleApp extends Application implements XposedServiceHelper.OnServiceListener {
    private static volatile XposedService service;
    private static final CopyOnWriteArraySet<ServiceStateListener> listeners =
            new CopyOnWriteArraySet<>();

    public interface ServiceStateListener {
        void onServiceStateChanged(XposedService service);
    }

    public static XposedService getService() {
        return service;
    }

    public static void addServiceStateListener(ServiceStateListener listener, boolean notifyImmediately) {
        listeners.add(listener);
        if (notifyImmediately) {
            listener.onServiceStateChanged(service);
        }
    }

    public static void removeServiceStateListener(ServiceStateListener listener) {
        listeners.remove(listener);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService boundService) {
        service = boundService;
        notifyListeners(boundService);
    }

    @Override
    public void onServiceDied(XposedService deadService) {
        if (service == deadService) {
            service = null;
            notifyListeners(null);
        }
    }

    private static void notifyListeners(XposedService value) {
        for (ServiceStateListener listener : listeners) {
            listener.onServiceStateChanged(value);
        }
    }
}
