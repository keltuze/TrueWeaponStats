package trueweaponstats;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import trueweaponstats.locale.LocaleInspector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Main plugin lifecycle controller for TrueWeaponStats.
 * Manages privileged classloading to operate seamlessly within Starsector's security architecture.
 */
public class TrueWeaponStatsPlugin extends BaseModPlugin {

    private static TWSPrivilegedClassLoader privilegedClassLoader;

    /**
     * Obtains the privileged URLClassLoader that defines internal mod classes
     * to safely interact with Starsector's UI reflection layer without triggering SecurityExceptions.
     */
    public static synchronized TWSPrivilegedClassLoader getPrivilegedClassLoader() {
        if (privilegedClassLoader == null) {
            URL modJarUrl = TrueWeaponStatsPlugin.class.getProtectionDomain().getCodeSource().getLocation();
            privilegedClassLoader = new TWSPrivilegedClassLoader(modJarUrl, TrueWeaponStatsPlugin.class.getClassLoader());
        }
        return privilegedClassLoader;
    }

    /**
     * Subpackages defined directly by this privileged loader.
     * IMPORTANT: TrueWeaponStatsPlugin itself is deliberately omitted to prevent duplicate
     * definition and infinite loader self-upgrade loops.
     */
    private static final String[] PRIVILEGED_PACKAGES = new String[] {
            "trueweaponstats.campaign.",
            "trueweaponstats.ui.",
            "trueweaponstats.engine.",
            "trueweaponstats.locale.",
            "trueweaponstats.util."
    };

    /**
     * Dedicated classloader for TrueWeaponStats.
     * Bypasses Starsector's ScriptClassLoader reflection restriction by defining
     * mod logic outside the script sandbox and delegating reflection calls to the system loader.
     */
    public static class TWSPrivilegedClassLoader extends URLClassLoader {

        public TWSPrivilegedClassLoader(URL jarUrl, ClassLoader parent) {
            super(new URL[] { jarUrl }, parent);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (name.startsWith("java.lang.reflect")) {
                return ClassLoader.getSystemClassLoader().loadClass(name);
            }
            return super.loadClass(name);
        }

        @Override
        public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                return loaded;
            }

            for (String pkg : PRIVILEGED_PACKAGES) {
                if (name.startsWith(pkg)) {
                    return findClass(name);
                }
            }
            return super.loadClass(name, resolve);
        }
    }

    @Override
    public void onApplicationLoad() {
        try {
            LocaleInspector.get().initialize();
            Global.getLogger(TrueWeaponStatsPlugin.class).info("[TrueWeaponStats] Locale inspection engine initialized.");
        } catch (Throwable t) {
            Global.getLogger(TrueWeaponStatsPlugin.class).error("[TrueWeaponStats] Error initializing locale engine", t);
        }
    }

    @Override
    public void onGameLoad(boolean newGame) {
        try {
            LocaleInspector.get().initialize();

            // Instantiate campaign listener through privileged classloader
            Class<?> listenerClass = getPrivilegedClassLoader().loadClass("trueweaponstats.campaign.TrueWeaponStatsRefitListener");
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandle constructor = lookup.findConstructor(listenerClass, MethodType.methodType(void.class));
            EveryFrameScript listenerInstance = (EveryFrameScript) constructor.invoke();

            Global.getSector().addTransientScript(listenerInstance);

            ListenerManagerAPI listeners = Global.getSector().getListenerManager();
            if (listeners != null && !listeners.hasListenerOfClass(listenerClass)) {
                listeners.addListener(listenerInstance, true);
            }

            Global.getLogger(TrueWeaponStatsPlugin.class).info("[TrueWeaponStats] Active refit listener registered successfully.");
        } catch (Throwable e) {
            Global.getLogger(TrueWeaponStatsPlugin.class).error("[TrueWeaponStats] Failed to initialize refit listener", e);
        }
    }
}
