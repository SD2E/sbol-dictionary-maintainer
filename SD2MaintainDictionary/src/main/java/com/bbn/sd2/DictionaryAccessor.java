package com.bbn.sd2;


import com.bbn.sd2.DictionaryEntry.StubStatus;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.DuplicateSheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Logger;

import org.apache.commons.cli.CommandLine;

public class DictionaryAccessor {
    private static Logger log = Logger.getGlobal();
    
    private static String spreadsheetId = null;
    
    private static Sheets service = null;

    private DictionaryAccessor() {} // static-only class
            
    /** Configure from command-line arguments */
    public static void configure(CommandLine cmd) {
    	spreadsheetId = cmd.getOptionValue("gsheet_id", MaintainDictionary.defaultSpreadsheet());
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
    public static List<DictionaryEntry> snapshotCurrentDictionary() throws Exception {
        log.info("Taking snapshot");
    	ensureSheetsService();
        // Go to each tab in turn, collecting entries
        List<DictionaryEntry> entries = new ArrayList<>();
        for(String tab : MaintainDictionary.tabs()) {
            log.info("Scanning tab " + tab);
        	Hashtable<String, Integer> header_map = getDictionaryHeaders(tab);
        	
            Collection<Integer> columns = header_map.values();

            char last_column = 'A';
            for(Integer column : columns) {
                char columnChar = (char)('A' + column);
                if(columnChar > last_column) {
                    last_column = columnChar;
                }
            }

            // Pull the current range
            String readRange = tab + "!A" + (row_offset+1) + ":" + last_column;
            ValueRange response = service.spreadsheets().values().get(spreadsheetId, readRange).execute();
            if(response.getValues()==null) {
            	log.info("No entries found on this tab");
            	continue; // skip empty sheets
            }
            int row_index = row_offset;
            
            for(List<Object> value : response.getValues()) {
                entries.add(new DictionaryEntry(tab, header_map, ++row_index, value));
            }
        }     
        log.info("Read " + entries.size());

        return entries;
    }

    public static void backup() throws IOException, GeneralSecurityException {
    	String GDRIVE_BACKUP_FOLDER = "1e3Lz-fzqZpEDKrH52Xso4bG0_y46Da1x";
        ensureSheetsService();
	    InputStream in = DictionaryAccessor.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
	    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
		HttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
		GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, Arrays.asList(DriveScopes.DRIVE_FILE))
                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
                .setAccessType("offline")
                .build();
		Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("owner");
		Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
                .setApplicationName(APPLICATION_NAME)
                .build();
			try {
				String backup_filename = drive.files().get(spreadsheetId).execute().getName();
				backup_filename += "_backup_" + MaintainDictionary.xmlDateTimeStamp();
				com.google.api.services.drive.model.File copiedFile = new com.google.api.services.drive.model.File();
				copiedFile.setName(backup_filename);
				copiedFile.setParents(Collections.singletonList(GDRIVE_BACKUP_FOLDER));
				drive.files().copy(spreadsheetId, copiedFile).execute();
				System.out.println("Successfully wrote back-up to " + backup_filename);
			} catch (IOException e) {
				e.printStackTrace();
			}	
    }
    
    public static void exportCSV() throws IOException {
        for(String tab : MaintainDictionary.tabs()) {
            String readRange = tab;
            ValueRange response = service.spreadsheets().values().get(spreadsheetId, readRange).execute();
            if(response.getValues()==null) continue; // skip empty sheets
    		File file = new File("./" + tab + ".txt");
    		if (!file.exists()) {
    			file.createNewFile();
    		}
    		OutputStream outStream = new FileOutputStream(file); 
            for(List<Object> row : response.getValues()) {
            	String csv_row = "";
            	for (Object cell : row) {
            		csv_row += cell.toString() + ",";
            	}
            	if (csv_row.length() > 1)
            		csv_row = csv_row.substring(0, csv_row.length() - 1);
            	csv_row += "\r\n";
            	outStream.write(csv_row.getBytes());
            }
            outStream.flush();
            outStream.close();
            System.out.println("Wrote " + file.getAbsolutePath());
        }
    }

        
    public static Hashtable<String, Integer> getDictionaryHeaders(String tab) throws Exception {
    	Hashtable<String, Integer> header_map = new Hashtable();
        String headerRange = tab + "!" + row_offset + ":" + row_offset;
        ValueRange response = service.spreadsheets().values().get(spreadsheetId, headerRange).execute();
        if(response.getValues()==null) return header_map; // skip empty sheets
        List<Object> headers = response.getValues().get(0);
        // TODO: validate required headers Type, Common Name, etc.
        // TODO: if header cells aren't locked, might need to check for duplicate header entries
        for(int i_h = 0; i_h < headers.size(); ++i_h) {
        	String header = headers.get(i_h).toString();
            if (MaintainDictionary.headers().contains(header)) {
                header_map.put(header, i_h);
            }
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

    /**
     * Checks if entries under the given header are unique across all sheets in the Dictionary
     * @param header_name The spreadsheet column in which to look
     * @param entries A snapshot of the dictionary
     * @throws IOException
     * @throw GeneralSecurityException
     */
    public static void validateUniquenessOfEntries(String header_name, List<DictionaryEntry> entries) throws IOException, GeneralSecurityException {
        final Map<String, String> uidMap = DictionaryEntry.labUIDMap;
        Map<String, DictionaryEntry> headers = new TreeMap<String, DictionaryEntry>();
        boolean commonName = false;
        String uidTag = null;

        if(header_name.equals("Common Name")) {
            commonName = true;
        } else {
            uidTag = uidMap.get(header_name);
            if(uidTag == null) {
                return;
            }
        }

    	for(int e_i = 0; e_i < entries.size(); ++e_i) {
            DictionaryEntry entry_i = entries.get(e_i);
            String val_i = null;
            if(commonName) {
                val_i = entry_i.name;
            } else {
                val_i = entry_i.labUIDs.get(uidTag);
            }

            if(val_i == null) {
                continue;
            }

            val_i = val_i.trim();
            if(val_i.isEmpty()) {
                continue;
            }

            DictionaryEntry entry_j = headers.get(val_i);
            if(entry_j != null) {
                entry_i.statusLog =
                    "Duplicate entry. Found " + val_i + " in row " + entry_j.row_index +
                    " of " + entry_j.tab + " and row " + entry_i.row_index + " of " +
                    entry_i.tab;
                entry_i.statusCode = StatusCode.DUPLICATE_VALUE;
            }
            headers.put(val_i, entry_i);
         }
    }
    
    /**
     * Write text to an arbitrary single cell
     * @param writeRange location of cell
     * @param value Text to be written
     * @return The ValueRange json object to send to the Spreadsheets server
     * @throws IOException
     */
    private static ValueRange writeLocationText(String writeRange, String value) throws IOException {
        List<Object> row = new ArrayList<>();
        row.add(value);

        List<List<Object>> values = new ArrayList<>();
        values.add(row);

        return new ValueRange().setRange(writeRange).setValues(values);
    }

    public static void batchUpdateValues(List<ValueRange> values) throws IOException {
        BatchUpdateValuesRequest req = new BatchUpdateValuesRequest();
        req.setData(values);
        req.setValueInputOption("RAW");
        service.spreadsheets().values().batchUpdate(spreadsheetId, req).execute();
    }

    /**
     * Generate Cell location string
     * @param e reference entry
     * @param header_name column header name
     * @throws IOException
     */
    private static String getCellLocation(DictionaryEntry e, String header_name) throws IOException {
        Integer colInt = e.header_map.get(header_name);

        if(colInt == null) {
            throw new IOException("Tab " + e.tab + " is missing column \""
                                  + header_name + "\"");
        }
        char col = (char) ('A' + (char)(int)colInt);

        return e.tab + "!"  + col + e.row_index;
    }

    /**
     * Write the URI of the entry
     * @param e  entry to be written
     * @param uri definitive location for dictionary entry definition
     * @throws IOException
     */
    public static ValueRange writeEntryURI(DictionaryEntry e, URI uri) throws IOException {
        String location = getCellLocation(e, "SynBioHub URI");

        return writeLocationText(location, uri.toString());
    }
    
    /**
     * Write the URI of the entry
     * @param e  entry to be written
     * @param uri definitive location for dictionary entry definition
     * @throws IOException
     */
    public static ValueRange writeEntryStub(DictionaryEntry e, StubStatus stub) throws IOException {
        return writeLocationText(getCellLocation(e, "Stub Object?"), e.stubString());
    }
    
    /**
     * Write the URI of the entry 
     * @param e  entry to be written
     * @param uri definitive location for ontology source definition
     * @throws IOException
     */
    public static ValueRange writeEntryDefinition(DictionaryEntry e, URI attributeDefinition) throws IOException {
        String location = getCellLocation(e, "Definition URI");

        return writeLocationText(location, attributeDefinition.toString());
    }
    
    /**
     * Write the URI of the entry
     * @param e  entry to be written
     * @param notes string to be written
     * @throws IOException
     */
    public static ValueRange writeEntryNotes(DictionaryEntry e, String notes) throws IOException {
        String location = getCellLocation(e, "Status");

        return writeLocationText(location, notes);
    }

    /**
     * Write the status at the end of each round
     * @param status string to be written
     * @throws IOException
     */
    public static void writeStatusUpdate(String status) throws IOException {
        List<ValueRange> updates = new ArrayList<ValueRange>();

        for(String tab : MaintainDictionary.tabs()) {
            Hashtable<String, Integer> header_map;
            try {
                header_map = getDictionaryHeaders(tab);
            } catch (Exception e) {
                throw new IOException("Failed to get dictionary headers for tab " + tab);
            }

            int colInt = header_map.get("Status");
            char col = (char) ('A' + (char)colInt);

            String location = tab + "!"  + col + "1";

            updates.add(writeLocationText(location, status));
        }

        batchUpdateValues(updates);
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
