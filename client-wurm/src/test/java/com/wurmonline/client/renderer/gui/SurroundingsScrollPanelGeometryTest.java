package com.wurmonline.client.renderer.gui;

import org.junit.Assume;
import org.junit.Test;

import java.lang.reflect.Method;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Exercises the real pinned-client layout classes used by Surroundings. */
public final class SurroundingsScrollPanelGeometryTest {
    @Test public void rowsAddedAfterViewportLayoutRemainScrollable()
            throws Exception {
        requireBundledJavaFxRuntime();
        int previousWidth = WurmComponent.SCREEN_WIDTH;
        int previousHeight = WurmComponent.SCREEN_HEIGHT;
        WurmComponent.SCREEN_WIDTH = 1920;
        WurmComponent.SCREEN_HEIGHT = 1080;
        try {
            WurmArrayPanel<FlexComponent> table =
                    new WurmArrayPanel<FlexComponent>("test.table", 0, true);
            SurroundingsScrollPanel panel = new SurroundingsScrollPanel(
                    "test.scroll", table, 25, null);
            panel.setInitialSize(300, 100, false);
            panel.setPosition(0, 0);

            addRows(table, 10);

            assertEquals(250, table.height);
            panel.contentChanged();
            assertEquals(150, hiddenContent(panel));
            assertTrue(panel.scrollWheel(3));
            assertEquals(75, panel.yo);
            WurmComponent viewport = (WurmComponent) (Object) panel.offs;
            assertEquals(viewport.y - 75, table.y);
            assertEquals(table.y, table.components.get(0).y);
            WurmArrayPanel<?> firstRow = (WurmArrayPanel<?>)
                    table.components.get(0);
            assertEquals(firstRow.y, firstRow.components.get(0).y);

            panel.layout();
            assertEquals(viewport.y - 75, table.y);
            assertEquals(table.y, firstRow.y);
            assertEquals(firstRow.y, firstRow.components.get(0).y);
        } finally {
            WurmComponent.SCREEN_WIDTH = previousWidth;
            WurmComponent.SCREEN_HEIGHT = previousHeight;
        }
    }

    @Test public void nativeBarHitIsHandledByStableManualDrag()
            throws Exception {
        requireBundledJavaFxRuntime();
        int previousWidth = WurmComponent.SCREEN_WIDTH;
        int previousHeight = WurmComponent.SCREEN_HEIGHT;
        WurmComponent.SCREEN_WIDTH = 1920;
        WurmComponent.SCREEN_HEIGHT = 1080;
        try {
            WurmArrayPanel<FlexComponent> table =
                    new WurmArrayPanel<FlexComponent>("test.table", 0, true);
            SurroundingsScrollPanel panel = new SurroundingsScrollPanel(
                    "test.scroll", table, 25, null);
            panel.setInitialSize(300, 100, false);
            addRows(table, 10);
            panel.contentChanged();

            WurmComponent bar = (WurmComponent) (Object)
                    panel.verticalScrollBar;
            int mouseX = bar.x + Math.max(1, bar.width / 2);
            int mouseY = bar.y + 13;
            assertEquals(panel, panel.getComponentAt(mouseX, mouseY));
            panel.leftPressed(mouseX, mouseY, 1);
            panel.mouseDragged(mouseX, mouseY + 30);
            panel.leftReleased(mouseX, mouseY + 30);

            assertTrue("thumb drag must move the table", panel.yo > 0);
        } finally {
            WurmComponent.SCREEN_WIDTH = previousWidth;
            WurmComponent.SCREEN_HEIGHT = previousHeight;
        }
    }

    private static void requireBundledJavaFxRuntime() {
        try {
            Class.forName("javafx.application.Application");
        } catch (ClassNotFoundException missing) {
            Assume.assumeNoException("requires Wurm's bundled JavaFX runtime",
                    missing);
        }
    }

    private static int hiddenContent(SurroundingsScrollPanel panel)
            throws Exception {
        Object scrollBar = (Object) panel.verticalScrollBar;
        Method method = scrollBar.getClass().getSuperclass()
                .getDeclaredMethod("getHiddenContentSize");
        method.setAccessible(true);
        return ((Integer) method.invoke(scrollBar)).intValue();
    }

    private static void addRows(WurmArrayPanel<FlexComponent> table,
                                int count) {
        FlexComponent[] rows = new FlexComponent[count];
        for (int index = 0; index < count; index++) {
            WurmArrayPanel<FlexComponent> row =
                    new WurmArrayPanel<FlexComponent>(
                            "test.row." + index, 1, 300, 25);
            WurmArrayPanel<FlexComponent> cell =
                    new WurmArrayPanel<FlexComponent>(
                            "test.cell." + index, 1, 300, 25);
            row.addComponent(cell);
            rows[index] = row;
        }
        table.addComponents(rows);
    }
}
