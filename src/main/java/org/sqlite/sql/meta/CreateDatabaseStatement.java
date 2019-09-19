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
package org.sqlite.sql.meta;

import org.sqlite.sql.SQLParseException;
import org.sqlite.sql.SQLParser;
import org.sqlite.sql.SQLStatement;

/** CREATE {DATABASE | SCHEMA} [IF NOT EXISTS] dbname [{LOCATION | DIRECTORY} 'data-dir']
 * 
 * @author little-pan
 * @since 2019-09-19
 *
 */
public class CreateDatabaseStatement extends SQLStatement implements MetaStatement {
    
    protected final Catalog catalog = new Catalog();
    private boolean quite; // true: if not exists

    public CreateDatabaseStatement(String sql) {
        super(sql, "CREATE DATABASE");
    }
    
    public Catalog getCatalog() {
        return this.catalog;
    }
    
    public boolean isQuite() {
        return quite;
    }

    public void setQuite(boolean quite) {
        this.quite = quite;
    }
    
    public void setDb(String db) {
        this.catalog.setDb(db);
    }
    
    public String getDb() {
        return this.catalog.getDb();
    }
    
    public String getDir() {
        return this.catalog.getDir();
    }
    
    public void setDir(String dir) {
        this.catalog.setDir(dir);
    }

    @Override
    public String getMetaSQL(String metaSchema) throws SQLParseException {
        String db = getDb(), dir, sql;
        if (db == null) {
            throw new SQLParseException("No dbname specified");
        }
        
        dir = getDir();
        if (dir == null) {
            sql = String.format("insert into '%s'.catalog(db, dir)values('%s', NULL)", 
                    metaSchema, db);
        } else {
            sql = String.format("insert into '%s'.catalog(db, dir)values('%s', '%s')", 
                    metaSchema, db, dir);
        }
        
        // check SQL
        try (SQLParser parser = new SQLParser(sql)) {
            SQLStatement stmt = parser.next();
            if ("INSERT".equals(stmt.getCommand()) && !parser.hasNext()) {
                return stmt.getSQL();
            }
        } catch (SQLParseException e){}
        
        throw new SQLParseException(getSQL());
    }

    @Override
    public boolean needSa() {
        return true;
    }
    
}
