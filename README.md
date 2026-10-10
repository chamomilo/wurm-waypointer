# Wurm Waypointer

![Wurm Waypointer](docs/wurm-waypointer-banner.png)

**Wurm Waypointer 1.6.17** is a client-side navigation and map mod for Wurm Unlimited, created especially for Sklotopolis.

Its main purpose is to let you create waypoints and navigate to them using a glowing, magic-like navigation pulse. Choose your destination and follow the light!

## New in 1.6.17

- Use the canonical Chamomilo updater **1.1.0** and its reviewed UI SDK **0.4.4**, verified against the reference in both the JAR and installable ZIP.
- Support the registry's **Force language** choice through Waypointer's saved settings and normal HUD update. English, Brazilian Portuguese, German and Russian are supported.
- Include the recent hub, Monitoring, mini-map and render fixes listed below. See [Render diagnostics](docs/RENDER_DIAGNOSTICS.md) for collecting evidence of a freeze.

## Included from 1.6.16

- Reuse the unchanged published map texture and terrain hover index across reconnects; retain one current map revision.
- Complete texture preparation and consume loader requests on the background worker. Skip superseded jobs before decoding.
- Flush dynamic map uploads only before the HUD queue, leaving world and reflection queues free of map upload work.
- Record native OpenGL queue and window/frame-output timings, slow events with timestamps, heap usage and long GC pauses in the diagnostic log.
- Embed the current canonical shared updater 1.1.0 with its pinned UI SDK 0.4.4.

## Included from 1.6.15

- Keep table headings fixed while scrolling rows in every hub table and Monitoring.
- Show complete rows in Monitoring, with a gap above the footer and green highlighting for active Mark/Clear actions.
- Place ADD TO MONITOR beside the table heading, keeping it fixed during scrolling.
- Filter MY FRIENDS by All, Online or Offline, using the status received from the server.
- Give the mini-map footer one shared font size and baseline, including larger, vertically centered topographic numbers.
- Calculate topographic contours in the background and reuse their pixels while moving, with bounded refreshes.
- Upload and retire dynamic map textures at OpenGL frame boundaries, reusing texture storage.
- Preserve render timings and warnings in `wurm-waypointer-data/waypointer-diagnostics-*.log`; `/wp perf` includes map and texture-upload measurements.

## Included from 1.6.14

- Keep padded table buttons aligned and fully drawn while scrolling up or down.
- Use bundled Chamomilo typography throughout Waypointer, including table contents, editable fields, option lists, dialog text, titles and map/HUD labels.

## Included from 1.6.13

- Leave a 4 px vertical gap between table actions while retaining the existing row density.
- Harmonize Monitoring's Mark buttons, title, count label, Back to Waypointer and Refresh actions.
- Refresh received terrain within five tiles of the player every second on both maps. Local patches retain recent observations during the session and update only when pixels change; distant terrain still uses the published server map.

## Included from 1.6.12

- Show every filter option without internal scrolling, using multiple columns where needed.
- Align surroundings headings with their body columns, including Mark/Unmark.
- Standardize all table headings on the updated Chamomilo Bold typography, glyphs and one outer frame without internal vertical dividers.
- Remove the complete Short name filter row from CONTAINERS AROUND and OBJECTS AND ITEMS AROUND.

## Included from 1.6.11

- Fix MY VEHICLES AND ANIMALS receiving default sorted native lists: animal, cart/wagon and ship responses populate this view without opening a standard Manage window.
- Load managed and friend catalogues on first opening their sections. Refresh has a visible caption and explanatory hint; repeated row actions share Chamomilo typography and spacing.
- Use compact column tables for managed targets and friends, with fixed Bold headings, three-state sorting, position/distance/history columns and stable scrolling.
- Embed the complete canonical shared updater 1.0.2 and verify its exact contents in the JAR and ZIP.

## Included from 1.6.10

- Keep editor keyboard focus on the owning hub. Closing the manager after editing or starting navigation removes its complete content tree, dropdowns and filter windows, and restores chat focus.
- Clean up panels promoted by the previous focus code and preserve unrelated windows and active navigation. Compass close/reopen always uses one hub.
- Embed the complete canonical shared updater 1.0.1 with separate module metadata, SHA-256 lock and exact JAR/ZIP verification.

## Included from 1.6.9

- Put Add before Refresh in one typography group in ALL WAYPOINTS. On and active NAV have green state highlights; Delete removes the UUID immediately.
- Unmark, Untrack and Clear all marks remove waypoint membership. Targets follow their native object ID; later movement cannot re-add a removed waypoint. Legacy Surroundings marks are cleared too.
- Replace confusing Live-age labels with Visible now and explanatory hints. Last seen retains the age of the last received position.
- Apply the same surroundings layout to mobs, containers and objects/items: filter headings above editor/Clear/example rows, count below the grouped filters, and clickable column headers cycling ascending, descending and original order. Remove the sort dropdown and Apply.
- Remove the unavailable Traits column. Keep ADD / TO / MONITOR uppercase with independently measured Low density spacing.
- Give filter lists 32 px rows, separate clickable square checkboxes, and one shared caption size/baseline.

## Included from 1.6.8

- Group Condition/Unique/Deed/Layer/Mark and Apply with one shared font and baseline. Reserve filter states and shorten overflowing captions with the full selection in the hint.
- Give manager and surroundings footer rows their own typography groups.
- Keep the equal left selectors Low density with uppercase captions, an explicit user override of the kit's default lowercase convention.

## Included from 1.6.7

- Compact the hub title band and the space above ALL WAYPOINTS. The left selectors use equal 56 px Low density buttons and one shared 24 px font size.
- ALL WAYPOINTS actions have exact 32 px heights, a shared caption size and baseline measured for the complete command group, widths fitted to their captions, 6 px gaps and fitted column text with full tooltips.
- ADD TO MONITOR is a separate 144 px square primary action with an independently fitted three-row Low density caption.
- Live coordinates and data ages update existing cells. Background updates keep row order, applied filters and scroll position; deletion preserves the top visible target and pixel offset. Refresh also retains filters.
- Dropdown choices take effect through Apply filters. The closed field and detached popup keep constant opacity through HUD transitions, using pinned Chamomilo UI 0.3.2.
- Delete removes the selected tracked UUID from ALL WAYPOINTS immediately and reports that UUID. Its Manage/friend catalogue entry remains available for another explicit Track. On/Off changes marker visibility independently of membership.

## Included from 1.6.6

- Keep open windows opaque through HUD focus/fade transitions, matching Keybinder.
- Use the Chamomilo button typography guidelines: bundled Alegreya Sans SC, Low density for mode selectors and ADD / TO / MONITOR, High density for row actions and map controls. Captions fit in regular and bold without changing the control on hover.
- **MOBS AROUND** displays streamed animals again. Hostility reads the native private field correctly and falls back to Unknown without hiding a creature.
- Waypointer uses **Chamomilo Interface Kit 0.3.0** for window chrome, buttons, fields, dropdowns, scrollbars and map frames. The compass retains its custom dial and rotating needle.

## Included from 1.6.3

- All hub tables use vertical scrolling only; the window minimum fits the columns and translated controls at the current font size.
- **MOBS AROUND** separates **Condition** and **Hostility**, without item-only columns or Position / ID. Hostility is the received server attitude. The unavailable Traits column was removed in 1.6.9.
- Surroundings footer actions share one row. The square **ADD TO MONITOR** button at the upper right opens the current filtered view in Monitoring. Watch filter presets and Clear watches have been removed.

## Included from 1.6.2

- Friends and Manage catalogue entries appear in **ALL WAYPOINTS** only after you explicitly press **Track** or add a nearby object as a waypoint. Automatic candidates, including unknown-position entries, remain in their catalogue views.
- **On/Off** controls marker visibility separately from addition to the list. **Delete** removes a tracked target from **ALL WAYPOINTS** immediately; refreshes do not add it back. You can add it again with **Track** in its catalogue.
- Previously enabled targets remain added. Old automatic disabled candidates disappear from **ALL WAYPOINTS** when the existing cache is loaded.

## Included from 1.6.1

- Fix the client crash when switching between surroundings views and Waypoints: native keyboard-focus queries no longer recurse between the selected panel and the hub.
- Remove the **+ filter** and **- filter** text rows from **ALL WAYPOINTS**.

## Included from 1.6.0

- One Waypointer hub with attached left selectors: **ALL WAYPOINTS**, **MOBS AROUND**, **CONTAINERS AROUND**, **OBJECTS AND ITEMS AROUND**, **MY VEHICLES AND ANIMALS**, **MY FRIENDS**, and **SETTINGS**. Views keep their filters when you switch. The frame is five pixels; controls use the standard Chamomilo interface kit.
- **MY VEHICLES AND ANIMALS → Refresh** explicitly requests vanilla Manage animals, carts/wagons and ships. Catalogue membership does not invent a position. **Direction** uses a fresh native question and shows the server's approximate animal search area; overlapping readings remain approximate. Native Manage permissions still apply, and unknown forms open normally.
- **MY FRIENDS** reads the friend list received by the client. Tracked animals, items, vehicles and locally visible friends follow received movement. After they leave view, their last coordinate stays labelled **Last known position** with its age. Unknown/other-server positions have no exact marker. Data survives restart and stays separate for each character and server endpoint.
- **SETTINGS** collects global preferences, including the navigation signal, scanner profile, marker budgets, retention and file paths. Saving validates the full draft and preserves configuration comments and unknown settings. File path changes take effect after restart.
- English, Brazilian Portuguese, German and Russian are selectable in **SETTINGS → Language**. Received object names and the native server protocol retain their original text.
- Every searchable list has matching **+ filter** and **- filter** rows. Enter comma-separated fragments, for example `horse, wolf`. Plus includes entries containing **any** fragment, regardless of case; minus removes entries containing **any** excluded fragment and takes precedence. Blank plus includes everything; blank minus excludes nothing. Matching is literal substring matching across the record's searchable fields.

See [Hub and tracking](docs/WAYPOINTER_HUB.md) for status meanings and the architecture.

## Included from 1.5.4

- Restore the complete original wood-and-iron buttons, with intact corners and upper/lower frames. Labels are centered inside a 28 px minimum height; only the middle stretches as width changes.
- Place **Mods Registry by Chamomilo** below the frame in the dark header. The reusable button and rendering details are documented in [Updater buttons](docs/UPDATER_BUTTONS.md).

## Included from 1.5.3

- Replace the old updater with Avatar 2.0's **Mods Registry by Chamomilo**: compact mod cards, descriptions, project links, **DOWNLOAD** actions and disabled **LATEST** buttons, with matching wood-and-iron artwork.
- Open the registry at any time through **Main menu > Mod updates**. **Don't show on next start** saves your startup preference in `.chamomilo/updater.properties`; version checks and the menu entry remain available. Downloads open ZIPs for manual installation.
- Remove the old Waypointer updater host and report the current runtime version consistently with the packaged mod descriptor. The shared protocol remains compatible with other Chamomilo mods; the first loaded updater copy determines the interface.

## Included from 1.5.2

- CAVE reinforcement uses two short orange lines on the right and bottom; structures use two short pink lines on the top and left. Both leave the corners clear and remain visible at every zoom level.

## Included from 1.5.1

- The compact **- / interval / +** control adds mini-map height contours; **Topographic view** appears in its hover text. All footer controls have the same height, and the mini-map metre scale is removed. Click -/+ or type a two-digit interval of **1–99 metres** to rebuild immediately; **0** turns contours off. Dense one-metre contours cover the complete received area while keeping the frame and markers visible. GROUND uses received terrain heights, CAVE uses received floor heights. Unknown terrain has no invented contours.
- **FULL MAP** is now a compact button to the right of GROUND/CAVE. The footer row is centred with equal gaps and visible metal corner plates.
- The full map's **Zoom speed: 1X** button cycles **1X → 2X → 4X → 1X**. It multiplies the original wheel step on the full map and both mini-map layers without changing their zoom limits.
- The player heading arrow keeps the original maximum full-map zoom size at every zoom level, including its outline, on the full map and both GROUND and CAVE mini-map layers.
- Ordinary tile grid lines appear only at maximum zoom on the full map and both mini-map layers, disappearing on the first zoom-out step. Orange reinforcement and pink structure edge marks remain visible in CAVE at every zoom level.
- Underground iron ore is dark red; copper ore is green.

## Included from 1.4.3

- Restored the complete NAV route on the surface map, including underground sections and cave approaches to the selected exit.
- Underground map segments keep the waypoint colour with 50% desaturation; surface segments keep the normal colour. The CAVE mini-map keeps its regular underground line.
- Cached map lines also preserve routes entirely underground.
- Replace the player cross with a live rotating heading arrow on the full map and both mini-map layers, sized to one map tile.

## Included from 1.4.2

- NAV approaches join the highway where they actually reach it, including when the initially selected entry cannot be reached.
- Missing distant highway terrain preserves the received highway prefix and the published map continuation instead of discarding the complete journey.
- Incomplete near-terrain corners fall back to available distant terrain.
- The cave mini-map shows the underground route; the surface map shows the continuation from the selected cave exit. Tunnel segments retain their layer and never connect separated surface spans through a cave.
- Already built surface routes remain visible during player-layer changes and cave receipt gaps; switching the target or stopping NAV clears the cached line.
- Detailed NAV logs include highway attempt results, actual approach endpoints, missing terrain coordinates and blocking coordinates.

## Included from 1.4.1

- Fixed junctions on the second highway lane and unnecessary trips to bridge/tunnel endpoints when changing lanes.
- Removed repeated branch excursions and overlapping terrain-leg loops from NAV routes.
- Kept the usable highway journey when only the final approach is incomplete, preserving highway preference over distant off-road shortcuts.
- Added routing to a reachable received cave exit when navigating to a surface target from a cave without a published highway. Unreceived cave floors and exits remain unknown.
- Cross-layer map lines use checked approach/target connectors instead of filling missing portions with straight lines.

## Included from 1.4.0

The Chamomilo versions window appears at every launch, including when everything is current or GitHub checks fail. It lists all mods from the public catalogue in [wurm-keybinder/chamomilo-mods.properties](https://github.com/chamomilo/wurm-keybinder/blob/main/chamomilo-mods.properties), verifies public repository membership and detects disabled or unloaded installations. Each row shows installed/latest versions; UPDATE and INSTALL open manual ZIP downloads. New catalogue entries appear without upgrading the updater. The window uses a thinner version of the map's high-resolution wood-and-metal frame.

Verified catalogue data is cached under your user home in `.chamomilo/mod-catalog.properties` for offline startup. All participating mods should include the current shared updater: an earlier-loaded mod with an older copy can still select the older interface.

## New in 1.3.2

- Fixed ore veins and unexcavated rock disappearing behind **Unknown (not received)** in the cave mini-map. All received rock and ore types are now shown even before their floor/ceiling are formed.
- Tile receipt follows the native cave buffer, keeping unreceived and overwritten cells unknown.

## Included from 1.3.1

- Verified the shared Chamomilo protocol-1 updater integration. Release builds now check every packaged updater class and the exact metadata in both the project and the installable ZIP.
- Fixed the Waypointer update name so notifications do not repeat the word Wurm.

## Included from 1.3.0

- The mini-map has a **GROUND / CAVE** button beside **OPEN FULL MAP**. Entering a cave selects CAVE once; leaving selects GROUND once. You can switch manually until the next layer transition.
- CAVE starts at **17×17 tiles**. Mouse-wheel zoom reaches **48×48 tiles**; GROUND keeps its separate zoom setting.
- Tunnels, entrances, water, reinforcement, paving, structures and received ore/rock types are shown in contrasting colours. Zinc, silver and marble have distinct colours and patterns.
- Hover shows the cave tile name, floor/ceiling/clearance, water depth and available reinforcement, paving, cladding and structure information. Ore quality and remaining yield are not supplied by this terrain stream. Unknown tiles remain marked as unknown.
- Waypoints and custom marks created in CAVE are saved on the cave layer.

## Also included from 1.2.2

- Fixed distant NAV routes using repeated local terrain coordinates, which could produce paths across water. Distant surface routes now use the distant terrain data; cave navigation stops where reliable cave data is unavailable.
- Fixed highway entry and exit calculations at junctions, avoiding unnecessary trips into branches and back onto the main road.

## Also included from 1.2.1

- `CENTER` on the full MAP returns the view to your character without changing the zoom. The mini-map follows your character automatically.
- Hover a loaded tree or bush tile on either map to see its age and `Harvestable` when the client reports fruit ready to collect.
- Hover a loaded field tile to see the crop type, growth stage and `Harvestable` for ripe crops. These details are available only within the client's live terrain range; distant map images do not contain crop or tree state.
- The mini-map title is centred, and `OPEN FULL MAP` sits two pixels lower.
- A shared Chamomilo update notification lists available mod releases and opens their ZIP download links for manual installation.

## Main features

1. **Waypoints**
   Create permanent or temporary waypoints and customize their size, pictogram, color, or white-light beam style. Waypoints can notify you when you arrive. You can also share them through chat, import waypoints shared by other players, or create one from a `/gps` message. An active Loot Map waypoint also appears in the Manager: switch it `Off` to hide it without losing hunt progress, then switch it `On` to restore it.

2. **Sklotopolis maps in-game**
   The server maps for all five Sklotopolis worlds are now available directly inside Wurm. Press `M` to see your position, waypoints, deeds, and published highways. While the active surface loads, the stock WO map is replaced by a branded gallery of Liberty, Novus, Caza, Infinity Round 5, and Old Infinity. The full map and mini-map use matching custom wood-and-iron frames and reused healthbar title plates. Use the `DEEDS`, `ROADS`, and `MARKS` buttons beside deed search to hide crowded overlay layers. `MINI MAP` enables a separate player-centred square view which remains independent of the full map; use the mouse wheel over it to change its 80-tile default range between 20 and 160 tiles in responsive eight-tile steps. The borderless mini-map groups the contour interval, GROUND/CAVE selector and FULL MAP button in its footer. The full-map `DEEDS` and `ROADS` buttons also control settlement boundaries, deed anchor labels, and roads on the mini-map. `NAV LINE` toggles the complete currently planned Navigator path, including cross-layer tunnel continuations, on both maps at every zoom. Hovering keeps coordinates and terrain on the first line, adds deed/interior/perimeter information below it, and identifies nearby waypoints and their state. Left-clicking either map creates a waypoint; right-clicking opens a labelled custom-map-mark editor. Click a deed marker to view its details and use `Nav to deed` to track it and start NAV immediately. Provider-managed `DEED` waypoints keep the same UUID when a deed moves and become visibly `Stale` rather than disappearing when a valid newer catalog no longer contains them. Waypoints and custom map marks are stored in `wurm-waypointer-data`, outside the installed mod directory, and legacy data is migrated automatically.

3. **Casual Loot Map hunting**
   Read your Loot Map and follow the white rabbit! Waypointer uses the readings to estimate the treasure location and tries to minimize the number of readings required. Its route planner can guide you across roads and bridges, through tunnels, and deep inside mountains.

4. **Archaeology Report assistance**
   When you complete an archaeology report, Waypointer rings a bell, helps you request directions, tracks the search, and guides you toward the hidden cache.

5. **Surroundings browser**
   If you have used Bdew's Scanner, the idea will feel familiar. The hub's three Surroundings views display nearby animals, containers and other objects. Search with **+ filter** and **- filter**, track any result and navigate to its received position. Tracked targets retain a clearly labelled last coordinate after leaving view.

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

`WurmLauncher/mods/wurm-waypointer/wurm-waypointer-1.6.17.jar`

When upgrading, keep only the current Waypointer JAR in that folder and check that `mods/wurm-waypointer.properties` points to it. Back up your configuration before extracting an update. Waypoints and custom marks are stored separately in `wurm-waypointer-data` under the client directory.

## Hub and mini-map controls

Table headings stay visible while their rows scroll, including in Monitoring. Active Mark/Clear actions are green. Monitoring shows complete rows and keeps a gap above **Back to Waypointer** and **Refresh**. **ADD TO MONITOR** sits beside the current Around table heading. **MY FRIENDS** combines its text filters with **All / Online / Offline**, using the status received from the server.

The Manage catalogue can list animals and vehicles that are outside the client's received area. **Position unknown** means no usable coordinate has been received; navigation remains unavailable until a position is known. **Direction** relies on the server's native Manage permissions and returns an approximate search area when supported. Waypointer does not derive a precise position from catalogue membership.

The mini-map footer groups **− / contour interval / +**, **GROUND / CAVE** and **FULL MAP** with a common font size and baseline. Set the contour interval to **0** to disable contours, or **1–99 metres** for received surface/cave heights. Contours are calculated in the background and cached while moving. This release reduces unnecessary map decoding and texture work; disappearance of the reported black screens still needs an in-game check.

## Building from source

Use a Java 8 JDK. Copy `local.properties.example` to `local.properties` and set `wurmClientLibDir` to a directory containing your client libraries (`client-patched.jar`, `common.jar`, `javassist.jar`, and `modlauncher.jar`). These proprietary libraries are not included in this repository.

If your Java 8 JDK does not include JavaFX, also set `wurmJavaFxJar` in `local.properties` to your existing Wurm `runtime/lib/ext/jfxrt.jar` for the native updater UI verification. The same path can be passed with `-PwurmJavaFxJar=...` or `WURM_JAVAFX_JAR`; it is used only during verification and is not bundled.

The canonical updater source reference is also required. Its default location is `C:/projects/updater`; set `CHAMOMILO_UPDATER_REFERENCE` or `-PchamomiloUpdaterReference=/path/to/updater` for another checkout. The reference includes the pinned UI SDK; no separate UI JAR is needed in `render-wurm/libs`. Product source contains only updater lifecycle calls, metadata and the supported-language capability.

Run `./gradlew clean build dist` (`gradlew.bat clean build dist` on Windows). The installable archive is written to `distribution/build/distributions/wurm-waypointer-1.6.17.zip`; the build verifies module boundaries, packaged runtime classes and languages, native hub registration/focus/close, the Waypointer layout and input routing on four languages, the embedded UI kit and ZIP contents. The canonical shared updater is built and checked separately, then embedded with exact entry/byte comparison and its module version/hash lock in `client-wurm/chamomilo-updater.lock.properties`.

## Chamomilo update checks

After the first game HUD is ready, Waypointer participates in one background check
for all installed Chamomilo mods with protocol-1 update metadata. Whichever mod
claims the shared coordinator first displays one registry with installed and
available stable versions, descriptions and project links. **DOWNLOAD** opens
the mod's ZIP link in your browser, falling back to the latest release page;
**LATEST** is disabled for current or newer installations. Installation is manual.
The registry remains in **Main menu > Mod updates** after closing. Its **Don't
show on next start** checkbox saves a user preference outside the mod directory.
The checker uses network timeouts, and failed checks do not interrupt gameplay.

The registry's **Force language** selector defaults to **Off**. A supported selection is saved in `.chamomilo/updater.properties` under the user's home and applied to participating mods, including Waypointer. Waypointer saves the choice through its existing `mods/wurm-waypointer.config` workflow and relocalizes on the normal HUD tick. Unsupported choices retain each mod's local language. **Off** stops future shared applications; it does not restore previous choices. The saved choice also applies when the startup registry is hidden; the registry remains accessible through **Main menu > Mod updates**.

Version 1.6.17 embeds updater **1.1.0**, protocol **1**, and UI SDK **0.4.4**. Other participating mods share the same loader, so their embedded module versions also matter. Update them to compatible current releases when available. The updater opens downloads for manual installation and does not replace game files.

## Freeze reports

Waypointer keeps three bounded diagnostic logs in `WurmLauncher/wurm-waypointer-data/waypointer-diagnostics-*.log`. `/wp perf` reports the current counters. A useful report includes the exact local time and timezone, whether the mini-map was enabled, its GROUND/CAVE mode and contour interval, and the diagnostic and client logs covering the event. See [Render diagnostics](docs/RENDER_DIAGNOSTICS.md) for what these measurements establish and their limits.

## Replacing other mods

Wurm Waypointer overlaps with the functionality of several existing mods, including Scanner, custom map mods, and Improved Compass. You can remove those mods or continue using them alongside Waypointer—the choice is yours.

## Feedback

Please try the mod and share your feedback! Bug reports and suggestions will help us make it better.

Download and source code: https://github.com/chamomilo/wurm-waypointer

Special thanks to **Wolfbane** and **FlpSilva** for beta testing, and to **Killerspike** for thoughtful suggestions and detailed bug reports.

Licensed under `GPL-3.0-only`.
