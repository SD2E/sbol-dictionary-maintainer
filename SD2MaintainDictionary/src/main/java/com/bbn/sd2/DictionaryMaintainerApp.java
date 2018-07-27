package com.bbn.sd2;

import java.util.logging.Logger;

import org.apache.commons.cli.*;

public class DictionaryMaintainerApp {
    private static Logger log = Logger.getGlobal();
    private static int sleepMillis;
    
    public static void main(String... args) {
        parseArguments(args);
        
        // Run as an eternal loop, reporting errors but not crashing out
        while(true) {
            try {
                MaintainDictionary.maintain_dictionary();
                Thread.sleep(sleepMillis);
            } catch(Exception e) {
                log.severe("Exception while maintaining dictionary:");
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Prepare and 
     * @param args Current command-line arguments, to be passed in
     */
    private static void parseArguments(String ...args) {
        // Set up options
        Options options = new Options();
        options.addOption(new Option("s", "sleep", true, "milliseconds to sleep between polls"));
        
        // Parse arguments
        CommandLine cmd = null;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("utility-name", options);

            System.exit(1);
        }
        
        // Attempt to make argument assignments
        sleepMillis = Integer.valueOf(cmd.getOptionValue("sleep","60000"));
    }

}
