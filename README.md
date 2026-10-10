# Wurm Waypointer

![Wurm Waypointer](wurm-waypointer-banner.png)

**Wurm Waypointer 1.7.6** is a client navigation and map mod for Wurm Unlimited, created especially for Sklotopolis. Create a destination, start NAV and follow the glowing navigation pulse.

[Download the latest release](https://github.com/chamomilo/wurm-waypointer/releases/latest)

## Features

- **Waypoints and navigation:** permanent or temporary destinations, custom colours/icons/sizes, arrival notifications, chat sharing and GPS imports. Choose Pulse, Solid or Moving navigation signals.
- **Sklotopolis maps:** browse Liberty, Novus, Caza, Infinity Round 5 and Old Infinity with pan/zoom and their own published deeds and roads. CENTER returns to your character.
- **Surface and cave mini-map:** received terrain, waypoints, roads, navigation and optional height contours. Unknown terrain stays unknown.
- **Surroundings and monitoring:** browse nearby mobs, containers, objects and items; filter, mark, navigate and monitor results.
- **Managed animals and vehicles:** track permitted animals, carts, wagons and ships. Animal Track starts an automatic native direction search outside view, then follows its live position once received. Vehicle navigation needs a known position.
- **Loot Maps and archaeology:** follow the white rabbit using Loot Map readings, or track archaeology searches and receive a completion chime.
- **Scanner profiles:** live animal, unique and treasure searches with optional model outlines and appearance notifications.
- **Chamomilo interface and updates:** bundled UI, one mod registry, manual ZIP downloads and shared English, Brazilian Portuguese, German and Russian language selection.

## New in 1.7.6

- Complete canonical updater **1.2.13** and Chamomilo UI **0.4.10**, verified by version, SHA-256 and exact archive contents.
- Separate publication images in the project; TXT documentation in the installable ZIP, with no Markdown or README images.
- Research captures, temporary logs and unused full-size design sources removed from the project.

Also includes the changes prepared since 1.6.17: the compact seven-section hub, separate animal and vehicle catalogues, automatic animal searches, tracked-creature death cleanup, immediate shared language switching, and continent browsing with independent deed and road layers. See [CHANGELOG.txt](CHANGELOG.txt).

## Installation

A working Wurm Unlimited **client modloader** is required.

1. Download `wurm-waypointer-1.7.6.zip` from [Releases](https://github.com/chamomilo/wurm-waypointer/releases/latest).
2. Close the game and back up your configuration.
3. Extract the ZIP into `WurmLauncher`, merging the included `mods` folder.
4. Keep only the current Waypointer JAR in `mods/wurm-waypointer`; check that `mods/wurm-waypointer.properties` points to it.
5. Start the game normally.

Installed JAR: `WurmLauncher/mods/wurm-waypointer/wurm-waypointer-1.7.6.jar`.

Waypoints and marks are saved separately in `WurmLauncher/wurm-waypointer-data`. Preserve this folder when upgrading. Installation is manual; the updater opens downloads without installing or replacing game files.

## Getting started

- Open Waypointer through its compass control. The hub contains ALL WAYPOINTS, MOBS AROUND, CONTAINERS AROUND, OBJECTS AND ITEMS AROUND, VEHICLES, MY ANIMALS and SETTINGS.
- Press `M` for the full map. ALL MAPS ON SERVER opens the continent gallery; CENTER returns home. DEEDS, ROADS, MARKS and NAV LINE control overlays. Browsed continents have independent deeds/roads; live terrain and waypoint creation belong to the connected world.
- MINI MAP enables a separate character-centred view. Scroll to zoom; its footer has GROUND/CAVE, FULL MAP and the height-contour interval. Use 1–99 metres or 0 to disable contours.
- Add a waypoint in the hub or by left-clicking a map; right-click a map for a custom mark. NAV starts navigation and `/wp nav off` stops it.
- Refresh MY ANIMALS or VEHICLES to request native catalogues, then Track or Untrack. Animal searches need native Manage/Find permissions and use approximate direction responses. Living targets leaving view keep their labelled last known position; confirmed death removes the waypoint and stops search/navigation.
- `+ filter` includes any comma-separated matching fragment; `- filter` excludes any matching fragment. Matching ignores case; exclusions take precedence. Example: `horse, wolf`.
- Select language in **Main menu → Mod updates**. It applies immediately to participating mods and saves in `.chamomilo/updater.properties` under the user's home.

Useful commands:

```text
/wp nav style pulse|solid|moving|status
/wp nav off
/wp scan animals|uniques|treasure
/wp scan off|status|window
/wp scan exclude catseye, corpse, pet
/wp scan unexclude corpse
/wp scan excludes
/wp scan clear-excludes
/wp scan notify on|off|status
/wp scan outline on|off|status
/wp deeds
/wp deeds refresh
/wp deeds status
/wp perf
```

Settings are in the hub and `mods/wurm-waypointer.config`. See [hub and tracking instructions](docs/WAYPOINTER_HUB.txt).

## Other servers

Sklotopolis uses explicit server/world mappings. Other servers can configure JSON or CSV deed feeds through `deedProviderMappings`, with semicolon-separated entries:

```text
endpoint[|exact world]|<JSON or CSV>|source[|mapWidth|mapHeight]
```

HTTPS and local `file:///...` imports are supported. Unknown servers show No provider. The deed picker is available through `/wp deeds`; malformed refreshes preserve validated caches. Terrain details and live positions depend on data actually received by the client.

## Building from source

Use a **Java 8 JDK**. Copy `local.properties.example` to `local.properties` and set `wurmClientLibDir` to a directory containing `client-patched.jar`, `common.jar`, `javassist.jar` and `modlauncher.jar`. Proprietary libraries are not included.

If the JDK lacks JavaFX, set `wurmJavaFxJar` to an existing `runtime/lib/ext/jfxrt.jar` for native interface verification. It is read only and not packaged.

The canonical updater checkout defaults to `C:/projects/updater`; override with `CHAMOMILO_UPDATER_REFERENCE` or `-PchamomiloUpdaterReference=/path/to/updater`. It supplies the pinned UI SDK. The UI verifier defaults to `C:/projects/interface items`; override with `-PchamomiloUiKitReference=/path/to/interface-items`.

```text
gradlew.bat clean build dist
```

On other platforms use `./gradlew clean build dist`. Output: `distribution/build/distributions/wurm-waypointer-1.7.6.zip`.

Release checks cover module boundaries, runtime classes, translations, native layout/input, exact updater/UI embedding and allowed ZIP files. `README.txt` is generated from this README during packaging. Publication images remain outside the mod JAR and installation ZIP.

## Diagnostics and feedback

`/wp perf` reports counters. Bounded logs are in `wurm-waypointer-data/waypointer-diagnostics-*.log`. Include the exact time/timezone, mini-map mode, contour interval and diagnostic/client logs in freeze reports; see [render diagnostics](docs/RENDER_DIAGNOSTICS.txt). Live in-game GPU behaviour requires manual verification.

Report problems on [GitHub](https://github.com/chamomilo/wurm-waypointer/issues). Mod integrations are documented in [EXTERNAL_MOD_API.txt](EXTERNAL_MOD_API.txt).

![Waypointer icon](design/branding/wurm-waypointer-logo-128.png)

Thanks to **Wolfbane** and **FlpSilva** for beta testing, and **Killerspike** for suggestions and detailed bug reports.

Licensed under **GPL-3.0-only**. See [LICENSE](LICENSE) and [THIRD_PARTY_NOTICES.txt](THIRD_PARTY_NOTICES.txt).
