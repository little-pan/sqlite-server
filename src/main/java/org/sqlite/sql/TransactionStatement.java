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
package org.sqlite.sql;

import java.sql.SQLException;

/**Transaction statement:
 * BEGIN, COMMIT/ROLLBACK etc
 * 
 * @author little-pan
 * @since 2019-09-05
 *
 */
public class TransactionStatement extends SQLStatement {
    
    public static final int DEFERRED   = 1;
    public static final int IMMEDIATE  = 2;
    public static final int EXCLUSIVE  = 4;
    
    protected TransactionMode transactionMode = new TransactionMode();
    protected String savepointName;
    private boolean isReadyStmt;
    protected int behavior = DEFERRED;
    
    public TransactionStatement(String sql, String command) {
        super(sql, command);
    }
    
    public TransactionStatement(String sql, String command, String savepointName) {
        this(sql, command);
        this.savepointName = savepointName;
    }
    
    @Override
    public boolean isTransaction() {
        return true;
    }
    
    @Override
    protected void checkPermission() throws SQLException {
        // All transaction statements PASS
    }
    
    @Override
    public void preExecute(int maxRows) throws SQLException, IllegalStateException {
        assert !inImplicitTx();
        super.preExecute(maxRows);
        
        if (this.context.isAutoCommit() && (isBegin() || isSavepoint())) {
            this.context.prepareTransaction(this);
            this.isReadyStmt = true;
        }
    }
    
    @Override
    protected boolean doExecute(int maxRows) throws SQLException {
        assert !inImplicitTx();
        
        boolean prepared = this.isReadyStmt;
        boolean failed = true;
        try {
            boolean resultSet = super.doExecute(maxRows);
            if (!prepared && isSavepoint()) {
                this.context.pushSavepoint(this);
            }
            
            failed = false;
            return resultSet;
        } finally {
            if (failed && prepared) {
                this.context.resetAutoCommit();
                this.isReadyStmt = false;
            }
        }
    }
    
    @Override
    protected void setFirstStatementInTx() {
        if (!this.isReadyStmt) {
            super.setFirstStatementInTx();
        }
    }
    
    @Override
    public void complete(boolean success) throws IllegalStateException {
        assert !inImplicitTx();
        
        String command = this.command;
        switch (command) {
        case "COMMIT":
        case "END":
        case "ROLLBACK":
            if (hasSavepoint()) {
                break;
            }
            this.context.releaseTransaction(this, true);
            break;
        case "RELEASE":
            this.context.releaseTransaction(this, false);
            break;
        }
        
        super.complete(success);
    }
    
    public String getSavepointName() {
        return this.savepointName;
    }
    
    @Override
    public String getExecutableSQL() throws SQLException {
        if (this.isDeferred() && isBegin()) {
            final Boolean readOnly = isReadOnly();
            if (readOnly != null && readOnly) {
                return "begin";
            }
            // deferred -> immediate: 
            // Solve busy can't be recovered in transaction!
            return "begin immediate;";
        }
        
        return super.getExecutableSQL();
    }
    
    public boolean isBegin() {
        return ("BEGIN".equals(getCommand()));
    }
    
    public boolean isCommit() {
        String cmd = getCommand();
        return ("COMMIT".equals(cmd) || "END".equals(cmd));
    }
    
    public boolean isRollback() {
        return ("ROLLBACK".equals(getCommand()));
    }
    
    public boolean isSavepoint() {
        return ("SAVEPOINT".equals(getCommand()));
    }
    
    public boolean isRelease() {
        return ("RELEASE".equals(getCommand()));
    }
    
    public boolean hasSavepoint() {
        return (getSavepointName() != null);
    }
    
    public boolean isDeferred() {
        return DEFERRED == this.behavior;
    }

    public void setDeferred(boolean deferred) {
        this.behavior = deferred? DEFERRED: 0;
    }
    
    public boolean isImmediate() {
        return IMMEDIATE == this.behavior;
    }

    public void setImmediate(boolean immediate) {
        this.behavior = immediate? IMMEDIATE: 0;
    }

    public boolean isExclusive() {
        return EXCLUSIVE == this.behavior;
    }

    public void setExclusive(boolean exclusive) {
        this.behavior = exclusive? EXCLUSIVE: 0;
    }
    
    public TransactionMode getTransactionMode() {
        return transactionMode;
    }

    public void setTransactionMode(TransactionMode transactionMode) {
        this.transactionMode = transactionMode;
    }
    
    public Boolean isReadOnly() {
        return (this.transactionMode.isReadOnly());
    }
    
    public int getIsolationLevel() {
        return (this.transactionMode.getIsolationLevel());
    }
    
}

