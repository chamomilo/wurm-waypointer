WURM WAYPOINTER 1.10.9
Waypoints, navigation, maps and highlights for the Wurm Unlimited client.

Illustrated guide: https://github.com/chamomilo/wurm-waypointer
Downloads: https://github.com/chamomilo/wurm-waypointer/releases/latest

INSTALL OR UPDATE

A working Wurm Unlimited client modloader is required.

1. Close the client. When updating, back up mods/wurm-waypointer.config and
   wurm-waypointer-data.
2. Extract this ZIP into WurmLauncher, merging the mods directory.
   Keep your existing .config when updating. The supplied configuration is
   a default for new installations.
3. Replace mods/wurm-waypointer.properties. Keep only
   wurm-waypointer-1.10.9.jar in mods/wurm-waypointer; remove older Waypointer
   JARs while preserving other files and saved data.
4. Start Wurm and click the compass to open the hub.

The ZIP contains exactly four files:
mods/wurm-waypointer.properties
mods/wurm-waypointer.config
mods/wurm-waypointer/README.txt
mods/wurm-waypointer/wurm-waypointer-1.10.9.jar

Artwork, compass layers, fonts, updater and license notices are in the JAR.
No separate image pack or updater installation is needed. Waypoints are stored
in wurm-waypointer-data by default. Map and deed caches have separate paths
configured in Settings.

QUICK START

Click the compass: open Waypointer.
ADD: create a waypoint. ON/OFF: show or hide its markers.
NAV: follow a target. STOP or /wp nav off: stop navigation.
M: full map. Drag to pan, scroll to zoom, CENTER to return to your character.
MINI MAP: open the character-centred surface/cave map.
Main menu > Mod updates > Waypointer > Settings: general preferences.

The eight tabs are Waypoints, Mobs, Containers, Objects / items, Vehicles,
My animals, Creatures Highlight and Tiles Highlight.

SETTINGS, LANGUAGE AND UPDATES

Waypointer settings are in the shared Mods Registry, opened through
Main menu > Mod updates. Choose Settings on the Waypointer card.

Navigation, map/deed feeds, tracking, scanners, creature rules, terrain radius,
colours, meteorite actions, storage and diagnostics are configured there.
Every setting has hover help. Valid changes save automatically and apply
during play where supported. Restart changes display an explanation and offer
Wurm's normal quit confirmation.

The registry's language selector supports English, Brazilian Portuguese,
German and Russian. It applies immediately to participating mods.
Embedded updater: 1.5.0. Chamomilo UI: 0.4.11.
Install downloaded updates manually with Wurm closed.

WAYPOINTS AND TRACKING

Add, edit, sort, share, import and export waypoints. Nearby tabs show entities
received by the client. Refresh My animals or Vehicles, then Track or Untrack.
Animal direction searches require the game's Manage/Find permissions.
Tracked targets retain their last known position after leaving view.

Text filters accept comma-separated fragments and ignore case. For example,
horse, wolf matches either name. The + filter includes matches; the - filter
excludes them. Exclusions take priority.

MAPS AND NAVIGATION

The full map has independent deed, highway, waypoint and navigation layers.
Left-click an empty place to add a waypoint; right-click to add a map mark.
On recognized Sklotopolis worlds, ALL MAPS ON SERVER browses Liberty, Novus,
Caza, Infinity Round 5 and Old Infinity, including published deed and roads.

The minimap footer switches GROUND/CAVE, opens the full map and sets height
contours: 1-99 metres, or 0 to disable them. Scroll over the minimap to zoom.

Ground routes combine highways with received terrain observations. Slope and
water checks help choose a road approach; fences, buildings and permissions
can still affect access. Unloaded terrain can require a fallback route.
Select Pulse, Solid or Moving dashes in Settings.

Version 1.10.9 keeps the frame and controls visible on dense map overviews and
lets Highway approaches join a later reachable section to avoid detours.

HIGHLIGHTS

Creatures Highlight controls outlines for players, animals, monsters, Unique
creatures and creature conditions. Labels show detected counts. ALL ON and
ALL OFF change outline switches. Color opens a picker: Apply saves; Cancel
or closing keeps the previous colour. Condition colours override Unique and
category colours.

Unique, Champion and Diseased support Auto-mark. A matching creature creates
one tracked waypoint even if its outline is disabled. Auto-mark does not
start navigation. Detection uses received creatures on the surface or in caves.

Tiles Highlight has independent rules for non-rideable terrain, flowers,
harvestable crops/fruit and Overaged/Shrivelled trees. Matching rules retain
separate coloured borders. These four terrain switches start OFF after each
client launch; colours and radius are retained.

The radius defaults to 7 tiles along each axis: a 15 x 15 square. Set Tile
highlight radius to 1-16 in the updater Settings; it applies during play.
Non-rideable terrain includes slopes above 40 dirt (4 m), water deeper than
0.7 m, lava and cave openings. Hover help explains harvest stages and ages.

Meteor outlines, Auto-mark and Chime default ON for new installations; saved
choices are respected. Detection uses loaded objects independently of the
terrain radius. Each newly detected meteorite is marked and sounded once per
session, without starting navigation.

LOOT MAPS, ARCHAEOLOGY AND SCANNERS

Loot Maps follow the white rabbit from native readings. Archaeology tracks
report targets, remembers confirmed cache locations per server and supports a
completion chime. Scanners search animals, uniques or treasure with optional
notifications and bounded outlines.

Useful commands:
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
/wp perf

SUPPORT AND LICENSE

Issues: https://github.com/chamomilo/wurm-waypointer/issues
Include the version, event time/timezone, relevant settings and client logs.
Performance logs: wurm-waypointer-data/waypointer-diagnostics-*.log.

GPL-3.0-only. Notices inside the JAR:
META-INF/waypointer/LICENSE
META-INF/waypointer/THIRD_PARTY_NOTICES.txt
Source: https://github.com/chamomilo/wurm-waypointer/tree/v1.10.9
Bundled fonts retain their SIL Open Font License in the JAR.
