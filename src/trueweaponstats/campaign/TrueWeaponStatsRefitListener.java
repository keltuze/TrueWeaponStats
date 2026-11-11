package trueweaponstats.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CoreUIAPI;
import com.fs.starfarer.api.campaign.CoreUITabId;
import com.fs.starfarer.api.campaign.listeners.CoreUITabListener;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.title.TitleScreenState;
import com.fs.state.AppDriver;
import org.apache.log4j.Logger;
import trueweaponstats.ui.RefitUIInspector;
import trueweaponstats.util.ReflectionHelper;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.List;

/**
 * Universal lifecycle controller for TrueWeaponStats.
 * Works seamlessly whether refit is opened via hotkey [R], Fleet screen [F],
 * docked station interaction dialogs, or title-screen missions/simulations.
 */
public class TrueWeaponStatsRefitListener extends BaseEveryFrameCombatPlugin implements CoreUITabListener, EveryFrameScript {

    private static final Logger log = Global.getLogger(TrueWeaponStatsRefitListener.class);

    private final RefitUIInspector inspector = new RefitUIInspector();
    private boolean wasInRefit = false;

    @Override
    public void reportAboutToOpenCoreTab(CoreUITabId id, Object param) {
    }

    private static volatile boolean privilegedInstanceSpawned = false;
    private boolean isDead = false;

    @Override
    public boolean isDone() {
        return isDead;
    }

    @Override
    public boolean runWhilePaused() {
        return true;
    }

    private int tickCount = 0;

    @Override
    public void advance(float amount) {
        // Enforce execution exclusively within the privileged classloader
        boolean isPrivileged = getClass().getClassLoader().getClass().getName().contains("TWSPrivilegedClassLoader");

        if (!isPrivileged) {
            isDead = true;
            if (!privilegedInstanceSpawned) {
                privilegedInstanceSpawned = true;
                try {
                    Class<?> cls = trueweaponstats.TrueWeaponStatsPlugin.getPrivilegedClassLoader()
                            .loadClass("trueweaponstats.campaign.TrueWeaponStatsRefitListener");
                    MethodHandles.Lookup lookup = MethodHandles.lookup();
                    MethodHandle mh = lookup.findConstructor(cls, MethodType.methodType(void.class));
                    EveryFrameScript privileged = (EveryFrameScript) mh.invoke();
                    Global.getSector().addTransientScript(privileged);
                    log.info("[TrueWeaponStats] Successfully escalated listener to privileged classloader.");
                } catch (Throwable t) {
                    log.error("[TrueWeaponStats] Classloader escalation failed: " + t, t);
                }
            }
            return;
        }

        try {
            tickCount++;
            boolean heartbeat = (tickCount % 60 == 1);

            if (tickCount == 5 || tickCount == 120 || (tickCount % 600 == 1)) {
                ReflectionHelper.dumpDiagnostics();
            }

            UIPanelAPI screenPanel = ReflectionHelper.getScreenPanel();
            UIPanelAPI core = ReflectionHelper.getCoreUI();
            UIPanelAPI dialogParent = ReflectionHelper.getDialogParent();

            // Locate active refit panel across all known entry points (Fleet [F], Refit [R], station dialog, etc.)
            Object refitPanel = findActiveRefitPanel(screenPanel, core, dialogParent);

            if (heartbeat) {
                log.info(String.format("[TrueWeaponStats] Heartbeat #%d: screenPanel=%s, core=%s, refitPanel=%s",
                        tickCount,
                        screenPanel != null ? screenPanel.getClass().getSimpleName() : "null",
                        core != null ? core.getClass().getSimpleName() : "null",
                        refitPanel != null ? refitPanel.getClass().getSimpleName() : "null"));
            }

            if (refitPanel == null) {
                if (wasInRefit) {
                    wasInRefit = false;
                    log.info("[TrueWeaponStats] Exited refit screen.");
                    inspector.reset();
                }
                return;
            }

            if (!wasInRefit) {
                wasInRefit = true;
                log.info("[TrueWeaponStats] Active refit panel detected: " + refitPanel.getClass().getName());
            }

            inspector.update(screenPanel, core != null ? core : screenPanel, dialogParent, refitPanel);
        } catch (Throwable t) {
            log.error("[TrueWeaponStats] Error in refit listener advance: " + t.getMessage(), t);
        }
    }

    /**
     * Resolves the active refit panel object across all known UI structures in Starsector 0.98a.
     */
    public static Object findActiveRefitPanel(UIPanelAPI screenPanel, UIPanelAPI core, UIPanelAPI dialogParent) {
        // 1. Core current tab check (e.g. space hotkey [R])
        if (core != null) {
            Object tab = ReflectionHelper.invokeMethod(core, "getCurrentTab");
            Object r = resolveRefitPanel(tab);
            if (r != null) return r;
        }

        // 2. Check screenPanel children (e.g. returnnew modal dialog opened from Fleet [F] -> Edit Ship)
        if (screenPanel != null) {
            Object r = searchHierarchyForRefit(screenPanel, 0);
            if (r != null) return r;
        }

        // 3. Check dialogParent children
        if (dialogParent != null && dialogParent != screenPanel) {
            Object r = searchHierarchyForRefit(dialogParent, 0);
            if (r != null) return r;
        }

        // 4. Check core children
        if (core != null && core != screenPanel) {
            Object r = searchHierarchyForRefit(core, 0);
            if (r != null) return r;
        }

        // 5. Check interaction dialog
        var sector = Global.getSector();
        if (sector != null && sector.getCampaignUI() != null) {
            var dialog = sector.getCampaignUI().getCurrentInteractionDialog();
            if (dialog != null) {
                Object r = resolveRefitPanel(dialog);
                if (r != null) return r;
                r = searchHierarchyForRefit(dialog, 0);
                if (r != null) return r;
            }
        }

        return null;
    }

    private static Object searchHierarchyForRefit(Object obj, int depth) {
        if (obj == null || depth > 3) return null;

        Object r = resolveRefitPanel(obj);
        if (r != null) return r;

        List<?> children = (List<?>) ReflectionHelper.invokeMethod(obj, "getChildrenNonCopy");
        if (children == null) {
            children = (List<?>) ReflectionHelper.invokeMethod(obj, "getChildrenCopy");
        }
        if (children != null) {
            for (Object child : children) {
                if (child == null || child == obj) continue;
                r = searchHierarchyForRefit(child, depth + 1);
                if (r != null) return r;
            }
        }
        return null;
    }

    public static Object resolveRefitPanel(Object target) {
        if (target == null) return null;

        // 1. Target itself is RefitPanel (U)
        if (ReflectionHelper.hasMethod(target, "syncWithCurrentVariant") || ReflectionHelper.hasMethod(target, "getShipDisplay")) {
            return target;
        }

        // 2. Target is RefitTab (void) with getRefitPanel()
        Object inner = ReflectionHelper.invokeMethod(target, "getRefitPanel");
        if (inner != null && (ReflectionHelper.hasMethod(inner, "syncWithCurrentVariant") || ReflectionHelper.hasMethod(inner, "getShipDisplay"))) {
            return inner;
        }

        // 3. Target is returnnew or a dialog with a field of type void or U
        Class<?> tc = target.getClass();
        while (tc != null && tc != Object.class) {
            for (Field f : tc.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object val = f.get(target);
                    if (val == null || val == target) continue;
                    if (ReflectionHelper.hasMethod(val, "syncWithCurrentVariant") || ReflectionHelper.hasMethod(val, "getShipDisplay")) {
                        return val;
                    }
                    if (ReflectionHelper.hasMethod(val, "getRefitPanel")) {
                        Object p = ReflectionHelper.invokeMethod(val, "getRefitPanel");
                        if (p != null) return p;
                    }
                } catch (Throwable ignored) {}
            }
            tc = tc.getSuperclass();
        }

        // 4. Target has children containing U
        List<?> children = (List<?>) ReflectionHelper.invokeMethod(target, "getChildrenNonCopy");
        if (children == null) {
            children = (List<?>) ReflectionHelper.invokeMethod(target, "getChildrenCopy");
        }
        if (children != null) {
            for (Object c : children) {
                if (c == null || c == target) continue;
                if (ReflectionHelper.hasMethod(c, "syncWithCurrentVariant") || ReflectionHelper.hasMethod(c, "getShipDisplay")) {
                    return c;
                }
                if (ReflectionHelper.hasMethod(c, "getRefitPanel")) {
                    Object p = ReflectionHelper.invokeMethod(c, "getRefitPanel");
                    if (p != null) return p;
                }
            }
        }

        return null;
    }

    @Override
    public void init(CombatEngineAPI engine) {
    }

    @Override
    public void advance(float amount, List<InputEventAPI> events) {
        try {
            var driver = AppDriver.getInstance();
            if (driver == null) return;
            var state = driver.getCurrentState();
            if (!(state instanceof TitleScreenState)) return;

            UIPanelAPI screenPanel = (UIPanelAPI) ReflectionHelper.getField(state, "screenPanel");
            if (screenPanel == null) return;

            UIPanelAPI coreUI = null;
            List<?> children = (List<?>) ReflectionHelper.invokeMethod(screenPanel, "getChildrenNonCopy");
            if (children == null) return;

            for (Object c : children) {
                if (c == null) continue;
                List<?> sub = (List<?>) ReflectionHelper.invokeMethod(c, "getChildrenNonCopy");
                if (sub != null && !sub.isEmpty() && sub.get(0) instanceof CoreUIAPI) {
                    coreUI = (UIPanelAPI) sub.get(0);
                    break;
                }
            }
            if (coreUI == null) return;

            Object tabData = ReflectionHelper.invokeMethod(coreUI, "getCurrentTabData");
            if (tabData == null) return;

            UIPanelAPI activeTab = null;
            for (Field f : tabData.getClass().getDeclaredFields()) {
                if (UIPanelAPI.class.isAssignableFrom(f.getType())) {
                    activeTab = (UIPanelAPI) ReflectionHelper.getField(tabData, f.getName());
                    break;
                }
            }

            if (activeTab != null) {
                Object refit = resolveRefitPanel(activeTab);
                if (refit != null) {
                    inspector.update(screenPanel, screenPanel, null, refit);
                }
            }
        } catch (Throwable ignored) {}
    }
}
