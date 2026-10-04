# Changelog

## 1.3.0 — 2026-10-05

- Added a green GROUND / charcoal CAVE mini-map button beside OPEN FULL MAP. Entering or leaving caves selects the corresponding view once; manual overrides last until the next layer transition.
- Cave view starts at 17×17 tiles and zooms out to the complete 48×48 local terrain window. Ground zoom is preserved independently.
- Added live tunnels, entrances, water, reinforced/clad/paved cave tiles, structures and all received rock/ore types. Zinc, silver and marble have separate colours and patterns.
- Cave hover shows coordinates, rock/ore names, floor/paving, reinforcement, partial cladding, floor/ceiling/clearance, water depth and known structure presence. Ore quality and remaining yield are not part of the terrain stream.
- Cave clicks create cave waypoints and custom marks. Unreceived tiles are visibly unknown; distant ring-buffer aliases are rejected.

## 1.2.2 — 2026-10-04

- Fixed distant NAV paths reading repeated near/cave terrain from Wurm's circular 512-tile buffers, which could invent dry routes across water. Distant surface tiles now use distant terrain.
- Highway entry/exit connectors now use the graph edges split at junctions, avoiding trips to an original segment endpoint and back through the same branch.

## 1.2.1 — 2026-10-04

- Added a Center button to the full MAP, preserving zoom while returning to the character.
- Added live tree/bush ages and Harvestable flags, plus crop species, growth stages and Harvestable flags to hover text on both maps.
- Centred the mini-map title/nameplate and moved Open Full Map down by two pixels.
- Embedded the shared Chamomilo protocol-1 update coordinator and notification window required by the project working agreements.

## 1.2.0 — 2026-10-04

- Fixed the borderless mini-map hiding its own canvas together with the native window background, and enabled the mini-map by default on every fresh client start.
- Fixed cross-layer NAV lines stopping at the nearest tunnel portal on the maps; the full published route and final target connector now remain visible at every map zoom.
- Added `Nav to deed` to deed details and restored deed-detail opening when that deed is already tracked.
- Added permanent labelled custom map marks through right-click on either map. Waypoint data now defaults outside the installed mod directory and migrates the legacy file so reinstalls do not erase marks.
- Removed obsolete development-stage options and names from the distributed config while retaining backwards-compatible parsing for existing installations.

## 1.1.0 — 2026-09-07

- Added an independently visible, player-centred mini-map toggled from the full map, with borderless custom artwork, the reused healthbar nameplate, frame-embedded metre scale and `OPEN FULL MAP` control, shared deed/road visibility, labelled deed anchors, mouse-wheel zoom across a 20–160 tile range, tile-first multiline hover text, and click-to-create waypoints.
- Added a shared `NAV LINE` map layer: the exact currently planned Navigator path is rendered continuously in the active target's colour on both the full map and mini-map.
- Added provider-managed `DEED` waypoints with stable UUIDs, automatic coordinate updates, stale transitions, and visible data age.
- Added an asynchronous Sklotopolis provider with explicit server mappings, safe conditional HTTPS/cache behavior, and manual refresh.
- Added configurable JSON/CSV HTTPS or local-file providers plus an explicit `No provider` state for unmapped servers.
- Added a high-resolution top-down HUD compass with a separately rotating red-tipped north needle and waypoint markers travelling around the glass contour.
- Fixed Wurm's game classloader preventing the new compass artwork from loading by shipping file-backed compass assets in the distribution.
- Fixed `DEED` waypoints being stored but excluded from labels, compass markers, world effects, and Nav targets.
- Newly tracked `DEED` waypoints are now enabled immediately; tracking one again also re-enables it.

## 1.0.7 — 2026-09-05

- Fixed the Surroundings scrollbar moving without its list by translating the complete absolute-coordinate row/cell tree and avoiding Wurm's detached-root screen clamp.

## 1.0.6 — 2026-09-05

- Fixed Surroundings wheel and scrollbar interaction while the live catalog is growing: row replacement now restores the clipped viewport, wheel and native-looking thumb input use a stable offset path, and a user-selected scroll position pauses automatic row rebuilding until the list returns to the top.
- Added built-in `uniques`, `treasure`, and `animals` Scanner profiles with `/wp scan ...` commands suitable for Keybinder.
- Added bounded coloured picked-object highlights for the nearest profile matches, including a configurable through-wall pass.
- Added appearance/disappearance Event notifications with technical remove/add debouncing and initial-profile seeding to prevent login spam.
- Added generic, multi-value minus-name filters to Surroundings and Scanner; the default remains empty and does not special-case catseyes or any other object.

## 1.0.5 — 2026-08-31

- Fixed vertical marker animation accelerating on moving creatures by retargeting the existing world effect without restarting its animation clock and phase.

## 1.0.4 — 2026-08-31

- Removed the redundant bright HUD exclamation from external API marks; they now use only the ordinary world marker.

## 1.0.3 — 2026-08-31

- Fixed all custom world beams, symbols, and route geometry becoming invisible when the server has no active Rift by explicitly binding the white texture required by Wurm's modern world shader.

## 1.0.2 — 2026-08-30

- Allowed Focus Bar marks on selected fences, hedges, and other positioned objects outside the Surroundings catalog by resolving their live HUD object coordinates.
- Added API v2 caller-supplied `WurmObjectSnapshot` fallback coordinates for other external integrations.

## 1.0.1 — 2026-08-30

- Fixed external API object markers being easy to miss in the world by shipping the high-contrast red `ALERT` marker in the release build.
- Preserved the marker type requested through the public API (`TARGET`, `BEAM`, or `COMPASS_ONLY`) instead of replacing it with an exclamation during render projection.

## 1.0.0 — 2026-08-30

First stable release of Wurm Waypointer.

- Added permanent and temporary waypoints with configurable symbols, colors, arrival notifications, chat sharing, and `/gps` import.
- Added in-game Sklotopolis maps with deeds, roads, marks, search, and click-to-navigate support.
- Added Loot Map and archaeology navigation assistance.
- Added the searchable Surroundings browser with object actions, monitoring filters, and temporary marks.
- Added a public API for other client mods to create, remove, and query object markers.
- Improved marker rendering, navigation pulses, route controls, and compatibility checks.
- Fixed object marks on wagons and other vehicles by placing the marker above the vehicle model and using a high-contrast alert style.
