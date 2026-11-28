package view.drawing;

import entity.Stroke;
import interface_adapter.canvas.CanvasMemento;
import view.answer.FormulaBook;

import java.awt.Color;
import java.util.List;
import java.util.Map;

/**
 * The memento of the canvas content: everything a content command may change (ink, formulas and
 * answers, pictures, graph cards), nothing about the view. Only the canvas reads it.
 */
public record CanvasSnapshot(List<Stroke> strokes, Map<Stroke, Color> colors, Map<Stroke, Float> widths,
                             FormulaBook book, List<CanvasPicture> pictures, List<GraphSlot> graphs)
        implements CanvasMemento {
}
