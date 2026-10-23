package dev.ytmlyrics.hook;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActionDrawScopeTest {
    @Test public void nativeDrawOutsideLyricActionHasNoTint() throws Throwable {
        var scope=new ActionDrawScope();assertNull(scope.color());
        assertEquals("native",scope.draw(null,()->{assertNull(scope.color());return "native";}));
        assertNull(scope.color());
    }
    @Test public void childrenInheritAndNestedActionsRestorePreviousColor() throws Throwable {
        var scope=new ActionDrawScope();
        scope.draw(0xffeeeeee,()->{
            assertEquals(Integer.valueOf(0xffeeeeee),scope.color());
            scope.draw(null,()->{assertEquals(Integer.valueOf(0xffeeeeee),scope.color());return null;});
            scope.draw(0xff111111,()->{assertEquals(Integer.valueOf(0xff111111),scope.color());return null;});
            assertEquals(Integer.valueOf(0xffeeeeee),scope.color());return null;
        });
        assertNull(scope.color());
    }
    @Test public void failedDrawStillRestoresScopeAndOtherThreadsStayUntinted() throws Throwable {
        var scope=new ActionDrawScope();
        try {
            scope.draw(0xffeeeeee,()->{
                var probe=new java.util.concurrent.FutureTask<>(scope::color);
                Thread worker=new Thread(probe);worker.start();assertNull(probe.get());
                throw new IllegalStateException("draw failure");
            });fail();
        } catch(IllegalStateException expected){assertEquals("draw failure",expected.getMessage());}
        assertNull(scope.color());
    }
}
