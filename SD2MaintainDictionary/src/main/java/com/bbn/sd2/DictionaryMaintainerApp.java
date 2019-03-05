package com.bbn.sd2;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.logging.Logger;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.Set;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;

import org.apache.commons.cli.*;
import org.synbiohub.frontend.SynBioHubException;

public class DictionaryMaintainerApp {
    public static final String VERSION = "1.4.0.2";

    private static Logger log = Logger.getGlobal();
    private static int sleepMillis;
    private static boolean stopSignal = false;
    private static Semaphore heartbeatSem = new Semaphore(0);
    private static Semaphore backupSem = new Semaphore(0);
    private static boolean stopWorkerThreads = false;

    /** Email addresses mapping failures are sent to */
    private static Map<String,String> mappingFailureToList = new HashMap<String,String>(){{
            put("BioFAB", "bjkeller@uw.edu");
            put("Transcriptic", "peter@transcriptic.com; aespah@mit.edu");
            put("Ginkgo", "narendra@ginkgobioworks.com");
        }
            static final long serialVersionUID = 0;
        };

    private static Map<String,String> mappingFailureCCList = new HashMap<String,String>(){{
            put("BioFAB", "jakebeal@gmail.com;weston@netrias.com");
            put("Transcriptic", "jakebeal@gmail.com;weston@netrias.com");
            put("Ginkgo", "jakebeal@gmail.com;weston@netrias.com");
        }
            static final long serialVersionUID = 0;
        };

    private static Map<String,String> mappingFailureToListTest = new HashMap<String,String>(){{
            put("BioFAB", "dan.sumorok@raytheon.com");
            put("Transcriptic", "dan.sumorok@raytheon.com");
            put("Ginkgo", "dan.sumorok@raytheon.com");
        }
            static final long serialVersionUID = 0;
        };

    private static Map<String,String> mappingFailureCCListTest = new HashMap<String,String>(){{
            put("BioFAB", "jakebeal@gmail.com");
            put("Transcriptic", "jakebeal@gmail.com");
            put("Ginkgo", "jakebeal@gmail.com");
        }
            static final long serialVersionUID = 0;
        };

    private static Map<String,String> entryFailureList = new HashMap<String,String>(){{
            put("To", "jakebeal@gmail.com;dan.sumorok@raytheon.com");
        }
            static final long serialVersionUID = 0;
        };

    private static Map<String,String> entryFailureListTest = new HashMap<String,String>(){{
            put("To", "dan.sumorok@raytheon.com");
        }
            static final long serialVersionUID = 0;
        };

    public static void main(String... args) throws Exception {
        // Parse arguments and configure
        CommandLine cmd = parseArguments(args);
        sleepMillis = 1000*Integer.valueOf(cmd.getOptionValue("sleep","60"));
        log.info("Dictionary Maintainer initializing "+(cmd.hasOption("test_mode")?"in single update mode":"for continuous operation"));
        stopWorkerThreads = false;
        kludge_heartbeat_reporter();
        final boolean backupInMainLoop = true;
        boolean test_mode = cmd.hasOption("test_mode");
        boolean no_email = cmd.hasOption("no_email");
        Map<String, Map<String, String>> emailLists = new TreeMap<>();

        if(!no_email) {
            if(!test_mode) {
                emailLists.put("MappingFailuresTo", mappingFailureToList);
                emailLists.put("MappingFailuresCC", mappingFailureCCList);
                emailLists.put("EntryFailures", entryFailureList);
            } else {
                emailLists.put("MappingFailuresTo", mappingFailureToListTest);
                emailLists.put("MappingFailuresCC", mappingFailureCCListTest);
                emailLists.put("EntryFailures", entryFailureListTest);
            }
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
