package com.jap.core.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class AgentPauseManager {

    private static final Logger log = LoggerFactory.getLogger(AgentPauseManager.class);

    private final Map<String, PausePoint> pausedAgents = new ConcurrentHashMap<>();

    public static class PausePoint {
        private final Lock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private volatile boolean resumed = false;
        private volatile boolean cancelled = false;

        public boolean await(long timeoutMs) throws InterruptedException {
            lock.lock();
            try {
                if (resumed) return true;
                if (cancelled) return false;
                return condition.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } finally {
                lock.unlock();
            }
        }

        public void resume() {
            lock.lock();
            try {
                resumed = true;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public void cancel() {
            lock.lock();
            try {
                cancelled = true;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        public boolean isResumed() {
            return resumed;
        }

        public boolean isCancelled() {
            return cancelled;
        }
    }

    public PausePoint createPausePoint(String taskId) {
        PausePoint point = new PausePoint();
        pausedAgents.put(taskId, point);
        log.info("[{}] Created pause point for agent", taskId);
        return point;
    }

    public boolean resume(String taskId) {
        PausePoint point = pausedAgents.get(taskId);
        if (point != null) {
            point.resume();
            pausedAgents.remove(taskId);
            log.info("[{}] Agent resumed from pause point", taskId);
            return true;
        }
        log.warn("[{}] No pause point found for agent", taskId);
        return false;
    }

    public boolean cancel(String taskId) {
        PausePoint point = pausedAgents.get(taskId);
        if (point != null) {
            point.cancel();
            pausedAgents.remove(taskId);
            log.info("[{}] Agent cancelled from pause point", taskId);
            return true;
        }
        return false;
    }

    public boolean isPaused(String taskId) {
        return pausedAgents.containsKey(taskId);
    }

    public void removePausePoint(String taskId) {
        pausedAgents.remove(taskId);
    }
}
