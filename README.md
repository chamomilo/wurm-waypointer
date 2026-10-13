# Wurm Waypointer

Waypoints, maps, navigation, nearby creature and object tracking, and configurable highlights for the **Wurm Unlimited client**.

**Current release: 1.10.4** · [Download ZIP](https://github.com/chamomilo/wurm-waypointer/releases/latest) · [Report a problem](https://github.com/chamomilo/wurm-waypointer/issues)

## Install or update

A working Wurm Unlimited client modloader is required. Install manually with the game closed.

1. Download `wurm-waypointer-1.10.4.zip` from Releases.
2. Back up `mods/wurm-waypointer.config` and `wurm-waypointer-data` if upgrading.
3. Extract the ZIP into `WurmLauncher`, merging its `mods` folder. Preserve your existing `.config` when upgrading; the included configuration supplies defaults for a new installation. New options are added by the mod when needed.
4. Keep only `wurm-waypointer-1.10.4.jar` in `mods/wurm-waypointer`. Use the new `mods/wurm-waypointer.properties`, which points to that JAR.
5. Start the game and open Waypointer using its compass control.

The ZIP contains exactly four files:

```text
mods/wurm-waypointer.properties
mods/wurm-waypointer.config
mods/wurm-waypointer/README.txt
mods/wurm-waypointer/wurm-waypointer-1.10.4.jar
```

Artwork, the shared updater, the interface SDK and license notices are inside the JAR. Saved waypoints and map data live separately in `wurm-waypointer-data`; preserve that folder during upgrades.

## Waypoints and navigation

Use **Waypoints** to add, edit, filter, share, import and export targets. Enable a target with **ON** and start navigation with **NAV**. Mark a location by left-clicking the full map; right-click for a custom mark. `/wp nav off` stops navigation.

![Waypoints, filters and navigation actions](docs/images/waypoints.png)

**Mobs**, **Containers** and **Objects / items** show received nearby entities with filters and marking actions. **My animals** and **Vehicles** use native catalogues. Refresh the list, then Track or Untrack a permitted target. Animal tracking can request native direction searches outside view; it requires the game's Manage/Find permissions. Targets leaving view retain their last known position.

Text filters accept comma-separated fragments, ignoring case. The `+` filter includes matches and the `-` filter excludes them; exclusions take precedence. For example, `horse, wolf` matches either word.

## Creatures Highlight

Toggle coloured outlines independently for players, non-aggressive farm animals, aggressive wild animals, monsters, Unique creatures and modifiers. Names show the number currently detected in the loaded area. **ON** is green; **ALL ON** and **ALL OFF** affect outline switches.

![Creature rules, live counts, colours and auto-mark controls](docs/images/creatures-highlight.png)

Traits occupy the left column; creature categories and Small/Mini/Tiny occupy the right. Modifier colours take priority over Unique, then category colours. The initial palette follows familiar ESP colours; Scared uses warm yellow to distinguish it from blue tower guards.

Press **Color** to open the colour picker. Apply saves the selected colour; Cancel or closing the popup keeps the previous colour. **Auto-mark** is available for Unique, Champion and Diseased. It creates one tracked waypoint per detected creature, independently of whether its outline is ON. It does not start navigation automatically.

## Tiles Highlight

Terrain rules work together. A tile matching several enabled rules receives separate coloured borders, raised slightly above the ground so they remain visible through grass.

![Tile rules and landed meteorite options](docs/images/tiles-highlight.png)

| Rule | What it highlights |
| --- | --- |
| Non-rideable tiles | Steep terrain, deep water, lava and cave openings; red initially. |
| Flower tiles | Flower-bearing terrain in the native flower colour. |
| Harvestable areas | Ripe field crops and mature trees or bushes with received harvestable fruit. |
| Overaged trees | Trees aged Overaged or Shrivelled; independent of harvestability. |
| Meteors | Landed meteorites in the loaded area, with optional Auto-mark and Chime. |

**The four terrain switches start OFF after every client launch.** Activate them manually when needed. Selected colours and radius are retained. Meteor outlines, Auto-mark and Chime default to ON for a new installation; explicit saved meteor preferences are respected. Each meteorite is marked and sounded once per detection session, without automatically starting navigation.

The default terrain area extends **7 tiles along each axis** around the player, including diagonal corners: a **15 × 15 square**. Change **Tile highlight radius** in the updater Settings, from **1 to 16**. Radius changes apply immediately and do not limit meteorite object detection.

Hover help explains the thresholds. Non-rideable means a corner height difference greater than 40 dirt (4 m), water deeper than 0.7 m above the lowest corner, a cave opening or lava. This is a terrain indication; fences, buildings and permissions also affect actual riding. Harvestable crops are native ripe stages 5 and 6; trees and bushes need a received fruit flag and a Mature through Very old age. Unknown terrain is not highlighted.

The interface images above illustrate enabled controls with sample names and counts.

## Maps and searches

Press **M** for the full map. Pan and zoom, use **CENTER** to return to your character, and switch deeds, roads, marks and navigation lines independently. On recognized Sklotopolis servers, **ALL MAPS ON SERVER** opens Liberty, Novus, Caza, Infinity Round 5 and Old Infinity. Each browsed continent has its own published deed and road layers.

Enable **MINI MAP** for a separate character-centred surface or cave view. Scroll to zoom. Its footer switches GROUND/CAVE, opens the full map and controls height contours: 1–99 metres, or 0 to disable them. Terrain and live positions use only data received by the client.

Loot Map navigation follows the white rabbit from native readings. Archaeology tracking keeps search targets and can play a completion chime. Scanner profiles search animals, uniques and treasure with optional notifications and model outlines.

## Settings and updates

Open **Main menu → Mod updates → Waypointer → Settings** in the shared **Mods Registry**. All general settings are there, including creature rules, tile colours, the highlight radius, meteorite options, navigation and storage. Every parameter has hover help. Valid edits save automatically; supported changes apply immediately. Startup-only changes offer Wurm's standard quit confirmation.

The Waypointer hub contains eight tabs: Waypoints, Mobs, Containers, Objects / items, Vehicles, My animals, Creatures Highlight and Tiles Highlight. General settings are available only in the updater; there is no Settings tab in Waypointer.

Select the shared interface language in the updater: **English, Brazilian Portuguese, German or Russian**. It applies immediately to participating mods. The embedded canonical updater is **1.5.0**, with **Chamomilo UI 0.4.11**. Updates open the release download for manual installation.

## Useful commands

```text
/wp nav off
/wp nav style pulse|solid|moving|status
/wp scan animals|uniques|treasure
/wp scan off|status|window
/wp scan notify on|off|status
/wp scan outline on|off|status
/wp scan exclude catseye, corpse, pet
/wp scan unexclude corpse
/wp scan excludes
/wp scan clear-excludes
/wp deeds
/wp deeds refresh
/wp deeds status
/wp perf
```

`/wp perf` reports performance counters. For a rendering problem, include the mod version, time and timezone, enabled highlights, radius, and client logs. Bounded mod diagnostics are stored in `wurm-waypointer-data/waypointer-diagnostics-*.log`.

## Build and integration

Build with a Java 8 JDK. Copy `local.properties.example` to `local.properties` and supply `client-patched.jar`, `common.jar`, `javassist.jar` and `modlauncher.jar` through `wurmClientLibDir` or `WURM_CLIENT_LIBS`. Proprietary client libraries are not distributed. If JavaFX is absent from the JDK, set `wurmJavaFxJar` to an existing client `runtime/lib/ext/jfxrt.jar` for interface checks.

The canonical updater reference defaults to `C:/projects/updater`; override with `CHAMOMILO_UPDATER_REFERENCE` or `-PchamomiloUpdaterReference=/path/to/updater`. The UI verifier defaults to `C:/projects/interface items`; override with `-PchamomiloUiKitReference=/path/to/interface-items`.

Run `gradlew.bat clean build dist` on Windows or `./gradlew clean build dist` elsewhere. Output: `distribution/build/distributions/wurm-waypointer-1.10.4.zip`. Release checks verify tests, native interface layout/input, complete updater/SDK embedding and the four-file ZIP. Live game GPU behaviour is a manual check.

See [settings](docs/SETTINGS.md), [hub and tracking](docs/WAYPOINTER_HUB.txt), [render diagnostics](docs/RENDER_DIAGNOSTICS.txt), [external mod API](EXTERNAL_MOD_API.txt) and [changelog](CHANGELOG.txt) for further details.

Licensed under **GPL-3.0-only**. [License](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.txt) are also embedded in `META-INF/waypointer/` inside the mod JAR. The fonts retain their SIL Open Font License inside the JAR.

Thanks to **Wolfbane** and **FlpSilva** for beta testing, and **Killerspike** for suggestions and detailed bug reports.
