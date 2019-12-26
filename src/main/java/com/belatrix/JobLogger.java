package com.belatrix;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Properties;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class JobLogger {
    private boolean logToFile;
    private boolean logToConsole;
    private boolean logMessage;
    private boolean logWarning;
    private boolean logError;
    private boolean logToDatabase;
    private Map dbParams;
    private static final Logger logger = Logger.getLogger("MyLog");
    private Connection connection;

    public JobLogger(boolean logToFileParam, boolean logToConsoleParam, boolean logToDatabaseParam,
                     boolean logMessageParam, boolean logWarningParam, boolean logErrorParam, Map dbParamsMap) throws IOException, SQLException {
        logger.setUseParentHandlers(false);
        logError = logErrorParam;
        logMessage = logMessageParam;
        logWarning = logWarningParam;
        logToDatabase = logToDatabaseParam;
        logToFile = logToFileParam;
        logToConsole = logToConsoleParam;
        dbParams = dbParamsMap;
        if (logToFile) {
            String pathname = dbParams.get("logFileFolder") + "/logFile.txt";
            File logFile = new File(pathname);
            if (!logFile.exists()) {
                logFile.createNewFile();

            }
            FileHandler fh = new FileHandler(pathname);
            logger.addHandler(fh);

        }
        if (logToConsole) {
            ConsoleHandler ch = new ConsoleHandler();
            logger.addHandler(ch);
        }
        if (logToDatabase) {
            connection = getConnection();
        }

    }

    private Connection getConnection() throws SQLException {
        Properties connectionProps = new Properties();
        String password = (String) dbParams.get("password");
        String userName = (String) dbParams.get("userName");
        if (userName == null || password == null) {
            throw new IllegalArgumentException("Invalid configuration database");
        }
        connectionProps.put("user", userName);
        connectionProps.put("password", password);
        String url = (String) dbParams.get("url");
        if (url == null) {
            url = "jdbc:" + dbParams.get("dbms") + "://" + dbParams.get("serverName")
                    + ":" + dbParams.get("portNumber") + "/" + dbParams.get("database");
        }
        return DriverManager.getConnection(url, connectionProps);

    }

    public void logMessage(String messageText, boolean message, boolean warning, boolean error) throws SQLException {
        validateArguments(message, warning, error);
        if (messageText == null || messageText.length() == 0) {
            return;
        }
        messageText = messageText.trim();
        int t;
        Level level;
        if (message && logMessage) {
            t = 1;
            level = Level.INFO;
        } else if (error && logError) {
            t = 2;
            level = Level.SEVERE;
        } else if (warning && logWarning) {
            t = 3;
            level = Level.WARNING;
        } else {
            return;
        }

        if (logToFile || logToConsole) {
            logger.log(level, messageText);
        }
        if (logToDatabase) {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("insert into Log_Values(message,type)VALUES ('" + message + "', " + t + ")");
            }

        }

    }

    private void validateArguments(boolean message, boolean warning, boolean error) {

        if (!logToConsole && !logToFile && !logToDatabase) {
            throw new IllegalArgumentException("Invalid configuration");
        }
        if ((!logError && !logMessage && !logWarning) || (!message && !warning && !error)) {
            throw new IllegalArgumentException("Error or Warning or Message must be specified");
        }

        if (message && (warning || error) || (warning && error)) {
            throw new IllegalArgumentException("Only one of type of message must be specified");
        }
    }


}

