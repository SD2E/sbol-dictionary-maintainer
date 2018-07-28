package com.bbn.sd2;


import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
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
import org.sbolstandard.core2.ModuleDefinition;
import org.sbolstandard.core2.SBOLConversionException;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.TopLevel;
import org.synbiohub.frontend.SynBioHubException;

/**
 * Helper class for importing SBOL into the working compilation.
 */
public final class MaintainDictionary {
    private static Logger log = Logger.getGlobal();
    
    private static Map<String,URI> componentTypes = new HashMap<String,URI>() {{
        put("Bead",URI.create("http://purl.obolibrary.org/obo/NCIT_C70671")); 
        put("CHEBI",ComponentDefinition.SMALL_MOLECULE); 
        put("DNA",ComponentDefinition.DNA); 
        put("Protein",ComponentDefinition.PROTEIN); 
        put("RNA",ComponentDefinition.RNA); 
    }};
    
    private static Map<String,URI> moduleTypes = new HashMap<String,URI>(){{
        put("Strain",URI.create("http://purl.obolibrary.org/obo/NCIT_C14419")); 
        put("Media",URI.create("http://purl.obolibrary.org/obo/OBI_0000079")); 
    }};
    
    /**
     * @param type String naming a type
     * @return true if we know how to handle entries of this type
     */
    private static boolean validType(String type) {
        return componentTypes.containsKey(type) || moduleTypes.containsKey(type);
    }
    
    /** @return A string listing all valid types */
    private static String allTypes() {
        Set<String> s = new HashSet<>(componentTypes.keySet());
        s.addAll(moduleTypes.keySet());
        return s.toString();
    }
    
    /**
     * Create a new dummy object
     * @param name Name of the new object, which will alo be converted to a displayID and URI
     * @param type 
     * @return
     * @throws SBOLValidationException
     * @throws SynBioHubException
     */
    private static SBOLDocument createDummyOfType(String name, String type) throws SBOLValidationException, SynBioHubException {
        SBOLDocument document = SynBioHubAccessor.newBlankDocument();
        String displayId = SynBioHubAccessor.sanitizeNameToDisplayID(name);
        if(componentTypes.containsKey(type)) {
            log.info("Creating dummy Component for "+name);
            ComponentDefinition cd = document.createComponentDefinition(displayId, "1", componentTypes.get(type));
            cd.setName(name);
            cd.createAnnotation(new QName("http://sd2e.org#","stub_object","sd2"), "true");
        } else if(moduleTypes.containsKey(type)) {
            log.info("Creating dummy Module for "+name);
            ModuleDefinition m = document.createModuleDefinition(displayId, "1");
            m.addRole(moduleTypes.get(type));
            m.setName(name);
            m.createAnnotation(new QName("http://sd2e.org#","stub_object","sd2"), "true");
        } else {
            log.info("Don't know how to make type: "+type);
            return null;
        }
        
        return document;
    }
    
    private static boolean update_entry(DictionaryEntry e) throws SBOLConversionException, IOException, SBOLValidationException, SynBioHubException {
        UpdateReport report = new UpdateReport();
        // This is never called unless the entry is known valid
        SBOLDocument document = null;
        boolean changed = false;
        // if the entry has no URI, create per type
        if(e.uri==null) {
            document = createDummyOfType(e.name, e.type);
            // pull out the first (and only) element to get the URI
            e.local_uri = document.getTopLevels().iterator().next().getIdentity();
            e.uri = SynBioHubAccessor.translateLocalURI(e.local_uri);
            report.success("Created stub in SynBioHub",true);
            DictionaryAccessor.writeEntryURI(e.row_index, e.uri);
            changed = true;
        } else { // otherwise get a copy from SynBioHub
            document = SynBioHubAccessor.retrieve(e.uri);
        }
        
        // if the entry has lab entries, check if they match and (re)annotate if different
        if(!e.labUIDs.isEmpty()) {
            TopLevel entity = document.getTopLevel(e.local_uri);
            log.info("Checking lab UIDs for "+e.name);
            for(String labKey : e.labUIDs.keySet()) {
                String labValue = e.labUIDs.get(labKey);
                QName labQKey = new QName("http://sd2e.org#",labKey,"sd2");
                Annotation annotation = entity.getAnnotation(labQKey);
                if(annotation==null || !labValue.equals(annotation.getStringValue())) {
                    log.info("Lab parameters updated for "+e.name+": "+labKey+"="+labValue);
                    if(annotation!=null) { entity.removeAnnotation(annotation); }
                    entity.createAnnotation(labQKey, labValue);
                    changed = true;
                    report.success(labKey+" for "+e.name+" is '"+labValue+"'",true);
                }
            }
        }
        if(changed) {
            document.write(System.out);
            SynBioHubAccessor.update(document);
            DictionaryAccessor.writeEntryNotes(e.row_index, report.toString());
        }
        
        return changed;
    }
    
    public static void maintain_dictionary() throws IOException, GeneralSecurityException, SBOLValidationException, SynBioHubException, SBOLConversionException {
        UpdateReport report = new UpdateReport();
        try {
            List<DictionaryEntry> entries = DictionaryAccessor.snapshotCurrentDictionary();
            log.info("Beginning dictionary update");
            int mod_count = 0, bad_count = 0;
            for(DictionaryEntry e : entries) {
                if(e.valid) {
                    boolean modified = update_entry(e);
                    mod_count += modified?1:0;
                } else {
                    // if the entry is not valid, ignore it
                    if(!e.valid) {
                        UpdateReport invalidReport = new UpdateReport();
                        log.info("Invalid entry for name "+e.name+", skipping");
                        invalidReport.subsection("Cannot update");
                        if(e.name==null) invalidReport.failure("Common name is missing");
                        if(e.type==null) { invalidReport.failure("Type is missing");
                        } else if(!validType(e.type)) {
                            invalidReport.failure("Type must be one of "+allTypes());
                        }
                        DictionaryAccessor.writeEntryNotes(e.row_index, invalidReport.toString());
                    }
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
        // TODO: report this on sheet, including N valid & invalid items, # just updated
    }
}
