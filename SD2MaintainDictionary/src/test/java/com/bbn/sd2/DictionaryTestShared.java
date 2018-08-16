package com.bbn.sd2;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class DictionaryTestShared {

    public static void initializeTestEnvironment(String gsheet_id) throws Exception {
//        String password = null;
//        try {
//            InputStream stream = DictionaryTestShared.class.getResourceAsStream("/password.txt");
//            password = IOUtils.toString(stream);
//        } catch(IOException e) {
//            fail("Can't get password for logging into SynBioHub");
//        }
//        CommandLine cmd = DictionaryMaintainerApp.parseArguments("-s","15","-p",password,"-l","jakebeal@ieee.org","-c","https://synbiohub.utah.edu/user/jakebeal/scratch_test_collection/","-S","https://synbiohub.utah.edu/");

	    // Configure options for DictionaryMaintainer to use the staging instance of SBH and temporary Google Sheets
		// Do not initiate tests if password for SBH instance is not provided
		String password = System.getProperty("p");
    	if (password == null) {
			fail("Unable to initialize test environment. Password for SynBioHub staging instance was not provided.");
		}
    	if (gsheet_id == null) {
			fail("Unable to initialize test environment. No Google Sheet was provided.");
    	}
    	String[] options = new String[] {"-s", "0", "-S", "https://hub-staging.sd2e.org", "-f", "https://hub.sd2e.org", "-g", gsheet_id, "-t", "yes", "-p", ""};
		options[options.length - 1] = password;  // Add password to command line

    	CommandLine cmd;
    	cmd = DictionaryMaintainerApp.parseArguments(options);
    		
    	DictionaryAccessor.configure(cmd);
        SynBioHubAccessor.configure(cmd);
        DictionaryAccessor.restart();
        SynBioHubAccessor.restart();
    	DictionaryMaintainerApp.restart();
		DictionaryMaintainerApp.main(options);
    }
    
    @Test
    public void verifyDictionaryTestEnvironment() throws Exception {
        DictionaryTestShared.initializeTestEnvironment(MaintainDictionary.defaultSpreadsheet());
    }

}
