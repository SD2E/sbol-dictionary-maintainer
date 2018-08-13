package com.bbn.sd2;

import java.util.logging.Logger;

import org.apache.commons.cli.*;

public class DictionaryMaintainerApp {
    public static final String VERSION = "1.0.1-alpha";
    
    private static Logger log = Logger.getGlobal();
    private static int sleepMillis;
    private static boolean oneShot = false;
    
    public static void main(String... args) {
        // Parse arguments and configure
        CommandLine cmd = parseArguments(args);
        sleepMillis = 1000*Integer.valueOf(cmd.getOptionValue("sleep","60"));
        oneShot = cmd.hasOption("one-shot");
        log.info("Dictionary Maintainer initializing "+(oneShot?"in single update mode":"for continuous operation"));
        DictionaryAccessor.configure(cmd);
        SynBioHubAccessor.configure(cmd);


        // Run as an eternal loop, reporting errors but not crashing out
        do {
            DictionaryAccessor.restart();
            SynBioHubAccessor.restart();
            
            do {
                try {
                    MaintainDictionary.maintain_dictionary();
                } catch(Exception e) {
                    log.severe("Exception while maintaining dictionary:");
                    e.printStackTrace();
                }
                try {
                    if(!oneShot) Thread.sleep(sleepMillis);
                } catch(InterruptedException e) {
                    // ignore sleep interruptions
                }
            } while(!oneShot);
        } while(!oneShot);
        log.info("Dictionary Maintainer run complete, shutting down.");
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
        options.addOption("o", "one-shot", false, "run only once and then quit");
        
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

}
