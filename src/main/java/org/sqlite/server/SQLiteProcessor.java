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

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Stack;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sqlite.Function;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.server.func.CurrentUserFunc;
import org.sqlite.server.func.SleepFunc;
import org.sqlite.server.func.StringResultFunc;
import org.sqlite.server.func.TimestampFunc;
import org.sqlite.server.func.UserFunc;
import org.sqlite.server.sql.SQLMetric;
import org.sqlite.server.sql.meta.Catalog;
import org.sqlite.server.sql.meta.CreateDatabaseStatement;
import org.sqlite.server.sql.meta.DropDatabaseStatement;
import org.sqlite.server.sql.meta.User;
import org.sqlite.server.util.IoUtils;
import org.sqlite.sql.AttachStatement;
import org.sqlite.sql.SQLContext;
import org.sqlite.sql.SQLStatement;
import org.sqlite.sql.Transaction;
import org.sqlite.sql.TransactionStatement;

import static org.sqlite.server.util.ConvertUtils.*;
import static java.lang.Integer.*;

/**
 * The SQLite server protocol handler.
 * 
 * @author little-pan
 * @since 2019-09-01
 *
 */
public abstract class SQLiteProcessor extends SQLContext implements AutoCloseable {
    
    static final Logger log = LoggerFactory.getLogger(SQLiteProcessor.class);
    
    // Read settings
    static final int initReadBuffer = getInteger("org.sqlite.server.procssor.initReadBuffer", 1<<12);
    static final int maxReadBuffer  = getInteger("org.sqlite.server.procssor.maxReadBuffer",  1<<16);
    // Write settings
    static final int maxWriteTimes  = getInteger("org.sqlite.server.procssor.maxWriteTimes",  1<<10);
    static final int maxWriteQueue  = getInteger("org.sqlite.server.procssor.maxWriteQueue",  1<<10);
    static final int maxWriteBuffer = getInteger("org.sqlite.server.procssor.maxWriteBuffer", 1<<12);
    
    protected final InetSocketAddress remoteAddress;
    protected SocketChannel channel;
    protected Selector selector;
    protected ByteBuffer readBuffer;
    protected volatile SQLiteQueryTask queryTask;
    protected Deque<ByteBuffer> writeQueue;
    protected SQLiteProcessorTask writeTask;
    
    protected final SQLiteServer server;
    protected final int id;
    protected final long createTime;
    protected final SQLiteProcessorState state;
    protected String name;
    protected int slot = -1; // slot in SlotAllocator
    protected SQLiteWorker worker;
    protected SQLiteAuthMethod authMethod;
    protected String userName;
    protected String databaseName;
    protected User user;
    
    private volatile boolean open = true;
    private volatile boolean stopped;
    
    private SQLiteConnection connection;
    private String metaSchema = null;
    protected SQLiteLocalDb localDb;
    protected Stack<TransactionStatement> savepointStack;
    
    protected long sqlStartNanoTime;
    
    protected SQLiteProcessor(SQLiteServer server, SocketChannel channel, int id) 
            throws NetworkException {
        this.createTime = System.currentTimeMillis();
        this.channel = channel;
        this.server = server;
        this.id = id;
        this.name = "proc-" + id;
        
        try {
            this.remoteAddress = (InetSocketAddress)channel.getRemoteAddress();
        } catch (IOException e) {
            throw new NetworkException(e);
        }
        
        this.writeQueue = new ArrayDeque<>();
        this.savepointStack = new Stack<>();
        this.state = new SQLiteProcessorState(this);
    }
    
    public SQLiteLocalDb attachLocalDb() throws SQLException {
        SQLiteLocalDb localDb = this.localDb;
        if (localDb == null) {
            this.localDb = localDb = new SQLiteLocalDb(this);
        }
        
        return localDb.attach();
    }
    
    public SQLiteProcessor detachLocalDb() throws IllegalStateException {
        SQLiteLocalDb localDb = this.localDb;
        if (localDb != null && isAutoCommit()) {
            try {
                if (localDb.detach()) {
                    this.localDb = null;
                    trace(log, "detach localDb {}", localDb);
                }
            } catch (SQLException e) {
                throw new IllegalStateException("Datach localDb error", e);
            }
        }
        
        return this;
    }
    
    public SQLiteProcessorState copyState() {
        return this.state.copy();
    }
    
    public long getCreateTime() {
        return this.createTime;
    }
    
    public int getId() {
        return this.id;
    }
    
    public String getName() {
        return this.name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public SQLiteServer getServer() {
        return this.server;
    }
    
    public User getUser() {
        return this.user;
    }
    
    public String getUserName() {
        return this.userName;
    }
    
    protected SocketChannel getChannel() {
        return this.channel;
    }
    
    protected Selector getSelector() {
        return selector;
    }
    
    protected void setSelector(Selector selector) {
        this.selector = selector;
    }
    
    protected SQLiteWorker getWorker() {
        return this.worker;
    }
    
    protected void setWorker(SQLiteWorker worker) {
        this.worker = worker;
    }
    
    protected SQLiteMetaDb getMetaDb() {
        return getServer().getMetaDb();
    }
    
    public String getMetaSchema() {
        return this.metaSchema;
    }
    
    public void attachMetaDb() throws SQLException {
        if (this.metaSchema == null) {
            SQLiteConnection conn = getConnection();
            this.metaSchema = getMetaDb().attachTo(conn);
            trace(log, "attach {}", this.metaSchema);
        }
    }
    
    public void detachMetaDb() throws IllegalStateException {
        SQLiteConnection conn = getConnection();
        if (this.metaSchema == null || !isAutoCommit()) {
            return;
        }
        
        try {
            getMetaDb().detachFrom(conn, this.metaSchema);
            trace(log, "detach {}", this.metaSchema);
            this.metaSchema = null;
        } catch (SQLException e) {
            throw new IllegalStateException("Detach metaDb error", e);
        }
    }
    
    public SQLiteAuthMethod newAuthMethod(String protocol, String authMethod) {
        return (this.server.newAuthMethod(protocol, authMethod));
    }
    
    public void setConnection(SQLiteConnection connection) throws SQLException {
        if (!isOpen()) {
            throw new IllegalStateException("Processor has been closed");
        }
        
        if (connection == null) {
            throw new NullPointerException("connection");
        }
        
        if (this.connection != null) {
            throw new IllegalStateException("connection has been set");
        }
        
        this.connection = connection;
        // init
        String host = getRemoteAddress().getHostName();
        Function func;
        
        func = this.server.startTimeFunc;
        Function.create(connection, "start_time", func);
        Function.create(connection, "pg_postmaster_start_time", func);
        func = this.server.versionFunc;
        Function.create(connection, "version", func);
        func = this.server.serverVersionFunc;
        Function.create(connection, "server_version", func);
        
        func = new UserFunc(this.user, host);
        Function.create(connection, "user", func);
        func = new CurrentUserFunc(this.user);
        Function.create(connection, "current_user", func);
        
        func = new StringResultFunc(this.databaseName);
        Function.create(connection, "database", func);
        Function.create(connection, "current_database", func);
        
        TimestampFunc timestampFunc;
        timestampFunc = this.server.clockTimestampFunc;
        Function.create(connection, timestampFunc.getName(), timestampFunc);
        timestampFunc = this.server.sysdateFunc;
        Function.create(connection, timestampFunc.getName(), timestampFunc);
        
        Function.create(connection, "sleep", new SleepFunc(this));
    }
    
    public SQLiteBusyContext getBusyContext() {
        SQLiteQueryTask queryTask = this.queryTask;
        if (queryTask == null) {
            return null;
        }
        
        return queryTask.getBusyContext();
    }
    
    public void setBusyContext(SQLiteBusyContext busyContext) {
        this.queryTask.setBusyContext(busyContext);
    }
    
    public SQLiteQueryTask getQueryTask() {
        return this.queryTask;
    }
    
    public SQLiteProcessorTask getWriteTask() {
        return this.writeTask;
    }
    
    public int getSlot() {
        return slot;
    }

    public void setSlot(int slot) throws IllegalStateException {
        if (this.slot != -1 && this.slot != slot) {
            throw new IllegalStateException("Slot has been set");
        }
        this.slot = slot;
    }
    
    protected SQLiteProcessorState getState() {
        return this.state;
    }
    
    // SQLContext methods
    @Override
    public void checkReadOnly(SQLStatement sqlStmt) throws SQLException {
        final boolean readOnly = sqlStmt.inReadOnlyTx();
        
        if (readOnly && !sqlStmt.isQuery() && !sqlStmt.isTransaction()) {
            String message = "Attempt to write in a readonly transaction";
            throw convertError(SQLiteErrorCode.SQLITE_READONLY, message);
        }
    }
    
    @Override
    public SQLiteConnection getConnection() {
        return this.connection;
    }
    
    @Override
    public File getDataDir() {
        return this.server.getDataDir();
    }
    
    @Override
    public String getDbName() {
        return this.databaseName;
    }
    
    @Override
    public String getMetaDbName() {
        return (getMetaDb().getDbName());
    }
    
    @Override
    public boolean isTrace() {
        return this.server.isTrace();
    }
    
    @Override
    public void trace(Logger log, String message) {
        this.server.trace(log, message);
    }
    
    @Override
    public void trace(Logger log, String format, Object ... args) {
        this.server.trace(log, format, args);
    }
    
    @Override
    public void traceError(Logger log, String message, Throwable cause) {
        this.server.traceError(log, message, cause);
    }
    
    @Override
    public void traceError(Logger log, String tag, String message, Throwable cause) {
        this.server.traceError(log, tag, message, cause);
    }
    
    @Override
    public void transactionComplelete() {
        getWorker().dbIdle();
        detachLocalDb();
    }
    
    @Override
    protected void prepareTransaction(TransactionStatement txSql) {
        this.server.trace(log, "tx: begin");
        Transaction tx = new Transaction(this, txSql.getTransactionMode());
        setTransaction(tx);
        this.savepointStack.clear();
        SQLiteConnection conn = getConnection();
        conn.getConnectionConfig().setAutoCommit(false);
        this.savepointStack.push(txSql);
        trace(log, "{}, '{}' readOnly {}", tx, this, this.readOnly);
    }
    
    @Override
    protected void preExecute(SQLStatement s) {
        final SQLMetric metric = this.worker.sqlMetric;
        
        switch(s.getCommand()) {
        case "SELECT":
            metric.selectStmts++;
            break;
        case "UPDATE":
            metric.updateStmts++;
            break;
        case "INSERT":
            metric.insertStmts++;
            break;
        case "DELETE":
            metric.deleteStmts++;
            break;
        default:
            break;
        }
        metric.totalStmts++;
        
        long longTime = this.server.getLongQueryNanoTime();
        if (longTime > 0L) {
            this.sqlStartNanoTime = System.nanoTime();
        } else {
            this.sqlStartNanoTime = 0L;
        }
    }
    
    @Override
    protected void postExecute(SQLStatement s) {
        long longTime = this.server.getLongQueryNanoTime();
        final SQLMetric metric = this.worker.getSQLMetric();
        
        if (longTime > 0L && this.sqlStartNanoTime > 0L) {
            if (System.nanoTime() - this.sqlStartNanoTime > longTime) {
                metric.slowStmts++;
            }
        }
        this.sqlStartNanoTime = 0L;
    }
    
    @Override
    protected void pushSavepoint(TransactionStatement txSql) {
        this.savepointStack.push(txSql);
    }
    
    @Override
    protected void resetAutoCommit() {
        getConnection().getConnectionConfig()
        .setAutoCommit(true);
        this.savepointStack.clear();
        setTransaction(null);
    }
    
    @Override
    protected void releaseTransaction(TransactionStatement txSql, boolean finished) {
        SQLiteConnection conn = getConnection();
        if (finished) {
            conn.getConnectionConfig().setAutoCommit(true);
            this.savepointStack.clear();
            trace(log, "tx: finished");
        } else {
            boolean autoCommit = this.savepointStack.isEmpty();
            String savepoint = txSql.getSavepointName();
            for (; !this.savepointStack.isEmpty(); ) {
                TransactionStatement spSql = this.savepointStack.peek();
                if (spSql.isBegin()) {
                    break;
                }
                this.savepointStack.pop();
                String target = spSql.getSavepointName();
                trace(log, "tx: release savepoint {}", target);
                if (target.equalsIgnoreCase(savepoint)) {
                    autoCommit = this.savepointStack.isEmpty();
                    break;
                }
            }
            if (autoCommit) {
                conn.getConnectionConfig().setAutoCommit(true);
                trace(log, "tx: finished");
            }
        }
        
        if (isAutoCommit()) {
            setTransaction(null);
            detachMetaDb();
        }
    }
    
    @Override
    public void dbWriteLock() throws SQLException {
        SQLiteBusyContext busyContext = getBusyContext();
        if (!this.server.tryDbWriteLock(this)) {
            if (busyContext == null) {
                long busyTimeout = this.server.getBusyTimeout();
                busyContext = new SQLiteBusyContext(true, busyTimeout);
                setBusyContext(busyContext);
            }
            busyContext.setOnDbWriteLock(true);
            throw convertError(SQLiteErrorCode.SQLITE_BUSY);
        }
        trace(log, "tx: db write lock");
        if (busyContext != null) {
            busyContext.setOnDbWriteLock(false);
        }
    }
    
    @Override
    public boolean dbWriteUnlock() {
        if (this.server.dbWriteUnlock(this)) {
            trace(log, "tx: db write unlock");
            return true;
        }
        return false;
    }
    
    @Override
    public boolean holdsDbWriteLock() {
        return (this.server.holdsDbWriteLock(this));
    }
    
    @Override
    protected boolean hasPrivilege(SQLStatement s) throws SQLException {
        if (this.user.isSa()) {
            return true;
        }
        
        String command = s.getCommand();
        if (s instanceof AttachStatement) {
            // ATTACH is special!
            final AttachStatement stmt = (AttachStatement)s;
            String dbName = stmt.getFileName(), dataDir = stmt.getDirPath();
            try {
                File dbFile = new File(dataDir, dbName).getCanonicalFile();
                if (dbFile.getParentFile().equals(getDataDir())) {
                    // Default data directory
                    dataDir = null;
                } else {
                    dataDir = dbFile.getParentFile().getCanonicalPath();
                }
            } catch (IOException e) {
                String message = "Database file path illegal";
                traceError(log, message, e);
                throw convertError(SQLiteErrorCode.SQLITE_ERROR, message);
            }
            final String host = this.user.getHost(), user = this.user.getUser();
            return this.server.hasPrivilege(host, user, dbName, command, dataDir);
        } else {
            return this.server.hasPrivilege(this.user, command);
        }
    }
    
    @Override
    protected void checkPermission(SQLStatement s) throws SQLException {
        if (hasPrivilege(s)) {
            return;
        }
        
        throw convertError(SQLiteErrorCode.SQLITE_PERM);
    }
    
    @Override
    public boolean isUniqueViolated(SQLException cause) {
        return (this.server.isUniqueViolated(cause));
    }
    // SQLContext methods
    
    public InetSocketAddress getRemoteAddress() {
        return this.remoteAddress;
    }
    
    public void cancelRequest() throws SQLException {
        SQLiteBusyContext busyContext = getBusyContext();
        if (busyContext != null) {
            busyContext.setCanceled(true);
            this.worker.wakeup();
        }
        
        SQLiteConnection conn = getConnection();
        if (conn != null && isOpen()) {
            conn.getDatabase().interrupt();
        }
    }
    
    public void cancelRequest(boolean query) throws SQLException {
        cancelRequest();
        if (!query) {
            stop();
        }
    }
    
    public void start() throws IOException, IllegalStateException {
        final String name = getName();
        if (isStopped()) {
            throw new IllegalStateException(name + " has been stopped");
        }
        
        SQLiteServer server = this.server;
        server.trace(log, "Connect: id {}", this.id);
        try {
            InetSocketAddress remoteAddr = this.remoteAddress;
            if (!server.isAllowed(remoteAddr)) {
                server.trace(log, "Host '{}' not allowed", remoteAddr.getHostName());
                deny(remoteAddr);
                return;
            }
            enableRead();
        } catch (SQLException e) {
            throw new IllegalStateException("Access metaDb fatal", e);
        }
    }
    
    protected boolean isRunning() throws SQLException {
        if (!isStopped()) {
            return true;
        }
        
        SQLiteConnection conn = getConnection();
        if (conn == null || conn.isClosed()) {
            return false;
        }
        return (!conn.getAutoCommit() /* Ongoing tx */);
    }
    
    protected abstract void deny(InetSocketAddress remote) throws IOException;
    
    protected void read() {
        try {
            if (isRunning()) {
                this.state.startRead();
                process();
            } else {
                this.worker.close(this); 
            }
        } catch (Exception e) {
            this.server.traceError(log, "process error", e);
            this.worker.close(this);
        } catch (OutOfMemoryError e) {
            this.worker.close(this);
            log.error("No memory", e);
        }
    }
    
    protected ByteBuffer getReadBuffer(final int minSize) {
        final ByteBuffer buf = this.readBuffer;
        if (buf == null) {
            int cap = Math.max(minSize, initReadBuffer);
            return (this.readBuffer = ByteBuffer.allocate(cap));
        }
        
        final int cap = buf.capacity(), pos = buf.position();
        int freeSize = cap - pos;
        if (freeSize >= minSize) {
            return buf;
        }
        
        trace(log, "{}: allocate a new read buffer - pos {}, freeSize {}, request minSize {}", 
            this, pos, freeSize, minSize);
        freeSize = Math.max(freeSize << 1, minSize);
        final int newSize = pos + freeSize;
        ByteBuffer newBuffer = ByteBuffer.allocate(newSize);
        buf.flip();
        newBuffer.put(buf);
        // fixbug - dead-loop issue for setting newBuffer limit as buf.limit()
        newBuffer.limit(newSize);
        return (this.readBuffer = newBuffer);
    }
    
    protected int resetReadBuffer() {
        final ByteBuffer buf = this.readBuffer;
        if (buf == null) {
            return 0;
        }
        
        final int cap = buf.capacity();
        int rem = buf.remaining();
        if (rem == 0) {
            if (cap > maxReadBuffer) {
                this.readBuffer = null;
            } else {
                buf.clear();
            }
            return 0;
        }
        
        buf.compact();
        if (buf.position() <= initReadBuffer && cap > maxReadBuffer) {
            ByteBuffer newBuffer = ByteBuffer.allocate(initReadBuffer);
            buf.flip();
            newBuffer.put(buf);
            this.readBuffer = newBuffer;
        }
        
        return (this.readBuffer.position());
    }
    
    protected void write() {
        try {
            flush();
        } catch (Exception e) {
            this.server.traceError(log, "flush error", e);
            this.worker.close(this);
        } catch (OutOfMemoryError e) {
            this.worker.close(this);
            log.error("No memory", e);
        }
    }
    
    protected void flush() throws IOException, SQLException {
        SocketChannel ch = getChannel();
        int i = 0;
        
        disableRead();
        for (;;) {
            ByteBuffer buf = nextWriteBuffer();
            if (buf != null) {
                this.state.startWrite();
                for (; buf.hasRemaining();) {
                    int n = ch.write(buf);
                    if (n == 0) {
                        break;
                    }
                    if (++i >= maxWriteTimes) {
                        break;
                    }
                }
                if (buf.hasRemaining()) {
                    this.writeQueue.offerFirst(buf);
                    this.state.startSleep();
                    return;
                }
                continue;
            }
            
            SQLiteProcessorTask task = this.writeTask;
            if (task != null) {
                task.run();
                continue;
            }
            
            disableWrite();
            if (isRunning()) {
                // Go on
                enableRead();
                ByteBuffer rb = this.readBuffer;
                if (rb != null && rb.position() > 0) {
                    read();
                }
                this.state.startSleep();
            } else {
                this.worker.close(this);
            }
            break;
        }
    }
    
    protected boolean canFlush() {
        ByteBuffer buf = this.writeQueue.peek();
        if (buf == null) {
            return false;
        } else if (buf.remaining() >= maxWriteBuffer) {
            // Case-1
            return true;
        } else if (this.writeQueue.size() >= maxWriteQueue) {
            // Case-2
            return true;
        }
        
        return false;
    }
    
    protected ByteBuffer nextWriteBuffer() {
        return this.writeQueue.poll();
    }
    
    protected void offerWriteBuffer(ByteBuffer writeBuffer) {
        final ByteBuffer last = this.writeQueue.peekLast();
        final int minSize = writeBuffer.remaining();
        if (last == null || minSize > maxWriteBuffer) {
            this.writeQueue.offer(writeBuffer);
            return;
        }
        
        // Merge buffer: optimize write performance!
        final int lim = last.limit(), cap = last.capacity();
        int freeSize = cap - lim;
        if (freeSize >= minSize) {
            // Case-1 space enough
            last.position(lim).limit(cap);
            last.put(writeBuffer);
            last.flip();
            return;
        }
        // - Try to expand capacity
        freeSize = Math.max(freeSize + cap, minSize);
        final int newSize = lim + freeSize;
        if (newSize <= maxWriteBuffer) {
            // Case-c space enough after expanding
            final ByteBuffer newBuffer = ByteBuffer.allocate(newSize);
            newBuffer.put(last);
            newBuffer.put(writeBuffer);
            newBuffer.flip();
            this.writeQueue.pollLast();
            this.writeQueue.offer(newBuffer);
            return;
        }
        
        this.writeQueue.offer(writeBuffer);
    }
    
    protected void enableRead() throws IOException {
        SelectionKey key = this.channel.keyFor(this.selector);
        if (key == null) {
            this.channel.register(this.selector, SelectionKey.OP_READ, this);
        } else {
            key.interestOps(key.interestOps() | SelectionKey.OP_READ);
        }
    }
    
    protected void disableRead() throws IOException {
        SelectionKey key = this.channel.keyFor(this.selector);
        if (key != null) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_READ);
        }
    }
    
    protected void enableWrite() throws IOException {
        SelectionKey key = this.channel.keyFor(this.selector);
        if (key == null) {
            this.channel.register(this.selector, SelectionKey.OP_WRITE, this);
        } else {
            key.interestOps(key.interestOps() | SelectionKey.OP_WRITE);
        }
    }
    
    protected void disableWrite() throws IOException {
        SelectionKey key = this.channel.keyFor(this.selector);
        if (key != null) {
            key.interestOps(key.interestOps() & ~SelectionKey.OP_WRITE);
        }
    }
    
    protected abstract void process() throws IOException;
    
    protected abstract void tooManyConns() throws IOException;
    
    protected abstract void interalError() throws IOException;
    
    public void createDbFile(CreateDatabaseStatement stmt) throws SQLException {
        String dir = stmt.getDir();
        if (dir == null) {
            dir = getDataDir().getAbsolutePath();
        } else {
            File dirFile = new File(dir);
            if (dirFile.equals(getDataDir())) {
                stmt.setDir(null);
            }
        }
        // Check
        String db = stmt.getDb();
        File dbDir = new File(dir);
        try {
            dbDir = dbDir.getCanonicalFile();
        } catch (IOException e) {
            SQLiteErrorCode error = SQLiteErrorCode.SQLITE_ERROR;
            throw convertError(error, "Data directory path illegal");
        }
        if (stmt.getDir() != null) {
            stmt.setDir(dbDir.getAbsolutePath());
        }
        
        File dbFile = new File(dbDir, db);
        if (!dbFile.getName().equals(db)) {
            SQLiteErrorCode error = SQLiteErrorCode.SQLITE_ERROR;
            throw convertError(error, "Database name isn't a file name");
        }
        if (dbFile.isFile() && dbFile.length() > 0L) {
            SQLiteErrorCode error = SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE;
            throw convertError(error, "Database file already exists");
        }
        if (dbFile.isFile()) {
            return;
        }
        
        // Do create
        if (!dbDir.isDirectory() && !dbDir.mkdirs()) {
            SQLiteErrorCode error = SQLiteErrorCode.SQLITE_IOERR;
            throw convertError(error, "Can't create data directory");
        }
        try {
            boolean exists = !dbFile.createNewFile();
            if (exists) {
                SQLiteErrorCode error = SQLiteErrorCode.SQLITE_CONSTRAINT_UNIQUE;
                throw convertError(error, "Database file already exists");
            }
        } catch (IOException e) {
            this.server.traceError(log, "Can't create databse file", e);
            SQLiteErrorCode error = SQLiteErrorCode.SQLITE_IOERR;
            throw convertError(error, "Can't create database file");
        }
    }
    
    public void deleteDbFile(DropDatabaseStatement stmt) throws SQLException {
        final String db = stmt.getDb();
        Catalog catalog = getMetaDb().selectCatalog(db);
        if (catalog == null) {
            if (stmt.isQuiet()) {
                return;
            }
            String message = String.format("Database catalog of '%s' not exists", db);
            throw convertError(SQLiteErrorCode.SQLITE_ERROR, message);
        }
        
        final String dir = catalog.getDir();
        final File dbFile = this.server.getDbFile(db, dir);
        if (!dbFile.isFile()) {
            if (stmt.isQuiet()) {
                return;
            }
            String message = String.format("Database file of '%s' not exists", dbFile);
            throw convertError(SQLiteErrorCode.SQLITE_ERROR, message);
        }
        // fix - On UNIX-like system, a file can be deleted even if it's used, so that 
        //we shouldn't delete the current database file.
        // @since 0.3.29
        if (db.equalsIgnoreCase(this.user.getDb())) {
            String message = String.format("Can't delete current database file of '%s'", dbFile);
            trace(log, "{}: {}", message, dbFile);
            throw convertError(SQLiteErrorCode.SQLITE_IOERR, message);
        }
        
        // Do delete
        if (!dbFile.delete()) {
            String message = String.format("Can't delete database file of '%s'", dbFile);
            trace(log, "{}: {}", this, message);
            throw convertError(SQLiteErrorCode.SQLITE_IOERR, message);
        }
        for (String ext: new String[] {"-wal", "-shm", "-journal"}) {
            final File extFile = this.server.getDbFile(db+ext, dir);
            if (extFile.isFile() && !extFile.delete()) {
                String message = String.format("Can't delete database log file of '%s'", db);
                log.error("{}: {}", message, extFile);
                throw convertError(SQLiteErrorCode.SQLITE_IOERR, message);
            }
        }
        // OK
    }
    
    public void statisticsCatalogs() throws SQLException {
        File dataDir = getServer().getDataDir();
        getMetaDb().statisticsCatalogs(dataDir);
    }
    
    public boolean isCurrentUser(User other) {
        if (other == null) {
            return false;
        }
        
        final User user = getUser();
        if (user == null) {
            InetSocketAddress remote = getRemoteAddress();
            return (other.getUser().equals(getUserName()) 
                    && remote.getHostString().equals(other.getHost()));
        }
        
        return (user.getUser().equals(other.getUser()) 
                && user.getHost().equals(other.getHost()));
    }
    
    protected boolean shutdownInput() {
        final SocketChannel ch = this.channel;
        if (ch == null) {
            return false;
        }
        
        if (ch.isConnected() && ch.isOpen()) {
            try {
                trace(log, "{}: shutdown input", this);
                ch.shutdownInput();
                return true;
            } catch (IOException e) {
                // ignore
            }
        }
        return false;
    }
    
    protected boolean shutdownOutput() {
        final SocketChannel ch = this.channel;
        if (ch == null) {
            return false;
        }
        
        if (ch.isConnected() && ch.isOpen()) {
            try {
                trace(log, "{}: shutdown output", this);
                ch.shutdownOutput();
                return true;
            } catch (IOException e) {
                // ignore
            }
        }
        return false;
    }
    
    protected abstract void sendErrorResponse(String message, String sqlState) 
        throws IOException;
    
    public void stop() {
        if (isStopped()) {
            return;
        }
        this.stopped = true;
        this.state.stop();
        shutdownInput();
        this.worker.wakeup();
    }
    
    public boolean isStopped() {
        return this.stopped;
    }
    
    public boolean isOpen() {
        return this.open;
    }
    
    @Override
    public String toString() {
        return this.name;
    }
    
    @Override
    public void close() {
        stop();
        if (!isOpen()) {
            return;
        }
        this.open = false;
        this.queryTask = null;
        this.writeTask = null;
        
        // release buffers
        this.readBuffer = null;
        this.writeQueue = null;
        this.savepointStack = null;
        
        // release connections
        shutdownOutput();
        IoUtils.close(this.channel);
        this.channel = null;
        IoUtils.close(this.connection);
        this.connection = null;
        this.dbWriteUnlock();
        this.worker.dbIdle();
        this.state.close();
        
        this.server.trace(log, "Close: id {}", this.id);
    }

}
