package org.waypoints.next.navigation;

/**
 * Allocation-free gate for view ownership reconciliation. Rendering and
 * projection may continue every frame while collections are rebuilt only when
 * their selection inputs actually change.
 */
public final class NavigationViewReconcileState {
    private long generation = Long.MIN_VALUE;
    private int playerTileX = Integer.MIN_VALUE;
    private int playerTileY = Integer.MIN_VALUE;
    private int viewportWidth = Integer.MIN_VALUE;

    public boolean requires(long nextGeneration, int nextPlayerTileX,
                            int nextPlayerTileY, int nextViewportWidth) {
        return generation != nextGeneration
                || playerTileX != nextPlayerTileX
                || playerTileY != nextPlayerTileY
                || viewportWidth != nextViewportWidth;
    }

    public void applied(long nextGeneration, int nextPlayerTileX,
                        int nextPlayerTileY, int nextViewportWidth) {
        generation = nextGeneration;
        playerTileX = nextPlayerTileX;
        playerTileY = nextPlayerTileY;
        viewportWidth = nextViewportWidth;
    }

    public void reset() {
        generation = Long.MIN_VALUE;
        playerTileX = Integer.MIN_VALUE;
        playerTileY = Integer.MIN_VALUE;
        viewportWidth = Integer.MIN_VALUE;
    }
}
