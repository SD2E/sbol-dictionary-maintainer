package com.bbn.sd2;

import java.io.IOException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.synbiohub.frontend.SynBioHubException;

public class DictionaryEntry {
    private static Logger log = Logger.getGlobal();
    public String tab = null;
    public String[] allowedTypes = null; // What sort of type is this allowed to be
    public int row_index = -1; 
    public StatusCode status_code = StatusCode.VALID;
    public String name = null;
    public String type = null;
    public URI uri = null;
    public URI local_uri = null;
    public Map<String,String> labUIDs = new HashMap<>();
    public boolean stub = false;
    public boolean attribute = false;
    public URI attributeDefinition = null;
    
    private boolean fullbox(List<Object> row,int i) {
        return row.size()>i && row.get(i).toString().length()>0;
    }
    
    public DictionaryEntry(String tab, int row_number, List<Object> row, String[] allowedTypes) throws IOException, GeneralSecurityException {
        this.tab = tab;
        this.allowedTypes = allowedTypes;
        row_index = row_number;
        
        if (fullbox(row,0)) {
            name = row.get(0).toString();
            if (!DictionaryAccessor.validateUniquenessOfEntry('A', row_number))
            	status_code = StatusCode.DUPLICATE_VALUE;
        }
        else
          	status_code = StatusCode.MISSING_NAME;
        
        if(fullbox(row,1)) {
        	type = row.get(1).toString();
        	// if type is restricted, watch out for it
            if(!validType()) 
            	status_code = StatusCode.INVALID_TYPE; 
        }
        else
            status_code = StatusCode.MISSING_TYPE;
        

        if("Attribute".equals(type)) attribute = true; // check if it's an attribute
        if(fullbox(row,2)) uri = URI.create(row.get(2).toString());
        if(fullbox(row,3)) {
        	labUIDs.put("BioFAB_UID", row.get(3).toString());
        	if (!DictionaryAccessor.validateUniquenessOfEntry('D', row_number))
                status_code = StatusCode.DUPLICATE_VALUE;
        }
        if(fullbox(row,4)) {
        	labUIDs.put("Ginkgo_UID", row.get(4).toString());
        	if (!DictionaryAccessor.validateUniquenessOfEntry('E', row_number))
                status_code = StatusCode.DUPLICATE_VALUE;
        };
        if(fullbox(row,5)) {
        	labUIDs.put("Transcriptic_UID", row.get(5).toString());
        	if (!DictionaryAccessor.validateUniquenessOfEntry('F', row_number))
                status_code = StatusCode.DUPLICATE_VALUE;
        }
        if(fullbox(row,6)) if(row.get(6).toString().equals("Stub")) stub=true;
        
        // If the URI is null and the name is not, attempt to resolve:
        if(uri==null && name!=null) {
            try {
                uri = SynBioHubAccessor.nameToURI(name);
                if(uri!=null) {
                    DictionaryAccessor.writeEntryURI(this,uri);
                }
            } catch (SynBioHubException e) {
                status_code = StatusCode.SBH_CONNECTION_FAILED; // Don't try to make anything if we couldn't check if it exists
                e.printStackTrace();
                log.warning("SynBioHub connection failed in trying to resolve URI to name");
            } catch (IOException e) {
                status_code = StatusCode.GOOGLE_SHEETS_CONNECTION_FAILED; // Don't try to update anything if we couldn't report the URI
                e.printStackTrace();
                log.warning("Google Sheets connection failed in trying to report resolved URI");
            }
        }
        if(uri!=null) {
            local_uri = SynBioHubAccessor.translateURI(uri);
        }
    }

    public boolean validType() {
        if(allowedTypes==null) return true; // if we don't have restrictions, don't worry about it
        for(String type : allowedTypes) {
            if(type.equals(this.type)) 
                return true;
        }
        return false;
    }

    public String allowedTypes() {
        String s = "";
        if(allowedTypes.length==0) s+="(INTERNAL ERROR: no valid types available)";
        if(allowedTypes.length>1) s+="one of ";
        for(int i=0;i<allowedTypes.length;i++) {
            if(i>0 && allowedTypes.length>2) s+= ", ";
            if(i>0 && i==allowedTypes.length-1) s+="or ";
            s+="'"+allowedTypes[i]+"'";
        }
        return s;
    }
}
