package dev.ytmlyrics.ui;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class RendererSessionTest {
    private static final class Driver implements RendererSession.Driver {
        final List<String> events=new ArrayList<>();
        Runnable task;
        long delay;
        int creates, destroys;
        @Override public void create(){creates++;events.add("create");}
        @Override public void destroy(){destroys++;events.add("destroy");}
        @Override public void changed(){events.add("changed");}
        @Override public void schedule(Runnable task,long delay){this.task=task;this.delay=delay;events.add("schedule");}
        @Override public void cancel(Runnable task){if(this.task==task)this.task=null;}
        void run(){Runnable next=task;task=null;assertNotNull(next);next.run();}
    }
    @Test public void startsOnlyWhenAttachedAndActive() {
        var d=new Driver();var s=new RendererSession(d);
        s.attached(true);assertEquals(0,d.creates);
        s.active(true);assertEquals(1,d.creates);assertFalse(s.ready());
        s.active(true);s.attached(true);assertEquals(1,d.creates);
        s.readyPage();assertTrue(s.ready());
        s.active(false);assertTrue(s.ready()); // Hidden healthy pages pause, not restart.
        s.attached(false);assertFalse(s.ready());assertEquals(1,d.destroys);
    }
    @Test public void destroysImmediatelyAndTriesOnlyTwiceWithBackoff() {
        var d=new Driver();var s=started(d);
        s.failed("crash",true);
        assertEquals(1,d.destroys);assertEquals(1,d.creates);assertFalse(s.ready());
        assertTrue(d.events.indexOf("destroy")<d.events.indexOf("schedule"));
        assertEquals(1000,d.delay);d.run();assertEquals(2,d.creates);
        s.readyPage();s.failed("crash again",true);
        assertEquals(3000,d.delay);d.run();assertEquals(3,d.creates);
        s.readyPage();s.failed("third crash",true);
        assertNull(d.task);assertEquals(2,s.attempts());assertTrue(s.status().contains("上限"));
        s.active(false);s.active(true);s.attached(false);s.attached(true);
        assertNull(d.task);assertEquals(3,d.creates);
    }
    @Test public void hiddenOrDetachedPagesDoNotSpendRecoveryAttempts() {
        var d=new Driver();var s=started(d);
        s.active(false);s.failed("system kill",true);
        assertNull(d.task);assertEquals(0,s.attempts());
        s.active(true);Runnable stale=d.task;s.attached(false);
        assertNull(d.task);stale.run();assertEquals(1,d.creates);
        s.attached(true);assertEquals(1000,d.delay);d.run();assertEquals(1,s.attempts());
        // Detaching a new page before ready must not make it permanently blocked.
        s.attached(false);s.attached(true);assertEquals(3,d.creates);
    }
    @Test public void manualRetryCancelsOldTimerAndRefillsBudget() {
        var d=new Driver();var s=started(d);
        s.failed("crash",true);Runnable stale=d.task;s.retry();
        assertNull(d.task);assertEquals(2,d.creates);assertEquals(0,s.attempts());
        stale.run();assertEquals(2,d.creates);
        s.readyPage();s.retry();assertEquals(2,d.creates); // Healthy retry only re-queries sources.
        s.failed("crash",true);d.run();assertEquals(1,s.attempts());
    }
    @Test public void ordinaryLoadOrScriptErrorsNeedManualRetry() {
        var d=new Driver();var s=started(d);
        s.failed("HTTP 403",false);
        assertTrue(s.status().contains("HTTP 403"));assertNull(d.task);
        s.active(false);s.active(true);assertEquals(1,d.creates);
        s.readyPage();assertFalse(s.ready()); // Late ready from a stopped page is ignored.
        s.retry();assertEquals(2,d.creates);s.readyPage();assertTrue(s.ready());
    }
    @Test public void duplicateFailureDoesNotScheduleMoreOrDestroyTwice() {
        var d=new Driver();var s=started(d);
        s.failed("crash",true);Runnable pending=d.task;
        s.failed("late callback",true);
        assertSame(pending,d.task);assertEquals(1,d.destroys);
        s.active(false);s.active(true);Runnable fresh=d.task;
        pending.run();assertEquals(1,d.creates);assertSame(fresh,d.task);
        d.run();assertEquals(2,d.creates);
    }
    @Test public void successfulRecoveredPageReopensWithoutRefillingBudget() {
        var d=new Driver();var s=started(d);
        s.failed("crash",true);d.run();s.readyPage();
        s.attached(false);s.attached(true);assertEquals(3,d.creates);
        assertTrue(s.status().contains("启动"));
        assertEquals(1,s.attempts());
        s.failed("second crash",true);d.run();s.failed("third crash",true);
        assertNull(d.task);assertEquals(2,s.attempts());
    }
    private static RendererSession started(Driver d) {
        var s=new RendererSession(d);s.active(true);s.attached(true);s.readyPage();return s;
    }
}
