WURM WAYPOINTER 1.10.4
Waypoints, maps, navigation, tracking and highlights for Wurm Unlimited.

Download and illustrated guide:
https://github.com/chamomilo/wurm-waypointer
Latest release:
https://github.com/chamomilo/wurm-waypointer/releases/latest

INSTALLATION / UPDATE

A working Wurm Unlimited client modloader is required.

1. Close the game. If updating, back up mods/wurm-waypointer.config and
   wurm-waypointer-data.
2. Extract this ZIP into WurmLauncher, merging its mods folder.
   Preserve your existing .config when upgrading. The included configuration
   supplies defaults for a new installation; the mod adds missing new options.
3. Keep only wurm-waypointer-1.10.4.jar in mods/wurm-waypointer.
   Use the new mods/wurm-waypointer.properties, which points to that JAR.
4. Start the game and open Waypointer using its compass control.

This ZIP contains exactly:
mods/wurm-waypointer.properties
mods/wurm-waypointer.config
mods/wurm-waypointer/README.txt
mods/wurm-waypointer/wurm-waypointer-1.10.4.jar

All runtime artwork, the shared updater and the interface SDK are embedded in
the JAR. Waypoints and cached map data live in wurm-waypointer-data; preserve
that folder when upgrading.

SETTINGS AND LANGUAGE

Open Main menu > Mod updates > Waypointer > Settings in the shared Mods Registry.
General Settings are available only in the shared updater Mods Registry.
It contains navigation, storage, creature rules, tile colours, highlight radius
and meteorite options. Every parameter has hover help. Valid edits save
automatically and apply immediately where supported. Startup-only edits offer
the game's normal quit confirmation.

Select English, Brazilian Portuguese, German or Russian in the updater.
Language changes apply immediately to participating mods.
Embedded updater: 1.5.0. Interface SDK: Chamomilo UI 0.4.11.
Updates open the release download; installation remains manual.

WAYPOINTER HUB

Eight tabs: Waypoints, Mobs, Containers, Objects / items, Vehicles, My animals,
Creatures Highlight and Tiles Highlight.

Add, edit, filter, share, import and export waypoints. ON enables a target;
NAV starts navigation. /wp nav off stops it. Nearby lists use received entities.
Refresh My animals or Vehicles to obtain native catalogues, then Track/Untrack.
Animal direction searches need the game's Manage/Find permissions. Last known
positions remain available after a living target leaves view.

Text filters accept comma-separated fragments, ignoring case. The + filter
includes matches and the - filter excludes them; exclusions take precedence.

CREATURES HIGHLIGHT

Traits are on the left; categories and Small/Mini/Tiny are on the right.
Players, non-aggressive farm animals, aggressive wild animals and monsters have
separate rules. Names show currently detected counts. ON is green. ALL ON and
ALL OFF change outline switches. Color opens a popup picker; Apply saves, while
Cancel or closing discards the draft. Initial colours follow the familiar ESP
palette; Scared is warm yellow to distinguish it from blue tower guards.

Unique, Champion and Diseased can Auto-mark one tracked target per detected
creature. Auto-mark works independently of the outline switch and never starts
navigation automatically. Modifier colours override Unique, then categories.

TILES HIGHLIGHT

Independent terrain rules:
- Non-rideable tiles: steep terrain, deep water, lava and cave openings; red.
- Flower tiles: native flower colours.
- Harvestable areas: ripe field crops and harvestable mature trees/bushes.
- Overaged trees: Overaged and Shrivelled trees, independently of harvestability.

All four terrain switches start OFF after every client launch. Enable them
manually when needed. Colours and radius are retained. Matching enabled rules
keep separate coloured borders on the same tile, slightly above the ground.

Terrain highlight radius defaults to 7 tiles along both axes, including diagonal
corners: a 15 x 15 square. Change it in updater Settings, from 1 to 16. Changes
apply immediately. Only received surface terrain is used.

Non-rideable thresholds: corner height difference greater than 40 dirt (4 m),
water deeper than 0.7 m above the lowest corner, cave openings or lava. Exactly
40 dirt and 0.7 m are allowed. Buildings, fences and permissions are not checked.
Harvestable crops are native ripe stages 5/6. Normal trees/bushes need a received
fruit flag and an age from Mature through Very old, including sprouting.
Young, Overaged and Shrivelled trees do not match Harvestable. Hover help
explains these thresholds in Settings.

Meteors detects landed meteorites in the loaded area. Outlines, Auto-mark and
Chime default ON for a new installation; explicit saved preferences are kept.
Targets and sounds are deduplicated per detected meteorite. Terrain radius does
not limit meteorite object detection. Auto-mark does not start navigation.

MAPS AND SEARCHES

Press M for the full map. Pan/zoom; CENTER returns to the player. DEEDS, ROADS,
MARKS and NAV LINE control layers. Left-click adds a waypoint; right-click adds
a custom mark. On recognized Sklotopolis servers, ALL MAPS ON SERVER browses
Liberty, Novus, Caza, Infinity Round 5 and Old Infinity.

MINI MAP opens a character-centred surface/cave view. Scroll to zoom. Its footer
switches GROUND/CAVE, opens the full map and controls height contours:
1-99 metres, or 0 to disable. Unknown terrain stays unknown.

Loot Maps follow the white rabbit from native readings. Archaeology tracks
searches and can play a completion chime. Scanner profiles search animals,
uniques and treasure with optional notifications and model outlines.

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

FEEDBACK

https://github.com/chamomilo/wurm-waypointer/issues
Include the version, time/timezone, enabled highlights, radius and client logs
when reporting rendering problems. Bounded diagnostics are stored in
wurm-waypointer-data/waypointer-diagnostics-*.log. Live game GPU behaviour needs
manual verification after installation.

LICENSE

GNU GPL version 3 only. License and third-party notices are embedded in the JAR:
META-INF/waypointer/LICENSE
META-INF/waypointer/THIRD_PARTY_NOTICES.txt
Source and corresponding license:
https://github.com/chamomilo/wurm-waypointer/tree/v1.10.4

Alegreya Sans / Alegreya Sans SC fonts retain their SIL Open Font License in
org/chamomilo/wurm/ui/v1/fonts/ inside the JAR. Client/modloader libraries are
supplied by the user's installation and are not bundled.
