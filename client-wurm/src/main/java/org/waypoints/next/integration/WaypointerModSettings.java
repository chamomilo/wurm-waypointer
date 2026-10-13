package org.waypoints.next.integration;

import org.chamomilo.wurm.update.*;
import org.waypoints.next.ui.SettingSpec;
import org.waypoints.next.surroundings.*;
import java.util.*;

/** The registry and the hub share one authoritative settings snapshot. */
final class WaypointerModSettings implements SharedModSettings.Provider {
    private static final String RADIUS_HELP="Radius in tiles along both axes, 1..16; default 7 gives a 15 x 15 square (225 tiles, 28 m in each direction). Diagonal corners are included. Uses only received surface terrain; changing the radius applies immediately and does not affect meteorite object detection.";
    private static final String RIDEABLE_HELP="Non-rideable if the highest and lowest corners differ by more than 40 dirt (4 m), or water is more than 0.7 m above the lowest corner. Exactly 40 dirt and 0.7 m remain allowed. Cave openings and lava always match. Terrain indication only; fences, buildings and server permissions are not checked.";
    private static final String HARVEST_HELP="Ripe crop stages 5 and 6 only. Normal trees and bushes require the received fruit flag and ages Mature through Very old (including sprouting). Young, Overaged and Shrivelled trees do not match. Updates after received harvest or growth changes; independent of the old-tree switch.";
    private final HubSettings controller;
    private final List<ModSetting> specs = new ArrayList<ModSetting>();
    WaypointerModSettings(HubSettings controller) {
        this.controller=controller;
        for (SettingSpec spec : SettingSpec.ALL) {
            if (spec.key.equals("language")) continue;
            specs.add(new ModSetting(spec.group,spec.key,spec.label,help(spec),
                ModSetting.Type.valueOf(spec.type.name()),spec.defaultValue,spec.minimum,spec.maximum,
                !spec.group.equals("Data storage"),spec.choices));
        }
        for (CreatureHighlightRule rule : CreatureHighlightRule.values()) {
            specs.add(ModSetting.bool("Creature highlights",rule.key("enabled"),rule.label()+" outline",
                "Enable the independent outline rule for " + rule.label() + ". Modifier rules override Unique, then category rules.",rule.defaultEnabled(),true));
            specs.add(ModSetting.color("Creature highlights",rule.key("color"),rule.label()+" color",
                "Outline color for " + rule.label() + ". Enter #RRGGBB.",rule.defaultColor()));
            if (rule.supportsAutoMark()) specs.add(ModSetting.bool("Creature highlights",rule.key("autoMark"),rule.label()+" auto mark",
                "Automatically create a tracked waypoint when " + rule.label() + " is detected. Independent of the outline switch.",false,true));
        }
        specs.add(ModSetting.number("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.RADIUS,"Tile highlight radius",RADIUS_HELP,
            org.waypoints.next.tiles.TileHighlightSettings.DEFAULT_RADIUS,org.waypoints.next.tiles.TileHighlightSettings.MINIMUM_RADIUS,org.waypoints.next.tiles.TileHighlightSettings.MAXIMUM_RADIUS,true,true));
        specs.add(ModSetting.bool("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.NON_RIDEABLE,"Non-rideable tiles",RIDEABLE_HELP,false,true));
        specs.add(ModSetting.color("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.COLOR,"Non-rideable tile color",
            "Color of non-rideable tile outlines. Enter #RRGGBB.",org.waypoints.next.tiles.TileHighlightSettings.DEFAULT_COLOR));
        specs.add(ModSetting.bool("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.FLOWERS,"Flower tiles",
            "Highlight received flower tiles using their native flower colors.",false,true));
        specs.add(ModSetting.bool("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.HARVESTABLE,"Harvestable areas",HARVEST_HELP,false,true));
        specs.add(ModSetting.color("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.HARVESTABLE_COLOR,"Harvestable area color",
            "Color of harvestable area outlines. Enter #RRGGBB.",org.waypoints.next.tiles.TileHighlightSettings.DEFAULT_HARVESTABLE_COLOR));
        specs.add(ModSetting.bool("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.OVERAGED,"Overaged trees",
            "Highlight received trees aged Overaged or Shrivelled, independently of harvestability.",false,true));
        specs.add(ModSetting.color("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.OVERAGED_COLOR,"Overaged tree color",
            "Color of Overaged and Shrivelled tree outlines. Enter #RRGGBB.",org.waypoints.next.tiles.TileHighlightSettings.DEFAULT_OVERAGED_COLOR));
        specs.add(ModSetting.bool("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.METEORS,"Meteors",
            "Highlight landed meteorites in the loaded client area. Initially ON.",true,true));
        specs.add(ModSetting.color("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.METEOR_COLOR,"Meteor color",
            "Outline and Auto-mark color for landed meteorites.",org.waypoints.next.tiles.TileHighlightSettings.DEFAULT_METEOR_COLOR));
        specs.add(ModSetting.bool("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.METEOR_MARK,"Meteor auto mark",
            "Create one target per detected meteorite. Initially ON; independent of the outline switch.",true,true));
        specs.add(ModSetting.bool("Tile highlights",org.waypoints.next.tiles.TileHighlightSettings.METEOR_CHIME,"Meteor chime",
            "Play a chime once per newly detected meteorite. Initially ON; independent of the outline switch.",true,true));
    }
    public List<ModSetting> settings() {
        List<ModSetting> translated=new ArrayList<ModSetting>();
        for(ModSetting spec:specs) {
            if(spec.key.equals(org.waypoints.next.tiles.TileHighlightSettings.RADIUS)||spec.description.equals(RIDEABLE_HELP)||spec.description.equals(HARVEST_HELP))
                translated.add(new ModSetting(spec.group,spec.key,org.waypoints.next.i18n.Messages.text(spec.label),org.waypoints.next.i18n.Messages.text(spec.description),spec.type,spec.defaultValue,spec.minimum,spec.maximum,spec.live,spec.choices()));
            else translated.add(spec);
        }
        return Collections.unmodifiableList(translated);
    }
    public String value(String key) {
        for (ModSetting spec : specs) if (spec.key.equals(key)) return controller.values().getProperty(key,spec.defaultValue);
        throw new IllegalArgumentException(key);
    }
    public void change(String key,String value) throws Exception {
        ModSetting selected=null;for(ModSetting spec:specs)if(spec.key.equals(key))selected=spec;
        if(selected==null)throw new IllegalArgumentException(key);
        controller.saveImmediate(key,selected.validate(value));
    }
    private static String help(SettingSpec spec) {
        switch(spec.key) {
            case "lootMapEnabled": return "Use event messages and opened chests to track Loot Map hunts.";
            case "archaeologyEnabled": return "Track archaeology reports and remember confirmed cache locations per server.";
            case "navigationRouteVisualStyle": return "Choose the visual route signal: pulse, solid line or moving dashes.";
            case "navigationPulseMaximumDistanceMetres": return "Maximum distance shown by the animated navigation pulse.";
            case "navigationCartMaximumSlopeDirt": return "Maximum dirt slope allowed by the cart route planner. Interpolated observations use half this limit.";
            case "navigationCartMaximumWaterDepthMetres": return "Maximum water depth accepted when planning a cart route.";
            case "navigationHighwaysEnabled": return "Prefer published highways when planning ground routes.";
            case "navigationHighwaysSyncMinutes": return "Minutes between background refreshes of published highways.";
            case "serverMapEnabled": return "Use the recognized server's map in the native M window.";
            case "serverMapShowDeeds": return "Show published deed locations on the server map.";
            case "serverMapShowHighways": return "Show published highways on the server map.";
            case "serverMapSyncMinutes": return "Minutes between background server map refreshes.";
            case "deedProviderEnabled": return "Create deed waypoints from a recognized server's published deed feed.";
            case "deedProviderMappings": return "Custom deed feeds: endpoint[|world]|JSON-or-CSV|https-or-file-source[|width|height], separated by semicolons.";
            case "scannerProfile": return "Saved scanner filter used on login. Change this during play to scan uniques, treasure or animals.";
            case "scannerNotifications": return "Report matching objects appearing or disappearing from loaded client data.";
            case "scannerOutlines": return "Draw bounded colored model outlines around scanner matches.";
            case "scannerMaximumOutlines": return "Maximum number of nearby scanner matches outlined at once.";
            case "scannerOutlineDistanceMetres": return "Maximum distance for scanner model outlines.";
            case "scannerExcludedNames": return "Comma-separated name fragments to omit from generic scans; at most 32 fragments of 80 characters.";
            case "trackingRetentionDays": return "Days to keep a tracked object's last known position before it becomes unknown.";
            case "maximumCompassMarkers": return "Maximum user waypoint markers drawn on the compass.";
            case "maximumWorldEffects": return "Maximum user waypoint effects drawn in the world.";
            case "worldEffectDistanceMetres": return "Maximum distance for user waypoint world effects.";
            case "maximumWorldLabels": return "Maximum waypoint text labels drawn in the world.";
            case "worldLabelDistanceMetres": return "Maximum distance for waypoint text labels.";
            case "waypointMapWidth": return "Server map width in tiles, used to interpret coordinates.";
            case "waypointMapHeight": return "Server map height in tiles, used to interpret coordinates.";
            case "archaeologyHistoryLimit": return "Maximum number of completed or dismissed archaeology sessions retained.";
            case "navigationRouteDiagnostics": return "Write route geometry for investigating route-planning problems.";
            case "navigationRouteLogTileInterval": return "Ignore small changes within this many leading route tiles in diagnostic output.";
            default: return spec.label + ". Location for " + spec.group.toLowerCase(Locale.ROOT) + "; existing data is kept at its current location until the next start.";
        }
    }
}
