package dev.ytmlyrics.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExclusiveContentTest {
    private static final class Surface { String text; Surface(String text){this.text=text;} }
    @Test public void swapsActualMountedContentAndRestoresUpdatedNativeObject() {
        List<Surface> attached=new ArrayList<>();List<String> events=new ArrayList<>();
        var slot=new ExclusiveContent<>(new ExclusiveContent.Mount<Surface>() {
            @Override public void add(Surface view){assertTrue(attached.isEmpty());attached.add(view);events.add("add");}
            @Override public void remove(Surface view){assertTrue(attached.remove(view));events.add("remove");}
        });
        var nativeView=new Surface("native lyric");var custom=new Surface("word lyric");
        slot.show(nativeView);slot.show(custom);
        assertEquals(List.of(custom),attached);
        nativeView.text="new song loaded while detached";
        slot.show(custom); // Source progress must not detach/recreate the current page.
        assertEquals(3,events.size());
        slot.show(nativeView);assertSame(nativeView,attached.get(0));
        assertEquals("new song loaded while detached",attached.get(0).text);
        assertEquals(List.of("add","remove","add","remove","add"),events);
        slot.show(null);assertTrue(attached.isEmpty());
    }
    @Test public void attachmentReentryDoesNotMountTheSameSurfaceTwice() {
        List<Object> attached=new ArrayList<>();
        @SuppressWarnings("unchecked") ExclusiveContent<Object>[] holder=new ExclusiveContent[1];
        holder[0]=new ExclusiveContent<>(new ExclusiveContent.Mount<Object>() {
            @Override public void add(Object view){attached.add(view);holder[0].show(view);}
            @Override public void remove(Object view){attached.remove(view);}
        });
        Object view=new Object();holder[0].show(view);assertEquals(List.of(view),attached);
    }
}
