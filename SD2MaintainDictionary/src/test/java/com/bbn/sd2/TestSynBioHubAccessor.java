package com.bbn.sd2;

import static org.junit.Assert.fail;

import java.net.URI;
import java.util.UUID;

import org.apache.commons.cli.CommandLine;
import org.junit.AfterClass;
import org.junit.Test;
import org.sbolstandard.core2.ModuleDefinition;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.TopLevel;
import org.synbiohub.frontend.SynBioHubException;

public class TestSynBioHubAccessor {

	@Test
	public void testSanitize() {
        String name = "scratch-test";
        assert(SynBioHubAccessor.sanitizeNameToDisplayID(name).equals("scratch0x2Dtest"));
	}
	
    @Test
    public void testAccess() throws Exception {
		// Do not initiate tests if password for SBH instance is not provided
		String password = System.getProperty("p");
    	if (password == null) {
			fail("Unable to initialize test environment. Password for SynBioHub staging instance was not provided.");
		}
    	String[] options = new String[] {"-S", "https://hub-staging.sd2e.org", "-f", "https://hub.sd2e.org", "-p", ""};
		options[options.length - 1] = password;  // Add password to command line
		SynBioHubAccessor.main(options);
		
    	SBOLDocument document = SynBioHubAccessor.newBlankDocument();
        String name = "scratch-test";
        String description = UUID.randomUUID().toString();
        ModuleDefinition m = document.createModuleDefinition(SynBioHubAccessor.sanitizeNameToDisplayID(name), "1");
        m.setName(name);
        m.setDescription(description);
        SynBioHubAccessor.update(document);
        final URI testURI = URI.create(SynBioHubAccessor.getCollectionPrefix() + m.getDisplayId() + "/1");

        SBOLDocument doc = SynBioHubAccessor.retrieve(testURI);
        TopLevel tl = doc.getTopLevels().iterator().next();
        assert(tl.getName().equals(name));
        assert(tl.getDescription().equals(description));
    }

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (System.getProperty("c") != null && System.getProperty("c").toLowerCase().equals("true"))	
			SynBioHubAccessor.clean();
	}

    
}
