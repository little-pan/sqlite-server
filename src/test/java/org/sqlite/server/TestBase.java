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

import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import junit.framework.TestCase;

/**
 * @author little-pan
 * @since 2019-08-31
 *
 */
public abstract class TestBase extends TestCase {
    
    public abstract void test() throws SQLException;
    
    protected static void printfln(String format, Object ...args) {
        printf(format + System.getProperty("line.separator"), args);
    }

    protected static void printf(String format, Object ...args) {
        DateFormat df = new SimpleDateFormat("HH:mm:ss.SSS");
        String prefix = String.format("%s[%s] ", 
                df.format(new Date()), Thread.currentThread().getName());
        System.out.printf(prefix +format, args);
    }
    
}
