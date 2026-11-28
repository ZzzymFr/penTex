package pdf_editor.view;

import pdf_editor.data_access.NoteRenderer;
import pdf_editor.data_access.PdfScreenRasterCache;
import pdf_editor.domain.PdfDocument;

import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/** Offscreen, real mouse event and Java2D checks. No desktop window or user data. */
public final class PdfDragStartSmoke {
    private PdfDragStartSmoke() { }

    public static void main(String[] args) throws Exception {
        verify();
        System.out.println("PDF selection and first-drag regression passed");
    }

    public static void verify() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                damageAndRasters();
                for (double zoom : new double[]{1, 2, 3}) drag(zoom, 1);
                for (double dpi : new double[]{1.25, 1.5, 2}) drag(3, dpi);
                imageAndGroup();
                resize();
            } catch (Exception ex) { throw new IllegalStateException(ex); }
        });
    }

    private static void damageAndRasters() throws Exception {
        var f = PdfViewportPaintSmoke.fixture();
        var items = f.controller().placements();
        var spans = f.controller().body();
        var note = items.get(2);
        var moved = note.withBounds(note.x()+12, note.y()+9, note.width(), note.height());
        var changed = new ArrayList<>(items); changed.set(2, moved);
        var visible = new Rectangle(0,0,1200,950);
        var old = state(f, items, Set.of());
        var selected = state(f, items, Set.of(note.id()));
        Rectangle pick = PdfPageDamage.between(old, selected, visible);
        check(!pick.isEmpty() && !pick.equals(visible), "Selection redraws only affected bounds");
        Rectangle move = PdfPageDamage.between(selected, state(f, changed, Set.of(note.id())), visible);
        check(move.contains(pick) && !move.equals(visible), "Old and new note positions are damaged");
        var wrap = items.get(0);
        var wrapped = new ArrayList<>(items);
        wrapped.set(0, wrap.withBounds(wrap.x()+15,wrap.y(),wrap.width(),wrap.height()));
        check(PdfPageDamage.between(old,state(f,wrapped,Set.of()),visible).equals(visible), "Wrap movement repaints flowing body");
        var reordered = new ArrayList<>(items); java.util.Collections.swap(reordered,0,1);
        check(PdfPageDamage.between(old,state(f,reordered,Set.of()),visible).equals(visible), "Stacking change repaints safely");
        var body = new PdfBodyRasterCache();
        BufferedImage first = body.render(spans, items,447,698);
        check(first == body.render(spans,changed,447,698), "Moving a note reuses body pixels");
        check(first != body.render(spans,wrapped,447,698), "Wrap geometry invalidates body raster");
        body.clear();
        var cache = new PdfScreenRasterCache();
        cache.duringPaint(() -> {
            BufferedImage a = NoteRenderer.render(note,420,300);
            check(a == NoteRenderer.render(moved,420,300), "Note movement does not typeset again");
            check(a != NoteRenderer.render(note.withNote("New text",0,16),420,300), "Changed note content invalidates pixels");
            check(a != NoteRenderer.render(note.withBounds(note.x(),note.y(),200,100),420,300), "Changed logical width invalidates wrapping");
        });
        cache.clear();
    }

    private static PdfPageDamage.State state(PdfViewportPaintSmoke.Fixture f,
                                             List<PdfDocument.Placement> items, Set<String> selected) {
        return new PdfPageDamage.State(0,3,f.page().getSize(),f.page().getBackground(),items,
                f.controller().body(),selected,false,null);
    }

    private static void drag(double zoom, double dpi) throws Exception {
        var f = PdfViewportPaintSmoke.fixture();
        var page = f.page(); var c = f.controller();
        JViewport viewport = viewport(page,zoom);
        var note = c.placements().get(2);
        render(page, viewport, dpi, false); // displayed page is warm before the first press
        int x = screen(note.x()+25,zoom), y = screen(note.y()+25,zoom);
        event(page, MouseEvent.MOUSE_PRESSED,x,y);
        check(note.id().equals(c.selected().id()),"Pointer selects actual note");
        compare(page,viewport,dpi,"Selection pixels at "+zoom+" / "+dpi);
        int dx = (int)(12*zoom), dy = (int)(9*zoom);
        event(page,MouseEvent.MOUSE_DRAGGED,x+dx,y+dy);
        check(c.selected().x() == note.x()+12 && c.selected().y() == note.y()+9,
                "First drag event updates position, without another mouse event");
        compare(page,viewport,dpi,"First drag pixels at "+zoom+" / "+dpi);
        event(page,MouseEvent.MOUSE_DRAGGED,x+2*dx,y+2*dy);
        compare(page,viewport,dpi,"Continued drag pixels");
        event(page,MouseEvent.MOUSE_RELEASED,x+2*dx,y+2*dy);
        compare(page,viewport,dpi,"Release and body reflow pixels");
        check(c.undo(),"Drag remains undoable");
        var restored = c.placements().stream().filter(p -> p.id().equals(note.id())).findFirst().orElseThrow();
        check(restored.x()==note.x() && restored.y()==note.y(),"Undo restores original position");
        compare(page,viewport,dpi,"Undo pixels");
    }

    private static void imageAndGroup() throws Exception {
        var f=PdfViewportPaintSmoke.fixture(); var page=f.page(); var c=f.controller();
        JViewport view=viewport(page,3);
        render(page,view,1,false);
        var image=c.placements().get(1); // behind-text image, disjoint from the note at this point
        int x=screen(image.x()+40,3), y=screen(image.y()+45,3);
        event(page,MouseEvent.MOUSE_PRESSED,x,y);
        check(c.selected().id().equals(image.id()),"Behind-text item remains hittable");
        event(page,MouseEvent.MOUSE_DRAGGED,x+21,y+15);
        check(c.selected().x()==image.x()+7 && c.selected().y()==image.y()+5,"Image moves on first event");
        compare(page,view,1,"Behind-text image drag retains stacking");
        event(page,MouseEvent.MOUSE_RELEASED,x+21,y+15);
        var originals=c.placements();
        c.selectAll();
        var note=originals.get(2);
        x=screen(note.x()+25,3); y=screen(note.y()+25,3);
        event(page,MouseEvent.MOUSE_PRESSED,x,y);
        event(page,MouseEvent.MOUSE_DRAGGED,x+18,y+12);
        for (int i=0;i<originals.size();i++) {
            check(c.placements().get(i).x()==originals.get(i).x()+6
                    && c.placements().get(i).y()==originals.get(i).y()+4,"Group moves together");
        }
        compare(page,view,1,"Group including wrapping image retains live layout");
        event(page,MouseEvent.MOUSE_RELEASED,x+18,y+12);
        compare(page,view,1,"Group release pixels");
    }

    private static void resize() throws Exception {
        var f=PdfViewportPaintSmoke.fixture();var page=f.page();var c=f.controller();
        JViewport view=viewport(page,3);
        var note=c.placements().get(2);
        render(page,view,1.5,false);
        int x=screen(note.x()+note.width()-2,3),y=screen(note.y()+note.height()-2,3);
        event(page,MouseEvent.MOUSE_PRESSED,x,y);
        check(c.selected().id().equals(note.id()),"Resize handle selects the note");
        event(page,MouseEvent.MOUSE_DRAGGED,x+30,y+24);
        check(c.selected().width()==note.width()+10 && c.selected().height()==note.height()+8,"Resize updates real size immediately");
        compare(page,view,1.5,"Resized note paints original typography and handle");
        event(page,MouseEvent.MOUSE_RELEASED,x+30,y+24);
        check(c.undo(),"Resize remains undoable");
        compare(page,view,1.5,"Resize Undo");
    }

    private static JViewport viewport(PdfPageCanvas page,double zoom) {
        page.setZoom(zoom);
        JViewport view=new JViewport();
        view.setSize(new Dimension(900,740)); view.setView(page);
        view.setViewSize(page.getPreferredSize()); view.setViewPosition(new Point(0,0));
        return view;
    }
    private static int screen(float value,double zoom) {return PdfPageCanvas.PAGE_MARGIN+(int)Math.round(value*zoom);}
    private static void event(PdfPageCanvas page,int id,int x,int y) {
        int modifiers = id==MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id==MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        page.dispatchEvent(new MouseEvent(page,id,System.currentTimeMillis(),modifiers,x,y,1,false,button));
    }
    private static void compare(PdfPageCanvas page,JViewport view,double dpi,String message) {
        BufferedImage a=render(page,view,dpi,false), b=render(page,view,dpi,true);
        int w=a.getWidth(),h=a.getHeight();
        check(Arrays.equals(a.getRGB(0,0,w,h,null,0,w),b.getRGB(0,0,w,h,null,0,w)),message);
    }
    private static BufferedImage render(PdfPageCanvas page,JViewport view,double dpi,boolean direct) {
        BufferedImage image=new BufferedImage((int)Math.ceil(view.getWidth()*dpi),
                (int)Math.ceil(view.getHeight()*dpi),BufferedImage.TYPE_INT_RGB);
        Graphics2D g=image.createGraphics();
        try {
            g.scale(dpi,dpi);g.setClip(0,0,view.getWidth(),view.getHeight());
            Point p=view.getViewPosition();g.translate(-p.x,-p.y);
            if(direct)page.print(g);else page.paint(g);
        } finally {g.dispose();}
        return image;
    }
    private static void check(boolean ok,String message) {if(!ok)throw new AssertionError(message);}
}
