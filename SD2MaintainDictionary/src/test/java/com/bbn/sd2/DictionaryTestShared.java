package com.bbn.sd2;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.junit.AfterClass;
import org.mortbay.log.Log;

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
        String noEmailOption = System.getProperty("no_email");

        List<String> optionList = new ArrayList<>();
        // Sleep zero seconds between updates
        optionList.add("-s");
        optionList.add("0");

        // URL for SynBioHub server
        optionList.add("-S");
        optionList.add("https://hub-staging.sd2e.org");

        // Spoofing URL prefix
        optionList.add("-f");
        optionList.add("https://hub.sd2e.org");

        // Set Google sheets id
        optionList.add("-g");
        optionList.add(gsheet_id);

        // Enable test mode
        optionList.add("-t");
        optionList.add("true");

        // Set password for SynBioHub
        optionList.add("-p");
        optionList.add(password);

        if(noEmailOption != null) {
            // Don' generate email messages
            optionList.add("-n");
            optionList.add("true");
        }

        String[] options = optionList.toArray(new String[0]);
        CommandLine cmd;
        cmd = DictionaryMaintainerApp.parseArguments(options);

        DictionaryAccessor.configure(cmd);
        SynBioHubAccessor.configure(cmd);
        DictionaryAccessor.restart();
        SynBioHubAccessor.restart();
        if(!SynBioHubAccessor.collectionExists()) {
            Log.info("Creating Collection");
            SynBioHubAccessor.createCollection();
        }
        DictionaryMaintainerApp.restart();
        DictionaryMaintainerApp.main(options);
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        SynBioHubAccessor.logout();
    }


}
