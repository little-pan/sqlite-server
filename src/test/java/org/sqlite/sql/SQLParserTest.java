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
import java.util.NoSuchElementException;

import org.sqlite.TestBase;
import org.sqlite.server.pg.sql.InsertReturningStatement;
import org.sqlite.server.sql.ShowColumnsStatement;
import org.sqlite.server.sql.ShowCreateIndexStatement;
import org.sqlite.server.sql.ShowCreateTableStatement;
import org.sqlite.server.sql.ShowIndexesStatement;
import org.sqlite.server.sql.ShowTablesStatement;
import org.sqlite.server.sql.TruncateTableStatement;
import org.sqlite.server.sql.local.KillStatement;
import org.sqlite.server.sql.local.LocalStatement;
import org.sqlite.server.sql.local.SetTransactionStatement;
import org.sqlite.server.sql.local.ShowProcesslistStatement;
import org.sqlite.server.sql.local.ShowStatusStatement;
import org.sqlite.server.sql.meta.AlterUserStatement;
import org.sqlite.server.sql.meta.CreateDatabaseStatement;
import org.sqlite.server.sql.meta.CreateUserStatement;
import org.sqlite.server.sql.meta.DropDatabaseStatement;
import org.sqlite.server.sql.meta.DropUserStatement;
import org.sqlite.server.sql.meta.GrantStatement;
import org.sqlite.server.sql.meta.MetaStatement;
import org.sqlite.server.sql.meta.RevokeStatement;
import org.sqlite.server.sql.meta.ShowDatabasesStatement;
import org.sqlite.server.sql.meta.ShowGrantsStatement;
import org.sqlite.server.sql.meta.ShowUsersStatement;
import org.sqlite.server.util.IoUtils;
import org.sqlite.sql.AttachStatement;
import org.sqlite.sql.DetachStatement;
import org.sqlite.sql.PragmaStatement;
import org.sqlite.sql.SQLParseException;
import org.sqlite.sql.SQLParser;
import org.sqlite.sql.SQLStatement;
import org.sqlite.sql.TransactionStatement;

/**
 * @author little-pan
 * @since 2019-09-05
 *
 */
public class SQLParserTest extends TestBase {
    
    public static void main(String args[]) throws SQLException {
        new SQLParserTest().test();
    }

    @Override
    protected void doTest() throws SQLException {
        // superuser or nosuperuser
        alterUserTest("alter user test@localhost superuser", 1,
                "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, true, null, null);
        alterUserTest("alter user test@localhost superuser ", 1,
                "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, true, null, null);
        alterUserTest("alter user test@localhost superuser--", 1, 
                "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, true, null, null);
        alterUserTest("alter user test@localhost superuser /*a*/", 1,
                "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, true, null, null);
        alterUserTest("alter user test@localhost nosuperuser", 1,
                "tests", "update 'tests'.user set sa = 0 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, false, null, null);
        alterUserTest("alter user test@localhost superuser nosuperuser", 1,
                "tests", "update 'tests'.user set sa = 0 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, false, null, null);
        alterUserTest("alter user test@localhost nosuperuser superuser", 1, 
                "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, true, null, null);
        alterUserTest("alter user test @ localhost nosuperuser superuser", 1, 
                "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, true, null, null);
        alterUserTest("alter user test @/*@*/localhost nosuperuser superuser", 1, 
                "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", null, true, null, null);
        try {
            alterUserTest("alter user test /*@*/localhost nosuperuser superuser", 1, 
                    "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                    "test", "localhost", null, true, null, null);
            fail("No '@' between user and host");
        } catch (SQLParseException e) {
            // OK
        }
        try {
            alterUserTest("alter user test@localhost nosuperusersuperuser", 1, 
                    "tests", "update 'tests'.user set sa = 1 where host = 'localhost' and user = 'test' and protocol = 'pg'",
                    "test", "localhost", null, true, null, null);
            fail("nosuperusersuperuser illegal");
        } catch (SQLParseException e) {
            // OK
        }
        // identified by 'password'
        alterUserTest("alter user test@localhost identified by a123", 1,
                "tests", "update 'tests'.user set password = 'a123' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "a123", null, null, null);
        alterUserTest("alter user test@localhost identified by a123 ", 1, 
                "tests", "update 'tests'.user set password = 'a123' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "a123", null, null, null);
        alterUserTest("alter user test@localhost identified by '123'", 1, 
                "tests", "update 'tests'.user set password = '123' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "123", null, null, null);
        alterUserTest("alter user test@localhost identified by '123' ;", 1, 
                "tests", "update 'tests'.user set password = '123' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "123", null, null, null);
        // identified with PROTOCOL [AUTH_METHOD]
        alterUserTest("alter user test@localhost identified with pg identified by '123'", 1,
                "tests", "update 'tests'.user set password = '123' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "123", null, null, null);
        alterUserTest("alter user test@localhost identified by '123' identified with pg ", 1,
                "tests", "update 'tests'.user set password = '123' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "123", null, null, null);
        alterUserTest("alter user test@localhost identified by '123' identified with pg md5", 1,
                "tests", "update 'tests'.user set password = '123', auth_method = 'md5' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "123", null, null, null);
        alterUserTest("alter user test@localhost identified by '123' identified with pg md5 ", 1,
                "tests", "update 'tests'.user set password = '123', auth_method = 'md5' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "123", null, null, null);
        alterUserTest("alter user test@localhost identified by '123' identified with pg password", 1,
                "tests", "update 'tests'.user set password = '123', auth_method = 'password' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "123", null, null, null);
        alterUserTest("alter user test@localhost identified by '123' identified with pg trust", 1,
                "tests", "update 'tests'.user set password = '123', auth_method = 'trust' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                "test", "localhost", "123", null, null, null);
        try {
            alterUserTest("alter user test@localhost identified by '123' identified with pg trustmd5", 1,
                    "tests", "update 'tests'.user set password = '123', auth_method = 'trust' where host = 'localhost' and user = 'test' and protocol = 'pg'",
                    "test", "localhost", "123", null, null, null);
            fail("trustmd5 illegal");
        } catch (SQLParseException e) {
            // ok
        }
        
        closeTest("select 1;");
        
        commentTest("-- sql/*sql*/", 1);
        commentTest("/*sql--*/", 1);
        commentTest("/*sql--*/--", 1);
        commentTest("/*sql--*/  --", 1);
        commentTest("/*sql--*/\n--", 1);
        commentTest("/*/**/*/", 1);
        commentTest("/*b/**/*/", 1);
        commentTest("/*b/*b*/*/", 1);
        commentTest("/*b/*b*/b*/", 1);
        commentTest("/*select 1;/*select 2;*/select 3*/", 1);
        commentTest("/*select 1;/*select 2;*/select 3;*/", 1);
        commentTest("/*select 1;/*select 2;*/select 3;*/--c", 1);
        commentTest("/*select 1;/*select 2;*/select 3;*/ --c", 1);
        
        createDatabaseTest("create database testdb", 1, false, "testdb", null);
        createDatabaseTest(" create database Testdb", 1, false, "testdb", null);
        createDatabaseTest("create DATABASE Testdb ", 1, false, "testdb", null);
        createDatabaseTest("CREATE DATABASE Testdb ;", 1, false, "testdb", null);
        createDatabaseTest("create database if not exists testdb", 1, true, "testdb", null);
        createDatabaseTest("create database IF/*if*/NOT EXISTS testdb;", 1, true, "testdb", null);
        createDatabaseTest("create database testdb location '/var/lib/sqlite'", 
                1, false, "testdb", "/var/lib/sqlite");
        createDatabaseTest("create database IF NOT EXISTS testdb location '/var/lib/sqlite'", 
                1, true, "testdb", "/var/lib/sqlite");
        createDatabaseTest("create database IF not exists 'TESTDB' directory '/var/lib/sqlite'", 
                1, true, "testdb", "/var/lib/sqlite");
        try {
            createDatabaseTest("create database if", 1, false, "testdb", null);
            fail("\"IF\" keyword can't be as dbname");
        } catch (SQLParseException e) {
            // OK
        }
        
        // simple "create user"
        createUserTest("create user test@localhost identified by '123';", 1, 
                "test", "localhost", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost' IDENTIFIED BY '123' ", 1, 
                "test", "localhost", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost.org' IDENTIFIED BY '123' ;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost.org' with IDENTIFIED BY '123' ;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost.org' WITH IDENTIFIED BY '123' ;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost.org' With IDENTIFIED BY '123' ;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost.org' with/*I*/IDENTIFIED BY '123' ;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost.org' with/*I*/IDENTIFIED BY '123' "
                + "identified with pg md5", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost.org' with/*I*/IDENTIFIED BY '123' "
                + "identified with pg md5 ", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
        createUserTest("CREATE USER 'test'@'localhost.org' with/*I*/IDENTIFIED BY '123' "
                + "identified with pg md5;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
        
        try {
            createUserTest("CREATE USER 'test'@'localhost.org' with/*I*/IDENTIFIED BY '123' "
                + "identified with pg md5 trust;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
            fail("auth method only select one");
        } catch (SQLParseException e) {
            // OK
        }
        try {
            createUserTest("CREATE USER 'test'@'localhost.org' with/*I*/IDENTIFIED BY '123' "
                + "identified with pg md5trust;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
            fail("md5trust illegal");
        } catch (SQLParseException e) {
            // OK
        }
        
        try {
            createUserTest("CREATE USER 'test'@'localhost.org' withIDENTIFIED BY '123' ;", 1, 
                "test", "localhost.org", "123", false, "pg", "md5");
            fail("No space between with and IDENTIFIED");
        } catch (SQLParseException e) {
            // OK
        }
        try {
            createUserTest("CREATE USER 'test'@'Localhost' IDENTIFIED BY '123'", 1, 
                "test", "localhost", "123", false, "pg", "md5");
            fail("'Localhost' incorrect");
        } catch (AssertionError e) {
            // OK
        }
        createUserTest("CREATE USER/*U*/'test'@'localhost' IDENTIFIED BY '123'", 1, 
                "test", "localhost", "123", false, "pg", "md5");
        try {
            createUserTest("CREATE USER'test'@'localhost' IDENTIFIED BY '123'", 1, 
                "test", "localhost", "123", false, "pg", "md5");
            fail("No space between USER keyword and user");
        } catch (SQLParseException e) {
            // OK
        }
        createUserTest("CREATE USER 'test'@'localhost'/**/IDENTIFIED BY '123'", 1, 
                "test", "localhost", "123", false, "pg", "md5");
        try {
            createUserTest("CREATE USER 'test'@'localhost'IDENTIFIED BY '123'", 1, 
                "test", "localhost", "123", false, "pg", "md5");
            fail("No space between host and IDENTIFIED");
        } catch (SQLParseException e) {
            // OK
        }
        createUserTest("CREATE USER 'test'/**/@/**/'localhost' IDENTIFIED BY '123'", 1, 
                "test", "localhost", "123", false, "pg", "md5");
        try {
            createUserTest("CREATE USER 'test''localhost' IDENTIFIED BY '123'", 1, 
                "test", "localhost", "123", false, "pg", "md5");
            fail("No '@' between user and host");
        } catch (SQLParseException e) {
            // OK
        }
        try {
            createUserTest("CREATE USER 'test'/*@*/'localhost' IDENTIFIED BY '123'", 1, 
                "test", "localhost", "123", false, "pg", "md5");
            fail("No '@' between user and host");
        } catch (SQLParseException e) {
            // OK
        }
        // identified with in "create user"
        createUserTest("create user test@localhost identified by '123' identified with pg md5", 1, 
                "test", "localhost", "123", false, "pg", "md5");
        try {
            createUserTest("create user test@localhost identified with pg md5", 1, 
                "test", "localhost", "123", false, "pg", "md5");
            fail("No password with auth method md5");
        } catch (SQLParseException e) {
            // OK
        }
        createUserTest("create user test@localhost identified with pg trust", 1, 
                "test", "localhost", null, false, "pg", "trust");
        createUserTest("create user test@localhost identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", false, "pg", "password");
        try {
            createUserTest("create user test@localhost identified with pg password", 1, 
                "test", "localhost", null, false, "pg", "password");
            fail("No password with auth method password");
        } catch (SQLParseException e) {
            // OK
        }
        try {
            createUserTest("create user test@localhost identified with pg passwd identified by '123'", 1, 
                    "test", "localhost", "123", false, "pg", "password");
            fail("Unknown passwd in pg protocol");
        } catch (SQLParseException e) {
            // OK
        }
        // superuser or nosuperuser
        createUserTest("create user test@localhost superuser identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", true, "pg", "password");
        createUserTest("create user test@localhost Superuser identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", true, "pg", "password");
        createUserTest("create user test@localhost superUser identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", true, "pg", "password");
        createUserTest("create user test@localhost SUPERUSER identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", true, "pg", "password");
        createUserTest("create user test@localhost nosuperuser identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", false, "pg", "password");
        createUserTest("create user test@localhost NOsuperuser identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", false, "pg", "password");
        createUserTest("create user test@localhost NOSUPERUSER identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", false, "pg", "password");
        try {
            createUserTest("create user test@localhost NOTSUPERUSER identified with pg password identified by '123'", 1, 
                "test", "localhost", "123", false, "pg", "password");
            fail("NOTsuperuser incorrect!");
        } catch (SQLParseException e) {
            // OK
        }
        
        deleteTest("delete from t where id =1", 1);
        deleteTest("Delete from t where id = 1", 1);
        deleteTest("deLete from t where id =1;/**/deletE from t where id = 2;", 2);
        deleteTest(" Delete from t where id =1-- sql", 1);
        deleteTest("/**/delete from t where id =1;", 1);
        deleteTest("/*sql*/delete from t where id =1;", 1);
        deleteTest("/*sql*/delete/*;*/from t /*'*/where id=1;", 1);
        deleteTest("/*sql*/delete/*;*/ from t where id=1-- sql", 1);
        deleteTest("/*sql*/delete/*;*/from t where id=1; DeleTe from t /*\"*/ where id=2-- sql", 2);
        
        dropDatabaseTest("drop database test;", 1, "test", false);
        dropDatabaseTest("drop database Test ;", 1, "test", false);
        dropDatabaseTest("drop database if exists Test", 1, "test", true);
        dropDatabaseTest(" DROP Schema if exists Test", 1, "test", true);
        dropDatabaseTest("DROP Schema if exists Test ; drop database if exists test", 2, "test", true);
        dropDatabaseTest("DROP Schema TEST ; drop database test", 2, "test", false);
        
        dropUserTest("drop user test@localhost;", 1, "meta_", new String[][]{{"localhost", "test", "pg"}});
        dropUserTest("drop user 'test' @/**/'localhost' ;", 1, "meta_", new String[][]{{"localhost", "test", "pg"}});
        dropUserTest("drop user 'test' @/**/'localhost' identified with PG ", 
                1, "meta_", new String[][]{{"localhost", "test", "pg"}});
        dropUserTest("drop user 'test' @/**/'localhost','test-a'@'127.0.0.1' identified with PG ", 
                1, "meta_", new String[][]{{"localhost", "test", "pg"}, {"127.0.0.1", "test-a", "pg"}});
        dropUserTest(" drop user 'test' @/**/'localhost' identified with pg , /*aaa*/ 'test-a'@'127.0.0.1' identified with PG ", 
                1, "meta_", new String[][]{{"localhost", "test", "pg"}, {"127.0.0.1", "test-a", "pg"}});
        try {
            dropUserTest("drop user ;", 1, "meta_", new String[][]{{"localhost", "test", "pg"}});
            fail("No 'user'@'host' specified");
        } catch (SQLParseException e) {
            // OK
        }
        
        emptyTest(";", 1);
        emptyTest(" ;", 1);
        emptyTest("; ", 2);
        emptyTest(" ; ", 2);
        emptyTest("/*;*/;", 1);
        emptyTest("/*;*/; ;", 2);
        
        grantTest("grant all on database testdb to test@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all on schema testdb to test@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all PRIvileges on database testdb to test@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all on database 'test.db', testdb to test@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"test.db", "testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all on database 'test.db', testdb to test@localhost, 'test1'@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"test.db", "testdb"}, 
                new String[][]{{"localhost", "test"}, {"localhost", "test1"}});
        try {
            grantTest("grant all on database 'test.db', testdb, to test@localhost, 'test1'@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"test.db", "testdb"}, 
                new String[][]{{"localhost", "test"}, {"localhost", "test1"}});
        } catch (SQLParseException e) {
            // OK
        }
        grantTest("grant all,select,vacuum on database testdb to test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all,select,vacuum on schema testdb to test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all,select,vacuum on testdb to test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all ,select, vacuum on database testdb to test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all , select, vacuum on database testdb to test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all , select , vacuum on database testdb to test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant select ,all ,  vacuum on database testdb to test@localhost", 
                1, "meta", new String[] {"select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant all privileges , select ,  vacuum on database testdb to test@localhost", 
                1, "meta", new String[] {"select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant select ,all privileges,  vacuum on database testdb to test@localhost", 
                1, "meta", new String[] {"select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant select ,  vacuum,all privileges on database testdb to test@localhost", 
                1, "meta", new String[] {"select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant attach, select ,  vacuum,all privileges on database testdb to test@localhost", 
                1, "meta", new String[] {"attach", "select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        grantTest("grant attach, select ,  vacuum,all privileges on testdb to test@localhost", 
                1, "meta", new String[] {"attach", "select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        try {
            grantTest("grant all , on on database testdb to test@localhost", 
                1, "meta", new String[] {"all", "on"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
            fail("Unknonw privilege 'on'");
        } catch (SQLParseException e) {
            // OK
        }
        
        killTest("kill 1;", 1, false, 1);
        killTest("kill connection 1", 1, false, 1);
        killTest("KILL CONNECTION 0x10", 1, false, 0x10);
        killTest("kill query 1", 1, true, 1);
        killTest("kill QUERY 1;", 1, true, 1);
        killTest("kill QUERY 0x1;", 1, true, 0x1);
        
        revokeTest("revoke all on database testdb from test@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all on schema testdb from test@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all PRIvileges on database testdb from test@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all on database 'test.db', testdb from test@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"test.db", "testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all on database 'test.db', testdb from test@localhost, 'test1'@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"test.db", "testdb"}, 
                new String[][]{{"localhost", "test"}, {"localhost", "test1"}});
        try {
            revokeTest("revoke all on database 'test.db', testdb, from test@localhost, 'test1'@localhost", 
                1, "meta", new String[] {"all"}, 
                new String[]{"test.db", "testdb"}, 
                new String[][]{{"localhost", "test"}, {"localhost", "test1"}});
        } catch (SQLParseException e) {
            // OK
        }
        revokeTest("revoke all,select,vacuum on database testdb from test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all,select,vacuum on schema testdb from test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all,select,vacuum on testdb from test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all ,select, vacuum on database testdb from test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all , select, vacuum on database testdb from test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all , select , vacuum on database testdb from test@localhost", 
                1, "meta", new String[] {"all", "select", "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke select ,all ,  vacuum on database testdb from test@localhost", 
                1, "meta", new String[] {"select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke all privileges , select ,  vacuum on database testdb from test@localhost", 
                1, "meta", new String[] {"select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke select ,all privileges,  vacuum on database testdb from test@localhost", 
                1, "meta", new String[] {"select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke select ,  vacuum,all privileges on database testdb from test@localhost", 
                1, "meta", new String[] {"select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke attach, select ,  vacuum,all privileges on database testdb from test@localhost", 
                1, "meta", new String[] {"attach", "select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        revokeTest("revoke attach, select ,  vacuum,all privileges on testdb from test@localhost", 
                1, "meta", new String[] {"attach", "select", "all",  "vacuum"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
        try {
            revokeTest("revoke all , on on database testdb from test@localhost", 
                1, "meta", new String[] {"all", "on"}, 
                new String[]{"testdb"}, new String[][]{{"localhost", "test"}});
            fail("Unknonw privilege 'on'");
        } catch (SQLParseException e) {
            // OK
        }
        
        selectTest("select 1", 1);
        selectTest("select 1;", 1);
        selectTest("Select 1;", 1);
        selectTest("sElect 1;", 1);
        selectTest("selecT 1;", 1);
        selectTest(" select 1-- sql", 1);
        selectTest("/**/select 1;", 1);
        selectTest("/*sql*/select 1;", 1);
        selectTest("/*sql*/select/*;*/ 1/*'*/;", 1);
        selectTest("/*sql*/select/*;*/ 1-- sql", 1);
        selectTest("/*sql*/select/*;*/ 1;select/*\"*/ 2-- sql", 2);
        selectTest("/*select 1;/*select 2;*/select 3;*/ select 1", 1);
        // function sleep() test
        selectTest("select sleep(1)", 1);
        selectTest("select sleep( 1/**/) ;", 1);
        selectTest("select sleep(0x10/**/) ;", 1);
        try {
            selectTest("select sleep(1), sleep(2);", 1);
            fail("Only support \"select sleep(N);\"");
        } catch (SQLParseException e) {
            // OK
        }
        selectTest("select 1, sleep(1) ;", 1);
        selectTest("select 1, sleep(1) -- sleep(2);", 1);
        selectTest("select 1, sleep(1) /*sleep(2)*/;", 1);
        selectTest("select 1, sleep(1) \n -- ;", 1);
        selectTest("select /*sleep(0)*/1, Sleep(1) ;", 1);
        selectTest("select 'sleep(0)', 1, SLEEP(1) ;", 1);
        selectTest("select \"sleep(0)\", 1, sleep(1) ;", 1);
        selectTest("select \"SLEEP(0)\", 1, -- sleep(0)\nsleep(1) ;", 1);
        selectTest("/***/select 1, sleep(1);", 1);
        try {
            selectTest("select sleep(1), 1 ;", 1);
            fail("Only support \"select [expr,] sleep(N);\"");
        } catch (SQLParseException e) {
            // OK
        }
        try {
            selectTest("select 1, Sleep(1), sleep(2);", 1);
            fail("Only support \"select [expr,] sleep(N);\"");
        } catch (SQLParseException e) {
            // OK
        }
        try {
            selectTest("select sleep(1) from t;", 1);
            fail("Only support \"select [expr,] sleep(N);\"");
        } catch (SQLParseException e) {
            // OK
        }
        
        selectForUpdateTest("select *from t for update", 1, "select *from t ");
        selectForUpdateTest("select *from T FOR UPDATE ;", 1, "select *from T ");
        selectForUpdateTest("SELECT * FROM t/***/for update", 1, "SELECT * FROM t/***/");
        selectForUpdateTest("select *from t/**for update*/for update", 
                1, "select *from t/**for update*/");
        selectForUpdateTest("select *from t/**for update*/for update --;", 
                1, "select *from t/**for update*/");
        selectForUpdateTest(" /***/select *from t/**for update*/for update --;", 
                1, " /***/select *from t/**for update*/");
        selectForUpdateTest(" /***/select 'for update' from t/**for update*/for update --;", 
                1, " /***/select 'for update' from t/**for update*/");
        
        updateTest("update t set a = 1", 1);
        updateTest("Update t set a = 1", 1);
        updateTest("updatE t set a = 1;/**/uPdate t set b=2;", 2);
        updateTest(" Update t set a = 1-- sql", 1);
        updateTest("/**/Update t set a = 1;", 1);
        updateTest("/*sql*/Update t set a = 1;", 1);
        updateTest("/*sql*/update/*;*/t set/*'*/a=1;", 1);
        updateTest("/*sql*/update/*;*/ t set a= 1-- sql", 1);
        updateTest("/*sql*/update/*;*/ t set a= 1;update t set/*\"*/ b=2-- sql", 2);
        
        insertTest("insert into t(a) values(1)", 1);
        insertTest("Insert into t(a) values(1)", 1);
        insertTest("inSerT into t(a) values(1);/**/insert into t(a) values(2);", 2);
        insertTest(" iNsert into t(a) values(1)-- sql", 1);
        insertTest("/**/insert into t(a) values(1);", 1);
        insertTest("/*sql*/insert into t(a) values(1);", 1);
        insertTest("/*sql*/insert/*;*/into t (a)/*'*/values(1);", 1);
        insertTest("/*sql*/insert/*;*/ into t(a) values(1)-- sql", 1);
        insertTest("/*sql*/insert/*;*/ into t(a) values(1); insert into t(a) /*\"*/values(2)-- sql", 2);
        
        insertSelectTest("insert into t(a) select 1", 1);
        insertSelectTest("insert into t(a) select 1 ;", 1);
        insertSelectTest("insert into t(a)/***/ select 1 --", 1);
        insertSelectTest(" /**/insert into t(a)-- \n/***/ select 1 --", 1);
        
        insertReturningTest("insert into t(a)values(1) returning*", 1, false, null, "t", "*");
        insertReturningTest("insert into t(a) values(1) returning *", 1, false, null, "t", "*");
        insertReturningTest("insert into t(a) values(1)  returning * ;", 1, false, null, "t", "* ");
        insertReturningTest("insert into t(a)/***/ values(1) --\nreturning *", 1, false, null, "t", "*");
        insertReturningTest(" /**/insert into t(a)-- \n/***/ select 1 --\n returning/****/--\n* /***/ ",
                1, false, null, "t", "* /***/ ");
        insertReturningTest("insert into t(a) select 1 returning*", 1, true, null, "t", "*");
        insertReturningTest("insert into t(a) select 1 returning *", 1, true, null, "t", "*");
        insertReturningTest("insert into t(a) select 1  returning * ;", 1, true, null, "t", "* ");
        insertReturningTest("insert into t(a)/***/ select 1 --\nreturning *", 1, true, null, "t", "*");
        insertReturningTest(" /**/insert into t(a)-- \n/***/ select 1 --\n returning/****/--\n* /***/ ",
                1, true, null, "t", "* /***/ ");
        insertReturningTest("insert into t(a) select 'returning' RETURNING*", 1, true, null, "t", "*");
        insertReturningTest("insert into t(a) select 1/*returning*/ returning *", 1, true, null, "t", "*");
        insertReturningTest("insert into t(a) select \"returning\"  returning * ;", 1, true, null, "t", "* ");
        insertReturningTest("insert into t(a)/***/ select 'RETURNING' --\nRETURNING *", 1, true, null, "t", "*");
        insertReturningTest(" /*returning*/insert into t(a)-- returning\n/***/ select 1 --returning\n returning/****/--\n* /***/ ",
                1, true, null, "t", "* /***/ ");
        
        truncateTableTest("truncate test;", 1, null, "test");
        truncateTableTest("truncate test ;", 1, null, "test");
        truncateTableTest("truncate a.test;", 1, "a", "test");
        truncateTableTest("truncate a.test ;", 1, "a", "test");
        truncateTableTest("truncate 'a'.test;", 1, "a", "test");
        truncateTableTest("truncate 'a'.test ;", 1, "a", "test");
        truncateTableTest("truncate 'a' .test;", 1, "a", "test");
        truncateTableTest("Truncate 'a'. test ;", 1, "a", "test");
        truncateTableTest("TRUNCATE 'a' . test ;", 1, "a", "test");
        truncateTableTest("TRUNCATE 'a' . test ;truncate table a.test", 2, "a", "test");
        truncateTableTest("TRUNCATE 'a' . test ; truncate table a.test", 2, "a", "test");
        truncateTableTest("truncate test", 1, null, "test");
        truncateTableTest("truncate test ", 1, null, "test");
        truncateTableTest("truncate a.test", 1, "a", "test");
        truncateTableTest("truncate a.test ", 1, "a", "test");
        truncateTableTest("truncate 'a'.test", 1, "a", "test");
        truncateTableTest("truncate 'a'.test ", 1, "a", "test");
        truncateTableTest("truncate 'a' .test", 1, "a", "test");
        truncateTableTest("Truncate 'a'. test ", 1, "a", "test");
        truncateTableTest("TRUNCATE 'a' . test ", 1, "a", "test");
        truncateTableTest("TRUNCATE 'a' . test ;truncate table a.test;", 2, "a", "test");
        truncateTableTest("TRUNCATE 'a' . test ; truncate table a.test;", 2, "a", "test");
        truncateTableTest("truncate table test;", 1, null, "test");
        truncateTableTest("truncate table test ;", 1, null, "test");
        truncateTableTest("truncate table a.test;", 1, "a", "test");
        truncateTableTest("truncate table a.test ;", 1, "a", "test");
        truncateTableTest("truncate table 'a'.test;", 1, "a", "test");
        truncateTableTest("truncate table 'a'.test ;", 1, "a", "test");
        truncateTableTest("truncate table 'a' .test;", 1, "a", "test");
        truncateTableTest("truncate table 'a'. test ;", 1, "a", "test");
        truncateTableTest("truncate table 'a' . test ;", 1, "a", "test");
        
        boolean deferred = true, immediate = false, exclusive = false;
        txBeginTest("begin", 1, deferred, immediate, exclusive);
        txBeginTest("begin;", 1, deferred, immediate, exclusive);
        txBeginTest("start transaction;", 1, deferred, immediate, exclusive);
        txBeginTest(" begin;", 1, deferred, immediate, exclusive);
        txBeginTest(" start transaction ;", 1, deferred, immediate, exclusive);
        txBeginTest("Begin;", 1, deferred, immediate, exclusive);
        txBeginTest(" Begin ;", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/ begin--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin --", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin/*tx*/--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin/*tx*/--;", 1, deferred, immediate, exclusive);
        txBeginTest("begin; begin", 2, deferred, immediate, exclusive);
        txBeginTest("begin;Begin;", 2, deferred, immediate, exclusive);
        txBeginTest(" begin;beGin;", 2, deferred, immediate, exclusive);
        txBeginTest("Begin;/*tx*/begin;", 2, deferred, immediate, exclusive);
        txBeginTest(" Begin ;begin;", 2, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin;begin--;", 2, deferred, immediate, exclusive);
        txBeginTest("begin;/*tx*/ begin--", 2, deferred, immediate, exclusive);
        txBeginTest("Begin;/*tx*/begin --", 2, deferred, immediate, exclusive);
        txBeginTest("begiN;/*tx*/begin/*tx*/--", 2, deferred, immediate, exclusive);
        txBeginTest("begIn;/*tx*/begin/*tx*/--;", 2, deferred, immediate, exclusive);
        txBeginTest("begIn transaction;/*tx*/begin/*tx*/--;", 2, deferred, immediate, exclusive);
        // deferred
        txBeginTest("begin deferred", 1, deferred, immediate, exclusive);
        txBeginTest("begin Deferred;", 1, deferred, immediate, exclusive);
        txBeginTest("start Deferred transaction ;", 1, deferred, immediate, exclusive);
        txBeginTest(" begin DEFERRED;", 1, deferred, immediate, exclusive);
        txBeginTest("Begin  deferred;", 1, deferred, immediate, exclusive);
        txBeginTest(" Begin  deferred ;", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin deferred--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/ begin deferred--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin deferred --", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin/*tx*/deferred--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin/*tx*/ deferred--;", 1, deferred, immediate, exclusive);
        txBeginTest("begin deferred; begin deferred", 2, deferred, immediate, exclusive);
        txBeginTest("begin deferred;Begin deferred;", 2, deferred, immediate, exclusive);
        txBeginTest(" begin deferred;beGin deferred;", 2, deferred, immediate, exclusive);
        txBeginTest("Begin deferred;/*tx*/begin deferred;", 2, deferred, immediate, exclusive);
        txBeginTest(" Begin deferred ;begin deferred;", 2, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin deferred;begin deferred--;", 2, deferred, immediate, exclusive);
        txBeginTest("begin deferred;/*tx*/ begin deferred--", 2, deferred, immediate, exclusive);
        txBeginTest("Begin deferred;/*tx*/begin deferred --", 2, deferred, immediate, exclusive);
        txBeginTest("begiN deferred;/*tx*/begin deferred/*tx*/--", 2, deferred, immediate, exclusive);
        txBeginTest("begIn deferred;/*tx*/begin deferred/*tx*/--;", 2, deferred, immediate, exclusive);
        txBeginTest("begIn deferred transaction;/*tx*/begin deferred/*tx*/--;", 2, deferred, immediate, exclusive);
        txBeginTest("begIn DEFERRED transaction;/*tx*/begin deferred/*tx*/work--;", 2, deferred, immediate, exclusive);
        // immediate
        deferred = false; immediate = true; exclusive = false;
        txBeginTest("begin immediate", 1, deferred, immediate, exclusive);
        txBeginTest("begin IMMEDIATE;", 1, deferred, immediate, exclusive);
        txBeginTest(" begin IMMEDIATE;", 1, deferred, immediate, exclusive);
        txBeginTest("Begin  IMMEDIATE;", 1, deferred, immediate, exclusive);
        txBeginTest(" Begin  IMMEDIATE ;", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin IMMEDIATE--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/ begin IMMEDIATE--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin IMMEDIATE --", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin/*tx*/IMMEDIATE--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin/*tx*/ IMMEDIATE--;", 1, deferred, immediate, exclusive);
        txBeginTest("begin IMMEDIATE; begin IMMEDIATE", 2, deferred, immediate, exclusive);
        txBeginTest("begin IMMEDIATE;Begin IMMEDIATE;", 2, deferred, immediate, exclusive);
        txBeginTest(" begin IMMEDIATE;beGin IMMEDIATE;", 2, deferred, immediate, exclusive);
        txBeginTest("Begin IMMEDIATE;/*tx*/begin IMMEDIATE;", 2, deferred, immediate, exclusive);
        txBeginTest(" Begin IMMEDIATE ;begin IMMEDIATE;", 2, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin IMMEDIATE;begin IMMEDIATE--;", 2, deferred, immediate, exclusive);
        txBeginTest("begin IMMEDIATE;/*tx*/ begin IMMEDIATE--", 2, deferred, immediate, exclusive);
        txBeginTest("Begin IMMEDIATE;/*tx*/begin IMMEDIATE --", 2, deferred, immediate, exclusive);
        txBeginTest("begiN IMMEDIATE;/*tx*/begin IMMEDIATE/*tx*/--", 2, deferred, immediate, exclusive);
        txBeginTest("begIn IMMEDIATE;/*tx*/begin IMMEDIATE/*tx*/--;", 2, deferred, immediate, exclusive);
        txBeginTest("begIn IMMEDIATE transaction;/*tx*/begin IMMEDIATE/*tx*/--;", 2, deferred, immediate, exclusive);
        txBeginTest("begIn IMMEDIATE transaction;/*tx*/begin IMMEDIATE/*tx*/work--;", 2, deferred, immediate, exclusive);
        // exclusive
        deferred = false; immediate = false; exclusive = true;
        txBeginTest("begin exclusive ", 1, deferred, immediate, exclusive);
        txBeginTest("begin EXCLUSIVE ;", 1, deferred, immediate, exclusive);
        txBeginTest(" begin EXCLUSIVE ;", 1, deferred, immediate, exclusive);
        txBeginTest("Begin  EXCLUSIVE ;", 1, deferred, immediate, exclusive);
        txBeginTest(" Begin  EXCLUSIVE ;", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin EXCLUSIVE--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/ begin EXCLUSIVE--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin EXCLUSIVE --", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin/*tx*/EXCLUSIVE--", 1, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin/*tx*/ EXCLUSIVE--;", 1, deferred, immediate, exclusive);
        txBeginTest("begin EXCLUSIVE; begin EXCLUSIVE", 2, deferred, immediate, exclusive);
        txBeginTest("begin EXCLUSIVE;Begin EXCLUSIVE;", 2, deferred, immediate, exclusive);
        txBeginTest(" begin EXCLUSIVE;beGin EXCLUSIVE;", 2, deferred, immediate, exclusive);
        txBeginTest("Begin EXCLUSIVE;/*tx*/begin EXCLUSIVE;", 2, deferred, immediate, exclusive);
        txBeginTest(" Begin EXCLUSIVE ;begin EXCLUSIVE;", 2, deferred, immediate, exclusive);
        txBeginTest("/*tx*/begin EXCLUSIVE;begin EXCLUSIVE--;", 2, deferred, immediate, exclusive);
        txBeginTest("begin EXCLUSIVE;/*tx*/ begin EXCLUSIVE--", 2, deferred, immediate, exclusive);
        txBeginTest("Begin EXCLUSIVE;/*tx*/begin EXCLUSIVE --", 2, deferred, immediate, exclusive);
        txBeginTest("begiN EXCLUSIVE;/*tx*/begin EXCLUSIVE/*tx*/--", 2, deferred, immediate, exclusive);
        txBeginTest("begIn EXCLUSIVE;/*tx*/begin EXCLUSIVE/*tx*/--;", 2, deferred, immediate, exclusive);
        txBeginTest("begIn EXCLUSIVE transaction;/*tx*/begin EXCLUSIVE/*tx*/--;", 2, deferred, immediate, exclusive);
        txBeginTest("begIn EXCLUSIVE transaction;/*tx*/begin EXCLUSIVE/*tx*/work--;", 2, deferred, immediate, exclusive);
        // transaction mode
        boolean readOnly;
        deferred = false; immediate = false; exclusive = true;
        readOnly = true;
        txBeginTest("begin read only", 1, true, immediate, false, readOnly);
        txBeginTest("begin", 1, true, immediate, false, null);
        txBeginTest("begin exclusive transaction read only", 1, deferred, immediate, exclusive, readOnly);
        txBeginTest("begin exclusive transaction", 1, deferred, immediate, exclusive, null);
        txBeginTest("begin exclusive transaction isolation level serializable, read only", 
                1, deferred, immediate, exclusive, readOnly, TransactionMode.SERIALIZABLE);
        txBeginTest("begin exclusive transaction read only,isolation level serializable", 
                1, deferred, immediate, exclusive, readOnly, TransactionMode.SERIALIZABLE);
        txBeginTest("begin exclusive transaction read only, isolation level read committed", 
                1, deferred, immediate, exclusive, readOnly, TransactionMode.READ_COMMITTED);
        txBeginTest("start exclusive transaction read only, isolation level read committed", 
                1, deferred, immediate, exclusive, readOnly, TransactionMode.READ_COMMITTED);
        txBeginTest("start exclusive transaction isolation level read committed", 
                1, deferred, immediate, exclusive, null, TransactionMode.READ_COMMITTED);
        readOnly = false;
        txBeginTest("begin read write;", 1, true, immediate, false, readOnly);
        txBeginTest("begin EXCLUSIVE transaction read write;", 1, deferred, immediate, exclusive, readOnly);
        txBeginTest("begin EXCLUSIVE transaction read write, isolation level serializable;", 
                1, deferred, immediate, exclusive, readOnly, TransactionMode.SERIALIZABLE);
        txBeginTest("begin EXCLUSIVE transaction isolation level serializable,read write;", 
                1, deferred, immediate, exclusive, readOnly, TransactionMode.SERIALIZABLE);
        txBeginTest("begin EXCLUSIVE transaction isolation level read uncommitted, read write;", 
                1, deferred, immediate, exclusive, readOnly, TransactionMode.READ_UNCOMMITTED);
        txBeginTest("start EXCLUSIVE transaction isolation level read uncommitted, read write;", 
                1, deferred, immediate, exclusive, readOnly, TransactionMode.READ_UNCOMMITTED);
        
        txSetTransactionTest("set transaction read only, isolation level serializable", 
                1, false, true);
        txSetTransactionTest("set transaction isolation level serializable", 
                1, false, null);
        txSetTransactionTest("set transaction isolation level serializable, read only", 
                1, false, true);
        txSetTransactionTest("set transaction isolation level serializable, read write", 
                1, false, false);
        txSetTransactionTest("set session characteristics as transaction isolation level serializable, read only", 
                1, true, true);
        txSetTransactionTest("set session characteristics as transaction isolation level serializable", 
                1, true, null);
        txSetTransactionTest("set session characteristics as transaction read only, isolation level serializable", 
                1, true, true);
        txSetTransactionTest("set session characteristics as transaction isolation level serializable, read write", 
                1, true, false);
        txSetTransactionTest("set session characteristics as transaction isolation level read committed, read only", 
                1, true, true, TransactionMode.READ_COMMITTED);
        txSetTransactionTest("set session characteristics as transaction isolation level read committed, read write", 
                1, true, false, TransactionMode.READ_COMMITTED);
        txSetTransactionTest("set session characteristics as transaction read write, isolation level read committed", 
                1, true, false, TransactionMode.READ_COMMITTED);
        txSetTransactionTest("set session characteristics as transaction isolation level read committed", 
                1, true, null, TransactionMode.READ_COMMITTED);
        
        // show
        showColumnsTest("show columns from test", 1, false, null, "test");
        showColumnsTest("show columns from test;", 1, false, null, "test");
        showColumnsTest("show columns from a.test;", 1, false, "a", "test");
        showColumnsTest("show columns FROM a.test from b;", 1, false, "b", "test");
        showColumnsTest("show columns in test", 1, false, null, "test");
        showColumnsTest("show columns in test;", 1, false, null, "test");
        showColumnsTest("show columns in a.test;", 1, false, "a", "test");
        showColumnsTest("show COLUMNS in a.test from b;", 1, false, "b", "test");
        showColumnsTest("SHOW columns IN a.test in b;", 1, false, "b", "test");
        showColumnsTest("show fields from test", 1, false, null, "test");
        showColumnsTest("show fields from test;", 1, false, null, "test");
        showColumnsTest("show fields from a.test ;", 1, false, "a", "test");
        showColumnsTest("show fields from a.test from b ;", 1, false, "b", "test");
        showColumnsTest("show fields in test", 1, false, null, "test");
        showColumnsTest("show fields in test;", 1, false, null, "test");
        showColumnsTest("show fields in a.test;", 1, false, "a", "test");
        showColumnsTest("show FIELDS in 'a'.test FROM b;", 1, false, "b", "test");
        showColumnsTest("SHOW fields in 'a'.test IN 'b';", 1, false, "b", "test");
        
        showCreateIndexTest("show create index idx_test;", 1, null, "idx_test");
        showCreateIndexTest("show create index idx_test ;", 1, null, "idx_test");
        showCreateIndexTest("show create index idx_test", 1, null, "idx_test");
        showCreateIndexTest("show create index idx_test ", 1, null, "idx_test");
        showCreateIndexTest("show create INDEX idx_test from s;", 1, "s", "idx_test");
        showCreateIndexTest("SHOW create index idx_test in s;", 1, "s", "idx_test");
        showCreateIndexTest("show CREATE INDEX idx_test IN s", 1, "s", "idx_test");
        showCreateIndexTest("show create index idx_test in s ", 1, "s", "idx_test");
        showCreateIndexTest("show create index idx_test from s;", 1, "s", "idx_test");
        showCreateIndexTest("show create index a.idx_test in s;", 1, "s", "idx_test");
        showCreateIndexTest("show create index a .idx_test in s", 1, "s", "idx_test");
        showCreateIndexTest("show create index a. idx_test in s ", 1, "s", "idx_test");
        showCreateIndexTest("show create index a/**/. idx_test in s ", 1, "s", "idx_test");
        
        showCreateTableTest("show create table test;", 1, null, "test");
        showCreateTableTest("show create table test ;", 1, null, "test");
        showCreateTableTest("show create table test", 1, null, "test");
        showCreateTableTest("show create table test ", 1, null, "test");
        showCreateTableTest("show create table test from s;", 1, "s", "test");
        showCreateTableTest("show create table test in s;", 1, "s", "test");
        showCreateTableTest("show create table test in s", 1, "s", "test");
        showCreateTableTest("show create table test in s ", 1, "s", "test");
        showCreateTableTest("show create table test from s;", 1, "s", "test");
        showCreateTableTest("show create table a.test in s;", 1, "s", "test");
        showCreateTableTest("show create table a .test in s", 1, "s", "test");
        showCreateTableTest("show create table a. test in s ", 1, "s", "test");
        showCreateTableTest("show create table a/**/. test in s ", 1, "s", "test");
        
        showDatabasesTest("show databases", 1, false);
        showDatabasesTest(" Show DATABASES", 1, false);
        showDatabasesTest(" Show all DATABASES", 1, true);
        showDatabasesTest(" Show ALL DATABASES;", 1, true);
        showDatabasesTest(" Show ALL DATABASES ;", 1, true);
        
        showGrantsTest("show grants for test@localhost", 
                1, "localhost", "test", false, true);
        showGrantsTest("show grants for 'test' @ 'localhost'", 
                1, "localhost", "test", false, true);
        showGrantsTest("show grants for 'test'", 1, "%", "test", false, true);
        showGrantsTest(" show GRANTS for current_user", 1, "%", null, true, false);
        showGrantsTest("show grants FOR current_user()", 1, "%", null, true, false);
        showGrantsTest("SHOW grants for CURRENT_USER ( ) ", 1, "%", null, true, false);
        showGrantsTest("show grants", 1, "%", null, true, false);
        showGrantsTest("show grants ", 1, "%", null, true, false);
        
        // show indexes
        showIndexesTest("show indexes", 1, null, null, false, false, null);
        showIndexesTest("show index", 1, null, null, false, false, null);
        showIndexesTest("show index ", 1, null, null, false, false, null);
        showIndexesTest("show index where '%idx%' ;", 1, null, null, false, false, "%idx%");
        showIndexesTest("SHOW indexes;", 1, null, null, false, false, null);
        showIndexesTest("SHOW INDEX ;", 1, null, null, false, false, null);
        showIndexesTest("show INDEX WHERE '%idx%' ;", 1, null, null, false, false, "%idx%");
        showIndexesTest("show indexes from test", 1, null, "test", false, false, null);
        showIndexesTest("show indexes from a.test", 1, "a", "test", false, false, null);
        showIndexesTest("show indexes from a.test;", 1, "a", "test", false, false, null);
        showIndexesTest("show indexes from a.test from b ;", 1, "b", "test", false, false, null);
        showIndexesTest("show indexes FROM test;", 1, null, "test", false, false, null);
        showIndexesTest("SHOW INDEX IN a.test;", 1, "a", "test", false, false, null);
        showIndexesTest("show INDEXES in a.test ;", 1, "a", "test", false, false, null);
        showIndexesTest("show indexes FROM a.test IN b ;", 1, "b", "test", false, false, null);
        // show indexes columns
        showIndexesTest("Show index columns from idx_test", 1, null, "idx_test", true, false, null);
        showIndexesTest("show indexes columns from a.idx_test", 1, "a", "idx_test", true, false, null);
        showIndexesTest("show indexes columns from a.idx_test;", 1, "a", "idx_test", true, false, null);
        showIndexesTest("show indexes columns from a.idx_test from b ;", 1, "b", "idx_test", true, false, null);
        showIndexesTest("show indexes COLUMNS FROM idx_test;", 1, null, "idx_test", true, false, null);
        showIndexesTest("SHOW INDEX COLUMNS IN a.idx_test;", 1, "a", "idx_test", true, false, null);
        showIndexesTest("show INDEXES COLUMNS in a.idx_test ;", 1, "a", "idx_test", true, false, null);
        showIndexesTest("show INDEXES COLUMNS in a.idx_test from b;", 1, "b", "idx_test", true, false, null);
        showIndexesTest("show INDEX COLUMNS in a.idx_test from b;", 1, "b", "idx_test", true, false, null);
        showIndexesTest("show indexes COLUMNS FROM a.idx_test IN b ;", 1, "b", "idx_test", true, false, null);
        showIndexesTest("show indexes columns in idx_accounts_balance from main;", 
                1, "main", "idx_accounts_balance", true, false, null);
        // show indexes extended columns
        showIndexesTest("Show index extended columns from idx_test", 1, null, "idx_test", true, true, null);
        showIndexesTest("show indexes extended columns from a.idx_test", 1, "a", "idx_test", true, true, null);
        showIndexesTest("show indexes extended columns from a.idx_test;", 1, "a", "idx_test", true, true, null);
        showIndexesTest("show indexes extended columns from a.idx_test from b ;", 1, "b", "idx_test", true, true, null);
        showIndexesTest("show indexes extended COLUMNS FROM idx_test;", 1, null, "idx_test", true, true, null);
        showIndexesTest("SHOW INDEX extended COLUMNS IN a.idx_test;", 1, "a", "idx_test", true, true, null);
        showIndexesTest("show INDEXES extended COLUMNS in a.idx_test ;", 1, "a", "idx_test", true, true, null);
        showIndexesTest("show INDEXES extended COLUMNS in a.idx_test from b;", 1, "b", "idx_test", true, true, null);
        showIndexesTest("show indexes extended COLUMNS FROM a.idx_test IN b ;", 1, "b", "idx_test", true, true, null);
        
        showProcesslistTest("show processlist;", 1, false);
        showProcesslistTest("show processlist", 1, false);
        showProcesslistTest("Show processlist;", 1, false);
        showProcesslistTest("SHOW PROCESSLIST;", 1, false);
        showProcesslistTest("show full processlist", 1, true);
        showProcesslistTest("show FULL processlist;", 1, true);
        showProcesslistTest("SHOW full PROCESSLIST", 1, true);
        showProcesslistTest("SHOW FULL PROCESSLIST;", 1, true);
        
        showStatusTest("show status;", 1);
        showStatusTest("show status", 1);
        showStatusTest("show status ", 1);
        showStatusTest("show status; show status;", 2);
        showStatusTest("show status;show status ", 2);
        showStatusTest("show status; /*show*/show status ", 2);
        
        showTablesTest("show tables; SHOW tables; show TABLES; SHOW TABLES", 4, null, null);
        showTablesTest("show tables; SHOW tables; show TABLES; SHOW TABLES", 4, null, null);
        showTablesTest("show tables; /**a**/SHOW tables; show TABLES/**a**/;/**a**/SHOW TABLES", 4, null, null);
        showTablesTest("show tables from test", 1, "test", null);
        showTablesTest("show tables from 'test'", 1, "test", null);
        showTablesTest("show tables from test ;", 1, "test", null);
        showTablesTest("show tables like '%a';", 1, null, "%a");
        showTablesTest("show tables like 'ab%';", 1, null, "ab%");
        showTablesTest("show tables like '_a';", 1, null, "_a");
        showTablesTest("show tables like 'ab_';", 1, null, "ab_");
        showTablesTest("show tables from test like '%a';", 1, "test", "%a");
        showTablesTest("show tables from test like 'ab%';", 1, "test", "ab%");
        showTablesTest("show tables from test like '_a';", 1, "test", "_a");
        showTablesTest("show tables from test like 'ab_';", 1, "test", "ab_");
        
        showUsersTest("show users;", 1, null);
        showUsersTest("show users ;", 1, null);
        showUsersTest("SHOW USERS;", 1, null);
        showUsersTest("show users where 't%';", 1, "t%");
        showUsersTest("show users WHERE 't%' ;", 1, "t%");
        showUsersTest("show users WHERE 't%' ", 1, "t%");
        
        txCommitTest("commit", 1);
        txCommitTest("commit transaction", 1);
        txCommitTest("commit;", 1);
        txCommitTest(" commit;", 1);
        txCommitTest("Commit;", 1);
        txCommitTest(" Commit ;", 1);
        txCommitTest("/*tx*/commit--", 1);
        txCommitTest("/*tx*/ commit--", 1);
        txCommitTest("/*tx*/commit --", 1);
        txCommitTest("/*tx*/commit/*tx*/--", 1);
        txCommitTest("/*tx*/commit/*tx*/--;", 1);
        txCommitTest("commit; commit", 2);
        txCommitTest("commit;Commit;", 2);
        txCommitTest(" commit;comMit;", 2);
        txCommitTest("Commit;/*tx*/commit;", 2);
        txCommitTest(" Commit ;commit;", 2);
        txCommitTest("/*tx*/commit;commit--;", 2);
        txCommitTest("commit;/*tx*/ commit--", 2);
        txCommitTest("commit;/*tx*/commit --", 2);
        txCommitTest("commiT;/*tx*/commit/*tx*/--", 2);
        txCommitTest("commIt;/*tx*/commit/*tx*/--;", 2);
        
        txEndTest("end", 1);
        txEndTest("End transaction", 1);
        txEndTest("end;", 1);
        txEndTest(" END;", 1);
        txEndTest("End;", 1);
        txEndTest(" End ;", 1);
        txEndTest("/*tx*/end--", 1);
        txEndTest("/*tx*/ end--", 1);
        txEndTest("/*tx*/end --", 1);
        txEndTest("/*tx*/end/*tx*/--", 1);
        txEndTest("/*tx*/end/*tx*/--;", 1);
        txEndTest("end; end", 2);
        txEndTest("end;End;", 2);
        txEndTest(" end;eNd;", 2);
        txEndTest("End;/*tx*/end;", 2);
        txEndTest(" End ;end;", 2);
        txEndTest("/*tx*/end;end--;", 2);
        txEndTest("end;/*tx*/ end--", 2);
        txEndTest("end;/*tx*/end transaction --", 2);
        txEndTest("enD transaction;/*tx*/end/*tx*/--", 2);
        txEndTest("end;/*tx*/end/*tx*/--;", 2);
        
        txSavepointTest("savepoint a", 1);
        txSavepointTest("savepoint 'a';", 1);
        txSavepointTest(" savepoint \"a\";", 1);
        txSavepointTest("Savepoint a;", 1);
        txSavepointTest(" Savepoint a ;", 1);
        txSavepointTest("/*tx*/savepoint a--", 1);
        txSavepointTest("/*tx*/ savepoint a--", 1);
        txSavepointTest("/*tx*/savepoint a --", 1);
        txSavepointTest("/*tx*/savepoint a/*tx*/--", 1);
        txSavepointTest("/*tx*/savepoint a/*tx*/--;", 1);
        txSavepointTest("savepoint a; savepoint b", 2);
        txSavepointTest("savepoint a;Savepoint b;", 2);
        txSavepointTest(" savepoint a;savEpoint b;", 2);
        txSavepointTest("Savepoint a;/*tx*/savepoint b;", 2);
        txSavepointTest(" Savepoint a ;savepoint b;", 2);
        txSavepointTest("/*tx*/savepoint a;savepoint b--;", 2);
        txSavepointTest("savepoint a;/*tx*/ savepoint b--", 2);
        txSavepointTest("savePoint a;/*tx*/savepoint b --", 2);
        txSavepointTest("sAvepoint a;/*tx*/savepoint b/*tx*/--", 2);
        txSavepointTest("saVepoint a;/*tx*/savepoint b/*tx*/--;", 2);
        
        txReleaseTest("release a", 1);
        txReleaseTest("release 'a';", 1);
        txReleaseTest(" release \"a\";", 1);
        txReleaseTest(" release savepoint \"a\";", 1);
        txReleaseTest("Release savepoint/*tx*/ a;", 1);
        txReleaseTest(" rElease a ;", 1);
        txReleaseTest("/*tx*/release a--", 1);
        txReleaseTest("/*tx*/ release a--", 1);
        txReleaseTest("/*tx*/release a --", 1);
        txReleaseTest("/*tx*/release a/*tx*/--", 1);
        txReleaseTest("/*tx*/release a/*tx*/--;", 1);
        txReleaseTest("release a; release b", 2);
        txReleaseTest("release a;Release b;", 2);
        txReleaseTest(" release a;Release savEpoint b;", 2);
        txReleaseTest("release a;/*tx*/release b;", 2);
        txReleaseTest(" release a ;release b;", 2);
        txReleaseTest("/*tx*/release savepoint a;release b--;", 2);
        txReleaseTest("release a;/*tx*/ release savepoint b--", 2);
        txReleaseTest("release a;/*tx*/release b --", 2);
        txReleaseTest("release a;/*tx*/release /*tx*/savepoint b/*tx*/--", 2);
        txReleaseTest("release a;/*tx*/release b/*tx*/--;", 2);
        
        txRollbackTest("rollback", 1, false);
        txRollbackTest("rollback to 'a';", 1, true);
        txRollbackTest(" Rollback to savepoint \"a\";", 1, true);
        txRollbackTest(" rollback to savepoint \"a\";", 1, true);
        txRollbackTest(" rollback transaction to savepoint \"a\";", 1, true);
        txRollbackTest("rOllback to/*tx*/ a;", 1, true);
        txRollbackTest(" roLlback ;", 1, false);
        txRollbackTest(" roLlback transaction;", 1, false);
        txRollbackTest("/*tx*/rollback to a--", 1, true);
        txRollbackTest("/*tx*/ rollback --", 1, false);
        txRollbackTest("/*tx*/rollback to a --", 1, true);
        txRollbackTest("/*tx*/rollback transaction to a --", 1, true);
        txRollbackTest("/*tx*/rollback to a/*tx*/--", 1, true);
        txRollbackTest("/*tx*/rollback/*tx*/--;", 1, false);
        txRollbackTest("rollback to a; rollback to b", 2, true);
        txRollbackTest("rollback;Rollback;", 2, false);
        txRollbackTest(" rollback to a;rollBack to savEpoint b;", 2, true);
        txRollbackTest("rollback;/*tx*/rollback;", 2, false);
        txRollbackTest(" rollback to a ;rollback to b;", 2, true);
        txRollbackTest("/*tx*/rollback to savepoint a;rollback to b--;", 2, true);
        txRollbackTest("rollback to a;/*tx*/ rollback to savepoint b--", 2, true);
        txRollbackTest("rollback;/*tx*/rollback --", 2, false);
        txRollbackTest("rollback to a;/*tx*/rollback to /*tx*/savepoint b/*tx*/--", 2, true);
        txRollbackTest("rollback to a;/*tx*/rollback to b/*tx*/--;", 2, true);
        
        attachTest("attach test as test;", 1, "test", "test");
        attachTest("ATTACH 'test' as test;", 1, "test", "test");
        attachTest("attach \"test\" as 'test';", 1, "test", "test");
        attachTest("attach database a as test;", 1, "a", "test");
        attachTest("ATTACH 'a' as test;", 1, "a", "test");
        attachTest("attach DATABASE \"a\" as 'test';", 1, "a", "test");
        attachTest("/*a*/attach test as test;", 1, "test", "test");
        attachTest("ATTACH /*a'*/ 'test' as test--;", 1, "test", "test");
        attachTest("attach \"test\" /*a*/as /*a*/'test'/*a*/;", 1, "test", "test");
        attachTest("attach/*a*/ /*a*/a/*a*/ /*a*/as/*a*/ /*a*/test/*a*/;", 1, "a", "test");
        attachTest("ATTACH/*a*//*a*/'a'/*a*//*a*/as/*a*//*a*/test/*a*/;", 1, "a", "test");
        attachTest("attach/*--a*//*a--*/\"a\"/*a*//*a*/as/*a*//*a*/'test'/*a*//*a*/;", 1, "a", "test");
        attachTest("attach DATABASE test as test;attach \"test\" as 'test';", 2, "test", "test");
        attachTest("ATTACH 'test' as test;ATTACH 'test' as test;", 2, "test", "test");
        attachTest("attach \"test\" as 'test';ATTACH 'test' as test;", 2, "test", "test");
        attachTest("attach 'C:\\test.db' as test;attach 'C:\\test.db' as test;", 
                2, "C:\\test.db", "test");
        attachTest("ATTACH DATABASE 'C:\\test.db' as test;attach 'C:\\test.db' as test;", 
                2, "C:\\test.db", "test");
        attachTest("attach \"/var/lib/test\" as 'test';attach \"/var/lib/test\" as 'test';",
                2, "/var/lib/test", "test");
        attachTest("/*a*/attach test as test;/*a*/attach test as test;", 2, "test", "test");
        attachTest("ATTACH DATABASE/*a'*/ 'test' as test /*a*/;attach \"test\" /*a*/as /*a*/'test'/*a*/", 
                2, "test", "test");
        attachTest("attach \"test\" /*a*/as /*a*/'test'/*a*/;ATTACH /*a'*/ 'test' as test--;", 
                2, "test", "test");
        attachTest("attach/*a*/DATABASE /*a*/a/*a*/ /*a*/as/*a*/ /*a*/test/*a*/;\nattach/*a*/ /*a*/a/*a*/ /*a*/as/*a*/ /*a*/test/*a*/;", 
                2, "a", "test");
        attachTest("ATTACH/*a*//*a*/'a'/*a*//*a*/as/*a*//*a*/test/*a*/;/*a*/attach/*a*/ /*a*/a/*a*/ /*a*/as/*a*/ /*a*/test/*a*/;", 
                2, "a", "test");
        attachTest("attach/*--a*/DATABASE/*a--*/\"a\"/*a*//*a*/as/*a*//*a*/'test'/*a*//*a*/;ATTACH /*a'*/ 'a' as test--;", 
                2, "a", "test");
        
        detachTest("detach test;", 1, "test");
        detachTest("DETACH 'test';", 1, "test");
        detachTest("detach database 'test';", 1, "test");
        detachTest("detach database \"test\";", 1, "test");
        detachTest("/*a*/detach test;", 1, "test");
        detachTest("DETACH /*a'*/ test--;", 1, "test");
        detachTest("detach \"test\" /*a*/", 1, "test");
        detachTest("detach/*a*/ /*a*//*a*/ /*a*//*a*/ /*a*/test/*a*/;", 1, "test");
        detachTest("DETACH/*a*//*a*//*a*//*a*//*a*//*a*/test/*a*/;", 1, "test");
        detachTest("/*--*/ -- a\ndetach/*--a*//*a--*/ /*a*//*a*/ /*a*//*a*/'test'/*a*//*a*/;", 1, "test");
        detachTest("detach DATABASE test;detach 'test'--", 2, "test");
        detachTest("-- a\nDETACH test;DETACH dATABASE test;", 2, "test");
        
        pragmaTest("pragma busy_timeout;", 1, null, "busy_timeout", null, false);
        pragmaTest("PRAGMA busy_timeout;", 1, null, "busy_timeout", null, false);
        pragmaTest("/*sql*/Pragma busy_timeout;", 1, null, "busy_timeout", null, false);
        pragmaTest("pragma busy_timeout = 1000;", 1, null, "busy_timeout", "1000", true);
        pragmaTest("pragma/*sql*/busy_timeout(1000);", 1, null, "busy_timeout", "1000", true);
        pragmaTest("pragma busy_timeout( 1000 );", 1, null, "busy_timeout", "1000", true);
        pragmaTest("pragma busy_timeout (/*sql*/1000) ;", 1, null, "busy_timeout", "1000", true);
        pragmaTest("pragma busy_timeout (/*sql*/0x1000) ;", 1, null, "busy_timeout", "0x1000", true);
        pragmaTest("pragma synchronous;", 1, null, "synchronous", null, false);
        pragmaTest("pragma synchronous = full;", 1, null, "synchronous", "full", true);
        pragmaTest("pragma synchronous = 'normal';", 1, null, "synchronous", "normal", true);
        pragmaTest("pragma test.busy_timeout;", 1, "test", "busy_timeout", null, false);
        pragmaTest("PRAGMA 'test'.busy_timeout;", 1, "test", "busy_timeout", null, false);
        pragmaTest("/*sql*/Pragma test. busy_timeout;", 1, "test", "busy_timeout", null, false);
        pragmaTest("pragma test .busy_timeout = 1000;", 1, "test", "busy_timeout", "1000", true);
        pragmaTest("pragma/*sql*/test . busy_timeout(1000);", 1, "test", "busy_timeout", "1000", true);
        pragmaTest("pragma test./*sql*/busy_timeout( 1000 );", 1, "test", "busy_timeout", "1000", true);
        pragmaTest("pragma test. /*sql*/busy_timeout (/*sql*/1000) ;", 1, "test", "busy_timeout", "1000", true);
        pragmaTest("pragma test . /*sql*/busy_timeout (/*sql*/0x1000) ;", 1, "test", "busy_timeout", "0x1000", true);
        pragmaTest("pragma test/*sql*/./*sql*/synchronous;", 1, "test", "synchronous", null, false);
        pragmaTest("pragma test./*sql*/-- sql\nsynchronous = full;", 1, "test", "synchronous", "full", true);
        pragmaTest("pragma test-- sql\n./*sql*/-- sql\nsynchronous = 'normal';", 1, "test", "synchronous", "normal", true);
        pragmaTest("pragma a = -.0;", 1, null, "a", "-.0", true);
        pragmaTest("pragma a = +.0;", 1, null, "a", "+.0", true);
        pragmaTest("pragma a = .0;", 1, null, "a", ".0", true);
        pragmaTest("pragma a = -1.0;", 1, null, "a", "-1.0", true);
        pragmaTest("pragma a = +1.0;", 1, null, "a", "+1.0", true);
        pragmaTest("pragma a = 1.0;", 1, null, "a", "1.0", true);
        pragmaTest("pragma a (1.0);", 1, null, "a", "1.0", true);
        try {
            pragmaTest("pragma a = .0.0;", 1, null, "a", ".0.0", true);
            fail();
        } catch (SQLParseException e) {
            // OK
        }
    }
    
    private void emptyTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            assertTrue(!stmt.isComment());
            assertTrue("".equals(stmt.getCommand()));
            assertTrue(stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void commentTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            assertTrue(stmt.isComment());
            assertTrue("".equals(stmt.getCommand()));
            assertTrue(stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void alterUserTest(String sqls, int stmts, String metaSchema, String metaSQL,
            String user, String host, String password, Boolean sa, String protocol, String authMethod) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            AlterUserStatement s = (AlterUserStatement)stmt;
            assertTrue(!stmt.isComment());
            assertTrue("ALTER USER".equals(stmt.getCommand()));
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            assertTrue(stmt instanceof MetaStatement);
            assertTrue(metaSQL.equals(s.getMetaSQL(metaSchema)));
            assertTrue(user.equalsIgnoreCase(s.getUser()));
            assertTrue(host.equals(s.getHost()));
            assertTrue(password == null || password.equals(s.getPassword()));
            assertTrue(protocol == null || protocol.equalsIgnoreCase(s.getProtocol()));
            assertTrue(authMethod == null || authMethod.equalsIgnoreCase(s.getAuthMethod()));
            assertTrue(sa == null || sa == s.isSa());
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void createDatabaseTest(String sqls, int stmts, boolean quiet, String dbname, String location) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            CreateDatabaseStatement s = (CreateDatabaseStatement)stmt;
            assertTrue(!stmt.isComment());
            assertTrue("CREATE DATABASE".equals(stmt.getCommand()));
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            assertTrue(stmt instanceof MetaStatement);
            assertTrue(dbname.equals(s.getDb()));
            assertTrue((location == null && location == s.getDir()) || location.equals(s.getDir()));
            assertTrue(quiet == s.isQuite());
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void createUserTest(String sqls, int stmts, String user, String host, String password,
            boolean sa, String protocol, String authMethod) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            CreateUserStatement s = (CreateUserStatement)stmt;
            assertTrue(!stmt.isComment());
            assertTrue("CREATE USER".equals(stmt.getCommand()));
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            assertTrue(stmt instanceof MetaStatement);
            assertTrue(user.equalsIgnoreCase(s.getUser()));
            assertTrue(host.equals(s.getHost()));
            assertTrue(password == null || password.equals(s.getPassword()));
            assertTrue(protocol.equalsIgnoreCase(s.getProtocol()));
            assertTrue(authMethod.equalsIgnoreCase(s.getAuthMethod()));
            assertTrue(sa == s.isSa());
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void dropDatabaseTest(String sqls, int stmts, String dbname, boolean quiet) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            
            assertTrue(!stmt.isComment());
            assertTrue("DROP DATABASE".equals(stmt.getCommand()));
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            assertTrue(stmt instanceof MetaStatement);
            
            DropDatabaseStatement s = (DropDatabaseStatement)stmt;
            assertTrue(dbname.equals(s.getDb()));
            assertTrue(quiet == s.isQuiet());
            assertTrue(s.isNeedSa());
            
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void dropUserTest(String sqls, int stmts, String metaSchema, String[][]users) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            DropUserStatement s = (DropUserStatement)stmt;
            assertTrue(!stmt.isComment());
            assertTrue("DROP USER".equals(stmt.getCommand()));
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            assertTrue(stmt instanceof MetaStatement);
            
            for (String[] user: users) {
                assertTrue(s.exists(user[0], user[1], user[2]));
            }
            StringBuilder sb = new StringBuilder("delete from '"+metaSchema+"'.user where ");
            for (int j = 0; j < users.length; ++j) {
                String[] user = users[j];
                sb.append(j == 0? "": " or ").append('(')
                .append("host = '").append(user[0]).append("' and ")
                .append("user = '").append(user[1]).append("' and ")
                .append("protocol = '").append(user[2]).append('\'')
                .append(')');
            }
            String metaSql = s.getMetaSQL(metaSchema);
            String sql = sb.toString();
            assertTrue(metaSql.equals(sql));
            
            ++i;
        }
        overTest(parser, i, stmts);
    }

    private void grantTest(String sqls, int stmts, String metaSchema, 
            String[] privs, String[] dbnames, String[][]users) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            GrantStatement s = (GrantStatement)stmt;
            assertTrue(!stmt.isComment());
            assertTrue("GRANT".equals(stmt.getCommand()));
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            assertTrue(stmt instanceof MetaStatement);
            
            for (String priv: privs) {
                assertTrue(s.hasPrivilege(priv));
            }
            for (String dbname: dbnames) {
                assertTrue(s.exists(dbname));
            }
            for (String[] user: users) {
                assertTrue(s.exists(user[0], user[1]));
            }
            
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void killTest(String sqls, int stmts, boolean query, int pid) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            KillStatement s = (KillStatement)stmt;
            assertTrue(!s.isComment());
            assertTrue("KILL".equals(s.getCommand()));
            assertTrue(!s.isEmpty());
            assertTrue(!s.isQuery());
            assertTrue(!s.isTransaction());
            assertTrue(s instanceof LocalStatement);
            assertTrue(query == s.isKillQuery());
            assertTrue(pid == s.getProcessorId());
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void revokeTest(String sqls, int stmts, String metaSchema, 
            String[] privs, String[] dbnames, String[][]users) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SQL %s", stmt);
            RevokeStatement s = (RevokeStatement)stmt;
            assertTrue(!stmt.isComment());
            assertTrue("REVOKE".equals(stmt.getCommand()));
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isTransaction());
            assertTrue(stmt instanceof MetaStatement);
            
            for (String priv: privs) {
                assertTrue(s.hasPrivilege(priv));
            }
            for (String dbname: dbnames) {
                assertTrue(s.exists(dbname));
            }
            for (String[] user: users) {
                assertTrue(s.exists(user[0], user[1]));
            }
            
            ++i;
        }
        overTest(parser, i, stmts);
    }
    
    private void selectTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SELECT %s", stmt);
            assertTrue("SELECT".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void selectForUpdateTest(String sqls, int stmts, String selectSQL) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SELECT %s", stmt);
            assertTrue("SELECT".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            
            SelectStatement s = (SelectStatement)stmt;
            assertTrue(s.isForUpdate());
            assertTrue(selectSQL.equals(s.getSQL()));
            
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showColumnsTest(String sqls, int stmts, 
            boolean extended, String schemaName, String tableName) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW COLUMNS %s", stmt);
            assertTrue("SHOW COLUMNS".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowColumnsStatement s = (ShowColumnsStatement)stmt;
            assertTrue(s.isExtended() == extended);
            assertTrue(schemaName == null || schemaName.equals(s.getSchemaName()));
            assertTrue(tableName  == null || tableName.equals(s.getTableName()));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showCreateIndexTest(String sqls, int stmts, String schemaName, String indexName) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW CREATE INDEX %s", stmt);
            assertTrue("SHOW CREATE INDEX".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowCreateIndexStatement s = (ShowCreateIndexStatement)stmt;
            assertTrue(schemaName == s.getSchemaName() || schemaName.equals(s.getSchemaName()));
            assertTrue(indexName  == s.getIndexName() || indexName.equals(s.getIndexName()));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showCreateTableTest(String sqls, int stmts, String schemaName, String tableName) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW CREATE TABLE %s", stmt);
            assertTrue("SHOW CREATE TABLE".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowCreateTableStatement s = (ShowCreateTableStatement)stmt;
            assertTrue(schemaName == s.getSchemaName() || schemaName.equals(s.getSchemaName()));
            assertTrue(tableName  == s.getTableName() || tableName.equals(s.getTableName()));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showDatabasesTest(String sqls, int stmts, boolean all) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW DATABASES %s", stmt);
            assertTrue("SHOW DATABASES".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowDatabasesStatement s = (ShowDatabasesStatement)stmt;
            assertTrue(stmt instanceof MetaStatement);
            assertTrue(all == s.isAll());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showGrantsTest(String sqls, int stmts, String host, String user, 
            boolean currentUser, boolean needSa) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW GRANTS %s", stmt);
            assertTrue("SHOW GRANTS".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowGrantsStatement s = (ShowGrantsStatement)stmt;
            assertTrue(stmt instanceof MetaStatement);
            assertTrue(host.equals(host));
            assertTrue(user == null || user.equals(user));
            assertTrue(currentUser == s.isCurrentUser());
            assertTrue(needSa == s.isNeedSa());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showIndexesTest(String sqls, int stmts, String schemaName, String name, 
            boolean indexColumns, boolean extended, String pattern) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW INDEXES %s", stmt);
            assertTrue("SHOW INDEXES".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowIndexesStatement s = (ShowIndexesStatement)stmt;
            assertTrue(schemaName == s.getSchemaName() || schemaName.equals(s.getSchemaName()));
            if (indexColumns) {
                assertTrue(name == s.getIndexName() || name.equals(s.getIndexName()));
            } else {
                assertTrue(name == s.getTableName() || name.equals(s.getTableName()));
            }
            assertTrue(indexColumns == s.isIndexColumns());
            assertTrue(extended == s.isExtended());
            assertTrue(pattern == s.getPattern() || pattern.equals(s.getPattern()));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showProcesslistTest(String sqls, int stmts, boolean full) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW PROCESSLIST %s", stmt);
            assertTrue("SHOW PROCESSLIST".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowProcesslistStatement s = (ShowProcesslistStatement)stmt;
            assertTrue(stmt instanceof LocalStatement);
            assertTrue(full == s.isFull());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showStatusTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW STATUS %s", stmt);
            assertTrue("SHOW STATUS".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            assertTrue(stmt instanceof ShowStatusStatement);
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showTablesTest(String sqls, int stmts, String schemaName, String pattern) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW TABLES %s", stmt);
            assertTrue("SHOW TABLES".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowTablesStatement s = (ShowTablesStatement)stmt;
            assertTrue(schemaName == s.getSchemaName() 
                    || schemaName.equals(s.getSchemaName()));
            assertTrue(pattern == s.getPattern() 
                    || pattern.equals(s.getPattern()));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void showUsersTest(String sqls, int stmts, String pattern) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SHOW USERS %s", stmt);
            assertTrue("SHOW USERS".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ShowUsersStatement s = (ShowUsersStatement)stmt;
            assertTrue(pattern == s.getPattern()
                    || pattern.equals(s.getPattern()));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void updateTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test UPDATE %s", stmt);
            assertTrue("UPDATE".equals(stmt.getCommand()));
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void insertTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test INSERT %s", stmt);
            assertTrue("INSERT".equals(stmt.getCommand()));
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void insertSelectTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test INSERT %s", stmt);
            assertTrue("INSERT".equals(stmt.getCommand()));
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            
            InsertSelectStatement s = (InsertSelectStatement)stmt;
            SQLStatement selStmt = s.getSelectStatement();
            assertTrue(selStmt.isQuery());
            assertTrue(!selStmt.isEmpty());
            assertTrue(!selStmt.isTransaction());
            assertTrue(!selStmt.isComment());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void insertReturningTest(String sqls, int stmts, boolean hasSelect,
            String schemaName, String tableName, String returningColumns) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test INSERT %s", stmt);
            assertTrue("INSERT".equals(stmt.getCommand()));
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            
            if (hasSelect) {
                InsertSelectStatement s = (InsertSelectStatement)stmt;
                SQLStatement selStmt = s.getSelectStatement();
                assertTrue(selStmt.isQuery());
                assertTrue(!selStmt.isEmpty());
                assertTrue(!selStmt.isTransaction());
                assertTrue(!selStmt.isComment());
            }
            
            InsertReturningStatement s = (InsertReturningStatement)stmt;
            assertTrue(schemaName == null || (schemaName.equalsIgnoreCase(s.getSchemaName())));
            assertTrue((tableName.equalsIgnoreCase(s.getTableName())));
            assertTrue((returningColumns.equals(s.getReturningColumns())));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void deleteTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test DELETE %s", stmt);
            assertTrue("DELETE".equals(stmt.getCommand()));
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void truncateTableTest(String sqls, int stmts, String schemaName, String tableName) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test TRUNCATE %s", stmt);
            assertTrue("DELETE".equals(stmt.getCommand()));
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            
            TruncateTableStatement s = (TruncateTableStatement)stmt;
            assertTrue(schemaName == s.getSchemaName() 
                    || schemaName.equals(s.getSchemaName()));
            assertTrue(tableName == s.getTableName() 
                    || tableName.equals(s.getTableName()));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void txBeginTest(String sqls, int stmts, 
            boolean deferred, boolean immediate, boolean exclusive) {
        txBeginTest(sqls, stmts, deferred, immediate, exclusive, null, TransactionMode.SERIALIZABLE);
    }
    
    private void txBeginTest(String sqls, int stmts, 
            boolean deferred, boolean immediate, boolean exclusive, Boolean readOnly) {
        txBeginTest(sqls, stmts, deferred, immediate, exclusive, readOnly, TransactionMode.SERIALIZABLE);
    }
    
    private void txBeginTest(String sqls, int stmts, 
            boolean deferred, boolean immediate, boolean exclusive,
            Boolean readOnly, int isolation) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test BEGIN %s", stmt);
            assertTrue("BEGIN".equals(stmt.getCommand()));
            assertTrue(stmt instanceof TransactionStatement);
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(stmt.isTransaction());
            assertTrue(!stmt.isComment());
            TransactionStatement tx = (TransactionStatement)stmt;
            assertTrue(tx.isBegin());
            assertTrue(!tx.isCommit());
            assertTrue(!tx.isRelease());
            assertTrue(!tx.isRollback());
            assertTrue(!tx.isSavepoint());
            assertTrue(!tx.hasSavepoint());
            assertTrue(deferred == tx.isDeferred());
            assertTrue(immediate == tx.isImmediate());
            assertTrue(exclusive == tx.isExclusive());
            assertTrue((readOnly == null && tx.isReadOnly() == null)
                    || readOnly.booleanValue() == tx.isReadOnly());
            assertTrue(isolation == tx.getIsolationLevel());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void txSetTransactionTest(String sqls, int stmts, boolean sessionScope, Boolean readOnly) {
        txSetTransactionTest(sqls, stmts, sessionScope, readOnly, TransactionMode.SERIALIZABLE);
    }
    
    private void txSetTransactionTest(String sqls, int stmts, boolean sessionScope, Boolean readOnly, 
            int isolation) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SET %s", stmt);
            assertTrue("SET TRANSACTION".equals(stmt.getCommand()));
            assertTrue(stmt instanceof SetTransactionStatement);
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            SetTransactionStatement setTx = (SetTransactionStatement)stmt;
            TransactionMode mode = setTx.getTransactionMode();
            assertTrue(sessionScope == setTx.isSessionScope());
            assertTrue((readOnly == null && mode.isReadOnly() == null)
                    || readOnly.booleanValue() == mode.isReadOnly());
            assertTrue(isolation == setTx.getTransactionMode().getIsolationLevel());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void txCommitTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test COMMIT %s", stmt);
            assertTrue("COMMIT".equals(stmt.getCommand()));
            assertTrue(stmt instanceof TransactionStatement);
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(stmt.isTransaction());
            assertTrue(!stmt.isComment());
            TransactionStatement tx = (TransactionStatement)stmt;
            assertTrue(!tx.isBegin());
            assertTrue(tx.isCommit());
            assertTrue(!tx.isRelease());
            assertTrue(!tx.isRollback());
            assertTrue(!tx.isSavepoint());
            assertTrue(!tx.hasSavepoint());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void txEndTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test END %s", stmt);
            assertTrue("END".equals(stmt.getCommand()));
            assertTrue(stmt instanceof TransactionStatement);
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(stmt.isTransaction());
            assertTrue(!stmt.isComment());
            TransactionStatement tx = (TransactionStatement)stmt;
            assertTrue(!tx.isBegin());
            assertTrue(tx.isCommit());
            assertTrue(!tx.isRelease());
            assertTrue(!tx.isRollback());
            assertTrue(!tx.isSavepoint());
            assertTrue(!tx.hasSavepoint());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void txSavepointTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test SAVEPOINT %s", stmt);
            assertTrue("SAVEPOINT".equals(stmt.getCommand()));
            assertTrue(stmt instanceof TransactionStatement);
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(stmt.isTransaction());
            assertTrue(!stmt.isComment());
            TransactionStatement tx = (TransactionStatement)stmt;
            assertTrue(!tx.isBegin());
            assertTrue(!tx.isCommit());
            assertTrue(!tx.isRelease());
            assertTrue(!tx.isRollback());
            assertTrue(tx.isSavepoint());
            assertTrue(tx.hasSavepoint());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void txReleaseTest(String sqls, int stmts) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test RELEASE %s", stmt);
            assertTrue("RELEASE".equals(stmt.getCommand()));
            assertTrue(stmt instanceof TransactionStatement);
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(stmt.isTransaction());
            assertTrue(!stmt.isComment());
            TransactionStatement tx = (TransactionStatement)stmt;
            assertTrue(!tx.isBegin());
            assertTrue(!tx.isCommit());
            assertTrue(tx.isRelease());
            assertTrue(!tx.isRollback());
            assertTrue(!tx.isSavepoint());
            assertTrue(tx.hasSavepoint());
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void txRollbackTest(String sqls, int stmts, boolean hasSavepoint) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test ROLLBACK %s", stmt);
            assertTrue("ROLLBACK".equals(stmt.getCommand()));
            assertTrue(stmt instanceof TransactionStatement);
            assertTrue(!stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(stmt.isTransaction());
            assertTrue(!stmt.isComment());
            TransactionStatement tx = (TransactionStatement)stmt;
            assertTrue(!tx.isBegin());
            assertTrue(!tx.isCommit());
            assertTrue(!tx.isRelease());
            assertTrue(tx.isRollback());
            assertTrue(!tx.isSavepoint());
            assertTrue(tx.hasSavepoint() == hasSavepoint);
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void attachTest(String sqls, int stmts, String dbName, String schemaName) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test ATTACH %s", stmt);
            assertTrue("ATTACH".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            
            AttachStatement as = (AttachStatement)stmt;
            assertTrue(as.getDbName().equals(dbName));
            assertTrue(as.getSchemaName().equals(schemaName));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void detachTest(String sqls, int stmts, String schemaName) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test DETACH %s", stmt);
            assertTrue("DETACH".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery());
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            
            DetachStatement as = (DetachStatement)stmt;
            assertTrue(as.getSchemaName().equals(schemaName));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void pragmaTest(String sqls, int stmts, String schemaName, String name, String value, boolean isSet) {
        SQLParser parser = new SQLParser(sqls);
        int i = 0;
        for (SQLStatement stmt: parser) {
            info("Test PRAGMA %s", stmt);
            assertTrue("PRAGMA".equals(stmt.getCommand()));
            assertTrue(stmt.isQuery() == !isSet);
            assertTrue(!stmt.isEmpty());
            assertTrue(!stmt.isTransaction());
            assertTrue(!stmt.isComment());
            
            PragmaStatement s = (PragmaStatement)stmt;
            assertTrue((schemaName == null && s.getSchemaName() == null)
                    || s.getSchemaName().equals(schemaName));
            assertTrue(s.getName().equals(name));
            assertTrue((value == null && s.getValue() == null)
                    || s.getValue().equals(value));
            ++i;
            parser.remove();
        }
        overTest(parser, i, stmts);
    }
    
    private void closeTest(String sql) {
        SQLParser parser = new SQLParser(sql);
        assertTrue(parser.isOpen());
        assertTrue(parser.hasNext());
        IoUtils.close(parser);
        assertTrue(!parser.isOpen());
        assertTrue(!parser.hasNext());
    }
    
    private void overTest(SQLParser parser, int n, int stmts) {
        assertTrue(n == stmts);
        assertTrue(!parser.hasNext());
        try {
            parser.next();
            fail();
        } catch (NoSuchElementException e) {
            // pass
        }
        parser.remove();
        try {
            parser.remove();
            fail();
        } catch (IllegalStateException e) {
            // pass
        }
    }

}
