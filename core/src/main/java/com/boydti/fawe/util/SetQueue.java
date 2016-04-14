package com.boydti.fawe.util;

import com.boydti.fawe.Fawe;
import com.boydti.fawe.config.Settings;
import com.boydti.fawe.object.FaweChunk;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class SetQueue {

    /**
     * The implementation specific queue
     */
    public static final SetQueue IMP = new SetQueue();

    public final ArrayDeque<FaweQueue> activeQueues;
    public final ArrayDeque<FaweQueue> inactiveQueues;

    /**
     * Used to calculate elapsed time in milliseconds and ensure block placement doesn't lag the server
     */
    private long last;
    private long secondLast;
    private long lastSuccess;
    
    /**
     * A queue of tasks that will run when the queue is empty
     */
    private final ArrayDeque<Runnable> runnables = new ArrayDeque<>();


    public SetQueue() {
        activeQueues = new ArrayDeque();
        inactiveQueues = new ArrayDeque<>();
        TaskManager.IMP.repeat(new Runnable() {
            @Override
            public void run() {
                if (!MemUtil.isMemoryFree()) {
                    final int mem = MemUtil.calculateMemory();
                    if (mem != Integer.MAX_VALUE) {
                        if ((mem <= 1) && Settings.ENABLE_HARD_LIMIT) {
                            for (FaweQueue queue : getQueues()) {
                                queue.saveMemory();
                            }
                            return;
                        }
                        if (SetQueue.this.forceChunkSet()) {
                            System.gc();
                        } else {
                            SetQueue.this.tasks();
                        }
                        return;
                    }
                }
                final long free = Settings.ALLOCATE + 50 + Math.min((50 + SetQueue.this.last) - (SetQueue.this.last = System.currentTimeMillis()), SetQueue.this.secondLast - System.currentTimeMillis());
                do {
                    final FaweChunk<?> current = next();
                    if (current == null) {
                        lastSuccess = last;
                        SetQueue.this.tasks();
                        return;
                    }
                } while (((SetQueue.this.secondLast = System.currentTimeMillis()) - SetQueue.this.last) < free);
            }
        }, 1);
    }

    public void enqueue(FaweQueue queue) {
        inactiveQueues.remove(queue);
        activeQueues.add(queue);
    }

    public List<FaweQueue> getQueues() {
        return new ArrayList<>(activeQueues);
    }

    public FaweQueue getNewQueue(String world, boolean autoqueue) {
        FaweQueue queue = Fawe.imp().getNewQueue(world);
        if (autoqueue) {
            inactiveQueues.add(queue);
        }
        return queue;
    }

    public FaweChunk<?> next() {
        while (activeQueues.size() > 0) {
            FaweQueue queue = activeQueues.poll();
            final FaweChunk<?> set = queue.next();
            if (set != null) {
                activeQueues.add(queue);
                return set;
            }
        }
        if (inactiveQueues.size() > 0) {
            ArrayList<FaweQueue> tmp = new ArrayList<>(inactiveQueues);
            if (Settings.QUEUE_MAX_WAIT != -1) {
                long now = System.currentTimeMillis();
                long diff = now - lastSuccess;
                if (diff > Settings.QUEUE_MAX_WAIT) {
                    for (FaweQueue queue : tmp) {
                        FaweChunk result = queue.next();
                        if (result != null) {
                            return result;
                        }
                    }
                    if (diff > Settings.QUEUE_DISCARD_AFTER) {
                        // These edits never finished
                        inactiveQueues.clear();
                    }
                    return null;
                }
            }
            if (Settings.QUEUE_SIZE != -1) {
                int total = 0;
                for (FaweQueue queue : tmp) {
                    total += queue.size();
                }
                if (total > Settings.QUEUE_SIZE) {
                    for (FaweQueue queue : tmp) {
                        FaweChunk result = queue.next();
                        if (result != null) {
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }

    public boolean forceChunkSet() {
        return next() != null;
    }

    public boolean isDone() {
        return activeQueues.size() == 0 && inactiveQueues.size() == 0;
    }

    public boolean addTask(final Runnable whenDone) {
        if (this.isDone()) {
            // Run
            this.tasks();
            if (whenDone != null) {
                whenDone.run();
            }
            return true;
        }
        if (whenDone != null) {
            this.runnables.add(whenDone);
        }
        return false;
    }

    public boolean tasks() {
        if (this.runnables.size() == 0) {
            return false;
        }
        final ArrayDeque<Runnable> tmp = this.runnables.clone();
        this.runnables.clear();
        for (final Runnable runnable : tmp) {
            runnable.run();
        }
        return true;
    }
}
