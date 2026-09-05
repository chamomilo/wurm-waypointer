package org.waypoints.next.integration;

import com.wurmonline.client.renderer.PickableUnit;
import com.wurmonline.client.renderer.backend.Queue;

/** Render-thread bridge with a small duplicate-pass guard for compatible mods. */
public final class ScannerOutlineRenderBridge {
    private static final PickableUnit[] NONE = new PickableUnit[0];
    private static final long DUPLICATE_PASS_NANOS = 2_000_000L;
    private static final ThreadLocal<RenderGate> GATE =
            new ThreadLocal<RenderGate>() {
                @Override protected RenderGate initialValue() {
                    return new RenderGate();
                }
            };

    private ScannerOutlineRenderBridge() { }

    public static PickableUnit[] targets(Queue queue) {
        long now = System.nanoTime();
        RenderGate gate = GATE.get();
        if (gate.queue == queue && now - gate.renderedAt < DUPLICATE_PASS_NANOS) {
            return NONE;
        }
        gate.queue = queue;
        gate.renderedAt = now;
        return WurmWaypointerRuntime.currentScannerOutlineTargets();
    }

    private static final class RenderGate {
        private Queue queue;
        private long renderedAt;
    }
}
