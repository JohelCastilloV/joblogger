package com.belatrix;

import org.junit.*;
import org.junit.rules.TemporaryFolder;


import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Logger;
import java.util.logging.StreamHandler;
import java.util.stream.Stream;


public class JobLoggerTest {
    private static final Logger log = Logger.getLogger("MyLog");
    private static OutputStream logCapturingStream = new ByteArrayOutputStream();
    private static StreamHandler customLogHandler;
    private static final String DB_USERNAME = "";
    private static final String DB_PASSWORD = "";
    private static final String DB_NAME = "test";
    private static Map<String, String> dbParams = new HashMap<>();

    @ClassRule
    public static final TemporaryFolder folder = new TemporaryFolder();

    private static Connection connection;

    public String getTestCapturedLog() {
        customLogHandler.flush();
        return logCapturingStream.toString();
    }

    private int countLog() throws SQLException {
        Statement st = connection.createStatement();
        ResultSet rs = st.executeQuery("SELECT count(1) FROM log_values");
        if (rs.next()) {
            return rs.getInt(1);
        }
        return 0;
    }

    @BeforeClass
    public static void setupDatabase() throws SQLException {
        //the creation of this table could be in a file
        String url = "jdbc:h2:mem:test;INIT=CREATE TABLE LOG_VALUES(ID INT PRIMARY KEY AUTO_INCREMENT,MESSAGE VARCHAR(400),TYPE INT)";
        connection = DriverManager.getConnection(url);
        dbParams.put("db", DB_NAME);
        dbParams.put("userName", DB_USERNAME);
        dbParams.put("password", DB_PASSWORD);
        dbParams.put("logFileFolder", folder.getRoot().getAbsolutePath());
        dbParams.put("url", "jdbc:h2:mem:test");

    }

    @Before
    public void attachLogCapture() {
        Handler[] handlers = log.getParent().getHandlers();
        customLogHandler = new StreamHandler(logCapturingStream, handlers[0].getFormatter());
        log.addHandler(customLogHandler);

    }

    @Test
    public void printMessagesToConsole() throws IOException,SQLException {

        JobLogger jobLogger = new JobLogger(false, true, false, true, false, false, new HashMap());
        //this message won't be printed because the setup logWarningParam is false an the type of log is a warning
        jobLogger.logMessage("This is a simple warning message", false, true, false);
        //this message will be printed because the setup logMessageParam is true an the type of log is a message
        jobLogger.logMessage("This is a simple message", true, false, false);
        String capturedLog = getTestCapturedLog();
        Assert.assertFalse(capturedLog.contains("This is a simple warning message"));
        capturedLog = getTestCapturedLog();
        Assert.assertTrue(capturedLog.contains("This is a simple message"));
    }

    @Test
    public void printMessagesToDatabase() throws IOException,SQLException {

        JobLogger jobLogger = new JobLogger(true, true, true, true, true, true, dbParams);
        int rowsLogBefore = countLog();
        jobLogger.logMessage("This is a simple warning message", false, true, false);
        jobLogger.logMessage("This is a simple error message", false, false, true);
        int rowsLogAfter = countLog();
        //the numbers of rows should be increased in 2
        Assert.assertTrue((rowsLogBefore + 2) == rowsLogAfter);
    }

    @Test
    public void printMessageToFile() throws IOException,SQLException {

        JobLogger jobLogger = new JobLogger(true, false, false, true, true, true, dbParams);
        //this message only will be printed in the file because the paramters logToConsoleParam and logToDdatabaseParam are false
        jobLogger.logMessage("This is a simple warning message", false, true, false);
        Stream<String> stream = Files.lines(Paths.get(folder.getRoot().getAbsolutePath() + "/logFile.txt"));
        //verify if inside the file  there is a line  that contains the expected message
        Assert.assertTrue(stream.anyMatch(line -> line.contains("This is a simple warning message")));


    }

    @Test
    public void throwsIllegalArgumentExceptionIfTypeOfMessageIsAmbiguos() throws IOException,SQLException {
        JobLogger jobLogger = new JobLogger(true, true, true, true, false, false, dbParams);
        //the message only must be info , warning or error , can't have 2 values.
        try {
            jobLogger.logMessage("This is a simple message", true, true, false);
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Only one of type of message must be specified", e.getMessage());
        }

    }

    @AfterClass
    public static void tearDown() throws SQLException {
        connection.close();
    }
}
