package io.github.heytapmarketclean;

import android.app.Application;
import android.content.SharedPreferences;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

/** 设置页侧：通过框架服务读写 RemotePreferences（被 hook 进程只读）。 */
public final class App extends Application implements XposedServiceHelper.OnServiceListener {
    private static volatile XposedService service;

    /** 未连接框架时返回 null —— 调用方必须容忍。 */
    static SharedPreferences preferences() {
        XposedService bound = service;
        if (bound == null) return null;
        try { return bound.getRemotePreferences(Config.GROUP); }
        catch (Throwable ignored) { return null; }
    }

    static boolean write(String key, boolean value) {
        SharedPreferences prefs = preferences();
        if (prefs == null) return false;
        try { return prefs.edit().putBoolean(key, value).commit(); }
        catch (Throwable ignored) { return false; }
    }

    /** 底栏隐藏名单这类字符串设置。 */
    static boolean write(String key, String value) {
        SharedPreferences prefs = preferences();
        if (prefs == null) return false;
        try { return prefs.edit().putString(key, value).commit(); }
        catch (Throwable ignored) { return false; }
    }

    static boolean reset() {
        SharedPreferences prefs = preferences();
        if (prefs == null) return false;
        try { return prefs.edit().clear().commit(); }
        catch (Throwable ignored) { return false; }
    }

    @Override public void onCreate() {
        super.onCreate();
        try { XposedServiceHelper.registerListener(this); }
        catch (Throwable ignored) { }
    }

    @Override public void onServiceBind(XposedService bound) { service = bound; }

    @Override public void onServiceDied(XposedService bound) {
        if (service == bound) service = null;
    }
}
