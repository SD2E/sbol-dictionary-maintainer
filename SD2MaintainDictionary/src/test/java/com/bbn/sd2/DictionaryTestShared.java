package com.bbn.sd2;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

public class DictionaryTestShared {

    public static void initializeTestEnvironment() {
        String password = null;
        try {
            InputStream stream = DictionaryTestShared.class.getResourceAsStream("/password.txt");
            password = IOUtils.toString(stream);
        } catch(IOException e) {
            fail("Can't get password for logging into SynBioHub");
        }

        CommandLine cmd = DictionaryMaintainerApp.parseArguments("-s","15","-p",password,"-l","jakebeal@ieee.org","-c","https://synbiohub.utah.edu/user/jakebeal/scratch_test_collection/","-S","https://synbiohub.utah.edu/");
        DictionaryAccessor.configure(cmd);
        SynBioHubAccessor.configure(cmd);
        DictionaryAccessor.restart();
        SynBioHubAccessor.restart();
    }
    
    @Test
    public void verifyDictionaryTestEnvironment() {
        DictionaryTestShared.initializeTestEnvironment();
    }

}
