package view.drawing;

import entity.InkPoint;
import entity.Stroke;
import interface_adapter.TextRun;
import interface_adapter.canvas.ViewportState;
import view.answer.AnswerInk;
import view.answer.CanvasAnswer;
import view.answer.Formula;
import view.answer.FormulaBook;
import workspace.domain.CanvasState;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.LongSupplier;

/**
 * Converts the canvas models to and from the saved {@link CanvasState} (Single Responsibility:
 * the canvas component no longer knows the file format's records).
 *
 * <p>Reading builds everything in fresh objects first, so invalid or corrupt data throws before
 * the caller touches the open canvas.
 */
public final class CanvasStateMapper {

    /** Everything read from a saved canvas, ready to replace the current content. */
    public record Restored(List<Stroke> strokes, Map<Stroke, Color> colors, Map<Stroke, Float> widths,
                           FormulaBook book, List<CanvasPicture> pictures, List<GraphSlot> graphs,
                           ViewportState viewport) {
    }

    private CanvasStateMapper() {
    }

    /**
     * Immutable session data; no rendered whole-canvas screenshot is used for saving.
     *
     * @param liveGraphDefinition the encoded definition of a live graph card's owner ("" if it has none);
     *                            called with every card tag that is not a saved placeholder, null included
     */
    public static CanvasState export(InkModel ink, List<CanvasPicture> pictures, List<GraphSlot> graphs,
                                     ViewportState view, Function<Object, String> liveGraphDefinition) {
        List<CanvasState.Ink> saved = new ArrayList<>();
        Map<Stroke, Integer> index = new HashMap<>();
        for (Stroke stroke : ink.strokes()) {
            List<CanvasState.Point> pts = stroke.getPoints().stream()
                    .map(p -> new CanvasState.Point(p.getX(), p.getY(), p.getTimeMillis())).toList();
            index.put(stroke, saved.size());
            saved.add(new CanvasState.Ink(pts, ink.colorOf(stroke).getRGB(), ink.widthOf(stroke)));
        }
        List<CanvasState.Formula> formulas = new ArrayList<>();
        for (Formula f : ink.book().all()) {
            List<Integer> members = f.getStrokes().stream().map(index::get)
                    .filter(Objects::nonNull).toList();
            if (members.isEmpty()) continue;
            CanvasAnswer a = f.getAnswer();
            formulas.add(new CanvasState.Formula(members, a == null ? "" : a.getText(), a != null && a.isError(),
                    a == null ? 0 : a.getX(), a == null ? 0 : a.getY(),
                    a == null ? 0 : a.getWidth(), a == null ? 0 : a.getHeight(),
                    a == null ? 0 : a.getFontSize(), a == null ? Color.BLACK.getRGB() : a.getColor().getRGB(),
                    a == null || a.getRuns() == null ? List.of() : a.getRuns().stream()
                            .map(run -> new CanvasState.TextPart(run.getText(), run.isRaised())).toList(),
                    a == null || !a.isTypeset() ? "" : a.getLatex()));
        }
        List<CanvasState.Asset> assets = new ArrayList<>();
        for (CanvasPicture p : pictures) {
            assets.add(new CanvasState.Asset(p.latex(), p.result(), png(p.image()),
                    p.box().getX(), p.box().getY(), p.box().getWidth(), p.box().getHeight()));
        }
        for (GraphSlot slot : graphs) {
            Rectangle2D c = slot.card();
            String definition = slot.tag() instanceof SavedGraphDefinition placeholder
                    ? placeholder.encoded() : liveGraphDefinition.apply(slot.tag());
            assets.add(new CanvasState.Asset("", "", png(slot.image()), c.getX(), c.getY(),
                    c.getWidth(), c.getHeight(), true, definition));
        }
        return new CanvasState(saved, formulas, assets, view.zoom(), view.originX(), view.originY());
    }

    /**
     * Reads a saved canvas into fresh objects.
     *
     * @param nextRevision gives each restored formula its revision
     * @throws IOException when the data refers to missing ink or holds an unreadable image
     */
    public static Restored read(CanvasState state, LongSupplier nextRevision) throws IOException {
        Objects.requireNonNull(state);
        List<Stroke> restoredInk = new ArrayList<>();
        Map<Stroke, Color> restoredColors = new HashMap<>();
        Map<Stroke, Float> restoredWidths = new HashMap<>();
        for (CanvasState.Ink sample : state.ink()) {
            Stroke stroke = new Stroke();
            for (CanvasState.Point p : sample.points()) stroke.addPoint(new InkPoint(p.x(), p.y(), p.timeMillis()));
            restoredInk.add(stroke);
            restoredColors.put(stroke, new Color(sample.argb(), true));
            restoredWidths.put(stroke, sample.width());
        }
        FormulaBook restoredBook = new FormulaBook();
        for (CanvasState.Formula info : state.formulas()) {
            List<Stroke> group = new ArrayList<>();
            for (int n : info.strokes()) {
                if (n < 0 || n >= restoredInk.size()) throw new IOException("Invalid formula reference");
                group.add(restoredInk.get(n));
            }
            Formula f = restoredBook.add(group, nextRevision.getAsLong());
            if (!info.answer().isBlank()) {
                CanvasAnswer a;
                if (!info.latex().isEmpty()) {
                    a = CanvasAnswer.typeset(info.answer(), info.latex(), info.error(), info.x(), info.y(),
                            info.width(), info.height(), info.fontSize());
                } else if (info.textParts().isEmpty()) {
                    a = new CanvasAnswer(info.answer(), info.error(), info.x(), info.y(),
                            info.width(), info.height(), info.fontSize());
                } else {
                    List<TextRun> runs = info.textParts().stream()
                            .map(part -> new TextRun(part.text(), part.superscript())).toList();
                    a = new CanvasAnswer(runs, info.error(), info.x(), info.y(),
                            info.width(), info.height(), info.fontSize());
                }
                float pen = InkModel.mostUsedPen(group, restoredWidths);  // box refit, older saves drew every answer at 4
                restoredBook.put(f.withAnswer(AnswerInk.refit(a.withColor(new Color(info.color(), true))
                        .withPenWidth(pen), pen)));
            }
        }
        List<CanvasPicture> restoredAssets = new ArrayList<>();
        List<GraphSlot> restoredGraphs = new ArrayList<>();
        for (CanvasState.Asset asset : state.assets()) {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(asset.png()));
            if (image == null) throw new IOException("Unrecognizable saved image");
            Rectangle2D rectangle = new Rectangle2D.Double(asset.x(), asset.y(), asset.width(), asset.height());
            if (asset.graph()) {
                restoredGraphs.add(new GraphSlot(image, rectangle, 0,
                        asset.graphDefinition().isEmpty() ? null : new SavedGraphDefinition(asset.graphDefinition())));
            } else {
                restoredAssets.add(new CanvasPicture(image, rectangle, asset.latex(), asset.result()));
            }
        }
        return new Restored(restoredInk, restoredColors, restoredWidths, restoredBook, restoredAssets,
                restoredGraphs, new ViewportState(state.zoom(), state.offsetX(), state.offsetY()));
    }

    private static byte[] png(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", out)) throw new IOException("PNG writer missing");
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot serialize canvas image", e);
        }
    }
}
