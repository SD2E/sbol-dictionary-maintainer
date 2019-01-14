package com.bbn.sd2;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import org.sbolstandard.core2.SBOLDocument;

import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.Request;

public class DictionaryEntry {
    private static Logger log = Logger.getGlobal();
    public String tab = null;
    public int row_index = -1;
    public StatusCode statusCode = StatusCode.VALID;
    public String statusLog = null;  // Store additional info for the user, such as when comparing entries across columns
    public String name = null;
    public String type = null;
    public URI uri = null;
    public Map<String,Set<String>> labUIDs = new HashMap<>();
    public enum StubStatus { YES, NO, UNDEFINED };
    public StubStatus stub = StubStatus.UNDEFINED;
    public boolean attribute = false;
    public URI attributeDefinition = null;
    public Hashtable<String, Integer> header_map;
    public boolean changed = false;
    public SBOLDocument document = null;
    public Color statusColor;
    public UpdateReport report = new UpdateReport();
    public static Map<String, String> labUIDMap =
        new TreeMap<String, String>() {
            private static final long serialVersionUID = 1L;
            {
                put("BioFAB UID", "BioFAB_UID");
                put("Ginkgo UID", "Ginkgo_UID");
                put("Transcriptic UID", "Transcriptic_UID");
                put("EmeraldCloud UID", "EmeraldCloud_UID");
                put("LBNL UID", "LBNL_UID");
            }
        };
    public static Map<String, String> reverseLabUIDMap = generateReverseLabUIDMap();

    private static Map<String, String> generateReverseLabUIDMap() {
        Map<String, String> reverseMap = new TreeMap<String, String>();

        for(String key : labUIDMap.keySet()) {
            reverseMap.put(labUIDMap.get(key), key);
        }

        return reverseMap;
    }

    public String stubString() {
        switch(stub) {
        case YES:
            return "YES";

        case NO:
            return "NO";

        default:
            return "";
        }

    }

    public DictionaryEntry(DictionaryEntry src) {
        row_index = src.row_index;
        statusCode = src.statusCode;
        statusLog = src.statusLog;
        name = src.name;
        type = src.type;
        uri = src.uri;

        labUIDs = new HashMap<>();
        for(String key : src.labUIDs.keySet()) {
            Set<String> uidSet = null;
            Set<String> srcSet = src.labUIDs.get(key);

            if(srcSet != null) {
                uidSet = new TreeSet<>();

                for(String labUID : srcSet) {
                    uidSet.add(labUID);
                }
            }

            labUIDs.put(key, uidSet);
        }

        stub = src.stub;
        attribute = src.attribute;
        attributeDefinition = src.attributeDefinition;
        header_map = src.header_map;
        changed = src.changed;
        // Should this be a deep copy?
        document = src.document;
    }

    private boolean fullbox(List<Object> row,int i) {
        return row.size()>i && row.get(i).toString().length()>0;
    }

    public DictionaryEntry(String tab, Hashtable<String, Integer> header_map, int row_number, List<Object> row) throws IOException, GeneralSecurityException {
        this.tab = tab;
        row_index = row_number;

        if (fullbox(row, header_map.get("Common Name")))
            name = row.get(header_map.get("Common Name")).toString();
        else
            statusCode = StatusCode.MISSING_NAME;
        log.info("Scanning entry " + name);

        if(fullbox(row, header_map.get("Type"))) {
            type = row.get(header_map.get("Type")).toString();
            // if type is restricted, watch out for it
            if(!MaintainDictionary.validType(tab, type))
                statusCode = StatusCode.INVALID_TYPE;
        }
        else
            statusCode = StatusCode.MISSING_TYPE;


        if("Attribute".equals(type)) attribute = true; // check if it's an attribute
        if(fullbox(row, header_map.get("SynBioHub URI"))) uri = URI.create(row.get(header_map.get("SynBioHub URI")).toString());

        for(String uidLabel : labUIDMap.keySet()) {
            String uidTag = labUIDMap.get(uidLabel);

            Integer column = header_map.get(uidLabel);
            if(column == null) {
                continue;
            }

            if(fullbox(row, column)) {
                String cellValue = (String)row.get(header_map.get(uidLabel));
                Set<String> uidSet = new TreeSet<>();
                uidSet.addAll(Arrays.asList(cellValue.split("\\s*,\\s*")));
                labUIDs.put(uidTag, uidSet);
            } else {
                labUIDs.put(uidTag, null);
            }
        }

        if (header_map.get("Stub Object?") != null && fullbox(row, header_map.get("Stub Object?"))) {
            String value = row.get(header_map.get("Stub Object?")).toString();
            if(value.equals("YES")) {
                stub = StubStatus.YES;
            } else if(value.equals("NO")) {
                stub = StubStatus.NO;
            }
        }

        this.header_map = header_map;
    }

    public Map<String, String> generateFieldMap() {
        Map<String, String> fieldMap = new TreeMap<String, String>();

        // Add Lab UIDs
        for(String key : labUIDs.keySet()) {
            String uidsString = null;

            Set<String> uidSet = labUIDs.get(key);

            List<String> uidList = new ArrayList<>();
            if(uidSet != null) {
                uidList.addAll(uidSet);
            }

            if(uidList.isEmpty()) {
                uidsString = "";
            } else {
                // The uid string is a comma-separated list.
                // Sort the list so the string can be compared later
                Collections.sort(uidList);
                for(String uidElement : uidList) {
                    if(uidsString == null) {
                        uidsString = uidElement;
                    } else {
                        uidsString = uidsString + ", " + uidElement;
                    }
                }
            }

            fieldMap.put(reverseLabUIDMap.get(key), uidsString);
        }


        fieldMap.put("Common Name", name);
        fieldMap.put("Stub Object?", stubString());
        fieldMap.put("Type", type);
        if(attributeDefinition != null) {
            fieldMap.put("Definition URI", attributeDefinition.toString() );
        }

        return fieldMap;
    }

    public Set<String> itemIdsForLabUID(String labUID) {
        Set<String> itemIds = new TreeSet<>();

        String key = labUID + " UID";
        String uidKey = labUIDMap.get(key);

        if(uidKey != null) {
            Set<String> _itemIds = labUIDs.get(uidKey);

            if(_itemIds != null) {
                itemIds.addAll( _itemIds );
            }
        }

        return itemIds;
    }

    public Request setColor(String columnName, Color color) throws Exception {
        char col = (char) ('A' + header_map.get(columnName));

        Integer sheetId =
            DictionaryAccessor.getCachedSheetProperties(tab).getProperties().getSheetId();

        return DictionaryAccessor.setStatusColor(this.row_index - 1, col, sheetId, color);
    }

//    public boolean validType() {
//        if(allowedTypes==null) return true; // if we don't have restrictions, don't worry about it
//        for(String type : allowedTypes) {
//            if(type.equals(this.type))
//                return true;
//        }
//        return false;
//    }
//
//    public String allowedTypes() {
//        String s = "";
//        if(allowedTypes.length==0) s+="(INTERNAL ERROR: no valid types available)";
//        if(allowedTypes.length>1) s+="one of ";
//        for(int i=0;i<allowedTypes.length;i++) {
//            if(i>0 && allowedTypes.length>2) s+= ", ";
//            if(i>0 && i==allowedTypes.length-1) s+="or ";
//            s+="'"+allowedTypes[i]+"'";
//        }
//        return s;
//    }
}
