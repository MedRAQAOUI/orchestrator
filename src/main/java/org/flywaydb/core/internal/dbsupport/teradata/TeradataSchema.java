/*
 * Copyright 2010-2017 Boxfuse GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flywaydb.core.internal.dbsupport.teradata;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.flywaydb.core.internal.dbsupport.JdbcTemplate;
import org.flywaydb.core.internal.dbsupport.Schema;
import org.flywaydb.core.internal.dbsupport.Table;
import org.flywaydb.core.internal.util.logging.Log;
import org.flywaydb.core.internal.util.logging.LogFactory;

/**
 * Teradata implementation of Schema.
 */
public class TeradataSchema extends Schema<TeradataDbSupport> {

    // Logger
    private static final Log LOG = LogFactory.getLog(TeradataSchema.class);

    // Generic SQL manipulation
    public static final String SQL_OBJ_LIST = "SELECT ${obj}Name from DBC.${obj}InfoV where creatorName = ? ORDER BY CreateTimeStamp DESC";
    public static final String SQL_OBJ_DELETE_ALL = "DELETE ${obj} ${o} ALL";
    public static final String SQL_OBJ_DROP = "DROP ${obj} ${o}";

    // Tables
    public static final String SQL_TABLE_ALL = "SELECT tableName FROM dbc.tablesV WHERE databaseName = ?";
    public static final String SQL_TABLE_COUNT = "SELECT COUNT(0) FROM dbc.tablesV WHERE databaseName = ?";

    // Foreign key
    public static final String SQL_FK_DROP = "ALTER TABLE ${tbl} DROP CONSTRAINT ${name}";
    public static final String SQL_FK_LIST = "SELECT a.childDB || '.' || a.childTable tbl, a.indexName idx " //
            + "FROM DBC.All_RI_ParentsV a, DBC.ChildrenV c " //
            + "WHERE a.indexName IS NOT NULL AND a.parentDB <> a.childDB AND a.parentDB = c.child AND c.parent = ?";

    // Join index
    public static final String SQL_JI_LIST = "SELECT a.joinIdxDataBaseName || '.' || a.joinIdxName jiName " //
            + "FROM DBC.JoinIndicesV a, DBC.ChildrenV c " //
            + "WHERE a.joinIdxDataBaseName = c.child AND c.parent = ?";

    // Recursive databases and users
    public static final String SQL_DB_USER_LIST = "" //
            + "WITH RECURSIVE recursive_tab (DatabaseName, OwnerName, Level) as ( "
            + "  select root.DatabaseName, root.OwnerName, 0 " //
            + "  from DBC.DatabasesV root " //
            + "  where root.DatabaseName = ? " //
            + "union all "//
            + "   select children.DatabaseName, children.OwnerName, parent.Level+1 "
            + "   from DBC.DatabasesV children, recursive_tab parent "
            + "   where children.OwnerName = parent.DatabaseName " //
            + ") select a.DatabaseName " //
            + "from recursive_tab a "//
            + "order by a.level desc ";

    /**
     * Creates a new schema.
     *
     * @param jdbcTemplate
     *            The Jdbc Template for communicating with the DB.
     * @param dbSupport
     *            The database-specific support.
     * @param name
     *            The name of the schema.
     */
    public TeradataSchema(JdbcTemplate jdbcTemplate, TeradataDbSupport dbSupport, String name) {
        super(jdbcTemplate, dbSupport, name);
    }

    @Override
    protected boolean doExists() throws SQLException {
        try {
            doAllTables();
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    @Override
    protected boolean doEmpty() throws SQLException {
        return jdbcTemplate.queryForInt(SQL_TABLE_COUNT, name) == 0;
    }

    @Override
    protected void doCreate() throws SQLException {
        LOG.info("Teradata does not support creating schemas. Schema not created: " + name);
    }

    @Override
    protected void doDrop() throws SQLException {
        LOG.info("Teradata does not support dropping schemas. Schema not dropped: " + name);
    }

    /*
     * Because Teradata does not support multiple DDL statements in a
     * transaction we set autocommit = true to avoid this SQLException :
     * [Teradata Database] [TeraJDBC 15.10.00.05] [Error 3932] [SQLState 25000]
     * Only an ET or null statement is legal after a DDL Statement.
     *
     * @throws SQLException when the clean failed.
     */
    @Override
    protected void doClean() throws SQLException {
        // Teradata does not support DDL transaction
        if (!dbSupport.supportsDdlTransactions()) {
            jdbcTemplate.getConnection().setAutoCommit(true);
        }
        LOG.info("Cleaning schema " + name);
        cleanForeignKey(name);
        cleanJoinIndex(name);
        cleanSubDatabaseAndUser(name);
        cleanObj("role", name);
        cleanObj("profile", name);
    }

    @Override
    protected Table[] doAllTables() throws SQLException {
        List<String> tableNames = jdbcTemplate.queryForStringList(SQL_TABLE_ALL, name);
        Table[] tables = new Table[tableNames.size()];
        for (int i = 0; i < tableNames.size(); i++) {
            tables[i] = new TeradataTable(jdbcTemplate, dbSupport, this, tableNames.get(i));
        }
        return tables;
    }

    @Override
    public Table getTable(String tableName) {
        return new TeradataTable(jdbcTemplate, dbSupport, this, tableName);
    }

    /**
     * Drop a SQL object by name.
     * 
     * @param objType
     *            object type (database, table, join index, ...)
     * @param name
     *            object name
     * @throws SQLException
     *             if error
     */
    protected void dropObject(String objType, String name) throws SQLException {
        String sql = SQL_OBJ_DROP.replace("${obj}", objType).replace("${o}", name);
        LOG.info(sql);
        jdbcTemplate.executeStatement(sql);
    }

    /**
     * Delete all objets in a schema (tables, views, procedure, macro). Does not
     * works for nested user or database.
     * 
     * @param objType
     *            object type (database, table, join index, ...)
     * @param name
     *            object name
     * @throws SQLException
     *             if error
     */
    protected void deleteAll(String objType, String name) throws SQLException {
        String sql = SQL_OBJ_DELETE_ALL.replace("${obj}", objType).replace("${o}", name);
        LOG.info(sql);
        jdbcTemplate.executeStatement(sql);
    }

    /**
     * Drop a table constraint by name.
     * 
     * @param tableName
     *            table name
     * @param constraintName
     *            constaint name
     * @throws SQLException
     *             if error
     */
    protected void dropConstraint(String tableName, String constraintName) throws SQLException {
        String sql = SQL_FK_DROP.replace("${tbl}", tableName).replace("${name}", constraintName);
        LOG.info(sql);
        jdbcTemplate.executeStatement(sql);
    }

    /**
     * Delete all foreign key of a schema.
     * <p>
     * Reason : DELETE DATABASE statement cannot operate if there is cross
     * database FOREIGN KEY left.
     * <p>
     * Warning : All the constraints must have a name.
     * 
     * @param schema
     *            top schema (user or database)
     * @throws SQLException
     *             if error
     */
    protected void cleanForeignKey(String schema) throws SQLException {
        String objName = "foreign index";
        List<Map<String, String>> res = jdbcTemplate.queryForList(SQL_FK_LIST, schema);
        LOG.info(String.format("%d %s(s) to drop", res.size(), objName));
        if (res != null && !res.isEmpty()) {
            for (Map<String, String> m : res) {
                dropConstraint(m.get("tbl"), m.get("idx"));
            }
        }
    }

    /**
     * Delete every join index inside a schema.
     * <p>
     * Reason : DELETE DATABASE statement cannot operate if there is JOIN INDEX
     * left.
     * 
     * @param schema
     *            top schema (user or database)
     * @throws SQLException
     *             if error
     */
    protected void cleanJoinIndex(String schema) throws SQLException {
        String objName = "join index";
        List<String> res = jdbcTemplate.queryForStringList(SQL_JI_LIST, schema);
        LOG.info(String.format("%d %s(s) to drop", res.size(), objName));
        if (res != null && !res.isEmpty()) {
            for (String o : res) {
                dropObject(objName, o);
            }
        }
    }

    /**
     * Delete every user and database nested inside a schema.
     * 
     * @param schema
     *            top schema (user or database)
     * @throws SQLException
     *             if error
     */
    protected void cleanSubDatabaseAndUser(String schema) throws SQLException {
        String objName = "database";
        List<String> res = jdbcTemplate.queryForStringList(SQL_DB_USER_LIST, schema);
        LOG.info(String.format("%d %s(s) to drop", res.size(), objName));
        if (!res.isEmpty()) {
            for (String o : res) {
                deleteAll(objName, o);
                // Do not drop current schema/user, just delete content
                if (o.equalsIgnoreCase(schema)) {
                    LOG.info("Skipping DROP " + o);
                    continue;
                }
                dropObject(objName, o);
            }
        }
    }

    /**
     * Delete by type every object created by a schema/user.
     * 
     * @param objType
     *            object type (profile or role)
     * @param schema
     *            creator name (should be schema/user)
     * @throws SQLException
     *             si erreur
     */
    protected void cleanObj(String objName, String schema) throws SQLException {
        String sql = SQL_OBJ_LIST.replace("${obj}", objName);
        List<String> res = jdbcTemplate.queryForStringList(sql, schema);
        LOG.info(String.format("%d %s(s) to drop", res.size(), objName));
        if (!res.isEmpty()) {
            for (String o : res) {
                dropObject(objName, o);
            }
        }
    }

}