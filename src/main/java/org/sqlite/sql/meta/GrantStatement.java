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

import static java.lang.String.*;

import java.util.HashSet;
import java.util.Set;

import org.sqlite.sql.SQLParseException;
import org.sqlite.sql.SQLParser;
import org.sqlite.sql.SQLStatement;

/**The GRANT statement:
 * GRANT ALL [PRIVILEGES]
 * ON {DATABASE | SCHEMA} dbname [, ...]
 * TO {'user'@'host' [, ...]}
 * 
 * @author little-pan
 * @since 2019-09-16
 *
 */
public class GrantStatement extends SQLStatement implements MetaStatement {
    
    protected final Set<String> granteds = new HashSet<>();
    protected final Set<Grantee> grantees = new HashSet<>();
    
    public GrantStatement(String sql) {
        super(sql, "GRANT");
    }

    @Override
    public String getMetaSQL(String metaSchema) throws SQLParseException {
        int m = this.granteds.size();
        int n = this.grantees.size();
        if (m == 0) {
            throw new SQLParseException("No granted name specified");
        }
        if (n == 0) {
            throw new SQLParseException("No grantee specified");
        }
        
        String f = "replace into '%s'.db(host, user, db)values";
        StringBuilder sb = new StringBuilder(format(f, metaSchema));
        int i = 0;
        for (Grantee u: this.grantees) {
            for (String g: this.granteds) {
                sb.append(i++ == 0? "": ',').append('(')
                .append('\'').append(u.host).append('\'').append(',')
                .append('\'').append(u.user).append('\'').append(',')
                .append('\'').append(g).append('\'')
                .append(')');
            }
        }
        
        // check
        try (SQLParser parser = new SQLParser(sb.toString())) {
            SQLStatement stmt = parser.next();
            if ("REPLACE".equals(stmt.getCommand()) && !parser.hasNext()) {
                return (stmt.getSQL());
            }
        } catch (SQLParseException e) {}
        
        throw new SQLParseException(getSQL());
    }

    @Override
    public boolean needSa() {
        return true;
    }
    
    public boolean addGranted(String granted) {
        return this.granteds.add(granted);
    }

    public boolean exists(String granted) {
        return this.granteds.contains(granted);
    }
    
    public boolean addGrantee(String host, String user) {
        Grantee u = new Grantee(host, user);
        return this.grantees.add(u);
    }
    
    public boolean exists(String host, String user) {
        Grantee g = new Grantee(host, user);
        return this.grantees.contains(g);
    }

    static class Grantee {
        final String host;
        final String user;
        
        Grantee(String host, String user) {
            this.host = host;
            this.user = user;
        }
        
        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o instanceof Grantee) {
                Grantee g = (Grantee)o;
                return (this.host.equals(g.host) && this.user.equals(g.user));
            }
            
            return false;
        }
        
        @Override
        public int hashCode() {
            return (this.host.hashCode() ^ this.user.hashCode());
        }
        
    }
    
}
