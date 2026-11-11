package trueweaponstats.ui;

import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import trueweaponstats.engine.WeaponStatsEngine;
import trueweaponstats.locale.LocaleInspector;
import trueweaponstats.util.ReflectionHelper;

import java.awt.Color;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calculates and formats tooltip metrics with buff/nerf highlighting.
 */
public class TooltipPatcher {

    private static final DecimalFormat INT_FMT = new DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat ONE_DEC = new DecimalFormat("#,##0.0", DecimalFormatSymbols.getInstance(Locale.US));
    private static final DecimalFormat TWO_DEC = new DecimalFormat("#,##0.00", DecimalFormatSymbols.getInstance(Locale.US));

    private static final Pattern WORD_NUMBER_PATTERN = Pattern.compile("\\b(\\d+)\\b");

    public static Color getColorBuff() {
        return RefitUIInspector.getColorBuff();
    }

    public static Color getColorNerf() {
        return RefitUIInspector.getColorNerf();
    }

    public static String formatVal(float value, boolean forceInteger) {
        if (Float.isNaN(value) || Float.isInfinite(value)) return "-";
        if (forceInteger || Math.abs(Math.round(value) - value) < 0.01f) {
            return INT_FMT.format(Math.round(value));
        }
        if (value < 10f) {
            return TWO_DEC.format(value);
        }
        return ONE_DEC.format(value);
    }

    public static RefitUIInspector.GridRowState createRowState(Object grid, WeaponStatsEngine engine,
                                                               WeaponSpecAPI spec, String labelKey,
                                                               int row, int col) {
        Object cell = ReflectionHelper.invokeMethod(grid, "getRow", row, col);
        if (cell == null) return null;

        LabelAPI valueLabel = (LabelAPI) ReflectionHelper.getField(cell, "valueLabel");
        if (valueLabel == null) return null;

        String vanillaText = valueLabel.getText();
        if (vanillaText == null) return null;

        LocaleInspector.MetricType metric = LocaleInspector.get().identifyMetric(labelKey, vanillaText, spec);
        if (metric == LocaleInspector.MetricType.UNKNOWN) {
            return null;
        }

        TextResult result = null;

        switch (metric) {
            case ORDNANCE_POINTS -> {
                int base = Math.round(spec.getOrdnancePointCost(null));
                int mod = engine.getEffectiveOrdnancePoints();
                result = buildSingleMetric(base, mod, true, true);
            }
            case RANGE -> {
                float base = spec.getMaxRange();
                float mod = engine.getEffectiveRange();
                result = buildSingleMetric(base, mod, false, false);
            }
            case DAMAGE -> {
                result = buildDamageMetric(vanillaText, engine);
            }
            case DPS_BURST -> {
                float base = spec.getDerivedStats() != null ? spec.getDerivedStats().getDps() : 0f;
                float mod = engine.calculateDps().burstDps();
                result = buildSingleMetric(base, mod, false, false);
            }
            case DPS_SUSTAINED -> {
                float baseBurst = spec.getDerivedStats() != null ? spec.getDerivedStats().getDps() : 0f;
                float baseSust = spec.getDerivedStats() != null ? spec.getDerivedStats().getSustainedDps() : 0f;
                var dps = engine.calculateDps();
                result = buildDualMetric(baseBurst, baseSust, dps.burstDps(), dps.sustainedDps(), false);
            }
            case FLUX_BURST -> {
                float base = spec.getDerivedStats() != null ? spec.getDerivedStats().getFluxPerSecond() : 0f;
                float mod = engine.calculateFlux().fluxPerSec();
                result = buildSingleMetric(base, mod, true, false);
            }
            case FLUX_SUSTAINED -> {
                float baseBurst = spec.getDerivedStats() != null ? spec.getDerivedStats().getFluxPerSecond() : 0f;
                float baseSust = spec.getDerivedStats() != null ? spec.getDerivedStats().getSustainedFluxPerSecond() : 0f;
                var flux = engine.calculateFlux();
                result = buildDualMetric(baseBurst, baseSust, flux.fluxPerSec(), flux.sustainedFluxPerSec(), true);
            }
            case FLUX_SHOT -> {
                float base = (spec instanceof ProjectileWeaponSpecAPI) ? ((ProjectileWeaponSpecAPI) spec).getEnergyPerShot() : 0f;
                float mod = engine.calculateFlux().fluxPerShot();
                result = buildSingleMetric(base, mod, true, false);
            }
            case FLUX_DAMAGE -> {
                float base = spec.getDerivedStats() != null ? spec.getDerivedStats().getFluxPerDam() : 0f;
                float mod = engine.calculateFlux().fluxPerDamage();
                result = buildSingleMetric(base, mod, true, false);
            }
            case HITPOINTS -> {
                result = buildMissileHpMetric(vanillaText, engine);
            }
            case AMMO_OR_CHARGES -> {
                result = buildAmmoMetric(vanillaText, spec, engine);
            }
            case RELOAD_TIME -> {
                float orig = spec.getAmmoPerSecond() <= 0f ? 0f : spec.getReloadSize() / spec.getAmmoPerSecond();
                float mod = engine.getReloadDuration();
                result = buildSingleMetric(orig, mod, true, false);
            }
            case REFIRE_DELAY -> {
                try {
                    float orig = Float.parseFloat(vanillaText.trim());
                    float mod = engine.getRefireCooldown(orig);
                    result = buildSingleMetric(orig, mod, true, false);
                } catch (Exception ignored) {}
            }
            case BURST_SIZE, UNKNOWN -> {}
        }

        if (result == null) return null;

        RefitUIInspector.GridRowState state = new RefitUIInspector.GridRowState();
        state.grid = grid;
        state.row = row;
        state.col = col;
        state.valueLabel = valueLabel;
        state.vanillaText = vanillaText;
        state.modifiedText = result.fullText;
        state.highlights = result.highlights;
        state.highlightColors = result.colors;
        return state;
    }

    public static class TextResult {
        public final String fullText;
        public final String[] highlights;
        public final Color[] colors;

        public TextResult(String fullText, String[] highlights, Color[] colors) {
            this.fullText = fullText;
            this.highlights = highlights;
            this.colors = colors;
        }
    }

    private static TextResult buildSingleMetric(float base, float actual, boolean lowerIsBetter, boolean integerOnly) {
        float diff = actual - base;
        boolean changed = Math.abs(diff) > 0.01f;
        String text = formatVal(actual, integerOnly);

        if (changed) {
            Color color = ((diff < 0f && !lowerIsBetter) || (diff > 0f && lowerIsBetter)) ? getColorNerf() : getColorBuff();
            return new TextResult(text, new String[] { text }, new Color[] { color });
        } else {
            return new TextResult(text, new String[0], new Color[0]);
        }
    }

    private static TextResult buildDualMetric(float baseBurst, float baseSust, float actualBurst, float actualSust, boolean lowerIsBetter) {
        String burstStr = formatVal(actualBurst, false);
        String sustStr = formatVal(actualSust, false);
        String full = burstStr + " (" + sustStr + ")";

        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();

        float diffBurst = actualBurst - baseBurst;
        if (Math.abs(diffBurst) > 0.01f) {
            highlights.add(burstStr);
            colors.add(((diffBurst < 0f && !lowerIsBetter) || (diffBurst > 0f && lowerIsBetter)) ? getColorNerf() : getColorBuff());
        }

        float diffSust = actualSust - baseSust;
        if (Math.abs(diffSust) > 0.01f) {
            highlights.add(sustStr);
            colors.add(((diffSust < 0f && !lowerIsBetter) || (diffSust > 0f && lowerIsBetter)) ? getColorNerf() : getColorBuff());
        }

        return new TextResult(full, highlights.toArray(new String[0]), colors.toArray(new Color[0]));
    }

    private static TextResult buildDamageMetric(String vanillaText, WeaponStatsEngine engine) {
        int xIdx = vanillaText.indexOf('x');
        float baseDmg;
        String suffix = null;

        try {
            if (xIdx != -1) {
                baseDmg = Float.parseFloat(vanillaText.substring(0, xIdx).trim());
                suffix = vanillaText.substring(xIdx).trim();
            } else {
                baseDmg = Float.parseFloat(vanillaText.trim());
            }
        } catch (Exception e) {
            return null;
        }

        engine.setCustomBaseDamage(baseDmg);
        float effDmg = engine.getEffectiveDamage();
        float diff = effDmg - baseDmg;

        if (Math.abs(diff) < 0.01f) {
            return new TextResult(vanillaText, new String[0], new Color[0]);
        }

        String effStr = formatVal(effDmg, false);
        String full = suffix != null ? (effStr + suffix) : effStr;
        Color color = diff > 0f ? getColorBuff() : getColorNerf();

        return new TextResult(full, new String[] { effStr }, new Color[] { color });
    }

    private static TextResult buildMissileHpMetric(String vanillaText, WeaponStatsEngine engine) {
        Matcher matcher = WORD_NUMBER_PATTERN.matcher(vanillaText);
        List<String> highlights = new ArrayList<>();
        List<Color> colors = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            int end = matcher.end();
            if (end < vanillaText.length() && vanillaText.charAt(end) == 'x') {
                matcher.appendReplacement(sb, matcher.group());
                continue;
            }

            int baseHp = Integer.parseInt(matcher.group());
            float effHp = engine.getMissileHealth(baseHp);
            if (Math.round(effHp) == baseHp) {
                matcher.appendReplacement(sb, String.valueOf(baseHp));
            } else {
                String effStr = formatVal(effHp, true);
                highlights.add(effStr);
                colors.add(effHp > baseHp ? getColorBuff() : getColorNerf());
                matcher.appendReplacement(sb, Matcher.quoteReplacement(effStr));
            }
        }
        matcher.appendTail(sb);

        return new TextResult(sb.toString(), highlights.toArray(new String[0]), colors.toArray(new Color[0]));
    }

    private static TextResult buildAmmoMetric(String vanillaText, WeaponSpecAPI spec, WeaponStatsEngine engine) {
        int baseAmmo = spec.getMaxAmmo();
        int effAmmo = engine.getEffectiveAmmo();

        if (baseAmmo == effAmmo) {
            return new TextResult(vanillaText, new String[0], new Color[0]);
        }

        String baseStr = Integer.toString(baseAmmo);
        String effStr = Integer.toString(effAmmo);
        Color color = effAmmo > baseAmmo ? getColorBuff() : getColorNerf();

        if (vanillaText.trim().equals(baseStr)) {
            return new TextResult(effStr, new String[] { effStr }, new Color[] { color });
        }

        String full = vanillaText.contains(baseStr) ? vanillaText.replace(baseStr, effStr) : vanillaText.replaceAll("\\d+", effStr);
        return new TextResult(full, new String[] { effStr }, new Color[] { color });
    }
}
