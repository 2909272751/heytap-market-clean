package io.github.libxposed.api;

import java.lang.reflect.Executable;

/**
 * compile-only stub of libxposed:api 102 XposedModule (abstract entry base).
 * The framework's real class provides these methods at runtime.
 */
public abstract class XposedModule implements XposedInterface {

    /**
     * 两套框架的入口构造器不一样，两个都留着（真机实测）：
     *  - Vector v2.2（LSPosed 1.9.2 之后的活跃分支）：只有 <init>()，
     *    真机日志：NoSuchMethodException: MainHook.<init> []
     *  - 老 LSPosed（LSPosed IT / 1.9.2 及更早）：只有 (XposedInterface, ModuleLoadedParam)，
     *    真机日志：NoSuchMethodException: MainHook.<init> [XposedInterface, ModuleLoadedParam]
     * 未被调用的那个构造器不会在类加载时被解析，所以两个框架都能跑。
     */
    protected XposedModule() {
        throw new AssertionError("stub");
    }

    protected XposedModule(XposedInterface base, XposedModuleInterface.ModuleLoadedParam param) {
        throw new AssertionError("stub");
    }

    @Override
    public HookBuilder hook(Executable target) { throw new AssertionError("stub"); }

    public void log(int priority, String tag, String message) { throw new AssertionError("stub"); }
    public void log(int priority, String tag, String message, Throwable throwable) { throw new AssertionError("stub"); }

    public int getApiVersion() { return 0; }
    public String getFrameworkName() { return null; }
    public String getFrameworkVersion() { return null; }

    public void deoptimize(Executable target) { throw new AssertionError("stub"); }

    /** 框架提供：同名 group 与模块 App 侧 XposedService 写入的 RemotePreferences 共享 */
    public android.content.SharedPreferences getRemotePreferences(String group) {
        throw new AssertionError("stub");
    }

    public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam param) {}
    public void onPackageLoaded(XposedModuleInterface.PackageLoadedParam param) {}
    public void onPackageReady(XposedModuleInterface.PackageReadyParam param) {}
    public void onSystemServerStarting(XposedModuleInterface.SystemServerStartingParam param) {}
    public boolean onHotReloading() { return false; }
    public void onHotReloaded() {}
}
