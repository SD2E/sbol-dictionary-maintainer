package com.bbn.sd2;


import java.net.URI;
import java.util.ArrayList;
import java.util.logging.Logger;

import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.synbiohub.frontend.IdentifiedMetadata;
import org.synbiohub.frontend.SearchCriteria;
import org.synbiohub.frontend.SearchQuery;
import org.synbiohub.frontend.SynBioHubException;
import org.synbiohub.frontend.SynBioHubFrontend;

/**
 * Helper class for importing SBOL into the working compilation.
 */
public final class SynBioHubAccessor {
    private static Logger log = Logger.getGlobal();
    
    private SynBioHubAccessor() {} // static-only class
    
    private static SynBioHubFrontend repository = null;
    
    /** Boot up link with SynBioHub repository, if possible 
     * @throws SynBioHubException */
    private static void ensureSynBioHubConnection() {
        if(repository != null) return;
        
        try {
            SynBioHubFrontend sbh = new SynBioHubFrontend("https://hub.sd2e.org/");
            sbh.login("sd2_service@sd2e.org", "password");
            repository = sbh;
            log.info("Successfully logged into SD2 SynBioHub");
        } catch(Exception e) {
            e.printStackTrace();
            log.severe("SD2 SynBioHub login failed");
        }
    }
    
    /**
     * 
     * @param name
     * @return URI for part with exact match of name, otherwise null
     * @throws SynBioHubException if the connection fails
     */
    public static URI nameToURI(String name) throws SynBioHubException {
        ensureSynBioHubConnection();
        // Search by name
        SearchQuery query = new SearchQuery();
        SearchCriteria criterion = new SearchCriteria();
        criterion.setKey("dcterms:title");
        criterion.setValue(name);
        query.addCriteria(criterion);
        ArrayList<IdentifiedMetadata> response = repository.search(query);
        // If anything comes back, return it; else null
        if(response.isEmpty()) {
            return null;
        } else if(response.size()==1) {
            return URI.create(response.get(0).getUri());
        } else {
            log.severe("Cannot resolve: multiple URIs match name "+name);
            return null;
        }
    }
    
    private static final String localNamespace = "http://localhost/";
    /**
     * Get an object from SynBioHub, translating into a local namespace
     * @param uri
     * @return Document containing our object, in our local namespace
     * @throws SynBioHubException
     * @throws SBOLValidationException
     */
    public static SBOLDocument retrieve(URI uri) throws SynBioHubException, SBOLValidationException {
        ensureSynBioHubConnection();
        
        SBOLDocument document = repository.getSBOL(uri);
        // convert to our own namespace:
        return document.changeURIPrefixVersion(localNamespace, null, "1");
    }

    static final String collectionPrefix = "https://hub.sd2e.org/user/sd2e/scratch_test_collection/";
    static final URI collectionID = URI.create("https://hub.sd2e.org/user/sd2e/scratch_test_collection/scratch_test_collection_collection/1");
    private static void ensureScratchCollectionExists() {
        try {
            repository.createCollection("scratch_test_collection", "1", "scratch collection", "Collection for experiments in safe space", "", false);
        } catch(SynBioHubException e) {
            // Ignore: it exists.
            //e.printStackTrace();
        }
    }
        
    public static void update(SBOLDocument document) throws SynBioHubException {
        ensureSynBioHubConnection();
        
        ensureScratchCollectionExists();

        repository.addToCollection(collectionID, true, document);
    }
    
    
    public static void main(String... args) {
        try {
            // Show a hit and a miss on name resolution:
            URI uri = nameToURI("L-Tryptophan");
            System.out.println("L-Tryptophan has URI: "+uri);
            uri = nameToURI("Owl-Gryptophan");
            System.out.println("Owl-Gryptophan has URI: "+uri);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    public static SBOLDocument newBlankDocument() {
        SBOLDocument document = new SBOLDocument();
        document.setDefaultURIprefix(localNamespace);
        return document;
    }

    // Map from the SD2 namespace to our local namespace
    public static URI translateURI(URI uri) {
        System.out.println("old URI: "+uri);
        // TODO Auto-generated method stub
        URI newURI = URI.create(uri.toString().replace(collectionPrefix, localNamespace));
        System.out.println("new URI: "+newURI);
        return newURI;
    }

}
