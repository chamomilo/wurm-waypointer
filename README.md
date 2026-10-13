# Wurm Waypointer

Waypoints, maps, creature tracking and configurable highlights for the **Wurm Unlimited client**.

**Version 1.10.6** · [Download the ZIP](https://github.com/chamomilo/wurm-waypointer/releases/latest) · [Report an issue](https://github.com/chamomilo/wurm-waypointer/issues)

## Install or update

Install with the game closed. A working Wurm Unlimited client modloader is required.

1. Download `wurm-waypointer-1.10.6.zip` from Releases.
2. If upgrading, back up `mods/wurm-waypointer.config` and `wurm-waypointer-data`.
3. Extract the ZIP into `WurmLauncher`, merging its `mods` folder. Keep your existing `.config` when upgrading; the supplied file contains defaults for a new installation.
4. Use the new `mods/wurm-waypointer.properties` and keep only `wurm-waypointer-1.10.6.jar` in `mods/wurm-waypointer`.
5. Start the client and click the compass to open Waypointer.

The installation archive contains four files:

```text
mods/wurm-waypointer.properties
mods/wurm-waypointer.config
mods/wurm-waypointer/README.txt
mods/wurm-waypointer/wurm-waypointer-1.10.6.jar
```

The compass, map artwork, shared updater, interface SDK and license notices are embedded in the JAR. The compass reads its dial and rotating needle directly from that JAR, including under Wurm's shared loader. It needs no loose or extracted images.

![Embedded compass dial and needle](design/branding/wurm-waypointer-hud-compass-128.png)

Saved waypoints and cached maps are stored in `wurm-waypointer-data`. Preserve that folder when upgrading.

## Waypoints and tracking

The hub has eight tabs: **Waypoints**, **Mobs**, **Containers**, **Objects / items**, **Vehicles**, **My animals**, **Creatures Highlight** and **Tiles Highlight**.

![Waypointer hub with waypoint controls and filters](docs/images/waypoints.png)

Add, edit, share, import and export waypoints. **ON** shows a target's markers; **NAV** starts navigation. Use `/wp nav off` to stop navigation. Nearby lists show entities received by the client. Refresh **My animals** or **Vehicles**, then Track or Untrack a target. Animal direction searches require the game's Manage/Find permissions. Tracked targets retain their last known position when they leave view.

Text filters accept comma-separated fragments and ignore case. The `+` filter includes matches; the `-` filter excludes them. Exclusions take priority. For example, `horse, wolf` includes either name.

## Creatures Highlight

Enable outlines independently for players, animal categories, monsters, Unique creatures and creature conditions. Each label shows the currently detected count. **ON** is green; **ALL ON** and **ALL OFF** change the outline switches.

![Creature categories, conditions, colours and Auto-mark controls](docs/images/creatures-highlight.png)

**Color** opens a picker. Apply saves the colour; Cancel or closing the picker keeps the previous value. Enabled condition colours take priority over Unique and category colours. Scared starts with warm yellow to distinguish it from blue tower guards.

Unique, Champion and Diseased support **Auto-mark**. A matching creature creates one tracked waypoint even if its outline is off. Auto-mark does not start navigation. Outlines and counts use creatures currently received by the client, on the surface or in caves.

## Tiles Highlight

Terrain rules can work together: a tile matching several enabled rules keeps separate coloured borders.

![Terrain highlight switches, colours and meteorite controls](docs/images/tiles-highlight.png)

| Rule | Highlights |
| --- | --- |
| Non-rideable tiles | Slopes above 40 dirt (4 m), water deeper than 0.7 m, lava and cave openings. |
| Flower tiles | Flower-bearing terrain in its native flower colour. |
| Harvestable areas | Ripe crops and mature trees or bushes with received harvestable fruit. |
| Overaged trees | Overaged and Shrivelled trees, independently of harvestability. |
| Meteors | Landed meteorites, with optional Auto-mark and Chime. |

The four terrain switches start **OFF after each client launch**. Colours and radius are retained. The default radius is **7 tiles along each axis**, a **15 × 15 square**. Change **Tile highlight radius** in the updater Settings, from **1 to 16**; changes apply during play.

Meteor outlines, Auto-mark and Chime default to ON for a new installation. Saved choices are respected. Detection uses loaded objects and is independent of the terrain radius. Each detected meteorite is marked and sounded once per session, without starting navigation.

Terrain highlights describe received terrain. Fences, buildings and permissions also affect whether a route can actually be ridden. Hover help explains crop stages, tree ages and other thresholds.

## Maps and searches

Press **M** for the full map. Pan and zoom, use **CENTER** to return to your character, and switch deed, road, mark and navigation layers independently. Left-click adds a waypoint; right-click adds a map mark.

**MINI MAP** opens a character-centred surface or cave view. Scroll to zoom. Its footer switches GROUND/CAVE, opens the full map and controls height contours: **1–99 metres**, or **0** to turn them off.

On recognized Sklotopolis servers, **ALL MAPS ON SERVER** browses Liberty, Novus, Caza, Infinity Round 5 and Old Infinity, including published deed and road layers. Loot Map tracking follows the white rabbit from native readings. Archaeology tracks search targets and supports a completion chime. Scanner profiles search animals, uniques and treasure with optional notifications and outlines.

## Settings, language and updates

Open **Main menu → Mod updates → Waypointer → Settings** in the shared **Mods Registry**. Navigation, storage, creature rules, tile colours, highlight radius and meteorite options are configured there. Every parameter has hover help. Valid edits save automatically and apply during play where supported. Startup-only changes offer Wurm's normal quit confirmation.

Choose **English, Brazilian Portuguese, German or Russian** using the updater's shared language selector. The choice applies immediately to participating mods. This release embeds the complete canonical **updater 1.5.0** and **Chamomilo UI 0.4.11**. Updates open the release download for manual installation.

The images show the interface with sample names and counts.

## Commands and support

| Command | Purpose |
| --- | --- |
| `/wp nav off` | Stop navigation. |
| `/wp nav style pulse\|solid\|moving\|status` | Select or inspect the navigation effect. |
| `/wp scan animals\|uniques\|treasure` | Start a scanner profile. |
| `/wp scan off\|status\|window` | Stop, inspect or open the scanner. |
| `/wp scan notify on\|off\|status` | Configure scanner notifications. |
| `/wp scan outline on\|off\|status` | Configure scanner outlines. |
| `/wp deeds` | Open deed search. |
| `/wp deeds refresh` | Refresh deed data. |
| `/wp perf` | Show performance counters. |

When reporting a problem, include the mod version, time and timezone, enabled highlights, radius and client logs. Bounded mod diagnostics are stored in `wurm-waypointer-data/waypointer-diagnostics-*.log`.

See the [settings guide](docs/SETTINGS.md), [render diagnostics](docs/RENDER_DIAGNOSTICS.txt), [external mod API](EXTERNAL_MOD_API.txt) and [changelog](CHANGELOG.txt).

## Building from source

Use Java 8 and the Gradle wrapper. Configure the client libraries through `local.properties`, `wurmClientLibDir` or `WURM_CLIENT_LIBS`; see `local.properties.example`. Proprietary client libraries are not distributed. Interface checks also need JavaFX when it is absent from the JDK.

The shared updater reference defaults to `C:/projects/updater`; override it with `CHAMOMILO_UPDATER_REFERENCE`. The interface verifier defaults to `C:/projects/interface items`; override it with `-PchamomiloUiKitReference=/path/to/interface-items`.

Run `gradlew.bat clean build dist` on Windows or `./gradlew clean build dist` elsewhere. The ZIP is written to `distribution/build/distributions/`. The release checks cover tests, native interface input and layout, exact updater/SDK embedding and the four-file installation archive.

Licensed under **GPL-3.0-only**. [License](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.txt) are also embedded in the JAR. Bundled fonts retain their SIL Open Font License.

Thanks to **Wolfbane** and **FlpSilva** for beta testing, and **Killerspike** for suggestions and detailed bug reports.
