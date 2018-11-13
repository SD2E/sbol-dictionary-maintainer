package com.bbn.sd2;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.synbiohub.frontend.SynBioHubException;

public class DictionaryEntry {
    private static Logger log = Logger.getGlobal();
    public String tab = null;
    public int row_index = -1; 
    public StatusCode statusCode = StatusCode.VALID;
    public String statusLog = null;  // Store additional info for the user, such as when comparing entries across columns
    public String name = null;
    public String type = null;
    public URI uri = null;
    public URI local_uri = null;
    public Map<String,String> labUIDs = new HashMap<>();
    public enum StubStatus { YES, NO, UNDEFINED };
    public StubStatus stub = StubStatus.UNDEFINED;
    public boolean attribute = false;
    public URI attributeDefinition = null;
    public Hashtable<String, Integer> header_map;
    public static Map<String, String> labUIDMap =
        new TreeMap<String, String>() {
            private static final long serialVersionUID = 1L;
            {
                put("BioFAB UID", "BioFAB_UID");
                put("Ginkgo UID", "Ginkgo_UID");
                put("Transcriptic UID", "Transcriptic_UID");
                put("EmeraldCloud UID", "EmeraldCloud_UID");
                put("LBNL UID", "LBNL UID");
            }
        };
    
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

            if(fullbox(row, column))
                labUIDs.put(uidTag, row.get(header_map.get(uidLabel)).toString());
             else
                labUIDs.put(uidTag, null);
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

        // If the URI is null and the name is not, attempt to resolve:
        if(uri==null && name!=null) {
            try {
                uri = SynBioHubAccessor.nameToURI(name);
                if(uri!=null) {
                    DictionaryAccessor.writeEntryURI(this,uri);
                }
            } catch (SynBioHubException e) {
            	statusCode = StatusCode.SBH_CONNECTION_FAILED; // Don't try to make anything if we couldn't check if it exists
                e.printStackTrace();
                log.warning("SynBioHub connection failed in trying to resolve URI to name");
            } catch (IOException e) {
            	statusCode = StatusCode.GOOGLE_SHEETS_CONNECTION_FAILED; // Don't try to update anything if we couldn't report the URI
                e.printStackTrace();
                log.warning("Google Sheets connection failed in trying to report resolved URI");
            }
        }
        if(uri!=null) {
            local_uri = SynBioHubAccessor.translateURI(uri);
        }
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
