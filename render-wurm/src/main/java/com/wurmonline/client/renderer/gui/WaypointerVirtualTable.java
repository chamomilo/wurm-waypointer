package com.wurmonline.client.renderer.gui;

import com.wurmonline.client.renderer.backend.Queue;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntConsumer;

/** Native rows are materialized only around the viewport; padding retains the full scroll range. */
final class WaypointerVirtualTable extends WurmArrayPanel<FlexComponent> {
    private final int stride;
    private final Runnable beforeRows, afterRows;
    private WurmScrollPanel scroll;
    private IntConsumer restoreOffset;
    private IntFunction<FlexComponent> factory;
    private int count, first = -1, last = -1;
    private boolean updating;

    WaypointerVirtualTable(String name, int stride, Runnable beforeRows, Runnable afterRows) {
        super(name, DIR_VERTICAL);
        if (stride < 1) throw new IllegalArgumentException("positive row stride required");
        this.stride = stride;
        this.beforeRows = beforeRows;
        this.afterRows = afterRows;
    }

    void bind(WurmScrollPanel scroll) { bind(scroll,offset->{scroll.componentResized();((ChamomiloUiV1ScrollPanel)scroll).scrollTo(0,offset);}); }
    void bind(WurmScrollPanel scroll, IntConsumer restoreOffset) { this.scroll = scroll;this.restoreOffset = restoreOffset; }

    void setRows(int count, int offset, IntFunction<FlexComponent> factory) {
        this.count = Math.max(0, count);
        this.factory = factory;
        first = last = -1;
        populate(offset);
    }

    List<FlexComponent> dataRows() {
        List<FlexComponent> result = new ArrayList<FlexComponent>();
        for (FlexComponent component : components) if (!(component instanceof Padding)) result.add(component);
        return result;
    }

    void refreshViewport() { if (scroll != null) populate(scroll.yo); }

    private void populate(int requestedOffset) {
        if (updating || factory == null || scroll == null) return;
        WurmComponent viewport = (WurmComponent) (Object) scroll.offs;
        int extent = Math.max(stride, viewport.height);
        int offset = Math.max(0, Math.min(requestedOffset, Math.max(0, count * stride - extent)));
        int nextFirst = Math.max(0, offset / stride - 1);
        int nextLast = Math.min(count, (offset + extent + stride - 1) / stride + 1);
        if (first == nextFirst && last == nextLast) return;
        updating = true;
        try {
            if (beforeRows != null) beforeRows.run();
            List<FlexComponent> next = new ArrayList<FlexComponent>();
            if (nextFirst > 0) next.add(new Padding(nextFirst * stride));
            for (int index = nextFirst; index < nextLast; index++) {
                FlexComponent row = factory.apply(index);
                next.add(row);
                if (row.height < stride) next.add(new Padding(stride - row.height));
            }
            if (nextLast < count) next.add(new Padding((count - nextLast) * stride));
            removeAllComponents();
            addComponents(next.toArray(new FlexComponent[next.size()]));
            first = nextFirst;
            last = nextLast;
            if (afterRows != null) afterRows.run();
            restoreOffset.accept(offset);
        } finally { updating = false; }
    }

    // Native Offsetter repositions content during wheel, thumb and programmatic
    // scrolling. Refresh before laying out children, without replacing the SDK scrollbar.
    @Override void performLayout() { refreshViewport();super.performLayout(); }
    @Override public void gameTick() { refreshViewport();super.gameTick(); }
    @Override protected void renderComponent(Queue queue, float alpha) { refreshViewport();super.renderComponent(queue, 1f); }
    @Override public FlexComponent getComponentAt(int x, int y) { refreshViewport();return super.getComponentAt(x, y); }

    private static final class Padding extends FlexComponent {
        Padding(int height) {
            super("waypointer.virtual-padding", 0, 0, 1, height);
            sizeFlags = FIXED_WIDTH | FIXED_HEIGHT;
        }
    }
}
