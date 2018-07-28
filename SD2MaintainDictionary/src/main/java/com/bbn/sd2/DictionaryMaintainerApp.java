package com.bbn.sd2;

import java.util.logging.Logger;

import org.apache.commons.cli.*;

public class DictionaryMaintainerApp {
    public static final String VERSION = "1.0.0-alpha";
    
    private static Logger log = Logger.getGlobal();
    private static int sleepMillis;
    
    public static void main(String... args) {
        // Parse arguments and configure
        CommandLine cmd = parseArguments(args);
        sleepMillis = 1000*Integer.valueOf(cmd.getOptionValue("sleep","60"));
        DictionaryAccessor.configure(cmd);
        SynBioHubAccessor.configure(cmd);

        // Run as an eternal loop, reporting errors but not crashing out
        while(true) {
            DictionaryAccessor.restart();
            SynBioHubAccessor.restart();
            
            while(true) {
                try {
                    MaintainDictionary.maintain_dictionary();
                } catch(Exception e) {
                    log.severe("Exception while maintaining dictionary:");
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(sleepMillis);
                } catch(InterruptedException e) {
                    // ignore sleep interruptions
                }
            }
        }
    }
    
    /**
     * Prepare and 
     * @param args Current command-line arguments, to be passed in
     */
    private static CommandLine parseArguments(String ...args) {
        // Set up options
        Options options = new Options();
        options.addOption("s", "sleep", true, "seconds to sleep between updates");
        options.addOption("l", "login", false, "login email account for SynBioHub maintainer account");
        options.addOption("p", "password", true, "login password for SynBioHub maintainer account");
        options.addOption("c", "collection", false, "URL for SynBioHub collection to be synchronized");
        options.addOption("g", "gsheet_id", false, "Google Sheets ID of spreadsheet");
        
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
