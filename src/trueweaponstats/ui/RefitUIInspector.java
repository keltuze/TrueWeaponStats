package trueweaponstats.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.UIPanelAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.coreui.refit.ModPickerDialogV3;
import com.fs.starfarer.coreui.refit.WeaponPickerDialog;
import com.fs.starfarer.ui.impl.CargoTooltipFactory;
import com.fs.starfarer.ui.impl.StandardTooltipV2;
import com.fs.starfarer.ui.impl.StandardTooltipV2Expandable;
import org.lwjgl.input.Keyboard;
import trueweaponstats.campaign.TrueWeaponStatsRefitListener;
import trueweaponstats.engine.WeaponStatsEngine;
import trueweaponstats.locale.LocaleInspector;
import trueweaponstats.util.ReflectionHelper;

import java.awt.Color;
import java.lang.reflect.Field;
import java.util.*;

/**
 * Traverses and inspects the live Starsector Refit screen hierarchy.
 * Manages reactive real-time toggling of vanilla vs modified stats via the ALT key.
 */
public class RefitUIInspector {

    private static Color colorBuff;
    private static Color colorNerf;

    public static Color getColorBuff() {
        if (colorBuff == null) {
            try {
                colorBuff = Global.getSettings().getColor("textFriendColor");
            } catch (Throwable ignored) {}
            if (colorBuff == null) colorBuff = new Color(100, 255, 100);
        }
        return colorBuff;
    }

    public static Color getColorNerf() {
        if (colorNerf == null) {
            try {
                colorNerf = Global.getSettings().getColor("textEnemyColor");
            } catch (Throwable ignored) {}
            if (colorNerf == null) colorNerf = new Color(255, 100, 100);
        }
        return colorNerf;
    }

    public static Color getDisabledBuffColor() {
        return Misc.interpolateColor(getColorBuff(), Color.BLACK, 0.45f);
    }

    public static Color getDisabledNerfColor() {
        return Misc.interpolateColor(getColorNerf(), Color.BLACK, 0.45f);
    }

    public static boolean isAltDown() {
        try {
            return Keyboard.isCreated() && (Keyboard.isKeyDown(Keyboard.KEY_LMENU) || Keyboard.isKeyDown(Keyboard.KEY_RMENU));
        } catch (Exception e) {
            return false;
        }
    }

    public static class GridRowState {
        public Object grid;
        public int row;
        public int col;
        public LabelAPI valueLabel;
        public String vanillaText;
        public String modifiedText;
        public String[] highlights;
        public Color[] highlightColors;

        public void apply(boolean altDown) {
            if (valueLabel == null || grid == null) return;
            if (altDown) {
                ReflectionHelper.invokeMethod(grid, "updateRow", row, col, vanillaText);
                valueLabel.setHighlight("");
            } else {
                ReflectionHelper.invokeMethod(grid, "updateRow", row, col, modifiedText);
                if (highlights != null && highlights.length > 0) {
                    valueLabel.setHighlight(highlights);
                    valueLabel.setHighlightColors(highlightColors);
                } else {
                    valueLabel.setHighlight("");
                }
            }
        }
    }

    public static class PickerButtonState {
        public LabelAPI opLabel;
        public String vanillaOPText;
        public String modifiedOPText;
        public Color vanillaOPColor;
        public Color modifiedOPHighlight;

        public LabelAPI rangeLabel;
        public String vanillaRangeText;
        public String modifiedRangeText;
        public String modifiedRangeHighlight;
        public Color rangeHighlightColor;

        public void apply(boolean altDown) {
            if (opLabel != null) {
                if (altDown) {
                    opLabel.setText(vanillaOPText);
                    opLabel.setColor(vanillaOPColor);
                    opLabel.setHighlight("");
                } else {
                    opLabel.setText(modifiedOPText);
                    if (modifiedOPHighlight != null) {
                        opLabel.setHighlight(modifiedOPText);
                        opLabel.setHighlightColor(modifiedOPHighlight);
                    } else {
                        opLabel.setHighlight("");
                    }
                }
                ReflectionHelper.invokeMethod(opLabel, "autoSize");
            }
            if (rangeLabel != null) {
                if (altDown) {
                    rangeLabel.setText(vanillaRangeText);
                    rangeLabel.setHighlight("");
                } else {
                    rangeLabel.setText(modifiedRangeText);
                    if (modifiedRangeHighlight != null) {
                        rangeLabel.setHighlight(modifiedRangeHighlight);
                        rangeLabel.setHighlightColor(rangeHighlightColor);
                    } else {
                        rangeLabel.setHighlight("");
                    }
                }
                ReflectionHelper.invokeMethod(rangeLabel, "autoSize");
            }
        }
    }

    public static class TotalFluxState {
        public LabelAPI valueLabel;
        public String vanillaText;
        public String modifiedText;
        public Color highlightColor;

        public void apply(boolean altDown) {
            if (valueLabel == null) return;
            if (altDown) {
                valueLabel.setText(vanillaText);
                valueLabel.setHighlight("");
            } else {
                valueLabel.setText(modifiedText);
                if (highlightColor != null) {
                    valueLabel.setHighlight(modifiedText.trim());
                    valueLabel.setHighlightColor(highlightColor);
                } else {
                    valueLabel.setHighlight("");
                }
            }
        }
    }

    private ShipAPI activeShip;
    private WeaponPickerDialog activePicker;
    private ButtonAPI firstPickerButton;
    private boolean lastAltDown = false;

    // Active tooltip states for instant ALT switching
    private StandardTooltipV2 lastHoveredTooltip;
    private final List<GridRowState> hoveredRowStates = new ArrayList<>();

    private StandardTooltipV2Expandable lastCurrTooltip;
    private final List<GridRowState> currRowStates = new ArrayList<>();

    private StandardTooltipV2Expandable lastActiveTooltip;
    private final List<GridRowState> activeRowStates = new ArrayList<>();

    private final Map<ButtonAPI, PickerButtonState> pickerButtonStates = new HashMap<>();
    private TotalFluxState totalFluxState = null;

    private static String cachedSlotField;
    private static String cachedRendererPanelField;
    private static String cachedFluxField;

    public ShipAPI getActiveShip() {
        return activeShip;
    }

    public void reset() {
        hoveredRowStates.clear();
        currRowStates.clear();
        activeRowStates.clear();
        pickerButtonStates.clear();
        lastHoveredTooltip = null;
        lastCurrTooltip = null;
        lastActiveTooltip = null;
        firstPickerButton = null;
        totalFluxState = null;
        activePicker = null;
    }

    public void update(UIPanelAPI screenPanel, UIPanelAPI core, UIPanelAPI dialogParent, Object refitPanelTarget) {
        try {
            LocaleInspector.get().initialize();

            boolean altDown = isAltDown();
            boolean altChanged = (altDown != lastAltDown);
            lastAltDown = altDown;

            Object refitPanel = TrueWeaponStatsRefitListener.resolveRefitPanel(refitPanelTarget);
            if (refitPanel == null) return;

            refreshActiveShip(refitPanel);
            refreshShipSustainedFlux(core, refitPanel, altDown, altChanged);

            boolean found = inspectHoveredTooltips(screenPanel, altDown, altChanged);
            if (!found && dialogParent != null && dialogParent != screenPanel) {
                found = inspectHoveredTooltips(dialogParent, altDown, altChanged);
            }
            if (!found && refitPanel instanceof UIPanelAPI && refitPanel != screenPanel) {
                found = inspectHoveredTooltips((UIPanelAPI) refitPanel, altDown, altChanged);
            }

            if (!found && lastHoveredTooltip != null) {
                lastHoveredTooltip = null;
                hoveredRowStates.clear();
            }

            inspectWeaponPicker(core, screenPanel, refitPanel, altDown, altChanged);
        } catch (Throwable t) {
            Global.getLogger(RefitUIInspector.class).error("[TrueWeaponStats] Error in update: " + t.getMessage(), t);
        }
    }

    private void refreshActiveShip(Object refitPanel) {
        try {
            Object shipDisplay = ReflectionHelper.invokeMethodNoCatch(refitPanel, "getShipDisplay");
            ShipAPI current = (ShipAPI) ReflectionHelper.invokeMethodNoCatch(shipDisplay, "getShip");
            if (current != activeShip) {
                activeShip = current;
                reset();
                if (current != null) {
                    Global.getLogger(RefitUIInspector.class).info(
                            String.format("[TrueWeaponStats] Active ship set to: %s (%s)",
                                    current.getName(), current.getHullSpec().getHullName()));
                }
            }
        } catch (Exception e) {
            activeShip = null;
        }
    }

    private boolean inspectHoveredTooltips(UIPanelAPI panelContainer, boolean altDown, boolean altChanged) {
        if (panelContainer == null) return false;
        List<?> children = (List<?>) ReflectionHelper.invokeMethod(panelContainer, "getChildrenNonCopy");
        if (children == null) {
            children = (List<?>) ReflectionHelper.invokeMethod(panelContainer, "getChildrenCopy");
        }
        boolean foundHovered = false;

        if (children != null) {
            for (Object child : children) {
                if (child == null) continue;

                boolean isCargoTooltip = (child.getClass().getEnclosingClass() == CargoTooltipFactory.class)
                        || child.getClass().getName().contains("CargoTooltipFactory")
                        || child instanceof StandardTooltipV2;

                if (!isCargoTooltip) continue;

                StandardTooltipV2 tooltip = null;
                if (child instanceof StandardTooltipV2) {
                    tooltip = (StandardTooltipV2) child;
                } else {
                    Object prev = ReflectionHelper.invokeMethod(child, "getPrev");
                    if (prev instanceof StandardTooltipV2) {
                        tooltip = (StandardTooltipV2) prev;
                    }
                }

                if (tooltip != null) {
                    foundHovered = true;
                    if (tooltip != lastHoveredTooltip) {
                        lastHoveredTooltip = tooltip;
                        hoveredRowStates.clear();

                        Runnable callback = tooltip.getBeforeShowing();
                        WeaponSlotAPI slot = extractSlotFromCallback(callback);
                        if (slot == null) {
                            Object widget = ReflectionHelper.invokeMethod(tooltip, "getWidgetAttachedTo");
                            if (widget != null) slot = extractSlotFromWidget(widget);
                        }

                        WeaponSpecAPI spec = extractWeaponSpec(tooltip, slot, activeShip);
                        if (spec == null && slot != null && activeShip != null && activeShip.getVariant() != null) {
                            String wid = activeShip.getVariant().getWeaponId(slot.getId());
                            if (wid != null) spec = Global.getSettings().getWeaponSpec(wid);
                        }

                        UIPanelAPI panel = null;
                        if (ReflectionHelper.hasMethod(tooltip, "getPanel")) {
                            Object p = ReflectionHelper.invokeMethod(tooltip, "getPanel");
                            if (p instanceof UIPanelAPI) panel = (UIPanelAPI) p;
                        }
                        if (panel == null && ReflectionHelper.hasMethod(tooltip, "getContents")) {
                            Object c = ReflectionHelper.invokeMethod(tooltip, "getContents");
                            if (c instanceof UIPanelAPI) panel = (UIPanelAPI) c;
                        }

                        if (panel != null && spec != null) {
                            List<?> panelItems = (List<?>) ReflectionHelper.invokeMethod(panel, "getChildrenNonCopy");
                            dispatchTooltipUpdate(panelItems, spec, slot, hoveredRowStates, altDown);
                        }
                    } else if (altChanged) {
                        for (GridRowState row : hoveredRowStates) {
                            row.apply(altDown);
                        }
                    }
                }
            }
        }

        return foundHovered;
    }

    private WeaponSlotAPI extractSlotFromWidget(Object widget) {
        if (widget == null) return null;
        Class<?> curr = widget.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                if (WeaponSlotAPI.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(widget);
                        if (val instanceof WeaponSlotAPI) return (WeaponSlotAPI) val;
                    } catch (Exception ignored) {}
                }
                try {
                    f.setAccessible(true);
                    Object val = f.get(widget);
                    if (val != null && val.getClass().getName().contains("returnsuper")) {
                        for (Field rf : val.getClass().getDeclaredFields()) {
                            if (WeaponSlotAPI.class.isAssignableFrom(rf.getType())) {
                                rf.setAccessible(true);
                                Object s = rf.get(val);
                                if (s instanceof WeaponSlotAPI) return (WeaponSlotAPI) s;
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
            curr = curr.getSuperclass();
        }
        return null;
    }

    private WeaponSlotAPI extractSlotFromCallback(Runnable callback) {
        if (callback == null) return null;

        Class<?> curr = callback.getClass();
        while (curr != null && curr != Object.class) {
            for (Field f : curr.getDeclaredFields()) {
                if (WeaponSlotAPI.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(callback);
                        if (val instanceof WeaponSlotAPI) return (WeaponSlotAPI) val;
                    } catch (Exception ignored) {}
                }
                if (WeaponAPI.class.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object val = f.get(callback);
                        if (val instanceof WeaponAPI) {
                            WeaponSlotAPI s = ((WeaponAPI) val).getSlot();
                            if (s != null) return s;
                        }
                    } catch (Exception ignored) {}
                }
            }
            curr = curr.getSuperclass();
        }

        Class<?> enclosing = callback.getClass().getEnclosingClass();
        if (enclosing != null) {
            for (Field f : callback.getClass().getDeclaredFields()) {
                if (enclosing.isAssignableFrom(f.getType())) {
                    try {
                        f.setAccessible(true);
                        Object refit = f.get(callback);
                        if (refit != null) {
                            Class<?> rCls = refit.getClass();
                            while (rCls != null && rCls != Object.class) {
                                for (Field rf : rCls.getDeclaredFields()) {
                                    if (WeaponSlotAPI.class.isAssignableFrom(rf.getType())) {
                                        rf.setAccessible(true);
                                        Object val = rf.get(refit);
                                        if (val instanceof WeaponSlotAPI) return (WeaponSlotAPI) val;
                                    }
                                    if (WeaponAPI.class.isAssignableFrom(rf.getType())) {
                                        rf.setAccessible(true);
                                        Object val = rf.get(refit);
                                        if (val instanceof WeaponAPI) {
                                            WeaponSlotAPI s = ((WeaponAPI) val).getSlot();
                                            if (s != null) return s;
                                        }
                                    }
                                }
                                rCls = rCls.getSuperclass();
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        return null;
    }

    private WeaponSpecAPI extractWeaponSpec(StandardTooltipV2 tooltip, WeaponSlotAPI slot, ShipAPI ship) {
        if (tooltip != null) {
            Class<?> curr = tooltip.getClass();
            while (curr != null && curr != Object.class) {
                for (Field field : curr.getDeclaredFields()) {
                    if (WeaponSpecAPI.class.isAssignableFrom(field.getType())) {
                        try {
                            field.setAccessible(true);
                            Object val = field.get(tooltip);
                            if (val instanceof WeaponSpecAPI) return (WeaponSpecAPI) val;
                        } catch (Exception ignored) {}
                    }
                }
                curr = curr.getSuperclass();
            }
        }
        if (slot != null && ship != null && ship.getVariant() != null) {
            String weaponId = ship.getVariant().getWeaponId(slot.getId());
            if (weaponId != null) {
                return Global.getSettings().getWeaponSpec(weaponId);
            }
        }
        return null;
    }

    private void inspectWeaponPicker(UIPanelAPI core, UIPanelAPI screenPanel, Object refitPanel, boolean altDown, boolean altChanged) {
        WeaponPickerDialog dialog = locatePickerInHierarchy(core);
        if (dialog == null) dialog = locatePickerInHierarchy(screenPanel);
        if (dialog == null && refitPanel instanceof UIPanelAPI) dialog = locatePickerInHierarchy((UIPanelAPI) refitPanel);

        if (dialog != activePicker) {
            activePicker = dialog;
            firstPickerButton = null;
            pickerButtonStates.clear();
            currRowStates.clear();
            activeRowStates.clear();
            lastCurrTooltip = null;
            lastActiveTooltip = null;
        }

        if (activePicker == null) return;

        WeaponSlotAPI slot = extractPickerSlot(activePicker);
        updateWeaponPickerRanges(slot, altDown, altChanged);

        // Current weapon comparison tooltip (holding CTRL in picker)
        StandardTooltipV2Expandable curr = (StandardTooltipV2Expandable) ReflectionHelper.getField(activePicker, "tooltipForCurr");
        if (curr != lastCurrTooltip) {
            lastCurrTooltip = curr;
            currRowStates.clear();
            if (curr != null) {
                WeaponSpecAPI spec = extractWeaponSpec(curr, slot, activeShip);
                dispatchTooltipUpdate(getStatsPanelChildren(curr), spec, slot, currRowStates, altDown);
            }
        } else if (altChanged) {
            for (GridRowState row : currRowStates) {
                row.apply(altDown);
            }
        }

        // Selected weapon preview tooltip
        @SuppressWarnings("unchecked")
        com.fs.starfarer.api.util.Pair<?, StandardTooltipV2Expandable> activePair =
                (com.fs.starfarer.api.util.Pair<?, StandardTooltipV2Expandable>) ReflectionHelper.invokeMethodExt(activePicker, "getActiveTooltip", true);
        StandardTooltipV2Expandable active = activePair != null ? activePair.two : null;
        if (active != lastActiveTooltip) {
            lastActiveTooltip = active;
            activeRowStates.clear();
            if (active != null) {
                WeaponSpecAPI spec = extractWeaponSpec(active, slot, activeShip);
                dispatchTooltipUpdate(getStatsPanelChildren(active), spec, slot, activeRowStates, altDown);
            }
        } else if (altChanged) {
            for (GridRowState row : activeRowStates) {
                row.apply(altDown);
            }
        }
    }

    private WeaponSlotAPI extractPickerSlot(WeaponPickerDialog dialog) {
        if (cachedSlotField == null) {
            for (Field field : dialog.getClass().getDeclaredFields()) {
                if (WeaponSlotAPI.class.isAssignableFrom(field.getType())) {
                    cachedSlotField = field.getName();
                    break;
                }
            }
        }
        return cachedSlotField != null ? (WeaponSlotAPI) ReflectionHelper.getField(dialog, cachedSlotField) : null;
    }

    private void updateWeaponPickerRanges(WeaponSlotAPI slot, boolean altDown, boolean altChanged) {
        if (activeShip == null || activePicker == null) return;

        Object innerPanel = ReflectionHelper.invokeMethod(activePicker, "getInnerPanel");
        if (innerPanel == null) return;
        List<?> wpdChildren = (List<?>) ReflectionHelper.invokeMethod(innerPanel, "getChildrenNonCopy");
        if (wpdChildren == null) return;

        ButtonAPI firstButton = null;
        List<ButtonAPI> buttons = new ArrayList<>();

        for (Object child : wpdChildren) {
            if (child instanceof ButtonAPI) {
                ButtonAPI btn = (ButtonAPI) child;
                if (firstButton == null) firstButton = btn;
                buttons.add(btn);
            } else if (child instanceof UIPanelAPI) {
                try {
                    List<?> inner = (List<?>) ReflectionHelper.invokeMethodNoCatch(child, "getItems");
                    if (inner != null) {
                        for (Object sub : inner) {
                            if (sub instanceof ButtonAPI) {
                                ButtonAPI btn = (ButtonAPI) sub;
                                if (firstButton == null) firstButton = btn;
                                buttons.add(btn);
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        }

        if (firstButton == null) return;

        if (firstButton != firstPickerButton) {
            firstPickerButton = firstButton;
            pickerButtonStates.clear();

            for (ButtonAPI button : buttons) {
                Object renderer = ReflectionHelper.invokeMethod(button, "getRenderer");
                if (renderer == null) continue;
                boolean isEnabled = button.isEnabled();

                if (cachedRendererPanelField == null) {
                    for (Field field : renderer.getClass().getDeclaredFields()) {
                        if (UIPanelAPI.class.isAssignableFrom(field.getType())) {
                            cachedRendererPanelField = field.getName();
                            break;
                        }
                    }
                }

                Object rendererPanel = cachedRendererPanelField != null ? ReflectionHelper.getField(renderer, cachedRendererPanelField) : null;
                if (rendererPanel == null) continue;

                Object info = ReflectionHelper.invokeMethod(rendererPanel, "getInfo");
                if (info == null) continue;
                WeaponSpecAPI spec = (WeaponSpecAPI) ReflectionHelper.invokeMethod(info, "getSpec");
                if (spec == null) continue;

                List<?> rendererChildren = (List<?>) ReflectionHelper.invokeMethod(rendererPanel, "getChildrenNonCopy");
                if (rendererChildren == null) continue;

                PickerButtonState state = new PickerButtonState();

                for (Object child : rendererChildren) {
                    if (child instanceof LabelAPI) {
                        LabelAPI label = (LabelAPI) child;
                        float textHeight = label.getPosition().getHeight();
                        String labelText = label.getText() != null ? label.getText().trim() : "";

                        // Identify OP label by matching spec cost or integer display bounds
                        int baseOP = Math.round(spec.getOrdnancePointCost(null));
                        boolean isOpLabel = labelText.equals(Integer.toString(baseOP)) || (textHeight > 18f && textHeight < 30f && labelText.matches("^\\d+$"));

                        if (isOpLabel && !LocaleInspector.get().containsPickerRange(labelText)) {
                            int newOP = Math.round(spec.getOrdnancePointCost(Global.getSector() != null ? Global.getSector().getPlayerStats() : null, activeShip.getMutableStats()));
                            state.opLabel = label;
                            state.vanillaOPText = Integer.toString(baseOP);
                            state.modifiedOPText = Integer.toString(newOP);
                            state.vanillaOPColor = isEnabled ? Misc.getButtonTextColor() : Misc.getGrayColor();
                            if (newOP > baseOP) {
                                state.modifiedOPHighlight = isEnabled ? getColorNerf() : getDisabledNerfColor();
                            } else if (newOP < baseOP) {
                                state.modifiedOPHighlight = isEnabled ? getColorBuff() : getDisabledBuffColor();
                            } else {
                                state.modifiedOPHighlight = null;
                            }
                        }

                        // Range label detection: checks localized range content
                        if (LocaleInspector.get().containsPickerRange(label.getText())) {
                            WeaponStatsEngine engine = new WeaponStatsEngine(activeShip, spec, null, slot);
                            float trueRange = engine.getEffectiveRange();
                            state.rangeLabel = label;
                            state.vanillaRangeText = label.getText();

                            if (Math.abs(trueRange - spec.getMaxRange()) > 0.01f) {
                                String formattedActual = TooltipPatcher.formatVal(trueRange, true);
                                String targetSubstr = LocaleInspector.get().extractPickerRangeSubstring(label.getText());
                                if (targetSubstr != null) {
                                    state.modifiedRangeText = label.getText().replace(targetSubstr, formattedActual);
                                } else {
                                    state.modifiedRangeText = label.getText();
                                }
                                state.modifiedRangeHighlight = formattedActual;
                                state.rangeHighlightColor = trueRange < spec.getMaxRange() ? (isEnabled ? getColorNerf() : getDisabledNerfColor()) : (isEnabled ? getColorBuff() : getDisabledBuffColor());
                            } else {
                                state.modifiedRangeText = state.vanillaRangeText;
                                state.modifiedRangeHighlight = null;
                            }
                        }
                    }
                }

                pickerButtonStates.put(button, state);
                state.apply(altDown);
            }
        } else if (altChanged) {
            for (PickerButtonState state : pickerButtonStates.values()) {
                state.apply(altDown);
            }
        }
    }

    private List<?> getStatsPanelChildren(StandardTooltipV2Expandable tooltip) {
        if (activePicker == null || tooltip == null) return Collections.emptyList();
        StandardTooltipV2Expandable statsBlock = (StandardTooltipV2Expandable)
                ReflectionHelper.invokeMethodExt(activePicker, "getStatsBlock", true, tooltip);
        if (statsBlock == null) return Collections.emptyList();
        UIPanelAPI panel = (UIPanelAPI) ReflectionHelper.invokeMethod(statsBlock, "getPanel");
        return panel == null ? Collections.emptyList() : (List<?>) ReflectionHelper.invokeMethod(panel, "getChildrenNonCopy");
    }

    private void dispatchTooltipUpdate(List<?> items, WeaponSpecAPI spec, WeaponSlotAPI slot, List<GridRowState> outStates, boolean altDown) {
        if (items == null || activeShip == null) return;

        if (spec == null && slot != null && activeShip.getVariant() != null) {
            String wid = activeShip.getVariant().getWeaponId(slot.getId());
            if (wid != null) spec = Global.getSettings().getWeaponSpec(wid);
        }
        if (spec == null) return;

        WeaponAPI weapon = WeaponStatsEngine.resolveWeaponInstance(activeShip, spec, slot);
        WeaponStatsEngine engine = new WeaponStatsEngine(activeShip, spec, weapon, slot);

        for (Object item : items) {
            findAndPatchGrids(item, engine, spec, outStates, altDown, 0);
        }
    }

    private void findAndPatchGrids(Object element, WeaponStatsEngine engine, WeaponSpecAPI spec, List<GridRowState> outStates, boolean altDown, int depth) {
        if (element == null || depth > 5) return;

        inspectAndPatchGrid(element, engine, spec, outStates, altDown);

        Object prev = ReflectionHelper.invokeMethod(element, "getPrev");
        if (prev != null && prev != element) {
            inspectAndPatchGrid(prev, engine, spec, outStates, altDown);
        }

        if (element instanceof UIPanelAPI || element.getClass().getSimpleName().contains("Panel") || element.getClass().getSimpleName().contains("Tooltip")) {
            List<?> children = (List<?>) ReflectionHelper.invokeMethod(element, "getChildrenNonCopy");
            if (children != null) {
                for (Object child : children) {
                    findAndPatchGrids(child, engine, spec, outStates, altDown, depth + 1);
                }
            }
        }
    }

    private void inspectAndPatchGrid(Object grid, WeaponStatsEngine engine, WeaponSpecAPI spec, List<GridRowState> outStates, boolean altDown) {
        if (grid == null) return;

        Map<?, ?> map = null;
        Class<?> gc = grid.getClass();
        while (gc != null && gc != Object.class) {
            for (Field f : gc.getDeclaredFields()) {
                if (Map.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try {
                        Object candidate = f.get(grid);
                        if (candidate instanceof Map && !((Map<?, ?>) candidate).isEmpty()) {
                            map = (Map<?, ?>) candidate;
                            break;
                        }
                    } catch (Throwable ignored) {}
                }
            }
            if (map != null) break;
            gc = gc.getSuperclass();
        }

        if (map == null) return;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object key = entry.getKey();
            Object cell = entry.getValue();
            if (cell == null || key == null) continue;

            LabelAPI nameLabel = (LabelAPI) ReflectionHelper.getField(cell, "nameLabel");
            if (nameLabel != null && nameLabel.getText() != null) {
                int row = 0;
                int col = 0;
                Object one = ReflectionHelper.getField(key, "one");
                Object two = ReflectionHelper.getField(key, "two");
                if (one instanceof Number) row = ((Number) one).intValue();
                if (two instanceof Number) col = ((Number) two).intValue();

                GridRowState rowState = TooltipPatcher.createRowState(grid, engine, spec, nameLabel.getText(), row, col);
                if (rowState != null) {
                    outStates.add(rowState);
                    rowState.apply(altDown);
                }
            }
        }
    }

    private void refreshShipSustainedFlux(UIPanelAPI core, Object refitPanel, boolean altDown, boolean altChanged) {
        if (activeShip == null || refitPanel == null) return;

        Object modDisplay = ReflectionHelper.invokeMethod(refitPanel, "getModDisplay");
        if (modDisplay == null) return;

        if (core != null) {
            List<?> coreChildren = (List<?>) ReflectionHelper.invokeMethod(core, "getChildrenNonCopy");
            if (coreChildren != null) {
                outer:
                for (Object c : coreChildren) {
                    if (c instanceof ModPickerDialogV3) {
                        ModPickerDialogV3 picker = (ModPickerDialogV3) c;
                        List<?> sub = (List<?>) ReflectionHelper.invokeMethod(picker, "getChildrenNonCopy");
                        if (sub != null) {
                            for (Object s : sub) {
                                if (s.getClass().equals(modDisplay.getClass())) {
                                    modDisplay = s;
                                    break outer;
                                }
                            }
                        }
                    }
                }
            }
        }

        if (cachedFluxField == null) {
            for (Field f : modDisplay.getClass().getDeclaredFields()) {
                try {
                    Object container = ReflectionHelper.getField(modDisplay, f.getName());
                    LabelAPI l = (LabelAPI) ReflectionHelper.getFieldNoCatch(container, "label");
                    if (l != null && LocaleInspector.get().isShipWeaponFluxLabel(l.getText())) {
                        cachedFluxField = f.getName();
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }

        if (cachedFluxField != null) {
            Object container = ReflectionHelper.getField(modDisplay, cachedFluxField);
            LabelAPI valLabel = (LabelAPI) ReflectionHelper.getField(container, "valueLabel");
            if (valLabel != null) {
                String currentText = valLabel.getText() != null ? valLabel.getText().trim() : "";
                boolean stateNeedsInit = (totalFluxState == null || totalFluxState.valueLabel != valLabel);
                boolean gameResetLabel = (totalFluxState != null && !currentText.equals(totalFluxState.modifiedText) && !currentText.equals(totalFluxState.vanillaText));

                if (stateNeedsInit || gameResetLabel) {
                    try {
                        float baseFlux = Float.parseFloat(currentText);
                        float totalSustained = 0f;
                        for (WeaponAPI w : activeShip.getAllWeapons()) {
                            if (w.isDecorative()) continue;
                            totalSustained += new WeaponStatsEngine(w).calculateFlux().sustainedFluxPerSec();
                        }

                        TotalFluxState state = new TotalFluxState();
                        state.valueLabel = valLabel;
                        state.vanillaText = currentText;
                        String formatted = TooltipPatcher.formatVal(totalSustained, true);
                        state.modifiedText = formatted;
                        float diff = totalSustained - baseFlux;
                        if (Math.abs(diff) > 0.5f) {
                            state.highlightColor = diff > 0f ? getColorNerf() : getColorBuff();
                        } else {
                            state.highlightColor = null;
                        }

                        totalFluxState = state;
                        state.apply(altDown);
                    } catch (Exception ignored) {}
                } else if (altChanged) {
                    totalFluxState.apply(altDown);
                }
            }
        }
    }

    private WeaponPickerDialog locatePickerInHierarchy(UIPanelAPI container) {
        if (container == null) return null;
        List<?> items = (List<?>) ReflectionHelper.invokeMethod(container, "getChildrenNonCopy");
        if (items == null) return null;
        for (Object it : items) {
            if (it instanceof WeaponPickerDialog) return (WeaponPickerDialog) it;
        }
        return null;
    }
}
