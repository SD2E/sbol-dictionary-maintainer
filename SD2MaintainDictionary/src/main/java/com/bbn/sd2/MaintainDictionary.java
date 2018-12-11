package com.bbn.sd2;


import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Logger;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import org.sbolstandard.core2.Annotation;
import org.sbolstandard.core2.Collection;
import org.sbolstandard.core2.ComponentDefinition;
import org.sbolstandard.core2.GenericTopLevel;
import org.sbolstandard.core2.ModuleDefinition;
import org.sbolstandard.core2.SBOLConversionException;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.SBOLValidationException;
import org.sbolstandard.core2.TopLevel;
import org.synbiohub.frontend.SynBioHubException;

import com.bbn.sd2.DictionaryEntry.StubStatus;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.ValueRange;

/**
 * Helper class for importing SBOL into the working compilation.
 */
public final class MaintainDictionary {
    private static Logger log = Logger.getGlobal();

    private static final QName STUB_ANNOTATION = new QName("http://sd2e.org#","stub_object","sd2");
    private static final QName CREATED = new QName("http://purl.org/dc/terms/","created","dcterms");
    private static final QName MODIFIED = new QName("http://purl.org/dc/terms/","modified","dcterms");

    private static final String STAGING_DICTIONARY = "1xyFH-QqYzoswvI3pPJRlBqw9PQdlp91ds3mZoPc3wCU";

    /** The ID for the default Dictionary Spreadsheet, currently the "staging instance" */
    private static final String SD2E_DICTIONARY = STAGING_DICTIONARY;

    /** Each spreadsheet tab is only allowed to contain objects of certain types, as determined by this mapping */
    private static Map<String, Set<String>> typeTabs = new HashMap<String,Set<String>>() {{
        put("Attribute", new HashSet<>(Arrays.asList("Attribute")));
        put("Reagent", new HashSet<>(Arrays.asList("Bead", "CHEBI", "DNA", "Protein", "RNA", "Media", "Stain", "Buffer", "Solution")));
        put("Genetic Construct", new HashSet<>(Arrays.asList("DNA", "RNA")));
        put("Strain", new HashSet<>(Arrays.asList("Strain")));
        put("Protein", new HashSet<>(Arrays.asList("Protein")));
        put("Collections", new HashSet<>(Arrays.asList("Challenge Problem")));
    }};

    /** Expected headers */
    private static final Set<String> validHeaders = new HashSet<>(Arrays.asList("Common Name", "Type", "SynBioHub URI",
                "Stub Object?", "Definition URI", "Status"));

    private static final Set<String> protectedColumns = new HashSet<>(Arrays.asList("SynBioHub URI",
                "Stub Object?", "Status"));

    public static final List<String> editors = Arrays.asList("bartleyba@sbolstandard.org",
                                                             "nicholasroehner@gmail.com",
                                                             "jakebeal@gmail.com",
                                                             "weston@netrias.com",
                                                             "vaughn@tacc.utexas.edu");

    /** These columns, along with the lab UID columns, will be checked for deleted cells that
     *  cause other cells to shift up */
    private static final Set<String> shiftCheckColumns = new HashSet<>(Arrays.asList("Common Name",
                "Definition URI"));

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

    /** Classes of object that are implemented as a Collection.
     *  Currently no subtypes of Collections other than Challenge Problem are
     *  specified, though that may change in the future */
    private static Map<String,URI> collectionTypes = new HashMap<String,URI>(){{
        put("Challenge Problem",URI.create(""));
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

    public static final Set<String> headers() {
        Set<String> allValidHeaders = new HashSet<String>();

        allValidHeaders.addAll(validHeaders);
        allValidHeaders.addAll(DictionaryEntry.labUIDMap.keySet());

        return allValidHeaders;
    }

    public static final Set<String> getProtectedHeaders() {
        return protectedColumns;
    }

    public static Set<String> tabs() {
        return typeTabs.keySet();
    }

    public static String defaultSpreadsheet() {
        return SD2E_DICTIONARY;
    }

    public static String stagingSpreadsheet() {
        return STAGING_DICTIONARY;
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
        } else if(collectionTypes.containsKey(type)) {
            if (entity instanceof Collection)
                return true;
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
        } else if(collectionTypes.containsKey(type)) {
            log.info("Creating stub Collection for "+name);
            Collection c = document.createCollection(displayId, "1");
            c.createAnnotation(STUB_ANNOTATION, "true");
            tl = c;
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

        return document;
    }

    /** Get current date/time in standard XML format */
    public static String xmlDateTimeStamp() {
        // Standard XML date format
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
        // return current date/time
        return sdfDate.format(new Date());
    }

    /**
     * Clear all prior instances of an annotation and replace with the new one
     * @throws SBOLValidationException
     */
    private static void replaceOldAnnotations(TopLevel entity, QName key, String new_value) throws SBOLValidationException {
        Set<String> new_values = new HashSet<String>() {{ add(new_value); }};
        replaceOldAnnotations(entity, key, new_values);
    }

    /**
     * Clear all prior instances of an annotation and replace with a set of new annotations
     * @throws SBOLValidationException
     */
    private static void replaceOldAnnotations(TopLevel entity, QName key, Set<String> new_values) throws SBOLValidationException {
        while(entity.getAnnotation(key)!=null) {
            entity.removeAnnotation(entity.getAnnotation(key));
        }
        for (String value : new_values)
            entity.createAnnotation(key, value);
    }

    /**
     * Update a single dictionary entry, assumed to be valid
     * @param e entry to be updated
     * @return true if anything has been changed
     * @throws Exception
     */
    private static DictionaryEntry update_entry(DictionaryEntry e, List<ValueRange> valueUpdates) throws SBOLConversionException, IOException, SBOLValidationException, SynBioHubException {
        assert(e.statusCode == StatusCode.VALID);

        UpdateReport report = new UpdateReport();
        // This is never called unless the entry is known valid
        URI local_uri = null;
        DictionaryEntry originalEntry = null;

        // If the URI is null and the name is not, attempt to resolve:
        if(e.uri==null && e.name!=null) {
            try {
                e.uri = SynBioHubAccessor.nameToURI(e.name);
                if(e.uri!=null) {
                    // This is an update to the spreadsheet, but not to symBioHub,
                    // so "changed" is not updated
                    valueUpdates.add(DictionaryAccessor.writeEntryURI(e, e.uri));
                }
            } catch (SynBioHubException exception) {
                e.statusCode = StatusCode.SBH_CONNECTION_FAILED; // Don't try to make anything if we couldn't check if it exists
                exception.printStackTrace();
                log.warning("SynBioHub connection failed in trying to resolve URI to name");
                return originalEntry;
            }
        }

        // if the entry has no URI, create per type
        if(e.uri==null) {
            e.document = createStubOfType(e.name, e.type);
            if(e.document==null) {
                report.failure("Could not make object "+e.name, true);
                valueUpdates.add(DictionaryAccessor.writeEntryNotes(e, report.toString()));
                return originalEntry;
            }
            // pull out the first (and only) element to get the URI
            local_uri = e.document.getTopLevels().iterator().next().getIdentity();
            e.uri = SynBioHubAccessor.translateLocalURI(local_uri);
            report.success("Created stub in SynBioHub",true);
            valueUpdates.add(DictionaryAccessor.writeEntryURI(e, e.uri));
            e.changed = true;
        } else { // otherwise get a copy from SynBioHub
            local_uri = SynBioHubAccessor.translateURI(e.uri);
            try {
                e.document = SynBioHubAccessor.retrieve(e.uri);
                originalEntry = new DictionaryEntry(e);
            } catch(SynBioHubException sbhe) {
                report.failure("Could not retrieve linked object from SynBioHub", true);
                log.severe(sbhe.getMessage());
                valueUpdates.add(DictionaryAccessor.writeEntryNotes(e, report.toString()));
                return originalEntry;
            }
        }

        // Check if object belongs to the target Collection
        if(e.uri.equals(local_uri)) { // this condition occurs when the entry does not belong to the target collection, probably a more explicit and better way to check for it
            report.failure("Object does not belong to Dictionary collection " + SynBioHubAccessor.getCollectionID());
            valueUpdates.add(DictionaryAccessor.writeEntryNotes(e, report.toString()));
            return originalEntry;
        }

        // Make sure we've got the entity to update in our hands:
        TopLevel entity = e.document.getTopLevel(local_uri);
        if(entity==null) {
            report.failure("Could not find or make object", true);
            valueUpdates.add(DictionaryAccessor.writeEntryNotes(e, report.toString()));
            return originalEntry;
        }

        // Check if typing is valid
        if(!validateEntityType(entity,e.type)) {
            report.failure("Type does not match '"+e.type+"'", true);
        }

        // Note that the "stub" field is defined by the SynBioHub document.
        // The spreadsheet is updated to be consistent with the SynBioHub
        // document, but "changed" flag is not updated since the SynBioHub
        // document is not updated.
        if(e.attribute) {
            if(e.stub != StubStatus.UNDEFINED) {
                e.stub = StubStatus.UNDEFINED;
                valueUpdates.add(DictionaryAccessor.writeEntryStub(e, e.stub));
            }
        } else {
            boolean entity_is_stub = (entity.getAnnotation(STUB_ANNOTATION) != null);
            if((entity_is_stub && e.stub!=StubStatus.YES) || (!entity_is_stub && e.stub!=StubStatus.NO)) {
                e.stub = entity_is_stub ? StubStatus.YES : StubStatus.NO;
                valueUpdates.add(DictionaryAccessor.writeEntryStub(e, e.stub));
                report.note(entity_is_stub?"Stub object":"Linked with non-stub object", true);
            }
        }

        // update entity name if needed
        if(e.name!=null && !e.name.equals(entity.getName())) {
            if(originalEntry != null) {
                originalEntry.name = entity.getName();
            }
            entity.setName(e.name);
            e.changed = true;
            report.success("Name changed to '"+e.name+"'",true);
        }

        // if the entry has lab entries, check if they match and (re)annotate if different
        for(String labKey : e.labUIDs.keySet()) {
            QName labQKey = new QName("http://sd2e.org#",labKey,"sd2");
            String labEntry = e.labUIDs.get(labKey);
            Set<String> labIds = new HashSet<String>();
            if(labEntry != null)
                labIds.addAll(Arrays.asList(labEntry.split("\\s*,\\s*")));  // Separate by comma and whitespace
            Set<String> currentIds = new HashSet<String>();
            List<Annotation> annotations = entity.getAnnotations();
            for (Annotation ann : annotations) {
                if(ann.getQName().equals(labQKey)) {
                    currentIds.add(ann.getStringValue());
                }
            }

            if(!labIds.equals(currentIds)) {
                if(originalEntry != null) {
                    // Extract lab IDs from document
                    String originalLabIDs = null;
                    for(String labId : currentIds) {
                        if(originalLabIDs == null) {
                            originalLabIDs = labId;
                        } else {
                            originalLabIDs = originalLabIDs + "," + labId;
                        }
                    }

                    if(originalLabIDs == null) {
                        originalEntry.labUIDs.remove(labKey);
                    } else {
                        originalEntry.labUIDs.put(labKey, originalLabIDs);
                    }
                }

                replaceOldAnnotations(entity,labQKey,labIds);
                e.changed = true;
                if(labIds.size() > 0)
                    report.success(labKey+" for "+e.name+" is "+String.join(", ", labIds),true);
                else
                    report.success("Deleted lab UID", true);
            }
        }

        if(e.attribute && e.attributeDefinition!=null) {
            Set<URI> derivations = entity.getWasDerivedFroms();
            if(originalEntry != null) {
                if(derivations.size() == 0) {
                    originalEntry.attributeDefinition = null;
                } else {
                    originalEntry.attributeDefinition =
                            derivations.iterator().next();
                }
            }

            if(derivations.size()==0 || !e.attributeDefinition.equals(derivations.iterator().next())) {
                derivations.clear(); derivations.add(e.attributeDefinition);
                entity.setWasDerivedFroms(derivations);
                e.changed = true;
                report.success("Definition for "+e.name+" is '"+e.attributeDefinition+"'",true);
            }
        }

        // Update the spreadsheet with the entry notes
        valueUpdates.add(DictionaryAccessor.writeEntryNotes(e, report.toString()));

        return originalEntry;
    }

    private static Map< String, Map<String, String>> generateFieldMap(List<DictionaryEntry> entries) {
        Map<String, Map<String, String>> retVal = new TreeMap< String, Map<String, String>>();

        for(DictionaryEntry entry : entries) {
            if(entry.uri == null) {
                continue;
            }
            String uri = entry.uri.toString();

            Map<String, String> fieldMap = entry.generateFieldMap();
            retVal.put(uri, fieldMap);
        }

        return retVal;
    }

    private static void checkShifts(List<DictionaryEntry> currentEntries,
                                    List<DictionaryEntry> originalEntries) throws Exception {
        // Extract spreadsheet data into a map
        Map< String, Map<String, String>> originalEntryMap = generateFieldMap(originalEntries);

        // allShiftCheckColumns contains the headers of the columns that are
        // checked for value shifts (i.e. deleted cells)
        Set<String> allShiftCheckColumns = new HashSet<>(shiftCheckColumns);
        for(String labUIDTag : DictionaryEntry.labUIDMap.keySet()) {
            allShiftCheckColumns.add(labUIDTag);
        }

        // This code looks for deleted cells that caused the remaining
        // cells in the column to shift up.  For each column,
        // "maxShifts" defines the minimum number of cell value shifts
        // that prevent updates from being committed
        final int maxShifts = 3;
        Map<String, Integer> upShiftCounts = null;
        String tab = null;

        // Loop through spreadsheet rows
        Map<String, String> previousRowValues = null;
        for(DictionaryEntry e : currentEntries) {
            if(e.tab != tab) {
                // Starting a new tab
                tab = e.tab;
                // Keeps track of shift counts for each column in this tab
                upShiftCounts = new HashMap<String, Integer>();
                previousRowValues = null;
            }

            if(e.uri == null) {
                log.severe("Row " + e.row_index + " in tab \"" + e.tab
                                   + " is missing a uri ");
                continue;
            }

            // Find field values from last SynBioHub update
            Map<String, String> originalValues = originalEntryMap.get(e.uri.toString());
            if(originalValues == null) {
                continue;
            }

            // Generate map of field values in this spreadsheet row
            Map<String, String> currentValues = e.generateFieldMap();

            // Ensure we have the field values from the row above
            // the current row
            if(previousRowValues == null) {
                // No information about the value in the row above
                previousRowValues = currentValues;
                continue;
            }

            // Loop through columns in this row to check for value shifts
            for(String key : allShiftCheckColumns) {
                String currentValue = currentValues.get(key);
                if(currentValue == null) {
                    // This row does not contain a value in column "key"
                    continue;
                }

                String originalValue = originalValues.get(key);
                if(originalValue == null) {
                    // The value in this cell was just created, and therefore
                    // does not have previous value it changed from
                    continue;
                }

                if(originalValue.equals(currentValue)) {
                    // This value in this cell is consistent with SynBioHub
                    continue;
                }

                String previousRowValue = previousRowValues.get(key);
                if(previousRowValue == null) {
                    // No value for cell directly above
                    continue;
                }

                if(previousRowValue.equals(originalValue)) {
                    // The value has shifted up a row
                    Integer count = upShiftCounts.get(key);
                    if(count == null) {
                        upShiftCounts.put(key, 1);
                    } else {
                        upShiftCounts.put(key, count + 1);
                    }
                    count = upShiftCounts.get(key);
                    if(count == maxShifts) {
                        String errMsg = "Found potential shift in column \"" +
                                    key + "\" of tab \"" + tab + "\"";
                        log.severe(errMsg);
                        throw new Exception(errMsg);
                    }
                }

            }

            previousRowValues = currentValues;
        }
    }

    public static Color makeColor(int red, int green, int blue) {
        Color newColor = new Color();

        newColor.setAlpha(1.0f);
        newColor.setRed((float)red / 255.0f);
        newColor.setGreen((float)green / 255.0f);
        newColor.setBlue((float)blue / 255.0f);

        return newColor;
    }

    /**
     * Run one pass through the dictionary, updating all entries as needed
     */
    public static void maintain_dictionary() throws IOException, GeneralSecurityException, SBOLValidationException, SynBioHubException, SBOLConversionException {
        Color green = makeColor(0, 144, 81);
        Color red = makeColor(148, 17, 0);
        Color gray = makeColor(146, 146, 146);

        UpdateReport report = new UpdateReport();
        try {
            DictionaryAccessor.cacheSheetProperties();

            List<DictionaryEntry> currentEntries = DictionaryAccessor.snapshotCurrentDictionary();
            DictionaryAccessor.validateUniquenessOfEntries("Common Name", currentEntries);
            for(String uidTag : DictionaryEntry.labUIDMap.keySet()) {
                DictionaryAccessor.validateUniquenessOfEntries(uidTag, currentEntries);
            }

            log.info("Beginning dictionary update");
            int mod_count = 0, bad_count = 0;

            // This will contain updates to be made to the spreadsheet
            List<ValueRange> spreadsheetUpdates = new ArrayList<ValueRange>();

            // This will contain the status column formatting updates
            List<Request> statusFormattingUpdates = new ArrayList<>();

            // This will contain the spreadsheet information according to what is
            // currently in SynBioHub
            List<DictionaryEntry> originalEntries = new ArrayList<DictionaryEntry>();

            // Loop through the spreadsheet rows
            for(DictionaryEntry e : currentEntries) {
                DictionaryEntry originalEntry = null;

                if (e.statusCode == StatusCode.VALID) {
                    // At this point the spreadsheet row has passed some rudimentary
                    // sanity checks.  The following method fetches or creates the
                    // corresponding SBOL Document and updates the document according
                    // to the spreadsheet row.  The method returns the "original"
                    // spreadsheet row, based on data in SynBioHub
                    originalEntry = update_entry(e, spreadsheetUpdates);
                }

                Color statusColor;
                if(e.statusCode == StatusCode.VALID) {
                    // This row looks good
                    if(originalEntry != null) {
                        // Save original (SynBioHub) entry
                        originalEntries.add(originalEntry);
                    }

                    if(e.changed) {
                        ++mod_count;
                    }
                    statusColor = green;
                } else {
                    // There is a problem with this row
                    UpdateReport invalidReport = new UpdateReport();
                    invalidReport.subsection("Cannot update");
                    switch (e.statusCode) {
                    case MISSING_NAME:
                        log.info("Invalid entry, missing name, skipping");
                        invalidReport.failure("Common name is missing");
                        statusColor = red;
                        break;
                    case MISSING_TYPE:
                        log.info("Invalid entry for name "+e.name+", skipping");
                        invalidReport.failure("Type is missing");
                        statusColor = red;
                        break;
                    case INVALID_TYPE:
                        log.info("Invalid entry for name "+e.name+", skipping");
                        invalidReport.failure("Type must be one of "+ typeTabs.get(e.tab).toString());
                        statusColor = red;
                        break;
                    case DUPLICATE_VALUE:
                        log.info("Invalid entry for name "+e.name+", skipping");
                        invalidReport.failure(e.statusLog);
                        statusColor = red;
                        break;
                    case SBH_CONNECTION_FAILED:
                        statusColor = gray;
                        break;
                    case GOOGLE_SHEETS_CONNECTION_FAILED:
                        statusColor = gray;
                        break;
                    default:
                        statusColor = red;
                        break;
                    }

                    if(invalidReport.condition < 0) {
                        spreadsheetUpdates.add(DictionaryAccessor.writeEntryNotes(e, invalidReport.toString()));
                    }
                    bad_count++;
                }

                statusFormattingUpdates.add( e.setColor("Status", statusColor) );
            }

            // Check for deleted cells that caused column values to shift up
            // If a deleted cell if found, an exception will be thrown
            checkShifts(currentEntries, originalEntries);

            // Commit changes to SynBioHub
            for(DictionaryEntry e : currentEntries) {
                if(e.changed) {
                    URI local_uri = e.document.getTopLevels().iterator().next().getIdentity();
                    TopLevel entity = e.document.getTopLevel(local_uri);
                    replaceOldAnnotations(entity, MODIFIED,xmlDateTimeStamp());
                    //e.document.write(System.out);
                    SynBioHubAccessor.update(e.document);
                    spreadsheetUpdates.add(DictionaryAccessor.writeEntryNotes(e, report.toString()));
                    if(!e.attribute) {
                        spreadsheetUpdates.add(DictionaryAccessor.writeEntryStub(e, e.stub));
                    } else {
                        if(e.attributeDefinition!=null) {
                            spreadsheetUpdates.add(DictionaryAccessor.writeEntryDefinition(e, e.attributeDefinition));
                        }
                    }
                }
            }

            // Commit updates to spreadsheet
            if(!spreadsheetUpdates.isEmpty()) {
                log.info("Updating spreadsheet");
                DictionaryAccessor.batchUpdateValues(spreadsheetUpdates);
            }

            if(!statusFormattingUpdates.isEmpty()) {
                DictionaryAccessor.submitRequests(statusFormattingUpdates);
            }

            log.info("Completed certification of dictionary");
            report.success(currentEntries.size()+" entries",true);
            report.success(mod_count+" modified",true);
            if(bad_count>0) report.failure(bad_count+" invalid",true);

            // Delay to throttle Google requests
            Thread.sleep(30000);

            DictionaryAccessor.checkProtections();

            // Delay to throttle Google requests
            Thread.sleep(30000);
        } catch(Exception e) {
            e.printStackTrace();
            //report.failure("Dictionary update failed with exception of type "+e.getClass().getName(), true);
            report.failure("Dictionary update failed: " + e.getMessage());
        }
        DictionaryAccessor.writeStatusUpdate("SD2 Dictionary ("+DictionaryMaintainerApp.VERSION+") "+report.toString());
        //DictionaryAccessor.exportCSV();
    }
}
