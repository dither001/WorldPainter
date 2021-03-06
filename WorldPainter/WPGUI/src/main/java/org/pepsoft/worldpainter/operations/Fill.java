package org.pepsoft.worldpainter.operations;

import org.pepsoft.worldpainter.Dimension;
import org.pepsoft.worldpainter.WorldPainterView;
import org.pepsoft.worldpainter.brushes.Brush;
import org.pepsoft.worldpainter.painting.DimensionPainter;
import org.pepsoft.worldpainter.painting.Paint;

import javax.swing.*;

/**
 * Created by pepijn on 14-5-15.
 */
public class Fill extends AbstractBrushOperation implements PaintOperation {
    public Fill(WorldPainterView view) {
        super("Fill", "Flood fill an area of the world with any kind of layer or terrain", view, "operation.fill");
    }

    @Override
    protected void tick(int centreX, int centreY, boolean inverse, boolean first, float dynamicLevel) {
        painter.setUndo(inverse);
        Dimension dimension = getDimension();
        dimension.setEventsInhibited(true);
        try {
            painter.fill(dimension, centreX, centreY, SwingUtilities.getWindowAncestor(getView()));
        } catch (IndexOutOfBoundsException e) {
            // This most likely indicates that the area being flooded was too
            // large
            dimension.undoChanges();
            JOptionPane.showMessageDialog(getView(), "The area to be flooded is too large; please retry with a smaller area", "Area Too Large", JOptionPane.ERROR_MESSAGE);
        } finally {
            dimension.setEventsInhibited(false);
        }
    }

    @Override
    public Paint getPaint() {
        return painter.getPaint();
    }

    @Override
    public void setPaint(Paint paint) {
        if (getBrush() != null) {
            paint.setBrush(getBrush());
        }
        painter.setPaint(paint);
    }

    @Override
    protected void brushChanged(Brush newBrush) {
        if (painter.getPaint() != null) {
            painter.getPaint().setBrush(newBrush);
        }
    }

    private final DimensionPainter painter = new DimensionPainter();
}