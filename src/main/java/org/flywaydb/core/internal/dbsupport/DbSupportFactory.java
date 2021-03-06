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
package org.flywaydb.core.internal.dbsupport;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.internal.dbsupport.db2.DB2DbSupport;
import org.flywaydb.core.internal.dbsupport.db2zos.DB2zosDbSupport;
import org.flywaydb.core.internal.dbsupport.derby.DerbyDbSupport;
import org.flywaydb.core.internal.dbsupport.greenplum.GreenPlumDbSupport;
import org.flywaydb.core.internal.dbsupport.enterprisedb.EnterpriseDBDbSupport;
import org.flywaydb.core.internal.dbsupport.h2.H2DbSupport;
import org.flywaydb.core.internal.dbsupport.hsql.HsqlDbSupport;
import org.flywaydb.core.internal.dbsupport.mysql.MySQLDbSupport;
import org.flywaydb.core.internal.dbsupport.oracle.OracleDbSupport;
import org.flywaydb.core.internal.dbsupport.phoenix.PhoenixDbSupport;
import org.flywaydb.core.internal.dbsupport.postgresql.PostgreSQLDbSupport;
import org.flywaydb.core.internal.dbsupport.redshift.RedshfitDbSupportViaPostgreSQLDriver;
import org.flywaydb.core.internal.dbsupport.redshift.RedshfitDbSupportViaRedshiftDriver;
import org.flywaydb.core.internal.dbsupport.redshift.RedshiftDbSupport;
import org.flywaydb.core.internal.dbsupport.saphana.SapHanaDbSupport;
import org.flywaydb.core.internal.dbsupport.solid.SolidDbSupport;
import org.flywaydb.core.internal.dbsupport.sqlite.SQLiteDbSupport;
import org.flywaydb.core.internal.dbsupport.sqlserver.SQLServerDbSupport;
import org.flywaydb.core.internal.dbsupport.sybase.ase.SybaseASEDbSupport;
import org.flywaydb.core.internal.dbsupport.teradata.TeradataDbSupport;
import org.flywaydb.core.internal.dbsupport.vertica.VerticaDbSupport;
import org.flywaydb.core.internal.util.logging.Log;
import org.flywaydb.core.internal.util.logging.LogFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

/**
 * Factory for obtaining the correct DbSupport instance for the current connection.
 */
public class DbSupportFactory {
    private static final Log LOG = LogFactory.getLog(DbSupportFactory.class);

    /**
     * Prevent instantiation.
     */
    private DbSupportFactory() {
        //Do nothing
    }

    /**
     * Initializes the appropriate DbSupport class for the database product used by the data source.
     *
     * @param connection The Jdbc connection to use to query the database.
     * @param printInfo  Where the DB info should be printed in the logs.
     * @return The appropriate DbSupport class.
     */
    public static DbSupport createDbSupport(Connection connection, boolean printInfo) {
        String databaseProductName = getDatabaseProductName(connection);

        if (printInfo) {
            LOG.info("Database: " + getJdbcUrl(connection) + " (" + databaseProductName + ")");
        }

        if (databaseProductName.startsWith("Apache Derby")) {
            return new DerbyDbSupport(connection);
        }
        else if (databaseProductName.startsWith("SQLite")) {
            return new SQLiteDbSupport(connection);
        }
        else if (databaseProductName.startsWith("H2")) {
            return new H2DbSupport(connection);
        }
        else if (databaseProductName.contains("HSQL Database Engine")) {
            // For regular Hsql and the Google Cloud SQL local default DB.
            return new HsqlDbSupport(connection);
        }
        else if (databaseProductName.startsWith("Microsoft SQL Server")) {
            return new SQLServerDbSupport(connection);
        }
        else if (databaseProductName.contains("MySQL")) {
            // For regular MySQL, MariaDB and Google Cloud SQL.
            // Google Cloud SQL returns different names depending on the environment and the SDK version.
            //   ex.: Google SQL Service/MySQL
            return new MySQLDbSupport(connection);
        }
        else if (databaseProductName.startsWith("Oracle")) {
            return new OracleDbSupport(connection);
        }
        else if (databaseProductName.startsWith("EnterpriseDB")) {
            return new EnterpriseDBDbSupport(connection);
        }
        if (databaseProductName.startsWith("PostgreSQL 8")) {
            // Redshift reports a databaseProductName of "PostgreSQL 8.0", and it uses the same JDBC driver,
            // but only supports a subset of features. Therefore, we need to execute a query in order to
            // distinguish it from the real PostgreSQL 8:
            RedshiftDbSupport redshift;
            if ("RedshiftJDBC".equals(getDriverName(connection))) {
                redshift = new RedshfitDbSupportViaRedshiftDriver(connection);
            } else {
                redshift = new RedshfitDbSupportViaPostgreSQLDriver(connection);
            }
            if (redshift.detect()) {
                return redshift;
            }
        }
        else if (databaseProductName.startsWith("PostgreSQL")) {
            return new PostgreSQLDbSupport(connection);
        }
        else if (databaseProductName.startsWith("DB2")) {
            if (getDatabaseProductVersion(connection).startsWith("DSN")) {
                return new DB2zosDbSupport(connection);
            } else {
                return new DB2DbSupport(connection);
            }
        }
        else if (databaseProductName.startsWith("Vertica")) {
            return new VerticaDbSupport(connection);
        }
        else if (databaseProductName.contains("solidDB")) {
            // SolidDB was originally developed by a company named Solid and was sold afterwards to IBM.
            // In the meanwhile IBM also sold solidDB to Unicom Systems.
            // Therefore no vendor string in search criteria
            return new SolidDbSupport(connection);
        }
        else if (databaseProductName.startsWith("Phoenix")) {
            return new PhoenixDbSupport(connection);
        }

        else if (databaseProductName.startsWith("ASE") || databaseProductName.startsWith("Adaptive") //Newer Sybase ASE versions
                || databaseProductName.startsWith("sql server") // Older Sybase ASE 12.5 installations
                ) {
            return new SybaseASEDbSupport(connection);
        }
        else if (databaseProductName.startsWith("HDB")) {
            return new SapHanaDbSupport(connection);
        }
       
        else if (databaseProductName.startsWith("Greenplum")) {
            return new GreenPlumDbSupport(connection);
        }

        else if (databaseProductName.startsWith("Teradata")) {
          return new TeradataDbSupport(connection);
        }
        
        throw new FlywayException("Unsupported Database: " + databaseProductName);
    }

    /**
     * Retrieves the Jdbc Url for this connection.
     *
     * @param connection The Jdbc connection.
     * @return The Jdbc Url.
     */

    private static String getJdbcUrl(Connection connection) {
        try {
            return connection.getMetaData().getURL();
        } catch (SQLException e) {
            throw new FlywaySqlException("Unable to retrieve the Jdbc connection Url!", e);
        }
    }

    /**
     * Retrieves the name of the database product.
     *
     * @param connection The connection to use to query the database.
     * @return The name of the database product. Ex.: Oracle, MySQL, ...
     */
    private static String getDatabaseProductName(Connection connection) {
        try {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            if (databaseMetaData == null) {
                throw new FlywayException("Unable to read database metadata while it is null!");
            }

            String databaseProductName = databaseMetaData.getDatabaseProductName();
            if (databaseProductName == null) {
                throw new FlywayException("Unable to determine database. Product name is null.");
            }

            int databaseMajorVersion = databaseMetaData.getDatabaseMajorVersion();
            int databaseMinorVersion = databaseMetaData.getDatabaseMinorVersion();

            return databaseProductName + " " + databaseMajorVersion + "." + databaseMinorVersion;
        } catch (SQLException e) {
            throw new FlywaySqlException("Error while determining database product name", e);
        }
    }

    /**
     * Retrieves the database version.
     *
     * @param connection The connection to use to query the database.
     * @return The version of the database product.
     * Ex.: DSN11015 DB2 for z/OS Version 11
     * SQL10050 DB" for Linux, UNIX and Windows Version 10.5
     */
    private static String getDatabaseProductVersion(Connection connection) {
        try {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            if (databaseMetaData == null) {
                throw new FlywayException("Unable to read database metadata while it is null!");
            }

            String databaseProductVersion = databaseMetaData.getDatabaseProductVersion();
            if (databaseProductVersion == null) {
                throw new FlywayException("Unable to determine database. Product version is null.");
            }
            return databaseProductVersion;
        } catch (SQLException e) {
            throw new FlywaySqlException("Error while determining database product version", e);
        }
    }

    /**
     * Retrieves the name of the JDBC driver
     *
     * @param connection The connection to use to query the database.
     * @return The name of the driver. Ex: RedshiftJDBC
     */
    private static String getDriverName(Connection connection) {
        try {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            if (databaseMetaData == null) {
                throw new FlywayException("Unable to read database metadata while it is null!");
            }

            String driverName = databaseMetaData.getDriverName();
            if (driverName == null) {
                throw new FlywayException("Unable to determine JDBC  driver name. JDBC driver name is null.");
            }

            return driverName;
        } catch (SQLException e) {
            throw new FlywaySqlException("Error while determining JDBC driver name", e);
        }
    }
}