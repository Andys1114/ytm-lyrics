package dev.ytmlyrics.ui;

/** UI-thread lifecycle and a finite renderer-process recovery budget, independent of Android. */
final class RendererSession {
    interface Driver {
        void create();
        void destroy();
        void schedule(Runnable task, long delayMs);
        void cancel(Runnable task);
        void changed();
    }
    private final Driver driver;
    private boolean attached, active, running, ready, blocked, pending;
    private int attempts;
    private Runnable scheduled;
    private String status = "正在启动逐字显示";

    RendererSession(Driver driver) { this.driver = driver; }
    boolean ready() { return ready; }
    String status() { return status; }
    int attempts() { return attempts; }
    void attached(boolean value) {
        attached = value;
        if (!value) { cancel(); stop(); }
        else resume();
    }
    void active(boolean value) {
        active = value;
        if (!value) cancel(); else resume();
    }
    void readyPage() {
        if (!running) return;
        ready = true;
        status = "";
        // A ready signal does not refill the budget: ready/crash loops must still stop.
        driver.changed();
    }
    void failed(String reason, boolean processGone) {
        if (!running) return;
        stop(); // Destroy the affected WebView before onRenderProcessGone returns.
        blocked = true;
        pending = processGone && attempts < 2;
        status = pending ? "逐字显示中断，待自动恢复（" + (attempts + 1) + "/2）"
                : processGone ? "逐字显示恢复已达上限，点“重试”再试"
                : "逐字显示不可用：" + reason;
        driver.changed();
        resume();
    }
    void retry() {
        if (running && ready) return;
        cancel(); stop();
        attempts = 0; blocked = false; pending = false;
        status = "正在重新启动逐字显示";
        driver.changed();
        resume();
    }
    private void resume() {
        if (!attached || !active || running || scheduled != null) return;
        if (pending) {
            Runnable task = new Runnable() {
                @Override public void run() {
                    if (scheduled != this) return;
                    scheduled = null;
                    if (!attached || !active || !pending) return;
                    pending = false; attempts++;
                    status = "正在自动恢复逐字显示（" + attempts + "/2）";
                    start();
                }
            };
            scheduled = task;
            driver.schedule(task, attempts == 0 ? 1000 : 3000);
        } else if (!blocked) start();
    }
    private void start() {
        running = true; ready = false; blocked = false;
        if (status.isEmpty()) status = "正在启动逐字显示";
        driver.changed(); driver.create();
    }
    private void stop() {
        ready = false;
        if (running) { running = false; driver.destroy(); }
    }
    private void cancel() {
        if (scheduled != null) { driver.cancel(scheduled); scheduled = null; }
    }
}
