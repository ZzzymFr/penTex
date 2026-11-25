package view.board;

import view.glass.GlassTextField;
import view.glass.GlassDialogs;
import view.glass.GlassButton;
import workspace.domain.CanvasState;
import workspace.domain.SavedCanvas;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JButton;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListCellRenderer;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** View-only searchable save browser. No example rows or persistence logic live here. */
public final class SavedCanvasChooser extends JPanel {
    private static final Color BLUE = BoardTheme.ACCENT;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("MMM d, yyyy 'at' h:mm a", Locale.ENGLISH)
            .withZone(ZoneId.systemDefault());
    private final List<SavedCanvas> all;
    private final DefaultListModel<SavedCanvas> results = new DefaultListModel<>();
    private final JList<SavedCanvas> list = new JList<>(results);
    private final view.glass.AsyncThumbnailCache<String> thumbnails = new view.glass.AsyncThumbnailCache<>(list::repaint);
    private final GlassTextField search = new GlassTextField(24, "Search canvases");
    private final JLabel count = new JLabel();
    private final JLabel empty = new JLabel("No canvases found", SwingConstants.CENTER);
    public enum Choice { OPEN, NEW, RENAME }
    private Choice choice = Choice.OPEN;
    private Runnable openSelected = () -> { };
    private java.util.function.Consumer<SavedCanvas> deleteSelected = canvas -> { };

    public SavedCanvasChooser(List<SavedCanvas> canvases) {
        super(new BorderLayout(0, 10));
        this.all = new java.util.ArrayList<>(canvases);
        setOpaque(false);
        setPreferredSize(new Dimension(540, 440));
        setBorder(BorderFactory.createEmptyBorder(4, 2, 0, 2));

        JPanel heading = new JPanel(new BorderLayout());
        heading.setOpaque(false);
        JLabel title = new JLabel("Open Canvas");
        title.setFont(BoardTheme.semibold(19f));
        heading.add(title, BorderLayout.WEST);
        count.setForeground(new Color(111, 111, 117));
        count.setFont(BoardTheme.font(12f));
        heading.add(count, BorderLayout.EAST);
        JPanel top = new JPanel(new BorderLayout(0, 7));
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(search, BorderLayout.CENTER);
        add(top, BorderLayout.NORTH);

        list.setOpaque(false);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(82);
        list.setCellRenderer(new Cell());
        list.setBorder(BorderFactory.createEmptyBorder(4, 3, 4, 3));
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent e) {
                int index = list.locationToIndex(e.getPoint());
                if (index >= 0 && list.getCellBounds(index, index) != null
                        && list.getCellBounds(index, index).contains(e.getPoint())
                        && e.getX() >= list.getWidth() - 44) {
                    deleteSelected.accept(results.get(index));
                    return;
                }
                if (e.getClickCount() == 2 && list.locationToIndex(e.getPoint()) >= 0
                        && list.getCellBounds(list.locationToIndex(e.getPoint()), list.locationToIndex(e.getPoint()))
                                .contains(e.getPoint())) openSelected.run();
            }
        });
        search.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { filter(); }
            public void removeUpdate(DocumentEvent e) { filter(); }
            public void changedUpdate(DocumentEvent e) { filter(); }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createEmptyBorder());  // rows float on the sheet, no box
        // Opaque: a translucent fill on an opaque viewport leaves stale grey paint below the rows.
        scroll.getViewport().setBackground(Color.WHITE);
        scroll.getVerticalScrollBar().setUnitIncrement(28);
        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        empty.setForeground(new Color(111, 111, 117));
        empty.setFont(BoardTheme.font(14f));
        center.add(scroll, BorderLayout.CENTER);
        center.add(empty, BorderLayout.SOUTH);
        add(center, BorderLayout.CENTER);
        JLabel hint = new JLabel("Double-click to open");
        hint.setForeground(new Color(111, 111, 117));
        hint.setFont(BoardTheme.font(11f));
        // The modal's shared action row owns all four buttons, so they have
        // precisely the same vertical position, height, and baseline.

        GlassButton create = GlassDialogs.actionButton("New", true);
        create.addActionListener(e -> { choice = Choice.NEW; openSelected.run(); });
        GlassButton rename = GlassDialogs.actionButton("Rename", false);
        rename.addActionListener(e -> { if (selected() != null) { choice = Choice.RENAME; openSelected.run(); } });
        GlassDialogs.setLeadingActions(this, create, rename);
        add(hint, BorderLayout.SOUTH);
        filter();
    }

    /** The enclosing modal registers its submit callback; no persistence or navigation is performed here. */
    public void onDelete(java.util.function.Consumer<SavedCanvas> callback) {
        deleteSelected = java.util.Objects.requireNonNull(callback);
    }
    public void removeSaved(String id) {
        all.removeIf(canvas -> canvas.id().equals(id));
        filter();
    }
    public void onDoubleClick(Runnable callback) { openSelected = callback == null ? () -> { } : callback; }
    public SavedCanvas selected() { return list.getSelectedValue(); }
    public Choice choice() { return choice; }
    public int resultCount() { return results.size(); }
    public void query(String query) { search.setText(query); }

    private void filter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        results.clear();
        for (SavedCanvas c : all) {
            if (c.name().toLowerCase(Locale.ROOT).contains(query)) results.addElement(c);
        }
        if (!results.isEmpty()) list.setSelectedIndex(0);
        count.setText(results.size() + (results.size() == 1 ? " canvas" : " canvases"));
        empty.setVisible(results.isEmpty());
    }

    @Override public void removeNotify() { thumbnails.close(); super.removeNotify(); }

    private final class Cell implements ListCellRenderer<SavedCanvas> {
        @Override public Component getListCellRendererComponent(JList<? extends SavedCanvas> list, SavedCanvas item,
                                                                  int index, boolean selected, boolean focused) {
            // opaque white row with a rounded, inset selection (macOS list), not a full-width bar
            JPanel panel = new JPanel(new BorderLayout(12, 0)) {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (selected) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(0, 122, 255, 30));
                        g2.fill(new java.awt.geom.RoundRectangle2D.Double(2, 2, getWidth() - 4, getHeight() - 4, 12, 12));
                        g2.dispose();
                    }
                }
            };
            panel.setOpaque(true);
            panel.setBackground(Color.WHITE);
            panel.setBorder(BorderFactory.createEmptyBorder(7, 12, 7, 10));
            JLabel miniature = new JLabel(list.isShowing() ? thumbnails.get(item.id(), () -> thumbnail(item.state())) : null);
            miniature.setPreferredSize(new Dimension(68,54));
            panel.add(miniature, BorderLayout.WEST);
            JPanel labels = new JPanel();
            labels.setOpaque(false);
            labels.setLayout(new BoxLayout(labels, BoxLayout.Y_AXIS));
            JLabel name = new JLabel(item.name());
            name.setFont(BoardTheme.semibold(14f));
            name.setForeground(BoardTheme.LABEL);
            JLabel date = new JLabel(WHEN.format(item.modifiedAt()));
            date.setForeground(new Color(111, 111, 117));
            date.setFont(BoardTheme.font(12f));
            labels.add(Box.createVerticalGlue());
            labels.add(name);
            labels.add(Box.createVerticalStrut(4));
            labels.add(date);
            labels.add(Box.createVerticalGlue());
            panel.add(labels, BorderLayout.CENTER);
            JComponent remove = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.translate(0, (getHeight() - 26) / 2.0);
                    view.glass.AppleLook.paintClose(g2, 26, 26, selected, false);
                    g2.dispose();
                }
            };
            remove.setToolTipText("Delete saved canvas");
            remove.setPreferredSize(new Dimension(32, 48));
            panel.add(remove, BorderLayout.EAST);
            return panel;
        }
    }

    /** Actual stroke/graph miniature, with stable bounds and no fake sample previews. */
    private static BufferedImage thumbnail(CanvasState state) {
            int width=68,height=54;
            BufferedImage miniature = new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = miniature.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(new Color(245, 246, 250));
            g.fillRoundRect(0, 0, width, height, 10, 10);
            g.setColor(new Color(219, 224, 233));
            g.drawRoundRect(0, 0, width-1, height-1, 10, 10);
            double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
            for (var stroke : state.ink()) for (var p : stroke.points()) {
                minX = Math.min(minX, p.x()); minY = Math.min(minY, p.y());
                maxX = Math.max(maxX, p.x()); maxY = Math.max(maxY, p.y());
            }
            for (var asset : state.assets()) {
                minX = Math.min(minX, asset.x()); minY = Math.min(minY, asset.y());
                maxX = Math.max(maxX, asset.x()+asset.width()); maxY = Math.max(maxY, asset.y()+asset.height());
            }
            if (Double.isFinite(minX)) {
                double scale = Math.min(52.0 / Math.max(1, maxX-minX), 39.0 / Math.max(1, maxY-minY));
                double dx = 8 + (52 - (maxX-minX)*scale) / 2.0;
                double dy = 7 + (39 - (maxY-minY)*scale) / 2.0;
                g.translate(dx, dy);
                g.scale(scale, scale);
                g.translate(-minX, -minY);
                for (var asset : state.assets()) {
                    try {
                        BufferedImage image = ImageIO.read(new ByteArrayInputStream(asset.png()));
                        if (image != null) g.drawImage(image, (int)asset.x(), (int)asset.y(),
                                Math.max(1,(int)asset.width()), Math.max(1,(int)asset.height()),null);
                    } catch (IOException ignored) { /* unreadable preview never blocks opening the saved file */ }
                }
                for (var stroke : state.ink()) {
                    g.setColor(new Color(stroke.argb(), true));
                    g.setStroke(new BasicStroke((float)Math.min(5, Math.max(.7, 1.5/scale)),
                            BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    var pts = stroke.points();
                    for (int i=1;i<pts.size();i++) {
                        g.drawLine(pts.get(i-1).x(), pts.get(i-1).y(), pts.get(i).x(), pts.get(i).y());
                    }
                }
            } else {
                g.setColor(new Color(120, 132, 153));
                g.setStroke(new BasicStroke(1.6f));
                g.drawLine(19, 34, 47, 34);
                g.drawLine(25, 36, 25, 14);
                g.drawArc(26, 18, 23, 23, 18, 130);
            }
            g.dispose();
            return miniature;
    }
}
