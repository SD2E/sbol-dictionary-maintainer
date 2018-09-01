package com.bbn.sd2;


import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import org.sbolstandard.core2.Annotation;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.GenericTopLevel;
import org.sbolstandard.core2.ModuleDefinition;
import org.sbolstandard.core2.SBOLConversionException;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.TopLevel;
import org.synbiohub.frontend.SynBioHubException;

import com.bbn.sd2.DictionaryEntry.StubStatus;

/**
 * Helper class for importing SBOL into the working compilation.
 */
public final class MaintainDictionary {
    private static Logger log = Logger.getGlobal();
    
    private static final QName STUB_ANNOTATION = new QName("http://sd2e.org#","stub_object","sd2");
    private static final QName CREATED = new QName("http://purl.org/dc/terms/","created","dcterms");
    private static final QName MODIFIED = new QName("http://purl.org/dc/terms/","modified","dcterms");
    
    /** The ID for the current SD2E Dictionary Spreadsheet */
    private static final String SD2E_DICTIONARY = "1xyFH-QqYzoswvI3pPJRlBqw9PQdlp91ds3mZoPc3wCU";
    
    /** Each spreadsheet tab is only allowed to contain objects of certain types, as determined by this mapping */
    private static Map<String, Set<String>> typeTabs = new HashMap<String,Set<String>>() {{
    	put("Attribute", new HashSet<>(Arrays.asList("Attribute")));
    	put("Reagent", new HashSet<>(Arrays.asList("Bead", "CHEBI", "DNA", "Protein", "RNA", "Media", "Stain", "Buffer", "Solution")));
    	put("Genetic Construct", new HashSet<>(Arrays.asList("DNA", "RNA")));
    	put("Strain", new HashSet<>(Arrays.asList("Strain")));
    	put("Protein", new HashSet<>(Arrays.asList("Protein")));
    }};

    /** Expected headers */
    private static final Set<String> validHeaders = new HashSet<>(Arrays.asList("Common Name", "Type", "SynBioHub URI", "BioFAB UID", "Ginkgo UID", "Transcriptic UID", "Stub Object?", "Definition URI")); 
    
    /** Classes of object that are implemented as a ComponentDefinition */
    private static Map<String,URI> componentTypes = new HashMap<String,URI>() {{
        put("Bead",URI.create("http://purl.obolibrary.org/obo/NCIT_C70671")); 
        put("CHEBI",URI.create("http://identifiers.org/chebi/CHEBI:24431")); 
        put("DNA",ComponentDefinition.DNA); 
        put("Protein",ComponentDefinition.PROTEIN);
        put("RNA",ComponentDefinition.RNA); 
    }};
    
    /** Classes of object that are implemented as a ModuleDefinition */
    private static Map<String,URI> moduleTypes = new HashMap<String,URI>(){{
        put("Strain",URI.create("http://purl.obolibrary.org/obo/NCIT_C14419")); 
        put("Media",URI.create("http://purl.obolibrary.org/obo/NCIT_C85504")); 
        put("Stain",URI.create("http://purl.obolibrary.org/obo/NCIT_C841"));
        put("Buffer",URI.create("http://purl.obolibrary.org/obo/NCIT_C70815"));
        put("Solution",URI.create("http://purl.obolibrary.org/obo/NCIT_C70830"));
    }};
    
    /** Classes of object that are not stored in SynBioHub, but are grounded in external definitions */
    private static Map<String,QName> externalTypes = new HashMap<String,QName>(){{
        put("Attribute",new QName("http://sd2e.org/types/#","attribute","sd2"));
    }};
        
    /**
     * @param tab String name of a spreadsheet tab
     * @param type String naming a type
     * @return true if we know how to handle entries of this type
     */
    public static boolean validType(String tab, String type) {
    	return typeTabs.get(tab).contains(type);
    }
    
    /**
     * @param tab String name of a spreadsheet tab
     * @return true if the given spreadsheet tab belongs to a predetermined set
     */
    public static boolean validTab(String tab) {
    	return typeTabs.keySet().contains(tab);
    }
    
    public static Set<String> headers() {
    	return validHeaders;
    }

    public static Set<String> tabs() {
    	return typeTabs.keySet();
    }
    
    public static String defaultSpreadsheet() {
    	return SD2E_DICTIONARY;
    }
    
    public static Set<String> getAllowedTypesForTab(String tab) {
    	return typeTabs.get(tab);
    }
    
    
    /** @return A string listing all valid types */
    private static String allTypes() {
        Set<String> s = new HashSet<>(componentTypes.keySet());
        s.addAll(moduleTypes.keySet());
        s.addAll(externalTypes.keySet());
        return s.toString();
    }
    
    private static boolean validateEntityType(TopLevel entity, String type) {
        if(componentTypes.containsKey(type)) {
            if(entity instanceof ComponentDefinition) {
                ComponentDefinition cd = (ComponentDefinition)entity;
                return cd.getTypes().contains(componentTypes.get(type));
            }
        } else if(moduleTypes.containsKey(type)) {
            if(entity instanceof ModuleDefinition) {
                ModuleDefinition md = (ModuleDefinition)entity;
                return md.getRoles().contains(moduleTypes.get(type));
            }
        } else if(externalTypes.containsKey(type)) {
            if(entity instanceof GenericTopLevel) {
                GenericTopLevel tl = (GenericTopLevel)entity;
                return tl.getRDFType().equals(externalTypes.get(type));
            }
        } else {
            log.info("Don't recognize type "+type);
        }
        return false;
    }
 
    /**
     * Create a new dummy object
     * @param name Name of the new object, which will also be converted to a displayID and URI
     * @param type 
     * @return
     * @throws Exception 
     */
    private static SBOLDocument createStubOfType(String name, String type) throws SBOLValidationException, SynBioHubException, SBOLConversionException {
        SBOLDocument document = SynBioHubAccessor.newBlankDocument();
        String displayId = SynBioHubAccessor.sanitizeNameToDisplayID(name);
        TopLevel tl = null;
        if(componentTypes.containsKey(type)) {
            log.info("Creating stub Component for "+name);
            ComponentDefinition cd = document.createComponentDefinition(displayId, "1", componentTypes.get(type));
            cd.createAnnotation(STUB_ANNOTATION, "true");
            tl = cd;
        } else if(moduleTypes.containsKey(type)) {
            log.info("Creating stub Module for "+name);
            ModuleDefinition m = document.createModuleDefinition(displayId, "1");
            m.addRole(moduleTypes.get(type));
            m.createAnnotation(STUB_ANNOTATION, "true");
            tl = m;
        } else if(externalTypes.containsKey(type)) {
            log.info("Creating definition placeholder for "+name);
            tl = document.createGenericTopLevel(displayId, "1", externalTypes.get(type));
        } else {
            log.info("Don't know how to make stub for type: "+type);
            return null;
        }
        // annotate with stub and creation information
        tl.setName(name);
        tl.createAnnotation(CREATED, xmlDateTimeStamp());
        
        // push to create now
        document.write(System.out);
        SynBioHubAccessor.update(document);
        return document;
    }
    
    /** Get current date/time in standard XML format */
    private static String xmlDateTimeStamp() {
        // Standard XML date format
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
        // return current date/time
        return sdfDate.format(new Date());
    }

    /** 
     * Remove all prior instances of an annotation and replace with the new one 
     * @throws SBOLValidationException 
     */
    private static void replaceOldAnnotations(TopLevel entity, QName key, String value) throws SBOLValidationException {
    	while(entity.getAnnotation(key)!=null) { 
            entity.removeAnnotation(entity.getAnnotation(key));
        }
        entity.createAnnotation(key, value);
    }

    /**
     * Update a single dictionary entry, assumed to be valid
     * @param e entry to be updated
     * @return true if anything has been changed
     * @throws Exception 
     */
    private static boolean update_entry(DictionaryEntry e) throws SBOLConversionException, IOException, SBOLValidationException, SynBioHubException {
        assert(e.statusCode == StatusCode.VALID);
        
        UpdateReport report = new UpdateReport();
        // This is never called unless the entry is known valid
        SBOLDocument document = null;
        boolean changed = false;
        // if the entry has no URI, create per type
        if(e.uri==null) {
            document = createStubOfType(e.name, e.type);
            if(document==null) {
                report.failure("Could not make object "+e.name, true);
                DictionaryAccessor.writeEntryNotes(e, report.toString());
                return changed;
            }
            // pull out the first (and only) element to get the URI
            e.local_uri = document.getTopLevels().iterator().next().getIdentity();
            e.uri = SynBioHubAccessor.translateLocalURI(e.local_uri);
            report.success("Created stub in SynBioHub",true);
            DictionaryAccessor.writeEntryURI(e, e.uri);
            DictionaryAccessor.writeEntryNotes(e, report.toString());
            changed = true;
        } else { // otherwise get a copy from SynBioHub
            try {
                document = SynBioHubAccessor.retrieve(e.uri);
            } catch(SynBioHubException sbhe) {
                report.failure("Could not retrieve linked object " + e.uri + " from SynBioHub", true);
                DictionaryAccessor.writeEntryNotes(e, report.toString());
                return changed;
            }
        }
        
        // Make sure we've got the entity to update in our hands:
        TopLevel entity = document.getTopLevel(e.local_uri);
        if(entity==null) {
            report.failure("Could not find or make object "+e.uri, true);
            DictionaryAccessor.writeEntryNotes(e, report.toString());
            return changed;
        }
        
        // Check if typing is valid
        if(!validateEntityType(entity,e.type)) {
            report.failure("Type does not match '"+e.type+"'", true);
        }
        
        boolean entity_is_stub = (entity.getAnnotation(STUB_ANNOTATION) != null);
        if((entity_is_stub && e.stub!=StubStatus.YES) || (!entity_is_stub && e.stub!=StubStatus.NO)) {
            e.stub = entity_is_stub ? StubStatus.YES : StubStatus.NO;
            report.note(entity_is_stub?"Stub object":"Linked with non-stub object", true);
            changed = true;
        }
        
        // update entity name if needed
        if(e.name!=null && !e.name.equals(entity.getName())) {
            entity.setName(e.name);
            changed = true;
            report.success("Name changed to '"+e.name+"'",true);
        }
        
        // if the entry has lab entries, check if they match and (re)annotate if different
        if(!e.labUIDs.isEmpty()) {
            for(String labKey : e.labUIDs.keySet()) {
                String labValue = e.labUIDs.get(labKey);
                QName labQKey = new QName("http://sd2e.org#",labKey,"sd2");
                Annotation annotation = entity.getAnnotation(labQKey);
                if(annotation==null || !labValue.equals(annotation.getStringValue())) {
                    replaceOldAnnotations(entity,labQKey,labValue);
                    changed = true;
                    report.success(labKey+" for "+e.name+" is '"+labValue+"'",true);
                }
            }
        }
        
        if(e.attribute && e.attributeDefinition!=null) {
            Set<URI> derivations = entity.getWasDerivedFroms();
            if(derivations.size()==0 || !e.attributeDefinition.equals(derivations.iterator().next())) {
                derivations.clear(); derivations.add(e.attributeDefinition);
                entity.setWasDerivedFroms(derivations);
                changed = true;
                report.success("Definition for "+e.name+" is '"+e.attributeDefinition+"'",true);
            }
        }
        
        if(changed) {
            replaceOldAnnotations(entity,MODIFIED,xmlDateTimeStamp());
            document.write(System.out);
            SynBioHubAccessor.update(document);
            DictionaryAccessor.writeEntryNotes(e, report.toString());
            if(!e.attribute) {
                DictionaryAccessor.writeEntryStub(e, e.stub);
            } else {
                if(e.attributeDefinition!=null) {
                    DictionaryAccessor.writeEntryDefinition(e, e.attributeDefinition);
                }
            }
        }
        
        return changed;
    }
    
    /**
     * Run one pass through the dictionary, updating all entries as needed
     */
    public static void maintain_dictionary() throws IOException, GeneralSecurityException, SBOLValidationException, SynBioHubException, SBOLConversionException {
        UpdateReport report = new UpdateReport();
        try {
            List<DictionaryEntry> entries = DictionaryAccessor.snapshotCurrentDictionary();
            DictionaryAccessor.validateUniquenessOfEntries("Common Name", entries);
            DictionaryAccessor.validateUniquenessOfEntries("BioFAB UID", entries);
            DictionaryAccessor.validateUniquenessOfEntries("Ginkgo UID", entries);
            DictionaryAccessor.validateUniquenessOfEntries("Transcriptic UID", entries);
            log.info("Beginning dictionary update");
            int mod_count = 0, bad_count = 0;
            for(DictionaryEntry e : entries) {
                if (e.statusCode == StatusCode.VALID) {
                    boolean modified = update_entry(e);
                    mod_count += modified?1:0;
                }
                else {
                    // if the entry is not valid, ignore it
                	UpdateReport invalidReport = new UpdateReport();
                    invalidReport.subsection("Cannot update");
                	switch (e.statusCode) {
                		case MISSING_NAME: 
                			log.info("Invalid entry, missing name, skipping");
                			invalidReport.failure("Common name is missing");
                			break;
                		case MISSING_TYPE: 
                			log.info("Invalid entry for name "+e.name+", skipping");
                			invalidReport.failure("Type is missing");
                			break;
                		case INVALID_TYPE: 
                			log.info("Invalid entry for name "+e.name+", skipping");
                			invalidReport.failure("Type must be one of "+ typeTabs.get(e.tab).toString());
                			break;
                		case DUPLICATE_VALUE: 
                			log.info("Invalid entry for name "+e.name+", skipping");
                			invalidReport.failure(e.statusLog);
                			break;
                		case SBH_CONNECTION_FAILED:
                			break;
                		case GOOGLE_SHEETS_CONNECTION_FAILED:
                			break;
					default:
						break;
                	}
                	DictionaryAccessor.writeEntryNotes(e, invalidReport.toString());
                	bad_count++;
                }
            }
            log.info("Completed certification of dictionary");
            report.success(entries.size()+" entries",true);
            report.success(mod_count+" modified",true);
            if(bad_count>0) report.failure(bad_count+" invalid",true);
        } catch(Exception e) {
            e.printStackTrace();
            report.failure("Dictionary update failed with exception of type "+e.getClass().getName(), true);
        }
        DictionaryAccessor.writeStatusUpdate("SD2 Dictionary ("+DictionaryMaintainerApp.VERSION+") "+report.toString());
        //DictionaryAccessor.exportCSV();
    }
}
