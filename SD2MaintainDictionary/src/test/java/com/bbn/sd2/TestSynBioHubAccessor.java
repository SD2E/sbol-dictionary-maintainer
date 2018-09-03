package com.bbn.sd2;

import static org.junit.Assert.fail;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.xml.namespace.QName;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.ParseException;
import org.junit.AfterClass;
import org.junit.Test;
import org.mortbay.log.Log;
import org.sbolstandard.core2.AccessType;
import org.sbolstandard.core2.Annotation;
import org.sbolstandard.core2.Collection;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.DirectionType;
import org.sbolstandard.core2.ModuleDefinition;
import org.sbolstandard.core2.SBOLConversionException;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.Sequence;
import org.sbolstandard.core2.TopLevel;
import org.synbiohub.frontend.SynBioHubException;
import org.synbiohub.frontend.SynBioHubFrontend;

public class TestSynBioHubAccessor {

	private static final String test_collection = "scratch-test";
	
	public void initializeTestInstance() throws ParseException, SynBioHubException {
		// Do not initiate tests if password for SBH instance is not provided
		String password = System.getProperty("p");
    	if (password == null) {
			fail("Unable to initialize test environment. Password for SynBioHub staging instance was not provided.");
		}
    	String[] options = new String[] {"-S", "https://hub-staging.sd2e.org", "-f", "https://hub.sd2e.org", "-p", ""};
//    	String[] options = new String[] {"-S", "https://hub.sd2e.org", "-p", ""};

    	options[options.length - 1] = password;  // Add password to command line
		SynBioHubAccessor.main(options);		
	}
	
	@Test
	public void testSanitize() {
        String name = "scratch-test";
        assert(SynBioHubAccessor.sanitizeNameToDisplayID(name).equals("scratch0x2Dtest"));
	}
	
    @Test
    public void testAccess() throws Exception {
    	initializeTestInstance();
    	SBOLDocument document = SynBioHubAccessor.newBlankDocument();
        String description = UUID.randomUUID().toString();
        ModuleDefinition m = document.createModuleDefinition(SynBioHubAccessor.sanitizeNameToDisplayID(test_collection), "1");
        m.setName(test_collection);
        m.setDescription(description);
        SynBioHubAccessor.update(document);
        final URI testURI = URI.create(SynBioHubAccessor.getCollectionPrefix() + m.getDisplayId() + "/1");
        SBOLDocument doc = SynBioHubAccessor.retrieve(testURI);

        // This assertion only works if the clean method is enabled and each test runs on a fresh scratch_collection Collection
        // otherwise the user must manually delete the scratch_collection prior to each run
        TopLevel tl = doc.getTopLevels().iterator().next();
        assert(tl.getName().equals(test_collection));
        assert(tl.getDescription().equals(description));
    }
    
    @Test
    public void testNonrecursiveFetch() throws Exception {
    	initializeTestInstance();
    	SBOLDocument document = SynBioHubAccessor.newBlankDocument();
        String description = UUID.randomUUID().toString();
        ModuleDefinition m = document.createModuleDefinition("testNonrecursiveFetch", "1");
        ComponentDefinition root = document.createComponentDefinition("root", "1", ComponentDefinition.DNA);
        Sequence seq = document.createSequence("root_seq", "nnn", Sequence.IUPAC_DNA);
        ComponentDefinition sub1 = document.createComponentDefinition("sub1", "1", ComponentDefinition.DNA);
        ComponentDefinition sub2 = document.createComponentDefinition("sub2", "1", ComponentDefinition.DNA);
        ComponentDefinition sub3 = document.createComponentDefinition("sub3", "1", ComponentDefinition.DNA);
        root.createComponent("sub1", AccessType.PUBLIC, sub1.getIdentity());
        root.createComponent("sub2", AccessType.PUBLIC, sub1.getIdentity());
        root.createComponent("sub3", AccessType.PUBLIC, sub1.getIdentity());
        root.setSequences(new HashSet<URI>(Arrays.asList(seq.getIdentity())));
        m.createFunctionalComponent("root_fc", AccessType.PUBLIC, root.getIdentity(), DirectionType.NONE);
        SynBioHubAccessor.update(document);
        final URI testURI = URI.create(SynBioHubAccessor.getCollectionPrefix() + m.getDisplayId() + "/1");
        assert(document.getTopLevels().size() == 6);
        document = SynBioHubAccessor.retrieve(testURI);
        assert(document.getTopLevels().size() == 1);      
    }

    /* Test whether properties of SBOL objects can be overwritten. A possible failure mode due
     * to configuration of Virtuoso back-end is documented in SBH issue #679 */
    @Test
    public void overwriteAnnotation() throws Exception {
    	initializeTestInstance();
    	QName DUMMY_ANNOTATION = new QName("http://sd2e.org#","dummy","sd2");
    	SBOLDocument doc = SynBioHubAccessor.newBlankDocument();
    	ModuleDefinition m = doc.createModuleDefinition(SynBioHubAccessor.sanitizeNameToDisplayID("Annotation_test"), "1");
    	m.createAnnotation(DUMMY_ANNOTATION, "foo");
    	SynBioHubAccessor.update(doc);
    	URI m_uri = m.getIdentity();
    	System.out.println(SynBioHubAccessor.translateLocalURI(m_uri));
    	doc = SynBioHubAccessor.retrieve(SynBioHubAccessor.translateLocalURI(m_uri));
    	m = doc.getModuleDefinitions().iterator().next();

    	assert(m.getIdentity().equals(m_uri));
    	assert(m.getAnnotation(DUMMY_ANNOTATION).getStringValue().equals("foo"));

    	// Clear annotations
    	while(m.getAnnotation(DUMMY_ANNOTATION)!=null) { 
    		m.removeAnnotation(m.getAnnotation(DUMMY_ANNOTATION));
    	}
    	SynBioHubAccessor.update(doc);
    	doc = SynBioHubAccessor.retrieve(SynBioHubAccessor.translateLocalURI(m_uri));
    	m = doc.getModuleDefinitions().iterator().next();
    	doc.write(System.out);
    	assert(m.getAnnotation(DUMMY_ANNOTATION) == null);
    }  

    /* Assert that launching SynBioHubAccessor main does not clobber the Dictionary collection 
     * Also assert that updating the Dictionary Collection appends new members without completely clobbering the Collection's contents */
    @Test
    public void testMergeCollection() throws SynBioHubException, SBOLValidationException, SBOLConversionException, ParseException, URISyntaxException {
    	initializeTestInstance();
    	SBOLDocument doc = SynBioHubAccessor.newBlankDocument();
        ModuleDefinition m1 = doc.createModuleDefinition("testMergeCollection1", "1");
        SynBioHubAccessor.update(doc);
    	
        initializeTestInstance();
    	doc = SynBioHubAccessor.newBlankDocument();
        ModuleDefinition m2 = doc.createModuleDefinition("testMergeCollection2", "1");
        SynBioHubAccessor.update(doc);
        URI m1_uri = SynBioHubAccessor.translateLocalURI(m1.getIdentity());
        URI m2_uri = SynBioHubAccessor.translateLocalURI(m2.getIdentity());
        doc = SynBioHubAccessor.retrieve(m1_uri);
        m1 = doc.getModuleDefinition(m1.getIdentity());
        assert(m1 != null);
        doc = SynBioHubAccessor.retrieve(m2_uri);
        m2 = doc.getModuleDefinition(m2.getIdentity());        
        assert(m2 != null);
    }    
    
    
	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		if (System.getProperty("c") != null && System.getProperty("c").toLowerCase().equals("true"))	
			SynBioHubAccessor.clean();
	}

    
}
