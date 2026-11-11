# 🎯 True Weapon Stats for Starsector

[![Game Version](https://img.shields.io/badge/Starsector-0.98a--RC8-blue.svg)](https://fractalsoftworks.com/)
[![Java Compatibility](https://img.shields.io/badge/Java-17-green.svg)]()
[![License](https://img.shields.io/badge/License-CC%20BY--NC--SA%204.0-lightgrey.svg)](https://creativecommons.org/licenses/by-nc-sa/4.0/)
[![Latest Release](https://img.shields.io/badge/Release-v1.0.1-brightgreen.svg)](https://github.com/keltuze/TrueWeaponStats/releases/tag/v1.0.1)

> **Real-time effective combat metrics inside the Refit screen!**  
> In vanilla Starsector, weapon tooltips always display default "factory" stats, ignoring active officer skills, rangefinder hullmods, and ship systems. **True Weapon Stats** dynamically calculates and displays actual combat performance (effective range, damage, burst & sustained DPS, flux/sec, and ammo recharge) directly in the UI.  
> 
> *An enhanced standalone revival and modernization of the concept originally created by **qcwxezda** (*Unthemed Weapons Utilities*), fully re-architected for Starsector 0.98a under **CC BY-NC-SA 4.0**.*

---

## 📦 Downloads (v1.0.1 Release)

Direct download links for the latest stable release:

| Package | Compatibility | Highlights & Features | Direct Download |
| :--- | :---: | :--- | :--- |
| **True Weapon Stats v1.0.1** | Starsector 0.98a-RC8 | Bugfix patch: Fixed candidate weapon resolution in picker, dynamic slot injection for fake weapons, clean English/multilingual dictionary, eliminated heartbeat logs | [**`TrueWeaponStats_v1.0.1.zip`**](https://github.com/keltuze/TrueWeaponStats/releases/download/v1.0.1/TrueWeaponStats_v1.0.1.zip) |

> 🔗 All release assets and past versions are available on the [**GitHub Releases Page**](https://github.com/keltuze/TrueWeaponStats/releases).

---

## 📝 Changelog v1.0.1:
* **Fixed Weapon Picker Candidate Resolution:** When browsing candidate weapons to install in an occupied slot, tooltips now correctly calculate stats for the hovered candidate rather than inheriting numbers from the currently mounted weapon.
* **Dynamic Slot Assignment for Synthesized Weapons:** Implemented runtime type-based reflection to assign `WeaponSlotAPI` to synthesized weapons, enabling vanilla range listener modifiers (such as *Ballistic Rangefinder*) to correctly calculate ranges.
* **Multi-Language Detection Overhaul:** Replaced fragile byte-offset scanning with a comprehensive, order-prioritized dictionary (English, Russian, Chinese, German, Spanish, French). Correctly distinguishes "Flux / damage" (where lower is green/buff), "Reload size", and "Charges gained".
* **Lazy ClassLoader Locale Initialization:** Automatically initializes localized metric tables inside whichever ClassLoader executes the UI hooks.
* **Eliminated Background Logging Spam:** Completely removed per-second heartbeat and diagnostic dump logs on the campaign map.
* **Combat / Simulation ClassLoader Escalation:** Added automatic privileged loader escalation to title-screen missions and combat simulations.

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
