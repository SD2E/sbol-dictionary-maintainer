package com.bbn.sd2;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.logging.Logger;

import org.apache.commons.cli.*;

public class DictionaryMaintainerApp {
    public static final String VERSION = "1.0.1-alpha";
    
    private static Logger log = Logger.getGlobal();
    private static int sleepMillis;
    private static boolean stopSignal = false;

    
    public static void main(String... args) throws Exception {
        // Parse arguments and configure
        CommandLine cmd = parseArguments(args);
        sleepMillis = 1000*Integer.valueOf(cmd.getOptionValue("sleep","60"));
        log.info("Dictionary Maintainer initializing "+(cmd.hasOption("test_mode")?"in single update mode":"for continuous operation"));
        DictionaryAccessor.configure(cmd);
        SynBioHubAccessor.configure(cmd);
        kludge_heartbeat_reporter();
        start_backup(1);

        // Run as an eternal loop, reporting errors but not crashing out
        while(!stopSignal) {
            DictionaryAccessor.restart();
            SynBioHubAccessor.restart();
            
            while(!stopSignal) {
                try {
                	long start = System.currentTimeMillis();
                    MaintainDictionary.maintain_dictionary();
                    long end = System.currentTimeMillis();
                    NumberFormat formatter = new DecimalFormat("#0.00000");
                    log.info("Dictionary update executed in " + formatter.format((end - start) / 1000d) + " seconds");
                } catch(Exception e) {
                    log.severe("Exception while maintaining dictionary:");
                    e.printStackTrace();
                }
                if (cmd.hasOption("test_mode"))
                	setStopSignal();
                else {
                	try {
                		Thread.sleep(sleepMillis);
                	} catch(InterruptedException e) {
                		// ignore sleep interruptions
                	}
                }
            }
        }
        kludge_heartbeat_stop = true;
        log.info("Dictionary Maintainer run complete, shutting down.");
    }
 
    private static void start_backup(int days) {
    	
    	new Thread() { 
    		public void run() {
    			while(!kludge_heartbeat_stop) {
        			log.info("Executing Dictionary backup");
					try {
						DictionaryAccessor.backup();
					} catch (IOException | GeneralSecurityException e) {
						e.printStackTrace();
					} 
					try {
						Thread.sleep(days*24*3600*1000);
					}
					catch(InterruptedException e) {}
    			}
    		}
    	}.start();
    }   
  
    private static boolean kludge_heartbeat_stop = false;
    private static void kludge_heartbeat_reporter() {
        new Thread() { public void run() {
            int count=0;
            while(!kludge_heartbeat_stop) {
                System.out.println("[Still Running: "+(count++)+" minutes]");
                try { Thread.sleep(60000); } catch(InterruptedException e) {}
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
}
