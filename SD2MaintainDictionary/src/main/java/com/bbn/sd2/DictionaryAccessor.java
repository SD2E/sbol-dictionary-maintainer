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
import java.util.Collections;
import java.util.HashMap;
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
    private final static String readColumns = "!A"+(row_offset+1)+":G";
    public static List<DictionaryEntry> snapshotCurrentDictionary() throws IOException, GeneralSecurityException {
        ensureSheetsService();
        // Go to each tab in turn, collecting entries
        List<DictionaryEntry> entries = new ArrayList<>();
        for(String tab : typeTabs.keySet()) {
            // pull the current range
            String readRange = tab+readColumns;
            ValueRange response = service.spreadsheets().values().get(spreadsheetId, readRange).execute();
            if(response.getValues()==null) continue; // skip empty sheets
            int row_index = row_offset;
            for(List<Object> value : response.getValues()) {
                entries.add(new DictionaryEntry(tab,++row_index,value,typeTabs.get(tab)));
            }
        }
        return entries;
    }
    
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
     * Write the URI of the entry in row i
     * @param i  absolute index of row (including header rows)
     * @param uri definitive location for dictionary entry definition
     * @throws IOException
     */
    public static void writeEntryURI(DictionaryEntry e, URI uri) throws IOException {
        writeLocationText(e.tab+"!C"+e.row_index, uri.toString());
    }
    
    /**
     * Write the URI of the entry in row i
     * @param i  absolute index of row (including header rows)
     * @param uri definitive location for dictionary entry definition
     * @throws IOException
     */
    public static void writeEntryStub(DictionaryEntry e, boolean stub) throws IOException {
        writeLocationText(e.tab+"!G"+e.row_index, stub?"Stub":"");
    }
    
    /**
     * Write the URI of the entry in row i
     * @param i  absolute index of row (including header rows)
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