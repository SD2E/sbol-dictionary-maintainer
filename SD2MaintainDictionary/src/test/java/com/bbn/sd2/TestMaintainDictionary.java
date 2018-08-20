package com.bbn.sd2;

import static org.junit.Assert.*;

import org.apache.commons.cli.CommandLine;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteSheetRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;

public class TestMaintainDictionary {
	
    private static final String APPLICATION_NAME = "SD2 Scratch Dictionary";
    private static final String CREDENTIALS_FILE_PATH = "/credentials.json";
    private static final String TOKENS_DIRECTORY_PATH = "tokens";
    private static final List<String> SCOPES = Arrays.asList(SheetsScopes.DRIVE);
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static Sheets sheetsService;
    private static String sheetId;
    private static Credential credential;
    private static Logger log = Logger.getGlobal();

    public static Spreadsheet getSratchSheet() throws IOException {
	    return sheetsService.spreadsheets().get(sheetId).execute();
    }
    
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		// Create scratch spreadsheet
		// Load credentials
	    InputStream in = DictionaryAccessor.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
	    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
	        
	    // Build flow and trigger user authorization request
	    HttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
	    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
	    			HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
	                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
	                .setAccessType("offline")
	                .build();
	    credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("owner");
	    
	    sheetsService = new Sheets.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential).setApplicationName(APPLICATION_NAME).build();
	    Sheets.Spreadsheets.Create create_sheet_request = sheetsService.spreadsheets().create(new Spreadsheet());
	    Spreadsheet SCRATCH_SHEET = create_sheet_request.execute();	    
	    sheetId = SCRATCH_SHEET.getSpreadsheetId();
	    log.info("Created " + SCRATCH_SHEET.getSpreadsheetId());

	    // Add tabs to test sheet
	    List<Request> add_sheet_requests = new ArrayList<>();
	    for (String tab : MaintainDictionary.tabs()) {
		    Request request = new Request().setAddSheet(new AddSheetRequest().setProperties(new SheetProperties().setTitle(tab)));
		    add_sheet_requests.add(request);
	    }
	    BatchUpdateSpreadsheetRequest update_sheet_request =
	            new BatchUpdateSpreadsheetRequest().setRequests(add_sheet_requests);
	    sheetsService.spreadsheets().batchUpdate(sheetId, update_sheet_request).execute();
	    
	    // Write headers to tabs
		List<Object> headers = new ArrayList<Object>();
		for (String header : MaintainDictionary.headers()) {
			headers.add(header);
		}
		List<List<Object>> values = new ArrayList<List<Object>>();
		values.add(headers);
		for (String tab : MaintainDictionary.tabs()) {
			String target_range = tab + "!A2";
			ValueRange body = new ValueRange()
			        .setValues(values);
			AppendValuesResponse result =
					sheetsService.spreadsheets().values().append(sheetId, target_range, body)
			                .setValueInputOption("RAW")
			                .execute();
		}
	    
	    DictionaryTestShared.initializeTestEnvironment(sheetId);
//		DictionaryMaintainerApp.main(options);

	}

	@Test
	public void testEntries() throws Exception {
		// Form entries
		List<List<Object>> values = new ArrayList<List<Object>>();
		
		// Populate each tab with objects
		for (String tab : MaintainDictionary.tabs()) {
        	Hashtable<String, Integer> header_map = DictionaryAccessor.getDictionaryHeaders(tab);

        	// Populate a dummy object for each allowed type
        	for (String type : MaintainDictionary.getAllowedTypesForTab(tab)) {
				String[] row_entries = new String[header_map.keySet().size()];
				for (String header : header_map.keySet()) {
					String entry = null;
					if (header.equals("Type"))
						entry = type;
					else if (header.equals("Common Name"))
						entry = UUID.randomUUID().toString().substring(0,6);
					else
						entry = "";  // Fill empty cells, null value won't work
					row_entries[header_map.get(header)] = entry;
				}
				List<Object> row = new ArrayList<Object>(Arrays.asList(row_entries));
				values.add(row);
			}
			
			// Write entries to spreadsheet range
			String target_range = tab + "!A3";
			ValueRange body = new ValueRange()
			        .setValues(values);
			AppendValuesResponse result =
					sheetsService.spreadsheets().values().append(sheetId, target_range, body)
			                .setValueInputOption("RAW")
			                .execute();
			System.out.printf("%d cells appended.", result.getUpdates().getUpdatedCells());
			values.clear();
		}
	    DictionaryTestShared.initializeTestEnvironment(sheetId);

	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		// Delete scratch sheet
		if (System.getProperty("c") != null && System.getProperty("c").toLowerCase().equals("true")) {
			log.info("Tearing down test Dictionary");		
		    InputStream in = DictionaryAccessor.class.getResourceAsStream(CREDENTIALS_FILE_PATH);
		    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));
			HttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
			GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
	                HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, Arrays.asList(DriveScopes.DRIVE_FILE))
	                .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
	                .setAccessType("offline")
	                .build();
	        credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("owner");
			Drive service = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
	                .setApplicationName(APPLICATION_NAME)
	                .build();
				try {
					service.files().delete(sheetId).execute();
					log.info("Successfully deleted scratch sheet " + sheetId);
				} catch (IOException e) {
					log.info("An error tearing down scratch sheet occurred: " + e);
				}	
		}
	}
}
