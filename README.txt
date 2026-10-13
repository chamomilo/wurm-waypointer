WURM WAYPOINTER 1.10.6
Waypoints, maps, tracking and highlights for the Wurm Unlimited client.

Illustrated guide: https://github.com/chamomilo/wurm-waypointer
Downloads: https://github.com/chamomilo/wurm-waypointer/releases/latest

INSTALL OR UPDATE

A working Wurm Unlimited client modloader is required.

1. Close the game. If upgrading, back up mods/wurm-waypointer.config and
   wurm-waypointer-data.
2. Extract the ZIP into WurmLauncher, merging its mods folder.
   Keep your existing .config when upgrading. The supplied configuration
   contains defaults for a new installation.
3. Use the new mods/wurm-waypointer.properties. Keep only
   wurm-waypointer-1.10.6.jar in mods/wurm-waypointer.
4. Start the client and click the compass to open Waypointer.

The ZIP contains four files:
mods/wurm-waypointer.properties
mods/wurm-waypointer.config
mods/wurm-waypointer/README.txt
mods/wurm-waypointer/wurm-waypointer-1.10.6.jar

Compass layers, map artwork, the updater, interface SDK and license notices
are embedded in the JAR. No loose or extracted compass images are needed.
Preserve wurm-waypointer-data: it contains saved waypoints and cached maps.

SETTINGS, LANGUAGE AND UPDATES

Open Main menu > Mod updates > Waypointer > Settings in the Mods Registry.
Navigation, storage, creature rules, tile colours, highlight radius and
meteorite options are configured there. Every parameter has hover help.
Valid edits save automatically and apply during play where supported.
Startup-only changes offer Wurm's normal quit confirmation.

The updater's shared language selector supports English, Brazilian Portuguese,
German and Russian. Language changes apply immediately to participating mods.
Embedded updater: 1.5.0. Chamomilo UI: 0.4.11.
Updates open the release download; install them manually with the game closed.

WAYPOINTS AND TRACKING

Eight tabs: Waypoints, Mobs, Containers, Objects / items, Vehicles, My animals,
Creatures Highlight and Tiles Highlight.

Add, edit, share, import and export waypoints. ON shows a target's markers;
NAV starts navigation. Use /wp nav off to stop. Nearby lists show received
entities. Refresh My animals or Vehicles, then Track or Untrack a target.
Animal direction searches require the game's Manage/Find permissions.
Tracked targets retain their last known position when they leave view.

Text filters accept comma-separated fragments and ignore case. The + filter
includes matches; the - filter excludes them. Exclusions take priority.

HIGHLIGHTS

Creatures Highlight controls outlines for players, animal categories,
monsters, Unique creatures and creature conditions. Labels show detected
counts. Color opens a picker; Apply saves, while Cancel keeps the old colour.
ALL ON and ALL OFF affect outlines. Unique, Champion and Diseased can
Auto-mark one waypoint per matching creature without starting navigation.

Tiles Highlight has independent rules for non-rideable terrain, flowers,
harvestable crops/fruit and Overaged/Shrivelled trees. Matching rules retain
separate coloured borders. These four terrain switches start OFF after every
client launch; colours and radius are retained.

Tile highlight radius defaults to 7 tiles along each axis: a 15 x 15 square.
Change it in the updater Settings, from 1 to 16. It applies during play.
Non-rideable terrain includes slopes above 40 dirt (4 m), water deeper than
0.7 m, lava and cave openings. Fences, buildings and permissions also affect
actual riding. Hover help explains harvest stages, tree ages and thresholds.

Meteors detects landed meteorites in the loaded area. Outlines, Auto-mark and
Chime default ON for new installations; saved choices are respected. The
terrain radius does not limit meteor detection. Each detected meteorite is
marked and sounded once per session, without starting navigation.

MAPS AND SEARCHES

Press M for the full map. Pan/zoom; CENTER returns to your character. Switch
deeds, roads, marks and navigation layers independently. Left-click adds a
waypoint; right-click adds a map mark.

MINI MAP opens a surface/cave view. Scroll to zoom. The footer switches
GROUND/CAVE, opens the full map and controls height contours: 1-99 metres,
or 0 to turn them off. Maps and live positions use received client data.

On recognized Sklotopolis servers, ALL MAPS ON SERVER browses Liberty, Novus,
Caza, Infinity Round 5 and Old Infinity, with published deed and road layers.
Loot Maps follow the white rabbit from native readings. Archaeology tracks
search targets and supports a completion chime. Scanners search animals,
uniques and treasure with optional notifications and outlines.

USEFUL COMMANDS

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
/wp deeds status
/wp perf

SUPPORT AND LICENSE

Issues: https://github.com/chamomilo/wurm-waypointer/issues
Include the version, time/timezone, enabled highlights, radius and client logs.
Mod diagnostics: wurm-waypointer-data/waypointer-diagnostics-*.log.

GPL-3.0-only. License and third-party notices are embedded in the JAR:
META-INF/waypointer/LICENSE
META-INF/waypointer/THIRD_PARTY_NOTICES.txt
Source: https://github.com/chamomilo/wurm-waypointer/tree/v1.10.6
Bundled fonts retain their SIL Open Font License in the JAR.
