# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

```bash
# Slimefun4 is a provided dependency from JitPack (com.github.slimefun:Slimefun:DEV-SNAPSHOT)
mvn clean package -DskipTests

# Output: target/ExoticGarden vUNOFFICIAL.jar
```

Java 21 target. No formatter configured. bStats shaded and relocated to `io.github.thebusybiscuit.exoticgarden.bstats`.

## Architecture

ExoticGardenComplex is a Slimefun addon for exotic plants, fruit trees, foods, wines, and a cooking system. It has two coexisting package roots:

### `io.github.thebusybiscuit.exoticgarden` (original EG code)
- `ExoticGarden.java` — main plugin class, registers everything.
- `ExoticItems.java` — all `SlimefunItemStack` constants.
- `EGPlant` / `Tree` / `Berry` — core item types for plants and fruit-bearing trees.
- `PlantType` — enum of all plant varieties.
- `YeastCulturer`, `SeedAnalyzer`, `ElectricityBrewing` — electric machines.
- `FoodListener`, `PlayerListener` — Bukkit event handlers.
- `PlayerAlcohol` — tracks alcohol level per player (from `CustomWine` consumption).
- `schematics/` — JNBT-based schematic loading for tree structures.

### `com.be` (extended content)
- `BETree.java` — extended tree type adding new species.
- `registry/` — `BEPlants`, `BETrees`, `BEFoodRegistry`, `BEItemGroups`, `BECommands` register additional content.
- `utils/BEListener.java`, `RegistryHandler.java` — event handling and registration wiring.

### Cooking System (`cooking/` subpackage)
Self-contained cooking module with its own state machine:

| Subpackage | Purpose |
|---|---|
| `block/` | `StoveBlock`, `CuttingBoardBlock` — physical block implementations |
| `state/` | `StoveState`, `FoodState`, `FuelEntry`, `IngredientSlot`, `SeasoningEntry`, `CharLevel` — immutable state model |
| `interaction/` | One handler class per interaction type (bowl, fuel, ingredient, seasoning, spatula) |
| `calculator/` | `DonenessCalculator` / `StandardDonenessCalculator` compute cook level from time + heat |
| `config/` | YAML loaders for fuel, ingredient, and seasoning configs |
| `task/StoveTickTask` | BukkitRunnable that ticks all active stoves |
| `hologram/` | Hologram display above stoves |
| `ai/DishGenerator` | Generates dish output from ingredients |

### Item Registration Pattern
Same Slimefun addon pattern: define `SlimefunItemStack` constants → instantiate item classes → call `item.register(addon)` → link to `Research`.

### Soft Dependencies
- **FluffyMachines** (`com.github.NCBPFluffyBear:FluffyMachines`) — provided scope, check presence before calling its API.
- **JSR-305** annotations (`@Nonnull`, `@Nullable`) used throughout.
