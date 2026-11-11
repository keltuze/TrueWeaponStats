package trueweaponstats.locale;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Universal language-agnostic detection engine for Starsector refit UI labels.
 * Dynamically extracts exact localized string constants directly from Starsector's compiled bytecode
 * at runtime, with multilingual keyword and mathematical value matching fallbacks.
 */
public class LocaleInspector {

    private static final LocaleInspector INSTANCE = new LocaleInspector();
    public static LocaleInspector get() {
        return INSTANCE;
    }

    // Extracted localized labels
    private String labelDamage = "Урон";
    private String labelDpsSustained = "Урон / сек (продолжительный)";
    private String labelDpsBurst = "Урон / секунду";
    private String labelOrdnancePoints = "Очки оснащения";
    private String labelRange = "Дальность";
    private String labelFluxSustained = "Нагрузка / сек (продолжительная)";
    private String labelFluxBurst = "Нагрузка / секунду";
    private String labelFluxShot = "Нагрузка / выстрел";
    private String labelFluxDamage = "Нагрузка / урон";
    private String labelFluxDamageNonEmp = "Нагрузка / урон (не ЭМИ)";
    private String labelHitpoints = "Прочность";
    private String labelRefireDelay = "Задержка между очередями";
    private String labelBurstSize = "Размер очереди";

    // ooOO and OoOo labels
    private String labelWeaponFluxTotal = "нагр. оружия/сек";
    private String labelPickerRange = ", дальности ";

    private boolean initialized = false;

    public synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            extractCargoTooltipStrings();
        } catch (Throwable t) {
            Global.getLogger(LocaleInspector.class).warn("[TrueWeaponStats] Bytecode string extraction for CargoTooltipFactory encountered notice: " + t.getMessage());
        }

        try {
            extractRefitDisplayStrings();
        } catch (Throwable t) {
            Global.getLogger(LocaleInspector.class).warn("[TrueWeaponStats] Bytecode string extraction for ooOO encountered notice: " + t.getMessage());
        }

        try {
            extractPickerStrings();
        } catch (Throwable t) {
            Global.getLogger(LocaleInspector.class).warn("[TrueWeaponStats] Bytecode string extraction for OoOo encountered notice: " + t.getMessage());
        }

        Global.getLogger(LocaleInspector.class).info(String.format(
                "[TrueWeaponStats] Localized UI labels loaded: Damage='%s', Range='%s', OP='%s', DPS='%s', Flux/sec='%s', TotalFlux='%s'",
                labelDamage, labelRange, labelOrdnancePoints, labelDpsBurst, labelFluxBurst, labelWeaponFluxTotal
        ));
    }

    public enum MetricType {
        ORDNANCE_POINTS,
        RANGE,
        DAMAGE,
        DPS_BURST,
        DPS_SUSTAINED,
        FLUX_BURST,
        FLUX_SUSTAINED,
        FLUX_SHOT,
        FLUX_DAMAGE,
        HITPOINTS,
        AMMO_OR_CHARGES,
        RELOAD_TIME,
        REFIRE_DELAY,
        BURST_SIZE,
        UNKNOWN
    }

    /**
     * Determines the metric type for a given grid row name and value text.
     */
    public MetricType identifyMetric(String rowName, String valueText, WeaponSpecAPI spec) {
        if (rowName == null) return MetricType.UNKNOWN;
        String cleanName = rowName.trim();
        String lower = cleanName.toLowerCase(Locale.ROOT);

        // 1. Exact match with detected runtime labels
        if (cleanName.equalsIgnoreCase(labelOrdnancePoints)) return MetricType.ORDNANCE_POINTS;
        if (cleanName.equalsIgnoreCase(labelRange)) return MetricType.RANGE;
        if (cleanName.equalsIgnoreCase(labelDamage)) return MetricType.DAMAGE;
        if (cleanName.equalsIgnoreCase(labelDpsBurst)) return MetricType.DPS_BURST;
        if (cleanName.equalsIgnoreCase(labelDpsSustained)) return MetricType.DPS_SUSTAINED;
        if (cleanName.equalsIgnoreCase(labelFluxBurst)) return MetricType.FLUX_BURST;
        if (cleanName.equalsIgnoreCase(labelFluxSustained)) return MetricType.FLUX_SUSTAINED;
        if (cleanName.equalsIgnoreCase(labelFluxShot)) return MetricType.FLUX_SHOT;
        if (cleanName.equalsIgnoreCase(labelFluxDamage) || cleanName.equalsIgnoreCase(labelFluxDamageNonEmp)) return MetricType.FLUX_DAMAGE;
        if (cleanName.equalsIgnoreCase(labelHitpoints)) return MetricType.HITPOINTS;
        if (cleanName.equalsIgnoreCase(labelRefireDelay)) return MetricType.REFIRE_DELAY;
        if (cleanName.equalsIgnoreCase(labelBurstSize)) return MetricType.BURST_SIZE;

        // 2. Multilingual dictionary match (English, Russian, Chinese, German, Spanish, French)
        if (matchesAny(lower, "ordnance points", "очки оснащения", "装配点", "ausstattungspunkte", "puntos de equipo", "points d'armement")) {
            return MetricType.ORDNANCE_POINTS;
        }
        if (matchesAny(lower, "range", "дальность", "射程", "reichweite", "alcance", "portée")) {
            return MetricType.RANGE;
        }
        if (matchesAny(lower, "damage", "урон", "伤害", "schaden", "daño", "dégâts") && !lower.contains("second") && !lower.contains("сек") && !lower.contains("秒")) {
            return MetricType.DAMAGE;
        }
        if (lower.contains("(sustained)") || lower.contains("(продолжительный)") || lower.contains("(прод)") || lower.contains("持续") || lower.contains("(dauerhaft)")) {
            if (lower.contains("damage") || lower.contains("урон") || lower.contains("dps") || lower.contains("伤害")) {
                return MetricType.DPS_SUSTAINED;
            }
            if (lower.contains("flux") || lower.contains("нагруз") || lower.contains("辐能")) {
                return MetricType.FLUX_SUSTAINED;
            }
        }
        if (matchesAny(lower, "damage / second", "damage/sec", "урон / секунду", "урон / сек", "урон/сек", "每秒伤害", "schaden / sekunde", "dps")) {
            return MetricType.DPS_BURST;
        }
        if (matchesAny(lower, "flux / second", "flux/sec", "нагрузка / секунду", "нагрузка / сек", "нагрузка/сек", "每秒辐能", "fluss / sekunde")) {
            return MetricType.FLUX_BURST;
        }
        if (matchesAny(lower, "flux / shot", "flux/shot", "нагрузка / выстрел", "单发辐能", "fluss / schuss")) {
            return MetricType.FLUX_SHOT;
        }
        if (lower.contains("flux /") || lower.contains("flux/") || lower.contains("нагрузка /") || lower.contains("нагрузка/") || lower.contains("辐能/")) {
            if (lower.contains("dam") || lower.contains("урон") || lower.contains("伤害")) {
                return MetricType.FLUX_DAMAGE;
            }
        }
        if (matchesAny(lower, "hitpoints", "hull", "прочность", "структура", "耐久", "trefferpunkte", "puntos de vida")) {
            return MetricType.HITPOINTS;
        }
        if (matchesAny(lower, "max charges", "max ammo", "charges", "ammo", "макс. боезапас", "заряды", "боеприпасы", "зарядов", "弹药", "充能", "munition")) {
            return MetricType.AMMO_OR_CHARGES;
        }
        if (lower.contains("recharge") || lower.contains("reload") || lower.contains("перезарядка") || lower.contains("восстановление") || lower.contains("装填") || lower.contains("nachladen")) {
            return MetricType.RELOAD_TIME;
        }
        if (lower.contains("refire") || lower.contains("задержка между очередями") || lower.contains("задержка выстрела") || lower.contains("连发间隔") || lower.contains("feuerpause")) {
            return MetricType.REFIRE_DELAY;
        }

        // 3. Mathematical value matching fallback (when spec is provided)
        if (spec != null && valueText != null) {
            try {
                String trimmedVal = valueText.trim();
                // Range
                if (isApprox(spec.getMaxRange(), trimmedVal)) {
                    return MetricType.RANGE;
                }
                // Burst DPS
                if (spec.getDerivedStats() != null && isApprox(spec.getDerivedStats().getDps(), trimmedVal)) {
                    return MetricType.DPS_BURST;
                }
                // Burst Flux
                if (spec.getDerivedStats() != null && isApprox(spec.getDerivedStats().getFluxPerSecond(), trimmedVal)) {
                    return MetricType.FLUX_BURST;
                }
                // Flux per shot
                if (spec instanceof ProjectileWeaponSpecAPI) {
                    ProjectileWeaponSpecAPI proj = (ProjectileWeaponSpecAPI) spec;
                    if (isApprox(proj.getEnergyPerShot(), trimmedVal)) {
                        return MetricType.FLUX_SHOT;
                    }
                }
                // Flux per damage
                if (spec.getDerivedStats() != null && isApprox(spec.getDerivedStats().getFluxPerDam(), trimmedVal)) {
                    return MetricType.FLUX_DAMAGE;
                }
            } catch (Exception ignored) {}
        }

        return MetricType.UNKNOWN;
    }

    public boolean isShipWeaponFluxLabel(String text) {
        if (text == null) return false;
        String t = text.trim();
        if (t.equalsIgnoreCase(labelWeaponFluxTotal)) return true;
        String lower = t.toLowerCase(Locale.ROOT);
        return lower.contains("weapon flux") || lower.contains("нагр. оруж") || lower.contains("нагр.оруж")
                || lower.contains("武器辐能") || (lower.contains("waffen") && lower.contains("fluss"));
    }

    public boolean isPickerRangeLabel(String text) {
        if (text == null) return false;
        if (text.contains(labelPickerRange)) return true;
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains("range ") || lower.contains("дальности ") || lower.contains("дальност")
                || lower.contains("射程") || lower.contains("reichweite");
    }

    public boolean containsPickerRange(String text) {
        return isPickerRangeLabel(text);
    }

    public String extractPickerRangeSubstring(String text) {
        if (text == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\b(\\d+)\\b").matcher(text);
        String lastNum = null;
        while (m.find()) {
            lastNum = m.group(1);
        }
        return lastNum;
    }

    public String getPickerRangePrefix() {
        return labelPickerRange;
    }

    private static boolean matchesAny(String input, String... candidates) {
        for (String c : candidates) {
            if (input.equals(c) || input.contains(c)) return true;
        }
        return false;
    }

    private static boolean isApprox(float expected, String actualText) {
        if (expected <= 0f) return false;
        try {
            float val = Float.parseFloat(actualText.replace(',', '.'));
            return Math.abs(val - expected) < 0.15f;
        } catch (Exception e) {
            return false;
        }
    }

    // --- Bytecode extraction helpers ---

    private void extractCargoTooltipStrings() {
        byte[] classBytes = readClassBytes("com/fs/starfarer/ui/impl/CargoTooltipFactory.class");
        if (classBytes == null) return;

        ClassConstantPool cp = new ClassConstantPool(classBytes);
        int methodCodeOffset = findMethodCodeOffset(classBytes, cp, "super", "BaseWeaponSpec");
        if (methodCodeOffset < 0) return;

        // Bytecode offsets in super(BaseWeaponSpec...):
        // 56 -> Damage
        // 2017 -> DPS sustained
        // 2075 -> DPS burst
        // 2689 -> OP cost
        // 2756 -> Range
        // 2951 -> Flux sustained
        // 3017 -> Flux burst
        // 3047 -> Flux/shot
        // 3089 -> Flux/damage
        // 3682 -> Hitpoints
        // 3841 -> Refire delay
        // 3873 -> Burst size
        String sDamage = cp.getStringAtBytecode(classBytes, methodCodeOffset + 56);
        if (sDamage != null && !sDamage.isEmpty()) labelDamage = sDamage;

        String sDpsSust = cp.getStringAtBytecode(classBytes, methodCodeOffset + 2017);
        if (sDpsSust != null && !sDpsSust.isEmpty()) labelDpsSustained = sDpsSust;

        String sDpsBurst = cp.getStringAtBytecode(classBytes, methodCodeOffset + 2075);
        if (sDpsBurst != null && !sDpsBurst.isEmpty()) labelDpsBurst = sDpsBurst;

        String sOp = cp.getStringAtBytecode(classBytes, methodCodeOffset + 2689);
        if (sOp != null && !sOp.isEmpty()) labelOrdnancePoints = sOp;

        String sRange = cp.getStringAtBytecode(classBytes, methodCodeOffset + 2756);
        if (sRange != null && !sRange.isEmpty()) labelRange = sRange;

        String sFluxSust = cp.getStringAtBytecode(classBytes, methodCodeOffset + 2951);
        if (sFluxSust != null && !sFluxSust.isEmpty()) labelFluxSustained = sFluxSust;

        String sFluxBurst = cp.getStringAtBytecode(classBytes, methodCodeOffset + 3017);
        if (sFluxBurst != null && !sFluxBurst.isEmpty()) labelFluxBurst = sFluxBurst;

        String sFluxShot = cp.getStringAtBytecode(classBytes, methodCodeOffset + 3047);
        if (sFluxShot != null && !sFluxShot.isEmpty()) labelFluxShot = sFluxShot;

        String sFluxDam = cp.getStringAtBytecode(classBytes, methodCodeOffset + 3089);
        if (sFluxDam != null && !sFluxDam.isEmpty()) labelFluxDamage = sFluxDam;

        String sHp = cp.getStringAtBytecode(classBytes, methodCodeOffset + 3682);
        if (sHp != null && !sHp.isEmpty()) labelHitpoints = sHp;

        String sRefire = cp.getStringAtBytecode(classBytes, methodCodeOffset + 3841);
        if (sRefire != null && !sRefire.isEmpty()) labelRefireDelay = sRefire;

        String sBurst = cp.getStringAtBytecode(classBytes, methodCodeOffset + 3873);
        if (sBurst != null && !sBurst.isEmpty()) labelBurstSize = sBurst;
    }

    private void extractRefitDisplayStrings() {
        byte[] classBytes = readClassBytes("com/fs/starfarer/coreui/refit/ooOO.class");
        if (classBytes == null) return;

        ClassConstantPool cp = new ClassConstantPool(classBytes);
        int methodCodeOffset = findMethodCodeOffset(classBytes, cp, "<init>", null);
        if (methodCodeOffset >= 0) {
            String s = cp.getStringAtBytecode(classBytes, methodCodeOffset + 245);
            if (s != null && !s.isEmpty()) {
                labelWeaponFluxTotal = s;
                return;
            }
        }

        // Fallback: search all constant pool strings in ooOO
        for (String s : cp.getAllStrings()) {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.contains("нагр. оруж") || lower.contains("weapon flux") || lower.contains("武器辐能")) {
                labelWeaponFluxTotal = s;
                break;
            }
        }
    }

    private void extractPickerStrings() {
        byte[] classBytes = readClassBytes("com/fs/starfarer/coreui/refit/OoOo.class");
        if (classBytes == null) return;

        ClassConstantPool cp = new ClassConstantPool(classBytes);
        for (String s : cp.getAllStrings()) {
            if (s.contains("дальност") || s.toLowerCase(Locale.ROOT).contains("range") || s.contains("射程")) {
                labelPickerRange = s;
                break;
            }
        }
    }

    private static byte[] readClassBytes(String internalPath) {
        try (InputStream is = LocaleInspector.class.getClassLoader().getResourceAsStream(internalPath)) {
            if (is == null) return null;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = is.read(buf)) != -1) {
                baos.write(buf, 0, r);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }

    private static int findMethodCodeOffset(byte[] data, ClassConstantPool cp, String methodName, String descKeyword) {
        try {
            int pos = cp.getFieldsEndOffset();
            int methodsCount = readU2(data, pos);
            pos += 2;
            for (int i = 0; i < methodsCount; i++) {
                int nameIdx = readU2(data, pos + 2);
                int descIdx = readU2(data, pos + 4);
                pos += 6;
                String mName = cp.getUtf8(nameIdx);
                String mDesc = cp.getUtf8(descIdx);
                int attrsCount = readU2(data, pos);
                pos += 2;
                int codeOffset = -1;
                for (int a = 0; a < attrsCount; a++) {
                    int aNameIdx = readU2(data, pos);
                    int aLen = readU4(data, pos + 2);
                    String aName = cp.getUtf8(aNameIdx);
                    if ("Code".equals(aName)) {
                        codeOffset = pos + 14;
                    }
                    pos += 6 + aLen;
                }
                if (methodName.equals(mName)) {
                    if (descKeyword == null || (mDesc != null && mDesc.contains(descKeyword))) {
                        return codeOffset;
                    }
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static int readU2(byte[] b, int pos) {
        return ((b[pos] & 0xFF) << 8) | (b[pos + 1] & 0xFF);
    }

    private static int readU4(byte[] b, int pos) {
        return ((b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16) | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
    }

    private static class ClassConstantPool {
        private final Map<Integer, String> utf8s = new HashMap<>();
        private final Map<Integer, Integer> stringRefs = new HashMap<>();
        private int fieldsEndOffset = 0;

        ClassConstantPool(byte[] data) {
            parse(data);
        }

        private void parse(byte[] data) {
            int pos = 8;
            int cpCount = readU2(data, pos);
            pos += 2;
            for (int i = 1; i < cpCount; i++) {
                int tag = data[pos] & 0xFF;
                pos++;
                switch (tag) {
                    case 1 -> { // Utf8
                        int len = readU2(data, pos);
                        pos += 2;
                        String val = new String(data, pos, len, StandardCharsets.UTF_8);
                        pos += len;
                        utf8s.put(i, val);
                    }
                    case 3, 4 -> pos += 4;
                    case 5, 6 -> { pos += 8; i++; }
                    case 7 -> pos += 2;
                    case 8 -> { // String
                        int uIdx = readU2(data, pos);
                        pos += 2;
                        stringRefs.put(i, uIdx);
                    }
                    case 9, 10, 11, 12 -> pos += 4;
                    case 15, 16, 18 -> pos += (tag == 15 ? 3 : 4);
                    case 19, 20 -> pos += 2;
                    default -> { return; }
                }
            }

            // Skip interfaces
            pos += 6;
            int interfacesCount = readU2(data, pos);
            pos += 2 + 2 * interfacesCount;

            // Skip fields
            int fieldsCount = readU2(data, pos);
            pos += 2;
            for (int f = 0; f < fieldsCount; f++) {
                pos += 6;
                int attrsCount = readU2(data, pos);
                pos += 2;
                for (int a = 0; a < attrsCount; a++) {
                    int aLen = readU4(data, pos + 2);
                    pos += 6 + aLen;
                }
            }
            fieldsEndOffset = pos;
        }

        String getUtf8(int idx) {
            return utf8s.get(idx);
        }

        String getString(int cpIdx) {
            Integer uIdx = stringRefs.get(cpIdx);
            return uIdx != null ? utf8s.get(uIdx) : null;
        }

        String getStringAtBytecode(byte[] code, int offset) {
            if (offset < 0 || offset >= code.length) return null;
            int op = code[offset] & 0xFF;
            if (op == 18) { // ldc
                int cpIdx = code[offset + 1] & 0xFF;
                return getString(cpIdx);
            } else if (op == 19) { // ldc_w
                int cpIdx = readU2(code, offset + 1);
                return getString(cpIdx);
            }
            return null;
        }

        Collection<String> getAllStrings() {
            List<String> list = new ArrayList<>();
            for (Integer uIdx : stringRefs.values()) {
                String s = utf8s.get(uIdx);
                if (s != null) list.add(s);
            }
            return list;
        }

        int getFieldsEndOffset() {
            return fieldsEndOffset;
        }
    }
}
