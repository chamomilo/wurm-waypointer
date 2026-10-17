WURM WAYPOINTER 1.10.11
Waypoints, navigation, maps and highlights for the Wurm Unlimited client.

Illustrated guide: https://github.com/chamomilo/wurm-waypointer
Downloads: https://github.com/chamomilo/wurm-waypointer/releases/latest

INSTALL OR UPDATE

Requires a working Wurm Unlimited client modloader.

1. Close Wurm. Back up mods/wurm-waypointer.config and wurm-waypointer-data.
2. Extract this ZIP into WurmLauncher, merging the mods directory.
   KEEP YOUR EXISTING .config when updating. The supplied file contains
   defaults for a new installation.
3. Replace mods/wurm-waypointer.properties. In mods/wurm-waypointer, remove
   older Waypointer JARs and keep wurm-waypointer-1.10.11.jar.
   Preserve saved data and other files.
4. Start the client and click the compass.

This ZIP contains only:
  mods/wurm-waypointer.properties
  mods/wurm-waypointer.config
  mods/wurm-waypointer/wurm-waypointer-1.10.11.jar
  mods/wurm-waypointer/README.txt

Artwork, compass, fonts, shared updater and license notices are in the JAR.
No separate resource pack or updater is required. Saved waypoints use
wurm-waypointer-data by default; map and deed caches have separate settings.

QUICK START

Click the compass: open the hub.
ADD: create a waypoint. ON/OFF: show or hide markers.
NAV: follow a target. STOP or /wp nav off: stop navigation.
COPY / SHARE / EXPORT / IMPORT / PASTE SHARED: share saved places.
M: full map. Drag to pan, scroll to zoom, CENTER to return to your character.
MINI MAP on the full map: open the character-centred surface/cave map.
Main menu > Mod updates > Waypointer > Settings: general preferences.

The eight tabs are Waypoints, Mobs, Containers, Objects / items, Vehicles,
My animals, Creatures Highlight and Tiles Highlight.

WAYPOINTS AND TRACKING

Filter waypoints by server, character, type or status. Click headings to sort.
Nearby tabs use entities received by the client. Refresh My animals or
Vehicles, then Track or Untrack. Animal direction searches require the game's
Manage/Find permissions. Tracked targets retain their last known position.

Text filters ignore case and accept comma-separated fragments: horse, wolf
matches either name. + filter includes matches; - filter excludes them.
Exclusions take priority. TRACK FILTERED applies to the complete result;
ADD TO MONITOR watches matching targets.

Version 1.10.11 improves large lists throughout Waypointer. Only visible rows
create interface controls; scrolling, search, sorting and bulk actions retain
all data. Native UI checks cover 2500 entries in all four supported languages.

MAPS AND ROUTES

The full map has separate deed, road, mark and navigation controls. Left-click
an empty place to create a waypoint; right-click to add a map mark.
On recognized Sklotopolis worlds, ALL MAPS ON SERVER browses Liberty, Novus,
Caza, Infinity Round 5 and Old Infinity, with published deeds and highways.
Downloads refresh in the background.

The minimap footer switches GROUND/CAVE, opens the full map and sets height
contours: 1-99 metres, or 0 to disable them. Scroll over the minimap to zoom.

Ground routes combine highways with received terrain observations. Slope and
water checks help choose a road approach; fences, buildings and permissions
still determine access. Unloaded terrain can require a fallback route.
Choose Pulse, Solid or Moving dashes in Settings.

HIGHLIGHTS

Creatures Highlight has independent outlines for players, animal groups,
monsters, Unique creatures and conditions. Counts show detected matches.
ALL ON / ALL OFF change outline switches. Color opens a picker: Apply saves;
Cancel or closing keeps the previous colour. Condition colours take priority
over Unique and category colours.

Unique, Champion and Diseased have independent Auto-mark switches. Each
matching creature receives one tracked waypoint even if its outline is off.
Auto-mark does not start navigation. Detection uses received creatures on the
surface or in caves.

Tiles Highlight has separate rules for non-rideable terrain, flowers,
harvestable crops/fruit and Overaged/Shrivelled trees. Matching rules retain
separate borders. The four terrain switches start OFF after every launch;
colours and radius persist. Non-rideable includes slopes above 40 dirt (4 m),
water deeper than 0.7 m, lava and cave openings. Hover help explains thresholds.
Tile highlight radius accepts 1-16, initially 7: a 15 x 15 square around you.
Change it during play in Waypointer Settings.

Meteor outlines, Auto-mark and Chime default ON for new installations and
respect saved choices. Detection uses loaded objects independently of terrain
radius. Each new meteorite is marked and sounded once per session, without
starting navigation.

SETTINGS, LANGUAGES AND UPDATES

Open Main menu > Mod updates for the shared Mods Registry. Choose Settings
on the Waypointer card. Navigation, maps, deed feeds, tracking, scanners,
creature rules, terrain, meteorites, storage and diagnostics are configured
there. Every setting has hover help. Valid edits save automatically and apply
during play where supported. Startup-only changes show a restart notice.
Continue configuring keeps Wurm open; Exit game opens its quit confirmation.
Changing a storage path does not move existing files.

The shared language selector supports English, Brazilian Portuguese, German
and Russian and applies immediately to participating mods.
Embedded Chamomilo updater: 1.5.0. UI SDK: 0.4.11.
Download and install updates manually with Wurm closed.

LOOT MAPS, ARCHAEOLOGY AND SCANNERS

Loot Maps follow the white rabbit from native readings. Archaeology tracks
report targets, remembers confirmed cache locations per server and supports
a completion chime. Scanners search animals, uniques or treasure with optional
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
Include the version, steps to reproduce, event time/timezone and client logs.
Performance logs: wurm-waypointer-data/waypointer-diagnostics-*.log.

GPL-3.0-only. Notices inside the JAR:
  META-INF/waypointer/LICENSE
  META-INF/waypointer/THIRD_PARTY_NOTICES.txt
Bundled fonts retain their SIL Open Font License.
Source: https://github.com/chamomilo/wurm-waypointer/tree/v1.10.11
