/**
 * Copyright 2019 little-pan. A SQLite server based on the C/S architecture.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *    http://www.apache.org/licenses/LICENSE-2.0
 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.sqlite.server;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import static java.lang.System.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.server.sql.SQLMetric;
import org.sqlite.server.sql.meta.User;
import org.sqlite.server.util.IoUtils;
import org.sqlite.server.util.SlotAllocator;
import org.sqlite.server.util.locks.SpinLock;

/**SQLite server worker thread.
 * 
 * @author little-pan
 * @since 2019-09-14
 *
 */
public class SQLiteWorker implements Runnable {
    static final Logger log = LoggerFactory.getLogger(SQLiteWorker.class);
    
    protected static final int ioRatio, busyMinWait;
    
    protected final SQLiteServer server;
    
    protected final AtomicBoolean open = new AtomicBoolean();
    protected final AtomicBoolean wakeup = new AtomicBoolean();
    protected final AtomicBoolean dbIdle = new AtomicBoolean(true);
    protected Selector selector;
    protected Thread runner;
    private volatile boolean stopped;
    
    protected final int id;
    protected final String name;
    
    protected final BlockingQueue<SQLiteProcessor> procQueue;
    protected final int maxConns;
    protected final SpinLock procsLock = new SpinLock();
    private final SlotAllocator<SQLiteProcessor> processors;
    private final SlotAllocator<SQLiteProcessor> busyProcs;
    
    protected final SQLMetric sqlMetric = new SQLMetric();
    
    public SQLiteWorker(SQLiteServer server, int id) {
        this.server = server;
        this.id = id;
        this.name = server.getName() + " worker-"+this.id;
        this.maxConns = server.getMaxConns();
        this.procQueue = new ArrayBlockingQueue<>(maxConns);
        this.processors = new SlotAllocator<>(this.maxConns);
        this.busyProcs = new SlotAllocator<>(this.maxConns);
    }
    
    public int getId() {
        return this.id;
    }
    
    public String getName() {
        return this.name;
    }
    
    public boolean isOpen() {
        return this.open.get();
    }
    
    public void start() throws IOException {
        if (!this.open.compareAndSet(false, true)) {
            throw new IllegalStateException(this.name + " has been started");
        }
        if (isStopped()) {
            throw new IllegalStateException(this.name + " has been stopped"); 
        }
        
        boolean failed = true;
        try {
            this.selector = Selector.open();
            
            Thread runner = new Thread(this, this.name);
            runner.setDaemon(true);
            runner.start();
            this.runner = runner;
            failed = false;
        } finally {
            if (failed) {
                IoUtils.close(this.selector);
            }
        }
    }
    
    public boolean isStopped() {
        return this.stopped;
    }
    
    public void stop() {
        this.stopped = true;
        this.selector.wakeup();
    }
    
    protected void close() {
        if (this.runner != Thread.currentThread()) {
            throw new IllegalStateException("Not in " + this.name);
        }
        
        this.open.set(false);
        IoUtils.close(this.selector);
        
        for (;;) {
            SQLiteProcessor p = this.procQueue.poll();
            if (p == null) {
                break;
            }
            IoUtils.close(p);
        }
        
        this.procsLock.lock();
        try {
            for (int i = 0, n = this.processors.maxSlot(); i < n; ++i) {
                SQLiteProcessor p = this.processors.deallocate(i);
                IoUtils.close(p);
            }
        } finally {
            this.procsLock.unlock();
        }
    }
    
    public void close(SQLiteProcessor processor) {
        if (processor == null) {
            return;
        }
        processor.close();
        
        int slot = processor.getSlot();
        if (slot >= 0) {
            this.procsLock.lock();
            try {
                this.processors.deallocate(slot, processor);
            } finally {
                this.procsLock.unlock();
            } 
        }
    }

    @Override
    public void run() {
        try {
            SlotAllocator<SQLiteProcessor> processors = this.processors;
            long lastIdleCheck = 0L, idleCheckIntv = -1L;
            
            for (; !isStopped() || processors.size() > 0;) {
                final long  curr = currentTimeMillis(), timeout;
                int n;
                
                // Idle check
                if (curr - lastIdleCheck >= idleCheckIntv) {
                    lastIdleCheck = curr;
                    processIdle(curr);
                    idleCheckIntv = idleCheckInterval();
                }
                
                // Do select
                timeout = minSelectTimeout(curr, idleCheckIntv);
                if (timeout < 0L) {
                    n = this.selector.select();
                } else if (timeout == 0L) {
                    n = this.selector.selectNow();
                } else {
                    n = this.selector.select(timeout);
                }
                this.wakeup.set(false);
                
                if (0 == n) {
                    processQueues(0L);
                    continue;
                }
                
                if (100 == ioRatio) {
                    processIO();
                    processQueues(0L);
                    continue;
                }
                
                long ioStart = System.nanoTime();
                processIO();
                long ioTime = System.nanoTime() - ioStart;
                processQueues(ioTime * (100 - ioRatio) / ioRatio);
            }
        } catch (IOException e) {
            log.error("Fatal event", e);
        } finally {
            close();
        }
    }
    
    protected void processQueues(long runNanos) throws IllegalStateException {
        BlockingQueue<SQLiteProcessor> queue = this.procQueue;
        Selector selector = this.selector;
        long deadNano = System.nanoTime() + runNanos;
        // Q1: procQueue
        SlotAllocator<SQLiteProcessor> processors = this.processors;
        for (;;) {
            if (runNanos > 0L && System.nanoTime() > deadNano) {
                return;
            }
            
            SQLiteProcessor p = queue.poll();
            if (p == null) {
                break;
            }
            
            try {
                p.setSelector(selector);
                p.setWorker(this);
                p.setName(this.name + "-" + p.getName());
                if (processors.size() >= this.maxConns) {
                    p.tooManyConns();
                    p.stop();
                    p.enableWrite();
                } else {
                    try {
                        p.start();
                    } catch (IllegalStateException e) {
                        log.warn("Can't start " + p.getName(), e);
                        IoUtils.close(p);
                        continue;
                    }
                    this.procsLock.lock();
                    try {
                        final int slot = processors.allocate(p);
                        if (slot == -1) {
                            throw new IllegalStateException("Processor allocator full");
                        }
                        p.setSlot(slot);
                    } finally {
                        this.procsLock.unlock();
                    }
                }
            } catch (IOException e) {
                log.debug("Handle processor error", e);
                IoUtils.close(p);
            }
        }
        
        // Q2: busyProcessors
        SlotAllocator<SQLiteProcessor> busyProcs = this.busyProcs;
        for (int i = 0, n = busyProcs.maxSlot(); i < n; ++i) {
            if (runNanos > 0L && System.nanoTime() > deadNano) {
                return;
            }
            
            SQLiteProcessor proc = busyProcs.get(i);
            if (proc == null) {
                continue;
            }
            SQLiteBusyContext busyContext = proc.getBusyContext();
            if (proc.isStopped() && busyContext == null) {
                if (busyProcs.deallocate(i, proc)) {
                    continue;
                }
                throw new IllegalStateException("Busy processors slot used");
            }
            
            if (busyContext.isReady() || busyContext.isCanceled()) {
                if (this.server.canHoldDbWriteLock(proc) || busyContext.isTimeout() 
                                                         || busyContext.isCanceled()) {
                    if (busyProcs.deallocate(i, proc)) {
                        this.server.trace(log, "Busy processor '{}' resumed", proc);
                        try {
                            Thread.currentThread().setName(proc.getName());
                            proc.queryTask.run();
                        } finally {
                            Thread.currentThread().setName(this.name);
                        }
                        continue;
                    }
                    throw new IllegalStateException("Busy processors slot used");
                }
                continue;
            }
        }
    }
    
    protected void processIdle(final long curr) {
        SlotAllocator<SQLiteProcessor> processors = this.processors;
        this.procsLock.lock();
        try {
            for (int i = 0, n = processors.maxSlot(); i < n; ++i) {
                SQLiteProcessor p = processors.get(i);
                if (p != null) {
                    final SQLiteProcessorState state = p.getState();
                    final long timeout, start;
                    String message = "timeout";
                    state.lock();
                    try {
                        start = state.getStartTime();
                        switch(state.getState()) {
                        case SQLiteProcessorState.AUTH:
                            timeout = this.server.getAuthTimeout();
                            message = "Authentication timeout";
                            break;
                        case SQLiteProcessorState.SLEEP:
                            timeout = this.server.getSleepTimeout();
                            message = "Sleep timeout";
                            break;
                        case SQLiteProcessorState.SLEEP_IN_TX:
                            timeout = this.server.getSleepInTxTimeout();
                            message = "Sleep in transaction timeout";
                            break;
                        default:
                            timeout = -1L;
                            break;
                        }
                    } finally {
                        state.unlock();
                    }
                    
                    if (timeout > 0L && curr - start > timeout) {
                        try {
                            p.sendErrorResponse(message, "53400");
                        } catch (IOException e) {
                            // ignore
                        } finally {
                            IoUtils.close(p.getConnection());
                            p.stop();
                        }
                    }
                    
                    if (p.isStopped()) {
                        p.write();
                    }
                }
            }
        } finally {
            this.procsLock.unlock();
        }
    }
    
    protected void processIO() {
        final Thread currThead = Thread.currentThread();
        Iterator<SelectionKey> keys = this.selector.selectedKeys().iterator();
        for (; keys.hasNext(); keys.remove()) {
            SelectionKey key = keys.next();
            SQLiteProcessor p;
            
            if (!key.isValid()) {
                continue;
            }
            
            if (key.isWritable()) {
                try {
                    p = (SQLiteProcessor)key.attachment();
                    currThead.setName(p.getName());
                    p.write();
                } finally {
                    currThead.setName(this.name);
                }
            } else if (key.isReadable()) {
                try {
                    p = (SQLiteProcessor)key.attachment();
                    currThead.setName(p.getName());
                    p.read();
                } finally {
                    currThead.setName(this.name);
                }
            } else {
                key.cancel();
            }
        }
    }
    
    public SQLiteWorker wakeup() {
        if (this.wakeup.compareAndSet(false, true)) {
            this.selector.wakeup();
        }
        
        return this;
    }

    public boolean offer(SQLiteProcessor process) {
        if (isStopped()) {
            return false;
        }
        
        if (this.procQueue.offer(process)) {
            wakeup();
            return true;
        }
        
        return false;
    }
    
    public boolean busy(SQLiteProcessor process) throws IllegalStateException {
        if (process.isStopped() || !process.isOpen()) {
            return false;
        }
        
        this.server.trace(log, "Busy processor '{}' suspended", process);
        if (this.busyProcs.allocate(process) == -1) {
            throw new IllegalStateException("Busy processors full");
        }
        SQLiteBusyContext busyContext = process.getBusyContext();
        if (!busyContext.isSleepable()) {
            this.dbIdle.set(false);
        }
        process.state.setStateText("busy");
        
        return true;
    }
    
    public void dbIdle() {
        dbIdle(true);
    }
    
    public void dbIdle(boolean global) {
        if (global) {
            this.server.dbIdle();
        } else if (this.dbIdle.compareAndSet(false, true)) {
            this.selector.wakeup();
            this.server.trace(log, "Hello '{}' db idle", this);
        }
    }
    
    protected long idleCheckInterval() {
        long timeout = this.server.getAuthTimeout();
        
        if (timeout <= 0L) {
            timeout = this.server.getSleepInTxTimeout();
        } else {
            timeout = Math.min(this.server.getSleepInTxTimeout(), timeout);
        }
        if (timeout <= 0L) {
            timeout = this.server.getSleepTimeout();
        } else {
            timeout = Math.min(this.server.getSleepTimeout(), timeout);
        }
        
        return (timeout == 0L? -1L: timeout);
    }
    
    protected long minSelectTimeout(final long curr, final long idleCheckIntv) {
        SlotAllocator<SQLiteProcessor> busyProcs = this.busyProcs;
        long timeout = -1L;
        
        for (int i = 0, n = busyProcs.maxSlot(); i < n; ++i) {
            SQLiteProcessor proc = busyProcs.get(i);
            
            if (proc == null) {
                continue;
            }
            
            SQLiteBusyContext busyContext = proc.getBusyContext();
            if (busyContext.isCanceled()) {
                timeout = 0L;
                break;
            }
            
            if (busyContext.isReady()) {
                if (busyContext.isSleepable() || this.dbIdle.get()) {
                    timeout = 0L;
                    break;
                }
                if (!busyContext.isOnDbWriteLock()) {
                    if (timeout > busyMinWait || timeout < 0L) {
                        timeout = busyMinWait;
                    }
                }
            }
            
            long remTime = busyContext.getTimeoutTime() - curr;
            if (timeout > remTime || timeout < 0L) {
                if (remTime <= 0L) {
                    timeout = 0L;
                    break;
                }
                timeout = remTime;
            }
        }
        
        if (timeout < 0L) {
            timeout = idleCheckIntv;
        }
        
        return timeout;
    }
    
    public SQLMetric getSQLMetric() {
        return this.sqlMetric;
    }

    SQLiteProcessor getProcessor(int pid) {
        SlotAllocator<SQLiteProcessor> processors = this.processors;
        this.procsLock.lock();
        try {
            for (int i = 0, n = processors.maxSlot(); i < n; ++i) {
                SQLiteProcessor p = processors.get(i);
                if (p != null && p.getId() == pid) {
                    return p;
                }
            }
            return null;
        } finally {
            this.procsLock.unlock();
        }
    }
    
    public List<SQLiteProcessorState> getProcessorStates(final SQLiteProcessor processor) {
        SlotAllocator<SQLiteProcessor> processors = this.processors;
        this.procsLock.lock();
        try {
            List<SQLiteProcessorState> states = new ArrayList<>();
            final User user = processor.getUser();
            for (int i = 0, n = processors.maxSlot(); i < n; ++i) {
                final SQLiteProcessor p = processors.get(i);
                if (p == null) {
                    continue;
                }
                
                if (p == processor) {
                    states.add(p.copyState());
                } else {
                    if (user == null) {
                        continue;
                    }
                    
                    if (user.isSa() || p.isCurrentUser(user)) {
                        states.add(p.copyState());
                    }
                }
            }
            return states;
        } finally {
            this.procsLock.unlock();
        }
    }
    
    @Override
    public String toString() {
        return this.name;
    }
    
    static {
        String prop = "org.sqlite.server.worker.ioRatio";
        int i = Integer.getInteger(prop, 50);
        if (i <= 0 || i > 100) {
            String message = prop + " " + i + ", expect (0, 100]";
            throw new ExceptionInInitializerError(message);
        }
        ioRatio = i;
        
        prop = "org.sqlite.server.worker.busyMinWait";
        i = Integer.getInteger(prop, 100);
        if (i < 0) {
            String message = prop + " " + i + ", expect [0, +∞)";
            throw new ExceptionInInitializerError(message);
        }
        busyMinWait = i;
    }
    
}
