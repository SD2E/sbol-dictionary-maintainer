package com.bbn.sd2;


import java.net.URI;
import java.util.ArrayList;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
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
    
    private static final String localNamespace = "http://localhost/";
    private static String collectionPrefix;
    private static URI collectionID;
    private static String login = null;
    private static String password = null;
    
    private static SynBioHubFrontend repository = null;
    
    private SynBioHubAccessor() {} // static-only class
    
    private static String collectionToCollectionName(String collectionPrefix) {
        return collectionPrefix.substring(collectionPrefix.substring(0, collectionPrefix.length()-1).lastIndexOf('/') + 1,collectionPrefix.length()-1);
    }
    
    /** Configure from command-line arguments */
    public static void configure(CommandLine cmd) {
        collectionPrefix = cmd.getOptionValue("collectionPrefix","https://hub.sd2e.org/user/sd2e/scratch_test_collection/");
        if(!collectionPrefix.endsWith("/")) collectionPrefix = collectionPrefix+"/";
        String collectionName = collectionToCollectionName(collectionPrefix);
        // TODO: is there ever a case on SBH where our collection version is not 1 or collection name is not derivable?
        collectionID = URI.create(collectionPrefix+collectionName+"_collection/1");
        
        login = cmd.getOptionValue("login","sd2_service@sd2e.org");
        password = cmd.getOptionValue("password");
    }

    /** Make a clean boot, tearing down old instance if needed */
    public static void restart() {
        repository = null;
        ensureSynBioHubConnection();
    }

    /** Boot up link with SynBioHub repository, if possible 
     * @throws SynBioHubException */
    private static void ensureSynBioHubConnection() {
        if(repository != null) return;
        
        try {
            SynBioHubFrontend sbh = new SynBioHubFrontend("https://hub.sd2e.org/");
            sbh.login(login, password);
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
        criterion.setValue(sanitizeNameToDisplayID(name));
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
    
    // displayID pattern imported from libSBOLj:
    private static final Pattern displayIDpattern = Pattern.compile("[a-zA-Z_]+[a-zA-Z0-9_]*");
    /**
     * Convert an arbitrary item name to a "safe" name for a displayID
     * @param name 
     * @return sanitized version of name
     */
    public static String sanitizeNameToDisplayID(String name) {
        String sanitized = "";
        for(int i=0;i<name.length();i++) {
            String character = name.substring(i, i+1);
            if(displayIDpattern.matcher(character).matches()) {
                sanitized += character;
            } else {
                sanitized += "0x"+String.format("%H", character);
            }
        }
        return sanitized;
    }
    
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

    /**
     * Push a document to SynBioHub to be updated
     * @param document Object to be updated
     * @throws SynBioHubException
     */
    public static void update(SBOLDocument document) throws SynBioHubException {
        ensureSynBioHubConnection();
        repository.addToCollection(collectionID, true, document);
    }
    
    
    /**
     * Create a document in the local namespace
     * @return new blank document
     */
    public static SBOLDocument newBlankDocument() {
        SBOLDocument document = new SBOLDocument();
        document.setDefaultURIprefix(localNamespace);
        return document;
    }

    /** Map a URI from the SynBioHub namespace to our local namespace */
    public static URI translateURI(URI uri) {
        return URI.create(uri.toString().replace(collectionPrefix, localNamespace));
    }

    /** Map a URI from our local namespace to the SynBioHub namespace */
    public static URI translateLocalURI(URI uri) {
        return URI.create(uri.toString().replace(localNamespace, collectionPrefix));
    }

    /**
     * The main function here creates a scratch test collection
     */
    public static void main(String... args) {
        Options options = new Options();
        options.addOption("l", "login", false, "login email account for SynBioHub maintainer account");
        options.addOption("p", "password", true, "login password for SynBioHub maintainer account");
        options.addOption("c", "collection", false, "URL for SynBioHub collection to be synchronized");
        
        try {
            configure(new DefaultParser().parse(options, args));
            ensureSynBioHubConnection();
            repository.createCollection(collectionToCollectionName(collectionPrefix), "1", "SD Dictionary Collection", "Collection targeted by SD2 Dictionary Maintainer", "", false);
        } catch(Exception e) {
            System.err.println("Repository collection creation failed.");
            e.printStackTrace();
        }
    }


}