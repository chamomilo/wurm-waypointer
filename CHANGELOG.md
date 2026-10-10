# Changelog

## 1.6.17 — 2026-10-10

- Integrate the canonical shared updater 1.1.0 and reviewed UI SDK 0.4.4, with exact module/hash verification in the mod JAR and installable ZIP.
- Participate in the updater's Force language selection for English, Brazilian Portuguese, German and Russian. Save through Waypointer's existing settings workflow and apply on the HUD tick; unsupported languages retain the local choice.
- Update installation, current controls, updater behavior, build prerequisites and render-diagnostics documentation. Remove unused old SDK JARs and temporary compiled probes; retain investigation evidence locally outside the published source tree.

## 1.6.16 — 2026-10-10

- Preserve one published surface texture and its terrain index across HUD replacement, disconnect and reconnect when the profile, file and revision match. Reuse the fixed gallery assets; replace the current surface on a new identity.
- Finish native texture preparation on the background worker, consuming completed loader requests even when replaced. Skip superseded surface/index jobs before decoding and publish texture references safely. HUD drawing no longer acquires prepared map textures through the native loader.
- Flush dynamic map uploads only at the HUD queue boundary, avoiding repeated checks and texture work during world/reflection queues.
- Measure native legacy/modern queue rendering and LwjglClient window/frame output separately. Persist coalesced slow samples of at least 250 ms with event timestamps, plus long GC start/end times and periodic heap/GC counters. Logging stays on the diagnostics worker.
- Embed and verify the current canonical updater 1.1.0 / UI SDK 0.4.4. The observed socket disconnects and remaining visual stalls are not claimed resolved by these changes.

## 1.6.15 — 2026-10-10

- Keep headings outside the scroll content in ALL WAYPOINTS, all Around views and Monitoring. Preserve column alignment, sorting, refresh offsets and the visible waypoint after deletion; verify the existing fixed headings in managed and friend tables too.
- Show only complete Monitoring rows by fitting the viewport and scroll offsets to the row height. Reserve at least 8 px above Back to Waypointer, Refresh and the count; keep the footer clear after resizing and scrollbar dragging.
- Highlight Monitoring's active Clear action in green, matching marked rows elsewhere. Retain the highlight across refreshes and remove it with waypoint membership.
- Move the square ADD TO MONITOR action beside the table heading, after the body scrollbar, with 24 px spacing. Keep it fixed during scrolling and free the full top area for filters.
- Add an All / Online / Offline filter to MY FRIENDS. Use typed native presence rather than names, notes or translated status strings; combine it with both text filters and retain it during refreshes and section switches. Unknown presence appears in All.
- Coordinate FULL MAP, GROUND/CAVE and topographic digits in the mini-map footer through one compact Alegreya Sans SC typography group. Reserve both regular and bold captions, center their shared baseline and show digits at least half as tall as the 20 px field without touching either rail.
- Move dynamic map texture uploads to the GL queue boundary and retirement to completed frames. Coalesce pending snapshots and reuse existing storage with sub-image uploads; allocate again only when dimensions or alpha change. Cover both legacy and modern render queues.
- Rasterize topographic contours on one background worker with at most one pending job per mini-map, a 500 ms sampling interval and a padded world-anchored image. Translate cached pixels during movement, reuse unchanged heights and discard work from closed windows or old layers/profiles.
- Reject off-screen highway segments before allocating projection points and clipping arrays in both maps.
- Extend `/wp perf` with map, contour and texture-upload timings and the HUD queue maximum. Preserve warnings and periodic counters across restarts in three bounded `wurm-waypointer-data/waypointer-diagnostics-*.log` files. Live GPU recovery remains to be verified in-game.

## 1.6.14 — 2026-10-10

- Keep padded table actions and their spacer children in the same native array layout, so scrolling translates every descendant. Fix clipped or missing Mark/actions in the Around views and Monitoring while retaining 28 px buttons and 4 px gaps.
- Apply the bundled Alegreya Sans body font to table contents, labels, editable fields and all option lists. Use the shared Alegreya Sans SC roles for titles, compact controls and map/HUD captions. Preserve names, input case, native editing and the existing action typography groups.
- Centre label text using its actual font metrics; use fitted Monitoring table cells with complete hover text. Audit the actual painted font families in all four language previews and test scrolling in both directions, fractional-row offsets, the bottom limit, refresh and live catalogue updates.
- Embed and verify the complete current canonical shared updater, recording its version and exact hash separately from the mod version.

## 1.6.13 — 2026-10-10

- Fit table action buttons to 28 px inside existing 32 px rows, leaving 2 px above and below each button. Preserve text, column alignment, scrolling and native hit testing in ALL WAYPOINTS, Around views, managed targets, friends and Monitoring.
- Rename Monitoring's return action to Back to Waypointer. Coordinate it with Refresh and the count label at one shared Alegreya Sans SC size and baseline; use the matching Bold font in the title. Reserve both Mark/Clear states and add the same vertical gaps to this table.
- Sample the received surface terrain in the 11×11 tile neighbourhood around the player once per second. Both maps draw shared, nearest-filtered local patches over the published server image, so felling, planting, farming, paving and terrain changes appear after receipt without waiting for the next map download.
- Retain recent observations during the current world session in a bounded 64-chunk cache. Reuse unchanged pixels/textures; evict old patches and clear them on disconnect, transfer or HUD replacement. Unknown cells remain transparent, and cached hover descriptions identify last observations.
- Verify the embedded UI against the updater's exact pinned SDK JAR, including entry bytes, rather than requiring unrelated classes from a newer SDK version.

## 1.6.12 — 2026-10-10

- Show complete filter option lists without inner scrollbars. Surroundings checkbox filters expand to full height and split into columns on shorter screens; standard Waypointer dropdowns use full option grids with native popup dismissal and fixed alpha.
- Align every surroundings header with its body column, including the shared Mark/Unmark width. Resize fixed SDK buttons through their explicit API so the header cannot remain narrower than the rows.
- Apply the updated Chamomilo table-header rule in ALL WAYPOINTS, every Around table, managed targets, friends and Monitoring: 18 px Alegreya Sans SC Bold, shared baseline, one 32 px band with only an outer frame, reserved code-native chevrons and fixed alpha. No heading band has internal vertical dividers.
- Remove the complete Short name filter row from CONTAINERS AROUND and OBJECTS AND ITEMS AROUND, including its unused UI state.

## 1.6.11 — 2026-10-10

- Read native BML backslashes literally, including the default ascending Name /\ header. Managed animal, cart/wagon and ship replies now populate the Waypointer catalogue instead of opening a native list and timing out.
- Request the catalogue on first opening MY VEHICLES AND ANIMALS; Refresh requests it again. MY FRIENDS also refreshes on first selection. Switching back preserves the list without repeating requests.
- Replace managed and friend cards with compact column tables. Freeze branded Bold headings above scrolling rows; sort the full filtered list ascending, descending and original order with reserved chevron indicators. Preserve row identity and scroll position on background updates.
- Avoid the native auto-width layout's transient zero-height content, which clamps scroll offsets. Use the SDK scrollbar for scroll restoration and verify insertion/removal above the viewport with 80 records.
- Restore the Refresh caption with explicit Chamomilo group metrics and a standard toolbar. Coordinate Track/Untrack, Nav/Stop, Direction and Clear last seen at one font, baseline and height, reserving both states.
- Verify native parser compatibility, all three owned Manage replies, first-selection requests and actual caption painting across four languages. Embed and verify canonical updater 1.0.2 with its reviewed UI 0.3.4 as the sole SDK copy.

## 1.6.10 — 2026-10-09

- Fix editor focus promoting the ALL WAYPOINTS panel into the HUD separately from its hub; the content can no longer survive the window after routing and closing.
- Detach all owned HUD components and dropdowns, dispose filter windows, restore keyboard focus, and clean up cached panels promoted by the old code. Panel cleanup failures cannot prevent window removal; unrelated windows and navigation stay active.
- Verify native HUD registration, editor typing, route/close, compass reopen, legacy orphan cleanup, filter disposal and chat focus.
- Replace owned updater sources with the complete canonical 1.0.1 module and enforce version, hash, entry names and byte equality in the JAR and installable ZIP.

## 1.6.9 — 2026-10-09

- Group Add and Refresh above ALL WAYPOINTS; use stable green On/NAV highlights, semantic hints, Visible now status and immediate UUID deletion.
- Remove waypoint membership on Unmark, Untrack and Clear all marks, including old static Surroundings marks. Preserve received history without re-adding removed rows on movement. Reuse Manage ship UUIDs when marking nearby ships.
- Harmonize mobs, containers and objects/items: inclusion/exclusion headings above editor rows, grouped compact controls and footer, count below filters, and interactive three-state sorting on every column. Remove sort dropdown/Apply and the unavailable Traits column.
- Use separate clickable check squares in fixed 32 px filter-choice rows, with shared caption metrics and fixed opacity. Center the uppercase three-row ADD TO MONITOR action with Low density spacing.
- Verify all column sorts, kind-specific counts, movement/removal/reload, native checkbox clicks, group metrics, scroll retention and opacity across four languages.

## 1.6.8 — 2026-10-09

- Coordinate the complete surroundings filter row, including Apply, as one typography group with stable shared size/baseline across selection states. Bound dynamic caption overflow with ellipsis and full selection hints.
- Give ALL WAYPOINTS and surroundings footer actions separate groups; coordinate the settings footer as well.
- Use equal Low density navigation selectors with uppercase captions per the user's explicit casing override, preserving shared regular/bold metrics and harmonious margins.
- Record and package the consumer typography plan; keep ADD TO MONITOR an independently fitted 144 px square primary action.

## 1.6.7 — 2026-10-09

- Reduce the blank title area above ALL WAYPOINTS. Keep all left selectors equal in size at 56 px height with Low density and shared 24 px captions.
- Coordinate all manager captions as one explicit typography group, including known state variants, with shared size/baseline, individual widths and exactly 32 px heights. Separate rows, columns, filters and footer actions with 6 px gaps. Fit table text to its column while retaining full tooltips.
- Treat ADD TO MONITOR as a separate 144 px square singleton with a harmonious three-row Low density caption.
- Reconcile live table cells without recreating their native controls or reordering distance-sorted rows in background updates. Preserve the visible UUID and intra-row scroll offset across deletion and explicit refreshes.
- Keep dropdown drafts separate from applied filters; Refresh retains those filters. Correct labels for unknown positions and approximate bearings instead of calling them Live.
- Pin Chamomilo UI 0.3.2, including fixed alpha for fields and detached popup contents. Independently verify field/popup opacity and native dismissal.
- Verify immediate UUID removal with 70 identically named cached animals, including movement after deletion. Report the removed UUID and catalogue retention in the client event.

## 1.6.6 — 2026-10-08

- Keep the complete Waypointer window opaque through HUD focus/fade transitions, as in Keybinder. Reproduce the 1.6.5 opacity regression and verify unchanged native primitive and caption alpha across multiple HUD transition values.
- Pin Chamomilo UI 0.3.0 and its bundled Alegreya Sans SC regular/bold fonts. Use High density for table actions, filters, footers and map toolbars; Low density for mode selectors, ADD / TO / MONITOR, settings actions and confirmation dialogs. Fit captions by visible ink bounds with press clearance and stable bold hover geometry.

## 1.6.5 — 2026-10-08

- Correct the native Chamomilo atlas UV transform with pinned kit 0.2.4. Resized buttons, window controls, map frames and title plates sample their own source crop instead of neighboring borders and caps.
- Submit UI quads with explicit alpha blending, no world depth writes, lighting or fog; clear pooled shader/texture state. Verify actual native primitive submission instead of replacing the SDK canvas in previews.
- Restore the custom compass dial and rotating needle, with both embedded and file-backed assets in the installable ZIP.
- Double the native caption font on the left navigation buttons and square monitor action. Center ADD / TO / MONITOR on three rows; size controls for both regular and bold hover captions.

## 1.6.4 — 2026-10-08

- Restore nearby animals in MOBS AROUND. Read the client's private attitude using the reflection access helper; unavailable hostility now becomes Unknown without dropping the creature. Reproduce the 1.6.3 failure with a real client renderable and verify entry into the runtime catalog.
- Use pinned Chamomilo Interface Kit 0.2.2 throughout Waypointer: windows, buttons, dropdowns, text fields, scrollbars, map frames/nameplates and editor chrome. Retain native input, map gestures and shared updater protocol 1.
- Remove former updater and map interface textures, logo and generated compass overlay from the JAR and ZIP. Keep map content and waypoint markers; the compass uses its native live dial. Reject legacy interface assets during packaging.
- Fit the mini-map footer within its 22 px lower frame using 20 px controls and native compact fonts. Size buttons for short localized captions, including ВСЯ КАРТА / ПОВЕРХ / ПОДЗЕМ and MAPA / SOLO / CAVERNA; retain the 300 px square without clipped labels.

## 1.6.3 — 2026-10-08

- Remove horizontal scrollbars and enforce a native window minimum that fits all hub sections, translated controls and the current font. Fit the waypoint table inside its vertical-scroll viewport.
- Give MOBS AROUND its own columns: Mark, Name, Condition, Traits, Hostility, Unique, Deed and Distance. Remove Category, Material, Rarity and Position / ID; rename the modifier filter to Condition. Use the client's received attitude for Hostility and explicit received trait descriptions for Traits, showing Unknown when breeding traits are unavailable.
- Arrange surroundings footer actions on one line with equal gaps. Move the square ADD TO MONITOR action to the upper right and open Monitoring using the current filter. Remove Watch filter, Clear watches and the preset accumulator.
- Embed Chamomilo UI kit 0.2.1 for the revised surroundings buttons and vertical scrollbar. Retain stable live-table scrolling and the shared update coordinator.

## 1.6.2 — 2026-10-08

- Keep automatic friend and Manage catalogue candidates out of ALL WAYPOINTS and navigation snapshots until the player explicitly presses Track or adds a nearby object as a waypoint.
- Persist waypoint membership separately from On/Off, so explicitly added but disabled targets remain in the manager after restart. Existing enabled tracked targets stay added; old automatic disabled candidates disappear from ALL WAYPOINTS.
- Allow Delete for tracked targets in ALL WAYPOINTS after confirmation. Remove their membership and disable tracking while retaining the catalogue/history; refreshes and movement cannot add them back without another explicit Track.
- Cover catalogue discovery, explicit addition, disabling, removal, refresh, movement, restart, legacy cache migration, production runtime snapshots and native Delete controls in all four languages.

## 1.6.1 — 2026-10-08

- Fix a StackOverflowError when switching from a surroundings view back to Waypoints. Content panels now terminate native input-focus queries; surroundings views expose their own search field instead of delegating back to the hub.
- Remove the + filter and - filter text rows from ALL WAYPOINTS.
- Reproduce the focus recursion with the pinned native client before the fix, then verify focus queries and native selector clicks for all 49 section pairs in each of the four languages.

## 1.6.0 — 2026-10-08

- Refactor the main UI into one hub and reusable feature panels, with seven left selectors and a final SETTINGS view. Preserve section filters, editor cleanup and native input/wheel routing.
- Use the shared approved Chamomilo buttons and five-pixel frame; keep the updater protocol compatible while moving the reusable skin/resource adapter into render-wurm.
- Add bounded vanilla Manage catalogue parsing for animals, carts/wagons and ships. Serialize explicit requests, correlate the HUD and expected form, refresh question IDs before animal direction queries, and leave unknown, manual, expired or failed forms available to the native client.
- Track received moving objects and friends; distinguish visible, last known, unknown and approximate positions. Persist last coordinates by account and endpoint, with configurable retention and guarded storage.
- Display animal bearings as intersections of recent distance/direction sectors without inventing exact coordinates.
- Centralize global settings with full-draft validation, background atomic saves, backup and comment preservation. Make English, Brazilian Portuguese, German and Russian available through SETTINGS.
- Give searchable lists identical plus/minus filter rows with comma-separated, case-insensitive literal substring alternatives and exclusion priority.
- Verify tracking persistence/isolation, filters, BML schemas and request ownership, configuration round-trips, seven production views, four languages, fonts and final packaging.

## 1.5.4 — 2026-10-08

- Restore the complete original updater button artwork with proportional end caps and its real upper and lower iron rails. Stretch only the middle horizontally, with a centered label and at least 28 px height.
- Extract a reusable ChamomiloSkinnedButton for future controls; preserve native input and normal, hover, pressed and disabled states.
- Move Mods Registry by Chamomilo into the dark header below the outer frame, reserving space above the cards at every font size.
- Verify the original rails, unchanged corners at different widths, label clearance, header spacing, menu and startup preference, and include the reusable button in artifact checks.

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
