package trueweaponstats.engine;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.MutableStat;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.StatBonus;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.loading.ProjectileWeaponSpecAPI;
import com.fs.starfarer.api.loading.WeaponSlotAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import trueweaponstats.util.ReflectionHelper;

/**
 * High-fidelity calculation engine for weapon combat stats under ship modifications and officer skills.
 * Computes modified range, damage, burst/sustained DPS, flux costs, and ammo dynamics.
 */
public class WeaponStatsEngine {

    public static class TrueDpsStats {
        private final float burstDps;
        private final float sustainedDps;

        public TrueDpsStats(float burstDps, float sustainedDps) {
            this.burstDps = burstDps;
            this.sustainedDps = sustainedDps;
        }

        public float burstDps() { return burstDps; }
        public float sustainedDps() { return sustainedDps; }
    }

    public static class TrueFluxStats {
        private final float fluxPerSec;
        private final float sustainedFluxPerSec;
        private final float fluxPerShot;
        private final float fluxPerDamage;

        public TrueFluxStats(float fluxPerSec, float sustainedFluxPerSec, float fluxPerShot, float fluxPerDamage) {
            this.fluxPerSec = fluxPerSec;
            this.sustainedFluxPerSec = sustainedFluxPerSec;
            this.fluxPerShot = fluxPerShot;
            this.fluxPerDamage = fluxPerDamage;
        }

        public float fluxPerSec() { return fluxPerSec; }
        public float sustainedFluxPerSec() { return sustainedFluxPerSec; }
        public float fluxPerShot() { return fluxPerShot; }
        public float fluxPerDamage() { return fluxPerDamage; }
    }

    private final WeaponAPI weapon;
    private final WeaponSpecAPI spec;
    private final ShipAPI ship;
    private float baseDamageOverride;

    public WeaponStatsEngine(WeaponAPI weapon) {
        this(weapon != null ? weapon.getShip() : null,
             weapon != null ? weapon.getSpec() : null,
             weapon,
             weapon != null ? weapon.getSlot() : null);
    }

    public WeaponStatsEngine(ShipAPI ship, WeaponSpecAPI spec, WeaponAPI weapon, WeaponSlotAPI slot) {
        this.ship = ship != null ? ship : (weapon != null ? weapon.getShip() : null);
        this.spec = spec != null ? spec : (weapon != null ? weapon.getSpec() : null);
        this.weapon = weapon;

        if (this.weapon != null && this.weapon.getDamage() != null) {
            this.baseDamageOverride = this.weapon.getDamage().getBaseDamage();
        } else if (this.spec != null && this.spec.getDerivedStats() != null) {
            this.baseDamageOverride = this.spec.getDerivedStats().getDamagePerShot();
        } else {
            this.baseDamageOverride = 0f;
        }
    }

    public static WeaponAPI resolveWeaponInstance(ShipAPI ship, WeaponSpecAPI spec, WeaponSlotAPI slot) {
        if (ship != null) {
            if (slot != null && slot.getId() != null) {
                String slotId = slot.getId();
                for (WeaponAPI mounted : ship.getAllWeapons()) {
                    if (mounted.getSlot() != null && slotId.equals(mounted.getSlot().getId())) {
                        return mounted;
                    }
                }
            }
            if (spec != null) {
                for (WeaponAPI mounted : ship.getAllWeapons()) {
                    if (mounted.getSpec() != null && spec.getWeaponId().equals(mounted.getSpec().getWeaponId())) {
                        return mounted;
                    }
                }
            }
        }
        var ce = Global.getCombatEngine();
        if (ce != null && spec != null) {
            WeaponAPI fake = ce.createFakeWeapon(ship, spec.getWeaponId());
            if (fake != null && slot != null) {
                ReflectionHelper.setField(fake, "slot", slot);
            }
            return fake;
        }
        return null;
    }

    public void setCustomBaseDamage(float damage) {
        this.baseDamageOverride = damage;
    }

    public float getEffectiveRange() {
        if (weapon != null) {
            return weapon.getRange();
        }
        if (spec == null) return 0f;
        float baseRange = spec.getMaxRange();
        if (ship == null || ship.getMutableStats() == null) return baseRange;

        MutableShipStatsAPI stats = ship.getMutableStats();
        StatBonus bonus = null;
        if (spec.isBeam()) {
            bonus = stats.getBeamWeaponRangeBonus();
        } else {
            switch (spec.getType()) {
                case BALLISTIC -> bonus = stats.getBallisticWeaponRangeBonus();
                case ENERGY -> bonus = stats.getEnergyWeaponRangeBonus();
                case MISSILE -> bonus = stats.getMissileWeaponRangeBonus();
                default -> bonus = null;
            }
        }
        return bonus != null ? bonus.computeEffective(baseRange) : baseRange;
    }

    public float getEffectiveDamage() {
        if (ship == null || ship.getMutableStats() == null) {
            return baseDamageOverride;
        }

        MutableShipStatsAPI stats = ship.getMutableStats();
        float flatBonus = 0f;
        float percentBonus = 0f;
        float multBonus = 1f;

        boolean isBeam = weapon != null ? weapon.isBeam() : (spec != null && spec.isBeam());
        if (isBeam) {
            MutableStat beamStat = stats.getBeamWeaponDamageMult();
            flatBonus += beamStat.getFlatMod();
            percentBonus += beamStat.getPercentMod();
            multBonus *= beamStat.getMult();
        }

        WeaponAPI.WeaponType type = weapon != null ? weapon.getType() : (spec != null ? spec.getType() : null);
        if (type != null) {
            MutableStat typeStat = switch (type) {
                case BALLISTIC -> stats.getBallisticWeaponDamageMult();
                case ENERGY -> stats.getEnergyWeaponDamageMult();
                case MISSILE -> stats.getMissileWeaponDamageMult();
                default -> null;
            };

            if (typeStat != null) {
                flatBonus += typeStat.getFlatMod();
                percentBonus += typeStat.getPercentMod();
                multBonus *= typeStat.getMult();
            }
        }

        return (baseDamageOverride + (baseDamageOverride * percentBonus / 100f) + flatBonus) * multBonus;
    }

    public TrueDpsStats calculateDps() {
        if (spec == null || spec.getDerivedStats() == null) {
            return new TrueDpsStats(0f, 0f);
        }

        float baseDps = spec.getDerivedStats().getDps();
        float baseSustained = spec.getDerivedStats().getSustainedDps();
        float damageRatio = baseDamageOverride > 0f ? getEffectiveDamage() / baseDamageOverride : 1f;

        float rof = getRateOfFireMultiplier();
        if (weapon != null && weapon.isBurstBeam()) {
            float chargeup = getBeamChargeupTime();
            float originalCycle = spec.getBurstDuration() + weapon.getCooldown() + chargeup;
            float modifiedCycle = spec.getBurstDuration() + (weapon.getCooldown() / (rof > 0f ? rof : 1f)) + chargeup;
            rof = modifiedCycle > 0f ? originalCycle / modifiedCycle : 1f;
        }

        float burstDps = baseDps * damageRatio * rof;
        float sustainedDps = baseSustained * damageRatio * getAmmoRegenMultiplier();

        if (weapon != null && (!weapon.usesAmmo() || weapon.getAmmoPerSecond() <= 0f)) {
            sustainedDps = burstDps;
        }

        return new TrueDpsStats(burstDps, sustainedDps);
    }

    public TrueFluxStats calculateFlux() {
        if (spec == null || spec.getDerivedStats() == null) {
            return new TrueFluxStats(0f, 0f, 0f, 0f);
        }

        float baseFps = spec.getDerivedStats().getFluxPerSecond();
        float baseSustainedFps = spec.getDerivedStats().getSustainedFluxPerSecond();
        float baseEnergyPerShot = (spec instanceof ProjectileWeaponSpecAPI) ?
                ((ProjectileWeaponSpecAPI) spec).getEnergyPerShot() : 0f;
        float baseFpd = spec.getDerivedStats().getFluxPerDam();

        float rof = getRateOfFireMultiplier();
        float regen = getAmmoRegenMultiplier();
        float fluxRatio = 1f;

        if (spec instanceof ProjectileWeaponSpecAPI) {
            ProjectileWeaponSpecAPI proj = (ProjectileWeaponSpecAPI) spec;
            int burstCount = spec.isInterruptibleBurst() ? 1 : Math.max(1, spec.getBurstSize());
            float energy = proj.getEnergyPerShot();
            fluxRatio = (energy > 0f && weapon != null) ? (weapon.getFluxCostToFire() / burstCount / energy) : 1f;
        } else if (weapon != null && weapon.isBurstBeam()) {
            float totalCycle = spec.getBurstDuration() + weapon.getCooldown() + getBeamChargeupTime();
            fluxRatio = (baseFps > 0f && totalCycle > 0f) ? (weapon.getFluxCostToFire() / totalCycle / baseFps) : 1f;
            float modifiedCycle = totalCycle - weapon.getCooldown() + (weapon.getCooldown() / (rof > 0f ? rof : 1f));
            rof = modifiedCycle > 0f ? totalCycle / modifiedCycle : 1f;
        } else if (baseFps > 0f && weapon != null) {
            fluxRatio = weapon.getFluxCostToFire() / baseFps;
        }

        float effectiveFps = Math.max(0f, baseFps * fluxRatio);
        float effectiveSustainedFps = Math.max(0f, baseSustainedFps * fluxRatio);

        if (weapon == null || !weapon.isBeam() || weapon.isBurstBeam()) {
            effectiveFps *= rof;
            effectiveSustainedFps *= regen;
        }

        if (weapon != null && (!weapon.usesAmmo() || weapon.getAmmoPerSecond() <= 0f)) {
            effectiveSustainedFps = effectiveFps;
        }

        float effectiveFluxShot = Math.max(0f, baseEnergyPerShot * fluxRatio);
        float damageRatio = baseDamageOverride > 0f ? getEffectiveDamage() / baseDamageOverride : 1f;
        float effectiveFpd = (damageRatio > 0f && baseFpd > 0f) ? (baseFpd * fluxRatio / damageRatio) : baseFpd;

        return new TrueFluxStats(effectiveFps, effectiveSustainedFps, effectiveFluxShot, effectiveFpd);
    }

    public int getEffectiveAmmo() {
        if (spec == null) return 0;
        if (ship == null || ship.getMutableStats() == null) return spec.getMaxAmmo();
        MutableShipStatsAPI stats = ship.getMutableStats();
        int base = spec.getMaxAmmo();
        WeaponAPI.WeaponType type = weapon != null ? weapon.getType() : spec.getType();
        float effective = switch (type) {
            case BALLISTIC -> stats.getBallisticAmmoBonus().computeEffective(base);
            case ENERGY -> stats.getEnergyAmmoBonus().computeEffective(base);
            case MISSILE -> stats.getMissileAmmoBonus().computeEffective(base);
            default -> base;
        };
        return Math.round(effective);
    }

    public float getReloadDuration() {
        if (spec == null) return 0f;
        float regenRate = getAmmoRegenMultiplier();
        if (spec.getAmmoPerSecond() <= 0f || regenRate <= 0f) return 0f;
        return spec.getReloadSize() / (spec.getAmmoPerSecond() * regenRate);
    }

    public float getRefireCooldown(float baseDelay) {
        float rof = getRateOfFireMultiplier();
        if (rof <= 0f) return baseDelay;
        float reduction = 1f - (1f / rof);
        if (weapon != null && weapon.isBurstBeam()) {
            return baseDelay - (weapon.getCooldown() * reduction);
        }
        return baseDelay - (baseDelay * reduction);
    }

    public int getEffectiveOrdnancePoints() {
        if (spec == null) return 0;
        if (ship == null) return Math.round(spec.getOrdnancePointCost(null));
        var sector = Global.getSector();
        return Math.round(spec.getOrdnancePointCost(sector != null ? sector.getPlayerStats() : null, ship.getMutableStats()));
    }

    public float getMissileHealth(float baseHp) {
        if (ship == null || ship.getMutableStats() == null) return baseHp;
        return ship.getMutableStats().getMissileHealthBonus().computeEffective(baseHp);
    }

    private float getRateOfFireMultiplier() {
        if (ship == null || ship.getMutableStats() == null) return 1f;
        MutableShipStatsAPI stats = ship.getMutableStats();
        WeaponAPI.WeaponType type = weapon != null ? weapon.getType() : (spec != null ? spec.getType() : null);
        if (type == null) return 1f;
        return switch (type) {
            case BALLISTIC -> stats.getBallisticRoFMult().getModifiedValue();
            case ENERGY -> stats.getEnergyRoFMult().getModifiedValue();
            case MISSILE -> stats.getMissileRoFMult().getModifiedValue();
            default -> 1f;
        };
    }

    private float getAmmoRegenMultiplier() {
        if (ship == null || ship.getMutableStats() == null) return 1f;
        MutableShipStatsAPI stats = ship.getMutableStats();
        WeaponAPI.WeaponType type = weapon != null ? weapon.getType() : (spec != null ? spec.getType() : null);
        if (type == null) return 1f;
        return switch (type) {
            case BALLISTIC -> stats.getBallisticAmmoRegenMult().getModifiedValue();
            case ENERGY -> stats.getEnergyAmmoRegenMult().getModifiedValue();
            case MISSILE -> stats.getMissileAmmoRegenMult().getModifiedValue();
            default -> 1f;
        };
    }

    private float getBeamChargeupTime() {
        if (spec == null) return 0f;
        Object val = ReflectionHelper.invokeMethod(spec, "getChargeupTime");
        return (val instanceof Number) ? ((Number) val).floatValue() : 0f;
    }
}
