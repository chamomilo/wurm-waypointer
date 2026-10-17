package com.wurmonline.client.renderer.gui;

import org.waypoints.next.i18n.Messages;
import org.waypoints.next.surroundings.*;
import org.waypoints.next.ui.SurroundingsController;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Real pinned-client widgets, clipping and input, with a dense received catalogue. */
public final class SurroundingsLargeCatalogProbe {
    public static void main(String[] args) throws Exception {
        Files.createDirectories(Paths.get(args[1]));
        for (String language : Messages.CODES) {
            Messages.select(language);
            WaypointerLayoutProbe.preparePreview(args[0], args[2], 12);
            Fixture controller = new Fixture();
            SurroundingsWindow panel = new SurroundingsWindow(controller);
            panel.selectKind(SurroundingKind.ITEM);
            panel.setSize(1120, 420);
            panel.setPosition(10, 10);
            panel.gameTick();
            assertBounded(panel, 2500);
            SurroundingsScrollPanel scroll = field(panel, "scrollPanel");
            WurmArrayPanel<?> table = field(panel, "table");
            check(table.height == 2500 * 28, "All 2500 results contribute to scroll range");
            int before = submissions();
            WaypointerLayoutProbe.savePreview(panel, Paths.get(args[1], "objects-2500-" + language + ".png").toString());
            int draws = submissions() - before;
            check(draws < 2000, "Dense list stays below native HUD primitive capacity: " + draws);
            check(WaypointerLayoutProbe.paintedLabel("item-2499") == null, "Offscreen rows do not paint captions");

            Object unchanged = table.components.get(0);
            Field refreshAt = SurroundingsWindow.class.getDeclaredField("nextAutoRefreshAt");
            refreshAt.setAccessible(true);
            refreshAt.setLong(panel, 0L);
            panel.gameTick();
            check(table.components.get(0) == unchanged, "Unchanged controller state retains controls when refresh is due");
            controller.playerX = 450;
            refreshAt.setLong(panel, 0L);
            panel.gameTick();
            check(table.components.get(0) != unchanged, "Player movement refreshes distances even for stationary items");
            scroll.scrollWheel(1);
            assertBounded(panel, 2500);
            check(scroll.yo == 84, "Wheel advances three rows");
            for (int target : new int[]{35007, Integer.MAX_VALUE, 7, 28000}) {
                scroll.restoreOffset(target);
                panel.gameTick();
                assertBounded(panel, 2500);
                verifyVisibleAction(panel, controller);
                int offset = scroll.yo;
                panel.refreshFromController();
                check(scroll.yo == offset, "Live refresh retains partial row offset");
            }
            scroll.restoreOffset(Integer.MAX_VALUE);
            panel.gameTick();
            WaypointerLayoutProbe.savePreview(panel, Paths.get(args[1], "objects-last-" + language + ".png").toString());
            check(WaypointerLayoutProbe.paintedLabel("item-2499") != null, "Last result is rendered and reachable");
            panel.setSize(1120, 680);
            panel.gameTick();
            assertBounded(panel, 2500);
            verifyVisibleAction(panel, controller);

            WurmInputField input = field(panel, "searchInput");
            input.setTextMoveToEnd("item-2499");
            panel.handleInputChanged(input, input.getText());
            check(scroll.yo == 0 && table.height == 28, "Filtering from bottom clamps to one result");
            assertBounded(panel, 1);
            input.setTextMoveToEnd("no matching object");
            panel.handleInputChanged(input, input.getText());
            assertBounded(panel, 0);
            check(table.height == 0, "Empty filter clears scroll extent");
            input.setTextMoveToEnd("");
            panel.handleInputChanged(input, "");
            panel.buttonClicked(field(panel, "waypointFiltered"));
            check(controller.batch.size() == 2500 && controller.batchEnabled, "Track filtered includes every offscreen result");
            panel.buttonClicked(field(panel, "clearFiltered"));
            check(controller.batch.size() == 2500 && !controller.batchEnabled, "Clear filtered includes every offscreen result");

            WurmArrayPanel<?> header = field(panel, "tableHeader");
            panel.buttonClicked((WButton) header.components.get(1));
            header = field(panel, "tableHeader");
            panel.buttonClicked((WButton) header.components.get(1));
            panel.gameTick();
            verifyVisibleAction(panel, controller);
            scroll.restoreOffset(Integer.MAX_VALUE);
            panel.gameTick();
            for (int index = 0; index < 2000; index++)
                controller.catalog.remove(new SurroundingKey(SurroundingKind.ITEM, index));
            panel.refreshFromController();
            panel.gameTick();
            assertBounded(panel, 500);
            check(scroll.yo <= Math.max(0, 500 * 28 - scroll.viewportHeight()), "Removal clamps bottom scroll position");
            verifyVisibleAction(panel, controller);
            int retained = scroll.yo;
            panel.selectKind(SurroundingKind.ANIMAL);
            panel.selectKind(SurroundingKind.ITEM);
            panel.gameTick();
            check(((SurroundingsScrollPanel) field(panel, "scrollPanel")).yo == retained, "Section changes retain scroll position");
            assertBounded(panel, 500);
            System.out.println("LARGE_SURROUNDINGS_OK: " + language + ", 2500 results, HUD primitives=" + draws);
        }
        WaypointerLargeListsProbe.verify(args[1]);
    }

    private static void assertBounded(SurroundingsWindow panel, int total) throws Exception {
        SurroundingsScrollPanel scroll = field(panel, "scrollPanel");
        WurmArrayPanel<?> table = field(panel, "table");
        Map<?, ?> actions = field(panel, "rowActions");
        List<?> keys = field(panel, "filteredKeys");
        int maximum = (scroll.viewportHeight() + 27) / 28 + 3;
        check(actions.size() <= maximum, "Control count depends on viewport, not catalogue: " + actions.size());
        check(table.components.size() <= maximum + 2, "Only visible rows and two padding extents are attached");
        check(keys.size() == total, "Filtering/bulk operations retain the full result set");
        check(actions.size() > 0 || total == 0, "Nonempty list has visible actions");
    }

    private static void verifyVisibleAction(SurroundingsWindow panel, Fixture controller) throws Exception {
        SurroundingsScrollPanel scroll = field(panel, "scrollPanel");
        WurmComponent viewport = (WurmComponent) (Object) scroll.offs;
        WurmArrayPanel<?> table = field(panel, "table");
        for (FlexComponent component : table.components) {
            if (!(component instanceof WurmArrayPanel) || component.y < viewport.y
                    || component.y + component.height > viewport.y + viewport.height) continue;
            WurmArrayPanel<?> row = (WurmArrayPanel<?>) component;
            WButton mark = ((WaypointerTableActionCell) row.components.get(0)).button;
            check(panel.getComponentAt(mark.x + mark.width / 2, mark.y + mark.height / 2) == mark,
                    "Visible action remains reachable through native hit testing");
            String caption = field(row.components.get(1), "caption");
            panel.buttonClicked(mark);
            check(controller.lastKey != null && caption.equals("item-" + controller.lastKey.getWurmId()),
                    "Rebuilt row action targets its current object ID");
            return;
        }
        throw new AssertionError("No fully visible row at offset " + scroll.yo);
    }

    private static int submissions() throws Exception {
        Field count = NativeUiRenderFixture.class.getDeclaredField("submitted");
        count.setAccessible(true);
        return count.getInt(null);
    }

    @SuppressWarnings("unchecked") private static <T> T field(Object owner, String name) throws Exception {
        Field field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(owner);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture implements SurroundingsController {
        final SurroundingsCatalog catalog = new SurroundingsCatalog();
        SurroundingKey lastKey;
        List<SurroundingKey> batch = new ArrayList<SurroundingKey>();
        boolean batchEnabled;
        double playerX = 400;
        Fixture() {
            for (int index = 0; index < 2500; index++) catalog.upsert(SurroundingEntry.builder()
                    .kind(SurroundingKind.ITEM).wurmId(index).name("item-" + index).shortName("lamp")
                    .category("Decorations").material("iron").position(index, 400, 2).build());
        }
        @Override public SurroundingsSnapshot snapshot(SurroundingsQuery query) { return catalog.snapshot(query, playerX, 400); }
        @Override public void setWaypoint(SurroundingKey key, boolean enabled) { lastKey = key;catalog.setWaypoint(key, enabled); }
        @Override public void setWaypoints(Collection<SurroundingKey> keys, boolean enabled) { batch = new ArrayList<SurroundingKey>(keys);batchEnabled = enabled;catalog.setWaypoints(keys, enabled); }
        @Override public void clearAllWaypoints() { catalog.clearAllWaypoints(); }
        @Override public void openWaypointManager() { }
        // Match the real runtime's composite scanner/catalogue revision domain.
        @Override public long revision() { return catalog.revision() * 31L + Double.doubleToLongBits(playerX); }
        @Override public void reportFailure(String operation, Throwable failure) { throw new AssertionError(operation, failure); }
    }
}
