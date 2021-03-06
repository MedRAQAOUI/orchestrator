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

import java.util.regex.Pattern;

import org.flywaydb.core.internal.dbsupport.Delimiter;
import org.flywaydb.core.internal.dbsupport.SqlStatementBuilder;
import org.flywaydb.core.internal.util.StringUtils;

public class TeradataSqlStatementBuilder extends SqlStatementBuilder {

    /**
     * Holds the beginning of the statement.
     */
    private String statementStart = "";

    /** DDL statement keywords. */
    private static final Pattern REGEX_DDL_STATEMENT = Pattern
            .compile("(?i)(NONTEMPORAL\\s+)?(ALTER|BEGIN|CALL|COLLECT|COMMENT|CREATE|DROP|GIVE|GRANT|LOCK|LOCKING|RENAME|RESTART|REPLACE|REVOKE|SET) .*");

    /** Multi-statement keywords : for function, procedure, macro. */
    private static final Pattern REGEX_MULTI_STATEMENT = Pattern
            .compile("(?i)(CREATE|REPLACE)\\s+(FUNCTION|MACRO|PROCEDURE|TRIGGER).*");

    /** Function/procedure/macro separator. */
    private static final Delimiter TERADATA_DELIMITER = new Delimiter(";;", true);

    @Override
    protected void applyStateChanges(String line) {
        super.applyStateChanges(line);

        if (!executeInTransaction) {
            return;
        }

        if (StringUtils.countOccurrencesOf(statementStart, " ") < 8) {
            statementStart += line;
            statementStart += " ";
            statementStart = statementStart.replaceAll("\\s+", " ");
        }

        // If a DDL transaction is detected we do not execute the migration in a
        // transaction.
        if (REGEX_DDL_STATEMENT.matcher(statementStart).matches()) {
            executeInTransaction = false;
        }
    }

    @Override
    protected Delimiter changeDelimiterIfNecessary(String line, Delimiter delimiter) {
        // Multi-line statement containing ';'
        if (REGEX_MULTI_STATEMENT.matcher(statementStart).matches()) {
            return TERADATA_DELIMITER;
        }
        // Classic statement
        if (StringUtils.countOccurrencesOf(this.statementStart, " ") < 8) {
            this.statementStart += line;
            this.statementStart += " ";
            this.statementStart = this.statementStart.replaceAll("\\s+", " ");
        }
        return delimiter;
    }

}