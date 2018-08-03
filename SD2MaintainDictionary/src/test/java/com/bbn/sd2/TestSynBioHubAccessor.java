package com.bbn.sd2;

import java.net.URI;

import org.junit.Test;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.synbiohub.frontend.SynBioHubException;

public class TestSynBioHubAccessor {

    @Test
    public void test() throws SynBioHubException, SBOLValidationException {
        DictionaryTestShared.initializeTestEnvironment();
        final URI testURI = URI.create("https://synbiohub.utah.edu/user/jakebeal/scratch_test_collection/L0x2DTryptophan/1");
        SBOLDocument doc = SynBioHubAccessor.retrieve(testURI);
        String id = doc.getTopLevels().iterator().next().getName();
        assert(id.equals("L-Tryptophan"));
    }

}
