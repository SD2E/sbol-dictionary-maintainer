package com.bbn.sd2;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.synbiohub.frontend.SynBioHubException;

public class DictionaryEntry {
    private static Logger log = Logger.getGlobal();
    public int row_index = -1; 
    public boolean valid = false;
    public String name = null;
    public String type = null;
    public URI uri = null;
    public URI local_uri = null;
    public Map<String,String> labUIDs = new HashMap<>();
    public boolean stub = false;
    
    private boolean fullbox(List<Object> row,int i) {
        return row.size()>i && row.get(i).toString().length()>0;
    }
    
    public DictionaryEntry(int row_number, List<Object> row) {
        row_index = row_number;
        valid = fullbox(row,0) && fullbox(row,1); // only valid if have both name and type
        
        if(fullbox(row,0)) name = row.get(0).toString();
        if(fullbox(row,1)) type = row.get(1).toString();
        if(fullbox(row,2)) uri = URI.create(row.get(2).toString());
        if(fullbox(row,3)) labUIDs.put("BioFAB_UID", row.get(3).toString());
        if(fullbox(row,4)) labUIDs.put("Ginkgo_UID", row.get(4).toString());
        if(fullbox(row,5)) labUIDs.put("Transcriptic_UID", row.get(5).toString());
        if(fullbox(row,6)) if(row.get(6).toString().equals("Stub")) stub=true;
        
        // If the URI is null and the name is not, attempt to resolve:
        if(uri==null && name!=null) {
            try {
                uri = SynBioHubAccessor.nameToURI(name);
                if(uri!=null) {
                    DictionaryAccessor.writeEntryURI(row_number,uri);
                }
            } catch (SynBioHubException e) {
                valid = false; // Don't try to make anything if we couldn't check if it exists
                e.printStackTrace();
                log.warning("SynBioHub connection failed in trying to resolve URI to name");
            } catch (IOException e) {
                valid = false; // Don't try to update anything if we couldn't report the URI
                e.printStackTrace();
                log.warning("Google Sheets connection failed in trying to report resolved URI");
            }
        }
        if(uri!=null) {
            local_uri = SynBioHubAccessor.translateURI(uri);
        }
    }
}
