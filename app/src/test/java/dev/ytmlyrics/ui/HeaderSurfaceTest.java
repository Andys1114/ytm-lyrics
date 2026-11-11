package dev.ytmlyrics.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static dev.ytmlyrics.ui.HeaderSurface.Style.*;
import static org.junit.Assert.*;

public class HeaderSurfaceTest {
    private static final class Node {
        final int id;
        final boolean icon;
        final List<Node> children = new ArrayList<>();
        Node parent;
        String background;
        Node(int id, boolean icon, String background) { this.id=id; this.icon=icon; this.background=background; }
        Node add(int id, boolean icon, String background) {
            var child = new Node(id, icon, background);
            child.parent=this; children.add(child);
            return child;
        }
    }
    private static final LyricsHeaderChrome.Tree<Node> TREE = new LyricsHeaderChrome.Tree<>() {
        public Node parent(Node view) { return view.parent; }
        public int id(Node view) { return view.id; }
        public boolean icon(Node view) { return view.icon; }
        public Node find(Node view, int id) {
            if (view.id == id) return view;
            for (var child : view.children) {
                var match = find(child,id);
                if (match != null) return match;
            }
            return null;
        }
    };
    private static HeaderSurface<Node,String> painter() {
        return new HeaderSurface<>(new HeaderSurface.Port<>() {
            public String background(Node view) { return view.background; }
            public void background(Node view, String value) { view.background=value; }
            public String styled(Node view, String original, HeaderSurface.Style style, int color) {
                return switch (style) { case SURFACE -> "album-"+color; case CLEAR -> "clear"; case TOUCH -> "ripple"; };
            }
        });
    }

    @Test public void nativeCloseIsBesideTitleSlotAndTheDragStripSharesItsBackground() {
        // VVR / K-a supply the outer shell, fup supplies each independent header holder.
        var sheet = new Node(0,false,null);
        var mini = sheet.add(0,false,"mini");
        var surface = sheet.add(0x7f0b05a2,false,"native-shell");
        var column = surface.add(0,false,null);
        var drag = column.add(0x7f0b0122,true,null);
        var wrapper = column.add(0x7f0b08b2,false,null);
        var border = column.add(0x7f0b08b1,false,"separator");
        var holder = wrapper.add(0,false,"native-holder");
        var row = holder.add(0,false,null);
        var slot = row.add(0x7f0b08b0,false,null);
        var close = row.add(0x7f0b026a,true,"native-close");
        var title = slot.add(0x7f0b05d0,false,null);
        var body = sheet.add(0x7f0b08ae,false,"body");
        var located = LyricsHeaderChrome.locate(title,TREE);
        assertEquals(new LyricsHeaderChrome.Shell<>(surface,holder,close,border),located);
        assertNull(LyricsHeaderChrome.locate(body,TREE));
        var painter = painter();
        painter.paint(List.of(new HeaderSurface.Target<>(located.surface(),SURFACE),
                new HeaderSurface.Target<>(located.holder(),CLEAR),
                new HeaderSurface.Target<>(located.close(),TOUCH),
                new HeaderSurface.Target<>(located.border(),CLEAR)),7);
        assertEquals("album-7",drag.parent.parent.background);
        assertEquals("clear",holder.background);
        assertEquals("ripple",close.background);
        assertEquals("body",body.background);
        assertEquals("mini",mini.background);
        painter.paint(List.of(),7);
        assertEquals("native-shell",surface.background);
        assertEquals("native-holder",holder.background);
        assertEquals("native-close",close.background);
        assertEquals("separator",border.background);
    }

    @Test public void hostRestylingUpdatesTheRestorePointEvenWhenAlbumColorStaysTheSame() {
        var view = new Node(0,false,"native");
        var painter = painter();
        var targets = List.of(new HeaderSurface.Target<>(view,SURFACE));
        painter.paint(targets,7);
        view.background = "host-updated";
        painter.paint(targets,7);
        assertEquals("album-7",view.background);
        assertTrue(painter.restore(view));
        assertEquals("host-updated",view.background);
        painter.paint(targets,7);
        view.background = "other-panel";
        painter.paint(List.of(),7);
        assertEquals("other-panel",view.background);
    }

    @Test public void changingHoldersKeepsSharedShellAndRestoresOldCloseButton() {
        var shell = new Node(0,false,"native");
        var left = shell.add(0,true,"left");
        var right = shell.add(0,true,"right");
        var painter = painter();
        var shared = new HeaderSurface.Target<>(shell,SURFACE);
        painter.paint(List.of(shared,new HeaderSurface.Target<>(left,TOUCH)),7);
        String applied = shell.background;
        painter.paint(List.of(shared,new HeaderSurface.Target<>(right,TOUCH)),7);
        assertSame(applied,shell.background);
        assertEquals("left",left.background);
        assertEquals("ripple",right.background);
        painter.paint(List.of(),7);
        assertEquals("native",shell.background);
        assertEquals("right",right.background);
    }
}
