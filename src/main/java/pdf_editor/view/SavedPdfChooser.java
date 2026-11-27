package pdf_editor.view;

import pdf_editor.use_case.PdfDocumentRepository.Summary;
import view.glass.GlassDialogs;
import view.glass.GlassButton;
import view.glass.GlassTextField;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Existing Glass dialog hosting a lightweight list of document thumbnails. */
final class SavedPdfChooser {
    private SavedPdfChooser() { }
    record Choice(UUID id, boolean rename) { }
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd  HH:mm", Locale.ROOT);
    static String formatModifiedAt(java.time.Instant time, ZoneId zone) { return DATE.withZone(zone).format(time); }
    static Choice choose(Component parent, List<Summary> entries,
                         java.util.function.BiPredicate<Component, Summary> delete) {
        List<Summary> all = new java.util.ArrayList<>(entries);
        boolean[] renameRequested = {false};
        JPanel panel = new JPanel(new BorderLayout(8, 8)); panel.setOpaque(false);
        JTextField search = new GlassTextField(24, "Search PDF documents"); search.setToolTipText("Search PDF documents");
        panel.add(search, BorderLayout.NORTH);
        DefaultListModel<Summary> model = new DefaultListModel<>();
        JList<Summary> list = new JList<>(model); list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setFixedCellHeight(92);
        list.setOpaque(false);
        view.glass.AsyncThumbnailCache<UUID> thumbnails = new view.glass.AsyncThumbnailCache<>(list::repaint);
        list.setCellRenderer((source, value, index, selected, focused) -> {
            JPanel row = new JPanel(new BorderLayout(10, 0)) {
                @Override protected void paintComponent(Graphics g) {
                    super.paintComponent(g);
                    if (selected) {
                        Graphics2D g2 = (Graphics2D) g.create();
                        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        g2.setColor(new Color(0, 122, 255, 30));
                        g2.fillRoundRect(2, 2, getWidth() - 4, getHeight() - 4, 12, 12);
                        g2.dispose();
                    }
                }
            };
            row.setBorder(BorderFactory.createEmptyBorder(6, 10, 6, 10));
            row.setBackground(Color.WHITE);
            JLabel image = new JLabel(list.isShowing() ? thumbnails.get(value.id(), () -> thumbnail(value)) : null); image.setPreferredSize(new Dimension(60, 80));
            JPanel text = new JPanel(); text.setOpaque(false); text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            JLabel title = new JLabel(value.name()); title.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
            JLabel detail = new JLabel(value.pages() + (value.pages() == 1 ? " page" : " pages") + " · " + formatModifiedAt(value.modifiedAt(), ZoneId.systemDefault()));
            title.setForeground(new Color(35, 43, 56));
            detail.setFont(new Font(Font.DIALOG, Font.PLAIN, 12)); detail.setForeground(new Color(111, 111, 117));
            text.add(Box.createVerticalGlue()); text.add(title); text.add(detail); text.add(Box.createVerticalGlue());
            JComponent remove = new JComponent() {
                @Override protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.translate(0, (getHeight() - 26) / 2.0);
                    view.glass.AppleLook.paintClose(g2, 26, 26, selected, false);
                    g2.dispose();
                }
            };
            remove.setPreferredSize(new Dimension(32, 48));
            row.add(image, BorderLayout.WEST); row.add(text, BorderLayout.CENTER);
            row.add(remove, BorderLayout.EAST); return row;
        });
        Runnable filter = () -> {
            UUID selected = list.getSelectedValue() == null ? null : list.getSelectedValue().id();
            model.clear(); String query = search.getText().strip().toLowerCase(Locale.ROOT);
            for (Summary entry : all) if (entry.name().toLowerCase(Locale.ROOT).contains(query)) {
                model.addElement(entry); if (entry.id().equals(selected)) list.setSelectedValue(entry, true);
            }
            if (list.getSelectedIndex() < 0 && !model.isEmpty()) list.setSelectedIndex(0);
        };
        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { filter.run(); }
            @Override public void removeUpdate(DocumentEvent e) { filter.run(); }
            @Override public void changedUpdate(DocumentEvent e) { filter.run(); }
        });
        JScrollPane scroll = new JScrollPane(list); scroll.setPreferredSize(new Dimension(440, 290));
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setBackground(Color.WHITE); scroll.getVerticalScrollBar().setUnitIncrement(28);
        panel.add(scroll, BorderLayout.CENTER);
        filter.run();
        GlassButton rename = GlassDialogs.actionButton("Rename", false);
        rename.setEnabled(list.getSelectedValue() != null);
        list.addListSelectionListener(e -> rename.setEnabled(list.getSelectedValue() != null));
        GlassDialogs.setLeadingActions(panel, rename);
        list.setToolTipText("Click × to delete; double-click to open");
        boolean accepted;
        try {
            accepted = GlassDialogs.choose(parent, "Open PDF Document", panel, "Open", accept -> {
            rename.addActionListener(e -> {
                if (list.getSelectedValue() != null) { renameRequested[0] = true; accept.run(); }
            });
            list.addMouseListener(new MouseAdapter() {
                @Override public void mouseClicked(MouseEvent e) {
                    int index = list.locationToIndex(e.getPoint());
                    if (index < 0 || !list.getCellBounds(index,index).contains(e.getPoint())) return;
                    if (e.getX() >= list.getWidth() - 44) {
                        // A double-click on × must never delete the next row after the first removal.
                        if (e.getClickCount() != 1) return;
                        Summary selected = model.get(index);
                        if (delete.test(panel, selected)) {
                            all.removeIf(entry -> entry.id().equals(selected.id()));
                            filter.run();
                        }
                        return;
                    }
                    if (e.getClickCount() == 2) accept.run();
                }
            });
            list.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "open-pdf");
            list.getActionMap().put("open-pdf", new AbstractAction() {
                @Override public void actionPerformed(java.awt.event.ActionEvent e) { if (list.getSelectedValue() != null) accept.run(); }
            });
            });
        } finally { thumbnails.close(); }
        return accepted && list.getSelectedValue() != null ? new Choice(list.getSelectedValue().id(), renameRequested[0]) : null;
    }
    private static java.awt.image.BufferedImage thumbnail(Summary entry) {
        byte[] bytes = entry.thumbnail();
        if (bytes != null) try {
            var image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                var scaled = new java.awt.image.BufferedImage(54,76,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics=scaled.createGraphics();
                graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                graphics.drawImage(image,0,0,54,76,null); graphics.dispose(); return scaled;
            }
        } catch (IOException ignored) { }
        var empty = new java.awt.image.BufferedImage(54, 76, java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g = empty.createGraphics(); g.setColor(Color.WHITE); g.fillRect(0, 0, 54, 76);
        g.setColor(Color.LIGHT_GRAY); g.drawRect(0, 0, 53, 75); g.dispose(); return empty;
    }
}
