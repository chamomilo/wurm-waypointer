# Changelog

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
