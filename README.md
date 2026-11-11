# 🎯 True Weapon Stats for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-17-green.svg)]()
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
[![Latest Release](https://img.shields.io/badge/Release-v1.0.2-brightgreen.svg)](https://github.com/keltuze/TrueWeaponStats/releases/tag/v1.0.2)

> **Real-time effective combat metrics inside the Refit screen!**  
> In vanilla Starsector, weapon tooltips always display default "factory" stats, ignoring active officer skills, rangefinder hullmods, and ship systems. **True Weapon Stats** dynamically calculates and displays actual combat performance (effective range, damage, burst & sustained DPS, flux/sec, and ammo recharge) directly in the UI.  
> 
> *An enhanced standalone revival and modernization of the concept originally created by **qcwxezda** (*Unthemed Weapons Utilities*), fully re-architected for Starsector 0.98a under **CC BY-NC-SA 4.0**.*

> 💡 **Community Audit & Patch Notes (v1.0.2):**  
> Sincere gratitude to the technical code audit and reverse-engineering breakdown provided by the Starsector Discord community! In this release, the following specific edge-cases and discrepancies were addressed and resolved:
> 1. **Candidate Weapon Lookup in Picker:** Hovering candidate weapons in the refit picker now calculates stats strictly for the hovered weapon rather than inheriting values from the weapon already installed in that slot.
> 2. **Obfuscated Weapon Slot Resolution:** Replaced static field name lookup with dynamic type scanning (`WeaponSlotAPI.class`), ensuring synthesized preview weapons acquire their slot and properly trigger listener-based range buffs (*Ballistic Rangefinder*).
> 3. **English & Multi-Language Dictionary Engine:** Replaced fragile byte-offset scanning with a priority dictionary (English, Russian, Chinese, German, Spanish, French). Fixed inverted coloring on "Flux / damage" (lower is better = green/buff), and separated "Reload size" from "Charges gained".
> 4. **ClassLoader Localization Isolation:** Added lazy auto-initialization for `LocaleInspector` to guarantee that runtime label discovery runs within whichever ClassLoader executes the UI hooks.
> 5. **Logging Cleanup & Performance:** Eliminated background per-second heartbeat and diagnostic logging during campaign gameplay.
> 6. **Combat & Simulation Escalation:** Added automatic privileged loader escalation to `EveryFrameCombatPlugin.init()` for full support during title-screen missions and simulations.

---

## 📦 Downloads (v1.0.2 Release)

Direct download links for the latest stable release:

| Package | Compatibility | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **True Weapon Stats v1.0.2** | Starsector 0.98a-RC8 | Community feedback patch: Candidate weapon resolution, dynamic slot injection for fake weapons, clean English/multilingual dictionary, zero background logs | [**`TrueWeaponStats_v1.0.2.zip`**](https://github.com/keltuze/TrueWeaponStats/releases/download/v1.0.2/TrueWeaponStats_v1.0.2.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/TrueWeaponStats/releases).

---

## 🚀 Key Features & Architectural Improvements

### 🔬 1. Direct `WeaponAPI` Resolution (Built-in Weapons & Unique Hulls)
* **Live Object Introspection:** Unlike older implementations that synthesized isolated mock weapons (`createFakeWeapon`), True Weapon Stats queries the live, mounted `WeaponAPI` instance directly from the ship's slot hierarchy.
* **100% Accuracy on Superweapons:** Accurately accounts for built-in superweapons, unique hull controllers (e.g. *Ziggurat*, boss ships, modded factions), and slot-conditional modifiers (*Ballistic Rangefinder*).

### ⚙️ 2. Native Starsector 0.98a UI Integration
* **Campaign State Hierarchy:** Discovers `CampaignState` dynamically through `AppDriver` and sector listeners, overcoming 0.98a's campaign UI overhaul.
* **Universal Refit Hooks:** Seamlessly hooks the Refit screen across all entry points: the Fleet screen (`[F]` modal dialogs), docked orbital stations, and planetary interaction menus.

### ⌨️ 3. Interactive `[ALT]` Key Toggle & Clean Visuals
* **Pristine Interface:** Color-coded green/red highlights indicate buffs and nerfs without cluttering tooltips with permanent brackets.
* **Instant Baseline Inspection:** **Hold `[ALT]`** at any time to instantly revert the tooltip back to its factory vanilla state. Release `[ALT]` to return to modified combat stats.

### 🌐 4. Dynamic Bytecode Multilingual Engine
* **Zero-Config Localization:** Eliminates hardcoded English string lookups (`"Damage"`, `"Range"`, `"weapon flux/sec"`).
* **Runtime Constant Pool Parsing:** Directly inspects compiled game class files (`CargoTooltipFactory.class`, `ooOO.class`, `OoOo.class`) to extract active translated UI tokens. Operates out-of-the-box across all languages (English, Russian, Chinese, German, etc.) without requiring external translation configs.

### 🧮 5. Corrected Combat Mathematics & Safety
* **Dynamic Flux Efficiency (Flux / Damage):** Accurately scales flux-per-damage against outgoing damage buffs (`baseFpd * fluxRatio / damageRatio`).
* **Multiplicative Damage Stacking:** Evaluates separate `MutableStat` modifiers (such as Beam and Energy damage) multiplicatively according to Starsector's native combat rules.
* **Arithmetic Safety:** Zero-division guards (`NaN`/`Infinity`) for exotic zero-energy weapons and null-safe `PlayerStats` handling in combat simulations.

### 🛡️ 6. Isolated Java 17 ClassLoader Bridge
* Custom `TWSPrivilegedClassLoader` routes reflection safely through the system loader without SecurityExceptions.
* Strictly excludes the root plugin from whitelist delegation to prevent class duplication and infinite self-upgrade loops.

---

## 📥 Installation

1. Download **`TrueWeaponStats_v1.0.0.zip`** from the [**Releases**](https://github.com/keltuze/TrueWeaponStats/releases) section.
2. Extract the archive into your `Starsector/mods/` folder so that you have:
   ```
   Starsector/mods/TrueWeaponStats/
   ├── data/
   ├── jars/
   │   └── TrueWeaponStats.jar
   ├── mod_info.json
   └── trueweaponstats.version
   ```
3. Enable **True Weapon Stats** in the Starsector launcher and launch the game.

---

## 📜 Credits & License

* **qcwxezda** — Original concept, foundational reflection discovery, and calculation logic in *Unthemed Weapons Utilities*.
* **keltuze** — Architecture overhaul for Starsector 0.98a, reactive `[ALT]` state machine, bytecode localization engine, live weapon resolution, and math corrections.
* Licensed under the **Creative Commons Attribution-NonCommercial-ShareAlike 4.0 International License (CC BY-NC-SA 4.0)**.
