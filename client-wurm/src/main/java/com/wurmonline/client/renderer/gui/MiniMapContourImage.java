package com.wurmonline.client.renderer.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import org.waypoints.next.map.MapViewport;
import org.waypoints.next.map.TopographicContours;

/** One transparent overlay keeps dense contours out of Wurm's 8192-primitive HUD queue. */
final class MiniMapContourImage {
    private MiniMapContourImage() { }

    static BufferedImage render(MapViewport viewport, int size, int originX, int originY,
                                int columns, int rows, float[] heights, int intervalMetres) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Path2D.Float path = new Path2D.Float();
        double scale = viewport.getPixelsPerTile();
        double left = size / 2.0d - viewport.getCenterX() * scale;
        double top = size / 2.0d - viewport.getCenterY() * scale;
        TopographicContours.forEach(originX, originY, columns, rows, heights, intervalMetres,
                segment -> {
                    path.moveTo(left + segment.x1 * scale, top + segment.y1 * scale);
                    path.lineTo(left + segment.x2 * scale, top + segment.y2 * scale);
                });
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(new Color(0.08f, 0.04f, 0.02f, 0.8f));
            graphics.draw(path);
            graphics.setStroke(new BasicStroke(0.9f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(new Color(0.95f, 0.85f, 0.62f, 0.9f));
            graphics.draw(path);
        } finally {
            graphics.dispose();
        }
        return image;
    }
}
