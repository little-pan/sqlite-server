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
package org.sqlite.server.sql;

/** SQL statement metric.
 * 
 * @author little-pan
 * @since 2019-12-21
 *
 */
public class SQLMetric {
    
    // Imprecise is intentional for performance
    public volatile long selectStmts;
    public volatile long updateStmts;
    public volatile long insertStmts;
    public volatile long deleteStmts;
    public volatile long totalStmts;
    
    public volatile long slowStmts;
    
    public SQLMetric() {
        
    }

}
