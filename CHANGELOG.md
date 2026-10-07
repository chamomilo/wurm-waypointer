# Changelog

## 1.5.3 — 2026-10-07

- Replace the old updater UI and remove its Waypointer-specific host. Embed the current Avatar 2.0 shared registry, including card descriptions, project links, DOWNLOAD/LATEST actions, five-pixel frame and matching button artwork.
- Register Mod updates in Main menu, with persistent Don't show on next start preference, close/reopen support and mouse-wheel access on short screens.
- Align the runtime version, mod descriptor and distribution at 1.5.3 so the updater compares the actual installed release.
- Verify the packaged preference class/button artwork, reject the removed updater host, and exercise native layout, menu lifecycle and startup preference during release builds.

## 1.5.2 — 2026-10-07

- Use two short orange edge lines on the right and bottom for cave reinforcement, and two short pink lines on the top and left for cave structures. Leave each tile's corners clear so the marks stay separate and the map remains readable.

## 1.5.1 — 2026-10-07

- Move Topographic view into hover text and make its controls the same height as GROUND/CAVE and FULL MAP. Remove the overlapping mini-map metre scale.
- Centre the compact footer row with equal eight-pixel gaps and clear margins around both decorative metal corner plates.
- Restore thin orange reinforcement and pink structure borders in CAVE at every zoom level; keep ordinary tile grid lines limited to maximum zoom. Tiles with both statuses retain both colours.
- Render contours as one cached transparent overlay instead of thousands of HUD primitives, preventing dense one-metre contours from exhausting Wurm's queue and hiding the frame, controls and markers.
- Generate contours across the complete received grid without a global segment cutoff; rebuild the overlay after interval, centre, zoom and layer changes.

## 1.5.0 — 2026-10-07

- Add a framed Topographic view mini-map control with a two-digit height interval, immediate -/+ adjustment and manual input. Intervals are 1–99 metres; 0 disables contours. Surface contours use received terrain heights, CAVE contours use received floor heights.
- Shorten OPEN FULL MAP to FULL MAP and move it right beside GROUND/CAVE to make room for the topographic controls.
- Add a full-map Zoom speed button cycling 1X, 2X and 4X; one shared factor multiplies the existing wheel steps on the full map and both mini-map layers.
- Keep the player heading arrow and its outline at the original maximum full-map zoom size, independent of zoom on the full map and both GROUND and CAVE mini-map layers.
- Show tile borders only at maximum zoom on the full map and both mini-map layers; hide them on the first zoom-out step. Remove baked cave borders and keep reinforcement as an interior marker.
- Render underground iron ore dark red and copper ore green.

## 1.4.3 — 2026-10-07

- Restore underground portions of the full NAV line on surface maps with 50% colour desaturation.
- Include received cave approaches before the surface continuation from the same selected exit; reject disconnected continuations.
- Preserve underground-only map routes during effect replacement. Keep CAVE mini-map lines at normal colour.
- Replace the player cross with a live rotating heading arrow on the full map and both mini-map layers, sized to one map tile.

## 1.4.2 — 2026-10-07

- Join terrain approaches at the highway point actually reached, without requiring a redundant trip to the initially selected entry.
- Preserve sampled highway prefixes and published map continuations when distant terrain is unavailable; do not attach disconnected target connectors.
- Fall back to distant terrain when a near-terrain tile lacks usable corners.
- Plan a surface continuation from the chosen received cave exit while keeping underground mini-map navigation separate.
- Keep the last built surface line while replacing effects or waiting for cave exit data, scoped to the exact server, waypoint and target coordinate.
- Preserve route layer metadata and exclude underground segments from surface-map lines.
- Add bounded, deduplicated highway-attempt diagnostics identifying entry failures, missing terrain and blocked assembled routes.

## 1.4.1 — 2026-10-06

- Fixed highway branches joining the second lane in the middle of a road, bridge or tunnel. Occupied bridge/tunnel access now uses the split graph, and nearby positions on the same span can change lanes without travelling to an endpoint.
- Removed closed excursions created when terrain legs overlap each other or the published highway route. Surface, bridge and cave positions remain distinct.
- Retained the usable highway route when its final terrain connector is incomplete, instead of discarding it and rebuilding the whole journey off road. Partial routes no longer claim to reach a target after the point budget truncates them.
- Underground navigation to a surface target now selects a reachable received cave exit, including exits behind the player, and considers the surface continuation. Cave navigation and the cave map share absolute tile receipt tracking so reused 64-tile ring slots cannot invent floors or exits.
- Full-map cross-layer NAV lines now join the actual approach and checked target connector; they no longer fill missing approaches or tails with unchecked straight lines.

## 1.3.2 — 2026-10-06

- Fixed received ore veins and unexcavated rock appearing as Unknown (not received) in the cave mini-map. Receipt is now tracked by absolute tile coordinates rather than the floor-height sentinel shared with unexcavated terrain.
- Cave coverage resets with the native buffer, rejects reused ring slots and leaves unfinished terrain updates unknown.
- Unexcavated rock hover reports that floor/ceiling are not formed; floor extras no longer replace the colour of solid ore walls.

## 1.3.1 — 2026-10-05

- Verified Waypointer's shared Chamomilo protocol-1 updater registration, release selection and single-host coordination.
- Release builds now require every compiled updater class, including nested workers and the Waypointer HUD bridge, and validate exact updater metadata in the source configuration and final ZIP.
- Fixed the update display name to avoid repeating Wurm in the notification.

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
