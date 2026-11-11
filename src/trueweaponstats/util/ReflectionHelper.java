package trueweaponstats.util;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ui.UIPanelAPI;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReflectionHelper {

    private static final Map<String, Field> fieldCache = new HashMap<>();

    public static Object getField(Object obj, String fieldName) {
        if (obj == null || fieldName == null) return null;
        String key = obj.getClass().getName() + "#" + fieldName;
        Field cached = fieldCache.get(key);
        if (cached != null) {
            try {
                return cached.get(obj);
            } catch (Exception ignored) {}
        }

        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(fieldName);
                f.setAccessible(true);
                fieldCache.put(key, f);
                return f.get(obj);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    public static Object getFieldNoCatch(Object obj, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        Object val = getField(obj, fieldName);
        if (val == null) {
            Class<?> current = obj.getClass();
            while (current != null && current != Object.class) {
                try {
                    Field f = current.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    return f.get(obj);
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            throw new NoSuchFieldException(fieldName + " on " + obj.getClass());
        }
        return val;
    }

    public static void setField(Object obj, String fieldName, Object value) {
        if (obj == null || fieldName == null) return;
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            try {
                Field f = current.getDeclaredField(fieldName);
                f.setAccessible(true);
                f.set(obj, value);
                return;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (Exception ignored) {
                return;
            }
        }
    }

    public static Object invokeMethod(Object obj, String methodName, Object... params) {
        if (obj == null || methodName == null) return null;
        try {
            return invokeMethodNoCatch(obj, methodName, params);
        } catch (Exception e) {
            return null;
        }
    }

    public static Object invokeMethodNoCatch(Object obj, String methodName, Object... params)
            throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (obj == null) return null;

        for (Method method : obj.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == params.length) {
                method.setAccessible(true);
                return method.invoke(obj, params);
            }
        }

        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == params.length) {
                    method.setAccessible(true);
                    return method.invoke(obj, params);
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(methodName + " on " + obj.getClass());
    }

    public static Object invokeMethodExt(Object obj, String methodName, boolean declared, Object... params) {
        return invokeMethod(obj, methodName, params);
    }

    public static boolean hasMethod(Object obj, String methodName) {
        if (obj == null || methodName == null) return false;
        Class<?> current = obj.getClass();
        while (current != null && current != Object.class) {
            for (Method m : current.getDeclaredMethods()) {
                if (m.getName().equals(methodName)) return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    public static Object getCampaignState() {
        // Path 1: CampaignEngine listener field or method
        try {
            var sector = Global.getSector();
            if (sector != null) {
                // Try direct field first (most reliable, avoids method reflection issues)
                Object listener = getField(sector, "listener");
                if (listener != null) return listener;

                Object l = invokeMethod(sector, "getListener");
                if (l != null) return l;
            }
        } catch (Throwable ignored) {}

        // Path 2: AppDriver getState("Campaign State")
        try {
            var driver = com.fs.state.AppDriver.getInstance();
            if (driver != null) {
                Object cState = driver.getState("Campaign State");
                if (cState != null) return cState;

                Object curr = driver.getCurrentState();
                if (curr != null) return curr;
            }
        } catch (Throwable ignored) {}

        return null;
    }

    public static UIPanelAPI getScreenPanel() {
        try {
            Object state = getCampaignState();
            if (state != null) {
                Object sp = getField(state, "screenPanel");
                if (sp instanceof UIPanelAPI) return (UIPanelAPI) sp;

                try {
                    Object msp = invokeMethod(state, "getScreenPanel");
                    if (msp instanceof UIPanelAPI) return (UIPanelAPI) msp;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Global.getLogger(ReflectionHelper.class).error("[TrueWeaponStats] Exception in getScreenPanel: " + t, t);
        }
        return null;
    }

    public static UIPanelAPI getCoreUI() {
        try {
            Object state = getCampaignState();
            if (state != null) {
                Object core = getField(state, "core");
                if (core instanceof UIPanelAPI) return (UIPanelAPI) core;

                try {
                    Object mcore = invokeMethod(state, "getCore");
                    if (mcore instanceof UIPanelAPI) return (UIPanelAPI) mcore;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            Global.getLogger(ReflectionHelper.class).error("[TrueWeaponStats] Exception in getCoreUI: " + t, t);
        }

        var sector = Global.getSector();
        if (sector != null && sector.getCampaignUI() != null) {
            var dialog = sector.getCampaignUI().getCurrentInteractionDialog();
            if (dialog != null) {
                Object dialogCore = invokeMethod(dialog, "getCoreUI");
                if (dialogCore instanceof UIPanelAPI) return (UIPanelAPI) dialogCore;
            }
        }

        return null;
    }

    public static UIPanelAPI getDialogParent() {
        try {
            Object state = getCampaignState();
            if (state != null) {
                Object dp = getField(state, "dialogParent");
                if (dp instanceof UIPanelAPI) return (UIPanelAPI) dp;

                try {
                    Object mdp = invokeMethod(state, "getDialogParent");
                    if (mdp instanceof UIPanelAPI) return (UIPanelAPI) mdp;
                } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public static void dumpDiagnostics() {
        org.apache.log4j.Logger log = Global.getLogger(ReflectionHelper.class);
        log.info("========== [TrueWeaponStats DIAGNOSTICS START] ==========");
        log.info("[Diag] ClassLoader: " + ReflectionHelper.class.getClassLoader().getClass().getName());
        try {
            var sector = Global.getSector();
            log.info("[Diag] Sector: " + (sector != null ? sector.getClass().getName() : "null"));
            if (sector != null) {
                Object lField = getField(sector, "listener");
                log.info("[Diag] sector.listener (field): " + (lField != null ? lField.getClass().getName() : "null"));

                try {
                    Method m = sector.getClass().getMethod("getListener");
                    Object lMethod = m.invoke(sector);
                    log.info("[Diag] sector.getListener() (method): " + (lMethod != null ? lMethod.getClass().getName() : "null"));
                } catch (Throwable t) {
                    log.info("[Diag] sector.getListener() error: " + t);
                }
            }
        } catch (Throwable t) {
            log.info("[Diag] Sector error: " + t);
        }

        try {
            var driver = com.fs.state.AppDriver.getInstance();
            log.info("[Diag] AppDriver: " + (driver != null ? driver.getClass().getName() : "null"));
            if (driver != null) {
                var curr = driver.getCurrentState();
                log.info("[Diag] driver.getCurrentState(): " + (curr != null ? curr.getClass().getName() : "null"));

                var cState = driver.getState("Campaign State");
                log.info("[Diag] driver.getState('Campaign State'): " + (cState != null ? cState.getClass().getName() : "null"));

                Object statesMap = getField(driver, "states");
                if (statesMap instanceof Map) {
                    log.info("[Diag] driver.states registered: " + ((Map<?, ?>) statesMap).keySet());
                }
            }
        } catch (Throwable t) {
            log.info("[Diag] AppDriver error: " + t);
        }

        try {
            Object state = getCampaignState();
            log.info("[Diag] Resolved CampaignState: " + (state != null ? state.getClass().getName() : "null"));
            if (state != null) {
                Object spField = getField(state, "screenPanel");
                log.info("[Diag] state.screenPanel (field): " + (spField != null ? spField.getClass().getName() : "null"));

                Object coreField = getField(state, "core");
                log.info("[Diag] state.core (field): " + (coreField != null ? coreField.getClass().getName() : "null"));

                Object dpField = getField(state, "dialogParent");
                log.info("[Diag] state.dialogParent (field): " + (dpField != null ? dpField.getClass().getName() : "null"));

                UIPanelAPI sp = getScreenPanel();
                if (sp != null) {
                    List<?> children = (List<?>) invokeMethod(sp, "getChildrenNonCopy");
                    if (children == null) children = (List<?>) invokeMethod(sp, "getChildrenCopy");
                    log.info("[Diag] screenPanel children count: " + (children != null ? children.size() : "null"));
                    if (children != null) {
                        for (int i = 0; i < children.size(); i++) {
                            Object c = children.get(i);
                            log.info(String.format("[Diag]   child[%d]: %s", i, c != null ? c.getClass().getName() : "null"));
                        }
                    }
                }
            }
        } catch (Throwable t) {
            log.info("[Diag] State inspect error: " + t);
        }
        log.info("========== [TrueWeaponStats DIAGNOSTICS END] ==========");
    }
}
