package com.bbn.sd2;

import static org.junit.Assert.*;

import org.apache.commons.cli.CommandLine;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.sbolstandard.core2.SBOLDocument;
import org.sbolstandard.core2.TopLevel;

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
import com.google.api.services.sheets.v4.model.AddProtectedRangeRequest;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.AppendValuesResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.DeleteSheetRequest;
import com.google.api.services.sheets.v4.model.Editors;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.ProtectedRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.SpreadsheetProperties;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.Set;
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

        // Send requests to Google
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
        //              DictionaryMaintainerApp.main(options);
    }

    // Adds an extra protection range on one of the tabs
    private void addExtraProtection(String tab) throws Exception {
        Sheet sheet = DictionaryAccessor.getSheetProperties(tab);

        ProtectedRange newRange = new ProtectedRange();

        GridRange gridRange = new GridRange();
        gridRange.setStartColumnIndex(1);
        gridRange.setEndColumnIndex(3);
        gridRange.setStartRowIndex(1);
        gridRange.setEndRowIndex(3);
        gridRange.setSheetId(sheet.getProperties().getSheetId());
        newRange.setRange(gridRange);

        AddProtectedRangeRequest addProtectedRangeRequest =
                new AddProtectedRangeRequest();
        addProtectedRangeRequest.setProtectedRange(newRange);

        Request request = new Request();
        request.setAddProtectedRange(addProtectedRangeRequest);
        List<Request> requests = new ArrayList<>();
        requests.add(request);
        BatchUpdateSpreadsheetRequest update_sheet_request =
                new BatchUpdateSpreadsheetRequest().setRequests(requests);
        sheetsService.spreadsheets().batchUpdate(sheetId, update_sheet_request).execute();
    }

    private void validateProtections() throws Exception {
        log.info("Validating Protections...");

        // This contains the names of the columns that should be protected
        Set<String> protectedColumnHeaders = MaintainDictionary.getProtectedHeaders();

        // This will contain the column indexes of the actual protected columns
        Set<Integer> protectedColumns = new HashSet<>();

        //  This will contain the columns indicies of the columns that should
        // be protected
        Set<Integer> expectedProtectedColumns = new HashSet<>();

        // Check the protections
        for (String tab : MaintainDictionary.tabs()) {
            // Get the protected ranges of the tap
            List<ProtectedRange> protectedRanges = DictionaryAccessor.getProtectedRanges(tab);

            // This maps the column header name to its index
            Hashtable<String, Integer> headerMap =
                    DictionaryAccessor.getDictionaryHeaders(tab);

            for(String key : headerMap.keySet()) {
                if(protectedColumnHeaders.contains(key)) {
                    expectedProtectedColumns.add(headerMap.get(key));
                }
            }

            // Verify the correct number of columns are protected
            assert(protectedRanges != null);
            assert(protectedRanges.size() == expectedProtectedColumns.size());

            for(ProtectedRange protectedRange : protectedRanges) {
                GridRange range = protectedRange.getRange();

                // Make sure protected range is a column
                assert(range.getStartRowIndex() == null);
                assert(range.getEndRowIndex() == null);
                protectedColumns.add(range.getStartColumnIndex());
            }

            // Make sure the correct columns are protected
            assert(protectedColumns.equals(expectedProtectedColumns));

            protectedColumns.clear();
            expectedProtectedColumns.clear();
        }

        log.info("Protections Validated.");
    }

    private void testCopyTab() throws Exception {
        log.info("Testing tab copy...");

        String tabToCopy = "Reagent";

        // Get the tab properties to extract the sheet id
        Sheet sheet = DictionaryAccessor.getSheetProperties(tabToCopy);
        int originalSheetId = sheet.getProperties().getSheetId();

        // Make a copy of the tab on the scratch sheet
        Set<String> tabSet = new HashSet<>();
        tabSet.add(tabToCopy);

        DictionaryAccessor.copyTabsFromOtherSpreadSheet(sheetId, sheetId, tabSet);

        sheet = DictionaryAccessor.getSheetProperties(tabToCopy);
        int newSheetId = sheet.getProperties().getSheetId();

        // If the tab copy worked, the sheet id should have changed
        assert(originalSheetId != newSheetId);

        log.info("Tab copy test succeeded");
    }

    private int colorFloatToInt(Float f) {
        if(f == null) {
                return 0;
        }

        return (int)Math.round(f * 255.0);
    }

    public boolean compareColors(Color c1, Color c2) {
        if(colorFloatToInt(c1.getRed()) != colorFloatToInt(c2.getRed())) {
                return false;
        }

        if(colorFloatToInt(c1.getBlue()) != colorFloatToInt(c2.getBlue())) {
                return false;
        }

        if(colorFloatToInt(c1.getGreen()) != colorFloatToInt(c2.getGreen())) {
                return false;
        }

        return true;
    }

    @Test
    public void testEntries() throws Exception {
        List<ValueRange> valueUpdates = new ArrayList<ValueRange>();

        // Populate each tab with objects
        for (String tab : MaintainDictionary.tabs()) {

            // Form entries
            List<List<Object>> values = new ArrayList<List<Object>>();

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
                    else if (header.equals("LBNL UID") && tab.equals("Reagent"))
                        entry = UUID.randomUUID().toString().substring(0,6) +
                            ", " + UUID.randomUUID().toString().substring(0,6);
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
                    .setValues(values).setRange(target_range);
            valueUpdates.add(body);
        }
        
        InputStream mappingFailureData =
        		DictionaryAccessor.class.getResourceAsStream("/mapping_failures.csv");

        DictionaryAccessor.importTabFromCSV("Mapping Failures", mappingFailureData);
        MaintainDictionary.processMappingFailures();
/*
        DictionaryAccessor.sendEmail("dan.sumorok@raytheon.com", "dsumorokraytheon@gmail.com", "Hello from Gmail",
        		"The quick brown fox jumps over the lazy dog!");
        */
        if(valueUpdates.size() > 0) {
        	return;
        }
        
        // Update the spreadsheet
        DictionaryAccessor.batchUpdateValues(valueUpdates);

        // Give Google a break
        Thread.sleep(20000);

        testCopyTab();

        // Give Google a break
        Thread.sleep(20000);

        // Run the dictionary application
        DictionaryTestShared.initializeTestEnvironment(sheetId);

        // Check the protections
        validateProtections();

        // Add an extra protection
        addExtraProtection("Strain");

        // Change an object name
        Integer updateRow = 4;
        String updateTab = "Genetic Construct";
        String updatedName = UUID.randomUUID().toString().substring(0,6);

        // Fetch the URI for the row
        String updateUri = DictionaryAccessor.getCellData(updateTab, "SynBioHub URI", updateRow);

        // Update the value
        DictionaryAccessor.setCellData(updateTab, "Common Name", updateRow, updatedName);

        // Delete a name
        DictionaryAccessor.setCellData("Attribute", "Common Name", 3, "");

        // Create an invalid type
        DictionaryAccessor.setCellData("Genetic Construct", "Type", 3, "Bad Type");

        // Give Google a break
        Thread.sleep(20000);

        // Run the Dictionary
        DictionaryTestShared.initializeTestEnvironment(sheetId);

        Color red = MaintainDictionary.redColor();
        Color green = MaintainDictionary.greenColor();

        // Retrieve formatting of the Status column in the Attribute tab
        List<CellFormat> cellFormatList = DictionaryAccessor.getColumnFormatting("Attribute", "Status");
        assert(cellFormatList.size() >= 4);

        // The text in row 3 of the attribute tab should be red since the common name is empty
        Color textColor = cellFormatList.get(2).getTextFormat().getForegroundColor();
        assert(compareColors(textColor, red));

        // The text in row 4 of the attribute tab should be green
        textColor = cellFormatList.get(4).getTextFormat().getForegroundColor();
        assert(compareColors(textColor, green));

        // Retrieve formatting of the Status column in the Genetic Construct tab
        cellFormatList = DictionaryAccessor.getColumnFormatting("Genetic Construct", "Status");
        assert(cellFormatList.size() >= 3);

        // The text in row 3 of the attribute tab should be red since the type
        // value is an illegal value
        textColor = cellFormatList.get(2).getTextFormat().getForegroundColor();
        assert(compareColors(textColor, red));

        // Check the protections
        validateProtections();

        // Translate the URI
        URI local_uri = SynBioHubAccessor.translateURI(new URI(updateUri));

        // Fetch the SBOL Document from SynBioHub
        SBOLDocument document = SynBioHubAccessor.retrieve(new URI(updateUri), false);
        TopLevel entity = document.getTopLevel(local_uri);

        // Make sure name was updated in SynBioHub
        if(!entity.getName().equals(updatedName)) {
            throw new Exception("Update Value Test Failed");
        }

        // Delete a cell
        String deleteColumn = "LBNL UID";

        DictionaryAccessor.deleteCellShiftUp("Reagent", deleteColumn, 5);

        // Give Google a break
        Thread.sleep(20000);

        // Run Dictionary update
        DictionaryTestShared.initializeTestEnvironment(sheetId);

        // Get sheet status
        String status = DictionaryAccessor.getCellData("Reagent", "Status", 1);

        // Look for column shift error message in sheet status
        String errMsg = "Found potential shift in column \"" +
                        deleteColumn + "\" of tab \"Reagent\"";

        if(status.indexOf(errMsg) == -1) {
            System.err.println("Cell Delete Test Failed");
            throw new Exception("Cell Delete Test Failed");
        }
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
        SynBioHubAccessor.logout();
    }
}
