package com.bbn.sd2;


import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

import org.apache.commons.cli.CommandLine;
import org.sbolstandard.core2.ComponentDefinition;

public class DictionaryAccessor {
    private static Logger log = Logger.getGlobal();
    
    private static String spreadsheetId = null;
    
    private static Sheets service = null;

    private DictionaryAccessor() {} // static-only class
    
    /** Expected headers */
    private static final List<String> validHeaders = Arrays.asList("Common Name", "Type", "SynBioHub URI", "BioFAB UID", "Ginkgo UID", "Transcriptic UID", "Stub Object?", "Definition URI"); 

    /** Type Tabs */
    private static final Map<String,String[]> typeTabs = new HashMap<String,String[]>() {{
        put("Genetic Construct",new String[]{"DNA","RNA"});
        put("Reagent",new String[]{"Bead","CHEBI","Media"});
        put("Strain",new String[]{"Strain"});
        put("Protein",new String[]{"Protein"});
        put("Attribute",new String[]{"Attribute"});
    }};
        
    /** Configure from command-line arguments */
    public static void configure(CommandLine cmd) {
        spreadsheetId = cmd.getOptionValue("gsheet_id","1oLJTTydL_5YPyk-wY-dspjIw_bPZ3oCiWiK0xtG8t3g");

    }

    /** Make a clean boot, tearing down old instance if needed */
    public static void restart() {
        service = null;
        ensureSheetsService();
    }

    // GSheets Variables:
    private static final String APPLICATION_NAME = "SD2 Maintain Dictionary";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this application
     * If modifying these scopes, delete your previously saved credentials/ folder.
     */
    private static final List<String> SCOPES = Collections.singletonList(SheetsScopes.SPREADSHEETS);
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";

    /**
     * Creates an authorized Credential object.
     * @param HTTP_TRANSPORT The network HTTP Transport.
     * @return An authorized Credential object.
     * @throws IOException If the credentials.json file cannot be found.
     */
    private static Credential getCredentials(final NetHttpTransport HTTP_TRANSPORT) throws IOException {
        // Load client secrets.
        InputStream in = DictionaryAccessor.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
        GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

        // Build flow and trigger user authorization request.
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }
    
    private static void ensureSheetsService() {
        if(service!=null) return;
        
        try {
            // Build a new authorized API client service.
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            service = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                    .setApplicationName(APPLICATION_NAME)
                    .build();
            log.info("Successfully logged into Google Sheets");
        } catch(Exception e) {
            e.printStackTrace();
            log.severe("Google Sheets connection failed");
        }
    }

    // TODO: generalize the readRange
    private final static int row_offset = 2; // number of header rows
    private final static String last_column = "G";
    public static List<DictionaryEntry> snapshotCurrentDictionary() throws Exception {
        ensureSheetsService();
        // Go to each tab in turn, collecting entries
        List<DictionaryEntry> entries = new ArrayList<>();
        for(String tab : typeTabs.keySet()) {
        	Hashtable<String, Integer> header_map = getDictionaryHeaders(tab);

            // pull the current range
            String readRange = tab + "!A" + (row_offset+1) + ":" + last_column;
            ValueRange response = service.spreadsheets().values().get(spreadsheetId, readRange).execute();
            if(response.getValues()==null) continue; // skip empty sheets
            int row_index = row_offset;
            for(List<Object> value : response.getValues()) {
                entries.add(new DictionaryEntry(tab, header_map, ++row_index, value, typeTabs.get(tab)));
            }
        }
        return entries;
    }

    public static Hashtable<String, Integer> getDictionaryHeaders(String tab) throws Exception {
    	Hashtable<String, Integer> header_map = new Hashtable();
        String headerRange = tab + "!A" + (row_offset) + ":" + last_column + (row_offset);
        ValueRange response = service.spreadsheets().values().get(spreadsheetId, headerRange).execute();
        if(response.getValues()==null) return header_map; // skip empty sheets
        List<Object> headers = response.getValues().get(0);
        // TODO: validate required headers Type, Common Name, etc.
        // TODO: if header cells aren't locked, might need to check for duplicate header entries
        for(int i_h = 0; i_h < headers.size(); ++i_h) {
        	String header = headers.get(i_h).toString();
        	if (!validHeaders.contains(header))
        		throw new Exception("Invalid header " + header + " found in table " + tab);
        	header_map.put(header, i_h);
        }    	
    	return header_map;
    }

    //    // Reads one column
//    public static List<String> snapshotColumn(char column_id) throws IOException, GeneralSecurityException {
//    	String column_range = "!" + column_id + (row_offset+1) + ":" + (char)(column_id+1);
//    	List<String> cell_vals = new ArrayList<>();
//    	Sheets.Spreadsheets.Values.Get request = service.spreadsheets().values().get(spreadsheetId, column_range);
//    	request.setMajorDimension("COLUMNS");
//    	ValueRange result = request.execute();
//        List<Object> column = result.getValues().get(0);
//        for (Object cell : column) {
//        	cell_vals.add(cell.toString());
//        }
//    	return cell_vals;
//    }

    // Checks if the cell value at the given spreadsheet coordinate is duplicated elsewhere in the column
    public static void validateUniquenessOfEntries(String header_name, List<DictionaryEntry> entries) throws IOException, GeneralSecurityException {
//		System.out.println("Validating " + column_id + row_number);
    	List<String> cell_vals = new ArrayList<>();
    	
    	// Get data column-wise
    	for(DictionaryEntry e : entries) {
    		System.out.println(e.row_index);
        	switch (header_name) {
        		case "Common Name": 
        			cell_vals.add(e.name);
        			break;
        		case "BioFAB UID": 
        			cell_vals.add(e.labUIDs.get("BioFAB_UID"));
        			break;
        		case "Ginkgo UID":
        			cell_vals.add(e.labUIDs.get("Ginkgo_UID"));
        			break;
        		case "Transcriptic UID":
        			cell_vals.add(e.labUIDs.get("Transcriptic_UID"));
        			break;
        		default:
        			break;
        	}
    	}
    	for (String val : cell_vals)
    		System.out.println(val);
    	
    	// Scan columns for duplicates
    	for(DictionaryEntry e : entries) {
        	Integer cell_index = e.row_index - DictionaryAccessor.row_offset - 1;
 
        	String entry = cell_vals.get(cell_index);
        	if (entry == null)
        		continue;
        	if (entry.trim().isEmpty())  // Ignore blank cells and their duplicates
        		continue;
    	
        	for (Integer i_cell=0; i_cell < cell_vals.size(); ++i_cell) {
            	System.out.println("Validating " + entry + "\t" + cell_index + "\t" + i_cell );
        		if (i_cell.equals(cell_index))
        			continue;
        		if (cell_vals.get(i_cell) == null)
        			continue;
        		else if (cell_vals.get(i_cell).equals(entry)) {
        			System.out.println("Found duplicated " + header_name + " in rows " + e.row_index + " and " + (i_cell+DictionaryAccessor.row_offset+1));
        			e.status_code = StatusCode.DUPLICATE_VALUE;
        		}
        	}
        	System.out.println("Validated " + entry);

    	}
    	System.out.println("Validated " + header_name);
    }
    
//    // Checks if the cell value at the given spreadsheet coordinate is duplicated elsewhere in the column
//    public static boolean validateUniquenessOfEntry(char column_id, Integer row_number) throws IOException, GeneralSecurityException {
////		System.out.println("Validating " + column_id + row_number);
//
//    	Integer row_index = row_number - DictionaryAccessor.row_offset - 1;
//    	List<String> cell_vals = snapshotColumn(column_id);
//    	if (row_index >= cell_vals.size())  {
//    		return true;
//
//    		// Last row, no more entries left in this column to validate
//    	}
//    	String entry = cell_vals.get(row_index);
//    	if (entry.trim().isEmpty())  // Ignore blank cells and their duplicates
//    		return true;
//    	
//    	for (Integer i_cell=0; i_cell < cell_vals.size(); ++i_cell) {
//        	if (i_cell == row_index)
//        		continue;
//        	else if (cell_vals.get(i_cell).equals(entry)) {
//        		System.out.println("Found duplicate in " + column_id + row_number + ":" + entry);
//        		return false;
//        	}
//       	}
//    	return true;
//    }
    
    /**
     * Write text to an arbitrary single cell
     * @param writeRange location of cell
     * @param value Text to be written
     * @throws IOException
     */
    private static void writeLocationText(String writeRange, String value) throws IOException {
        ensureSheetsService();
        List<List<Object>> values = new ArrayList<>();
        List<Object> row = new ArrayList<>();
        row.add(value); values.add(row);
        ValueRange content = new ValueRange().setRange(writeRange).setValues(values);
        service.spreadsheets().values().update(spreadsheetId, writeRange, content).setValueInputOption("RAW").execute();
    }
    
    /**
     * Write the URI of the entry
     * @param e  entry to be written
     * @param uri definitive location for dictionary entry definition
     * @throws IOException
     */
    public static void writeEntryURI(DictionaryEntry e, URI uri) throws IOException {
        writeLocationText(e.tab+"!C"+e.row_index, uri.toString());
    }
    
    /**
     * Write the URI of the entry
     * @param e  entry to be written
     * @param uri definitive location for dictionary entry definition
     * @throws IOException
     */
    public static void writeEntryStub(DictionaryEntry e, boolean stub) throws IOException {
        writeLocationText(e.tab+"!G"+e.row_index, stub?"Stub":"");
    }
    
    /**
     * Write the URI of the entry 
     * @param e  entry to be written
     * @param uri definitive location for ontology source definition
     * @throws IOException
     */
    public static void writeEntryDefinition(DictionaryEntry e, URI attributeDefinition) throws IOException {
        writeLocationText(e.tab+"!G"+e.row_index, attributeDefinition.toString());
    }
    
    /**
     * Write the URI of the entry
     * @param e  entry to be written
     * @param notes string to be written
     * @throws IOException
     */
    public static void writeEntryNotes(DictionaryEntry e, String notes) throws IOException {
        writeLocationText(e.tab+"!H"+e.row_index, notes);
    }

    /**
     * Write the status at the end of each round
     * @param status string to be written
     * @throws IOException
     */
    public static void writeStatusUpdate(String status) throws IOException {
        for(String tab : typeTabs.keySet()) {
            writeLocationText(tab+"!I1", status);
        }
    }

//    public static void main(String... args) throws IOException, GeneralSecurityException {
//        List<DictionaryEntry> entries = snapshotCurrentDictionary();
//        if (entries.isEmpty()) {
//            System.out.println("No data found.");
//        } else {
//            System.out.println("Name, URI");
//            for (DictionaryEntry e : entries) {
//                System.out.printf("%s, %s\n", e.name, e.uri==null?"no URI":e.uri.toString());
//            }
//        }
//    }

}