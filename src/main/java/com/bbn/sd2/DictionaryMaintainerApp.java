package com.bbn.sd2;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.commons.cli.*;
import org.json.*;
import org.synbiohub.frontend.SynBioHubException;

public class DictionaryMaintainerApp {
    public static final String VERSION = "1.4.4";

    private static Logger log = Logger.getGlobal();
    private static int sleepMillis;
    private static boolean stopSignal = false;
    private static Semaphore heartbeatSem = new Semaphore(0);
    private static Semaphore backupSem = new Semaphore(0);
    private static boolean stopWorkerThreads = false;
    private static Map<String, Map<String, String>> emailLists = new TreeMap<>();

    public static Map<String, String> labUIDMap = new TreeMap<>();
    public static Map<String, String> reverseLabUIDMap = new TreeMap<>();
    public static List<String> editors = new ArrayList<>();

    /** Email addresses mapping failures are sent to */
    public static void main(String... args) throws Exception {
        // Parse arguments and configure
        CommandLine cmd = parseArguments(args);
        sleepMillis = 1000*Integer.valueOf(cmd.getOptionValue("sleep","60"));
        log.info("Dictionary Maintainer initializing "+(cmd.hasOption("test_mode") ?
                                                        "in single update mode":
                                                        "for continuous operation"));
        stopWorkerThreads = false;
        kludge_heartbeat_reporter();
        final boolean backupInMainLoop = true;
        boolean test_mode = cmd.hasOption("test_mode");
        boolean no_email = cmd.hasOption("no_email");

        if(!cmd.hasOption("config_file")) {
            log.severe("No configuration file was specified");
            return;
        }

        loadConfigFile(cmd.getOptionValue("config_file"));

        if(no_email) {
            emailLists.clear();
        }

        DictionaryAccessor.configure(cmd);
        DictionaryAccessor.restart();

        if(!backupInMainLoop) {
            if(!test_mode) {
                start_backup(1);
            }
        }

        // Number of milliseconds in one hour
        long hourMillis = 3600000;

        // Number of milliseconds in one day
        long dayMillis = hourMillis * 24;

        // Get time, in milliseconds since 1970 UTC
        long now = System.currentTimeMillis();

        // Calculate time of next midnight (UTC)
        long nextMidnightUTC = now - (now % dayMillis) + dayMillis;

        // Backup time in hours after midnight UTC.
        long backTimeHoursAM_UTC = 8;

        // This keeps track of the next time to run a backup
        long nextBackupTime = nextMidnightUTC + (backTimeHoursAM_UTC * hourMillis);

        // Make sure collection exists

        SynBioHubAccessor.configure(cmd);
        SynBioHubAccessor.restart();
        try {
            if(!SynBioHubAccessor.collectionExists()) {
                URI collectionID = SynBioHubAccessor.getCollectionID();
                if(collectionID != null) {
                    log.severe("Collection " + collectionID + " does not exist");
                } else {
                    log.severe("Collection does not exist");
                }
                return;
            }
        } catch(SynBioHubException e) {
            e.printStackTrace();
            return;
        }
        SynBioHubAccessor.logout();

        // Run as an eternal loop, reporting errors but not crashing out
        while(!stopSignal) {
            try {
                long start = System.currentTimeMillis();
                SynBioHubAccessor.configure(cmd);
                SynBioHubAccessor.restart();
                MaintainDictionary.maintain_dictionary(emailLists);
                SynBioHubAccessor.logout();
                long end = System.currentTimeMillis();
                NumberFormat formatter = new DecimalFormat("#0.00000");
                log.info("Dictionary update executed in " + formatter.format((end - start) / 1000d) + " seconds");

            } catch(Exception e) {
                log.severe("Exception while maintaining dictionary:");
                e.printStackTrace();
            }

            if (test_mode) {
                setStopSignal();
            } else {
                if(backupInMainLoop) {
                    try {
                        Thread.sleep(sleepMillis);

                        if(System.currentTimeMillis() > nextBackupTime) {
                            // Back up spreadsheet
                            DictionaryAccessor.backup();

                            while(System.currentTimeMillis() > nextBackupTime) {
                                nextBackupTime += dayMillis;
                            }
                        }

                        copyTabsToStagingSpreadsheet();
                    } catch(Exception e) {
                        e.printStackTrace();
                    }
                }

                try {
                    Thread.sleep(sleepMillis);
                } catch(InterruptedException e) {
                    // ignore sleep interruptions
                }
            }
        }

        stopWorkerThreads = true;
        heartbeatSem.release(1);
        backupSem.release(1);
        log.info("Dictionary Maintainer run complete, shutting down.");
    }

    private static void start_backup(int days) {

        new Thread() {
            public void run() {
                while(!stopWorkerThreads) {
                    log.info("Executing Dictionary backup");
                    try {
                        DictionaryAccessor.backup();
                        copyTabsToStagingSpreadsheet();

                    } catch (IOException | GeneralSecurityException e) {
                        e.printStackTrace();
                    }
                    try {
                        backupSem.tryAcquire(1, days*24*3600, TimeUnit.SECONDS);
                    }
                    catch(InterruptedException e) {}
                }
                log.info("Stopping Dictionary backups");
            }
        }.start();
    }

    public static void loadConfigFile(String fName) throws IOException {
        FileInputStream fs = null;

        try {
            fs = new FileInputStream(fName);

            int fileLength = fs.available();
            byte[] fileContents = new byte[fileLength];
            int bytesRead = 0;

            while(bytesRead < fileLength) {
                bytesRead += fs.read(fileContents, bytesRead,
                                     fileLength - bytesRead);
            }

            JSONObject configFile = new JSONObject(new String(fileContents));

            labUIDMap.clear();
            reverseLabUIDMap.clear();
            editors.clear();

            processConfigFile(configFile);
            reverseLabUIDMap = generateReverseLabUIDMap();

        } catch(IOException e) {
            throw e;
        } finally {
            if(fs != null) {
                try {
                    fs.close();
                } catch(Exception e) {
                }
            }
        }
    }

    private static Map<String, String> generateReverseLabUIDMap() {
        Map<String, String> reverseMap = new TreeMap<String, String>();

        for(String key : labUIDMap.keySet()) {
            reverseMap.put(labUIDMap.get(key), key);
        }

        return reverseMap;
    }

    private static void processConfigFile(JSONObject configFile) {
        // Lab configuration
        JSONArray labs = configFile.getJSONArray("labs");
        for(int i=0; i<labs.length(); ++i) {
            JSONObject lab = labs.getJSONObject(i);
            String labName = lab.getString("labName");
            String propertyName = lab.getString("propertyName");
            labUIDMap.put(labName + " UID", propertyName);

            if(lab.has("mappingFailureEmail")) {
                JSONObject mappingFailureEmail =
                    lab.getJSONObject("mappingFailureEmail");

                String toList = emailListToString(mappingFailureEmail, "To");
                addListEmailAddress(labName, toList, "MappingFailuresTo");

                String ccList = emailListToString(mappingFailureEmail, "CC");
                addListEmailAddress(labName, ccList, "MappingFailuresCC");

            }
        }

        // Entry Failure Email list
        JSONObject entryFailureEmail = configFile.getJSONObject("entryFailureEmail");
        for(String key : entryFailureEmail.keySet()) {
            String emailAddresses = emailListToString(entryFailureEmail, key);
            addListEmailAddress(key, emailAddresses, "EntryFailures");
        }

        JSONArray configEditors = configFile.getJSONArray("editors");
        for(int i=0; i<configEditors.length(); ++i) {
            editors.add(configEditors.getString(i));
        }

        if(configFile.has("config")) {
            JSONObject config = configFile.getJSONObject("config");

            if(config.has("synBioHubAccessRetryCount")) {
                MaintainDictionary.synBioHubAccessRetryCount =
                    (int)config.getLong("synBioHubAccessRetryCount");
            }

            if(config.has("synBioHubAccessRetryPauseMS")) {
                MaintainDictionary.synBioHubAccessRetryPauseMS =
                    (int)config.getLong("synBioHubAccessRetryPauseMS");
            }
        }
    }

    private static void addListEmailAddress(String key, String emailList, String tag) {
        if(emailList == null) {
            return;
        }

        Map<String,String> labMap = null;
        if(!emailLists.containsKey(tag)) {
            labMap = new TreeMap<>();
            emailLists.put(tag, labMap);
        } else {
            labMap = emailLists.get(tag);
        }

        labMap.put(key, emailList);
    }

    private static String emailListToString(JSONObject parent, String key) {
        if(!parent.has(key)) {
            return null;
        }

        JSONArray list = parent.getJSONArray(key);
        String result = null;
        for(int i=0 ;i<list.length(); ++i) {
            String emailAddress = list.getString(i);
            if(result == null) {
                result = emailAddress;
            } else {
                result += ";" + emailAddress;
            }
        }

        return result;
    }

    private static void kludge_heartbeat_reporter() {
        new Thread() { public void run() {
            int count=0;
            while(!stopWorkerThreads) {
                System.out.println("[Still Running: "+(count++)+" minutes]");
                try {
                    heartbeatSem.tryAcquire(1, 60, TimeUnit.SECONDS);
                } catch(InterruptedException e) {}
            }
            System.out.println("[Stopped]");
        }}.start();
    }

    /**
     * Prepare and
     * @param args Current command-line arguments, to be passed in
     */
    public static CommandLine parseArguments(String ...args) {
        // Set up options
        Options options = new Options();
        options.addOption("s", "sleep", true, "seconds to sleep between updates");
        options.addOption("l", "login", true, "login email account for SynBioHub maintainer account");
        options.addOption("p", "password", true, "login password for SynBioHub maintainer account");
        options.addOption("c", "collection", true, "URL for SynBioHub collection to be synchronized");
        options.addOption("g", "gsheet_id", true, "Google Sheets ID of spreadsheet");
        options.addOption("S", "server", true, "URL for SynBioHub server");
        options.addOption("f", "spoofing", true, "URL prefix for a test SynBioHub server spoofing as another");
        options.addOption("t", "test_mode", false, "Run only one update for testing purposes, then terminate");
        options.addOption("T", "timeout", true, "Connection timeout in seconds (zero to disable timeout)");
        options.addOption("n", "no_email", false, "Don't send email");
        options.addOption("i", "config_file", true, "Configuration File");

        // Parse arguments
        CommandLine cmd = null;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("utility-name", options);

            System.exit(1);
        }
        return cmd;
    }

    public static void setStopSignal() {
        stopSignal = true;
    }

    public static void restart() {
        stopSignal = false;
    }

    private static void copyTabsToStagingSpreadsheet() throws IOException {
        String spreadsheetId = DictionaryAccessor.getSpreadsheetId();
        String stagingSpreadsheetId = MaintainDictionary.stagingSpreadsheet();

        // Copy Spreadsheet tabs to staging spreadsheet
        Set<String> tabList = new HashSet<>(MaintainDictionary.tabs());

        // This tab is treated differently and is not in the tab list
        tabList.add("Mapping Failures");

        DictionaryAccessor.copyTabsFromOtherSpreadSheet(spreadsheetId,
                                                        stagingSpreadsheetId,
                                                        tabList);
    }
}
