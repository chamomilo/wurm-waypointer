# Wurm Waypointer

![Wurm Waypointer](docs/wurm-waypointer-banner.png)

I would like to present **Wurm Waypointer**, a new client-side mod created especially for Sklotopolis.

Its main purpose is to let you create waypoints and navigate to them using a glowing, magic-like navigation pulse. Choose your destination and follow the light!

## Main features

1. **Waypoints**
   Create permanent or temporary waypoints and customize their size, pictogram, color, or white-light beam style. Waypoints can notify you when you arrive. You can also share them through chat, import waypoints shared by other players, or create one from a `/gps` message. An active Loot Map waypoint also appears in the Manager: switch it `Off` to hide it without losing hunt progress, then switch it `On` to restore it.

2. **Sklotopolis maps in-game**
   The server maps for all five Sklotopolis worlds are now available directly inside Wurm. Press `M` to see your position, waypoints, deeds, and published highways. While the active surface loads, the stock WO map is replaced by a branded gallery of Liberty, Novus, Caza, Infinity Round 5, and Old Infinity. The full map and mini-map use matching custom wood-and-iron frames and reused healthbar title plates. Use the `DEEDS`, `ROADS`, and `MARKS` buttons beside deed search to hide crowded overlay layers. `MINI MAP` enables a separate player-centred square view which remains independent of the full map; use the mouse wheel over it to change its 80-tile default range between 20 and 160 tiles in responsive eight-tile steps. The borderless mini-map embeds its view width in metres and an `OPEN FULL MAP` button directly in the custom frame. The full-map `DEEDS` and `ROADS` buttons also control settlement boundaries, deed anchor labels, and roads on the mini-map. `NAV LINE` toggles the complete currently planned Navigator path, including cross-layer tunnel continuations, on both maps at every zoom. Hovering keeps coordinates and terrain on the first line, adds deed/interior/perimeter information below it, and identifies nearby waypoints and their state. Left-clicking either map creates a waypoint; right-clicking opens a labelled custom-map-mark editor. Click a deed marker to view its details and use `Nav to deed` to track it and start NAV immediately. Provider-managed `DEED` waypoints keep the same UUID when a deed moves and become visibly `Stale` rather than disappearing when a valid newer catalog no longer contains them. Waypoints and custom map marks are stored in `wurm-waypointer-data`, outside the installed mod directory, and legacy data is migrated automatically.

3. **Casual Loot Map hunting**
   Read your Loot Map and follow the white rabbit! Waypointer uses the readings to estimate the treasure location and tries to minimize the number of readings required. Its route planner can guide you across roads and bridges, through tunnels, and deep inside mountains.

4. **Archaeology Report assistance**
   When you complete an archaeology report, Waypointer rings a bell, helps you request directions, tracks the search, and guides you toward the hidden cache.

5. **Surroundings browser**
   If you have used Bdew's Scanner, the idea will feel familiar. The Surroundings window displays nearby animals, vehicles, containers, and other objects. You can search and filter the list, mark any result with a temporary waypoint, and immediately navigate to it. The **Exclude names** field accepts several comma-separated fragments; an object matching any fragment is hidden, while the empty default hides nothing.

6. **Scanner profiles**
   `/wp scan uniques`, `/wp scan treasure`, and `/wp scan animals` enable ready-made live searches with distinct coloured model highlights. The nearest eight matches within 80 metres can be outlined through walls, and later appearance/disappearance transitions are reported in Event without replaying every already-loaded match when the profile is enabled. Use `/wp scan off` to stop, `/wp scan status` to inspect the active profile, and `/wp scan window` to open Surroundings.

Scanner minus-name rules are generic and session-local: `/wp scan exclude catseye, corpse, pet` adds several fragments, `/wp scan unexclude corpse` removes one, `/wp scan excludes` lists them, and `/wp scan clear-excludes` clears them. No object name is excluded by default. `/wp scan notify on|off|status` and `/wp scan outline on|off|status` control the two presentation channels. Outline count, distance, defaults, and optional startup exclusions can be configured with `scannerMaximumOutlines`, `scannerOutlineDistanceMetres`, `scannerNotifications`, `scannerOutlines`, and `scannerExcludedNames` in `mods/wurm-waypointer.config`.

The Navigator route-statistics window can be closed normally. Its **Navigation signal** selector switches the active route between `Pulse`, `Solid`, and `Moving` and saves the choice in `mods/wurm-waypointer.config`. The same setting is available through `/wp nav style pulse|solid|moving|status`; the older `/wp nav pulse on|off|status` aliases remain available. `/wp nav off` still stops Navigator completely.

## Deed providers

Sklotopolis HTTPS feeds are selected through an explicit endpoint/world table. Other Wurm Unlimited servers can configure plain JSON or CSV feeds with `deedProviderMappings`; both HTTPS URLs and local `file:///...` imports are supported. Each semicolon-separated mapping uses:

`endpoint[|exact world]|<JSON or CSV>|source[|mapWidth|mapHeight]`

Unknown servers, or every server when `deedProviderEnabled=false`, use the explicit `No provider` state. `/wp deeds` opens the current-server picker even on a server without a built-in map. Provider health and data age appear there and in `/wp deeds status`; `/wp deeds refresh` requests a background refresh. Downloads use timeouts, size/schema/bounds checks, conditional HTTP requests, retry backoff, and an atomic last-known-good cache. A malformed or empty refresh never replaces usable cached data.

## Installation

Wurm Waypointer requires a working Wurm Unlimited client modloader.

1. Download the latest ZIP from the [Releases page](https://github.com/chamomilo/wurm-waypointer/releases/latest).
2. Close Wurm Unlimited.
3. Extract the ZIP into your `WurmLauncher` directory and allow the included `mods` folder to merge with the existing one.
4. Start the game normally.

After installation, the mod should be located at:

`WurmLauncher/mods/wurm-waypointer/wurm-waypointer.jar`

## Replacing other mods

Wurm Waypointer overlaps with the functionality of several existing mods, including Scanner, custom map mods, and Improved Compass. You can remove those mods or continue using them alongside Waypointer—the choice is yours.

## Feedback

Please try the mod and share your feedback! Bug reports and suggestions will help us make it better.

Download and source code: https://github.com/chamomilo/wurm-waypointer

Special thanks to **Wolfbane** and **FlpSilva** for beta testing, and to **Killerspike** for thoughtful suggestions and detailed bug reports.

Licensed under `GPL-3.0-only`.
