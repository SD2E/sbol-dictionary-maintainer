package com.bbn.sd2;

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
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddProtectedRangeRequest;
import com.google.api.services.sheets.v4.model.AddSheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.ProtectedRange;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.ValueRange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Logger;

import javax.xml.namespace.QName;

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
        return execute(sheetsService.spreadsheets().get(sheetId));
    }

    public static <T> T execute(AbstractGoogleClientRequest<T> request) throws IOException {
        return DictionaryAccessor.execute(request);
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
    	DictionaryTestShared.initializeTestEnvironment("18LKKltDofXJchTv2BAAqBEiDhO466dmXVYOFnIY3gFk");
    	
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
        Spreadsheet SCRATCH_SHEET = execute(create_sheet_request);
        sheetId = SCRATCH_SHEET.getSpreadsheetId();
        log.info("Created " + SCRATCH_SHEET.getSpreadsheetId());

        // Add tabs to test sheet
        List<Request> add_sheet_requests = new ArrayList<>();
        for (String tab : MaintainDictionary.tabs()) {
            Request request = new Request().setAddSheet(new AddSheetRequest().setProperties(new SheetProperties().setTitle(tab)));
            add_sheet_requests.add(request);
        }

        // Mapping Failures tab
        {
            String tab = "Mapping Failures";
            Request request = new Request().setAddSheet(new AddSheetRequest().setProperties(new SheetProperties().setTitle(tab)));
            add_sheet_requests.add(request);
        }

        // Send requests to Google
        BatchUpdateSpreadsheetRequest update_sheet_request =
            new BatchUpdateSpreadsheetRequest().setRequests(add_sheet_requests);
        execute(sheetsService.spreadsheets().batchUpdate(sheetId, update_sheet_request));

        // Write headers to tabs
        List<ValueRange> vrList = new ArrayList<>();

        // Load Configuration file to get the LAB names for the headers
        DictionaryMaintainerApp.loadConfigFile(System.getProperty("config"));

        // Create headers list
        List<String> headers = new ArrayList<>();
        for (String header : MaintainDictionary.headers()) {
            headers.add(header);
        }

        // Create requests to add headers to each tab
        for (String tab : MaintainDictionary.tabs()) {
            String target_range = tab + "!2:2";
            vrList.add( DictionaryAccessor.writeRowText(target_range, headers) );
        }

        // Write mappig failures tab headers
        List<String> headerValues = new ArrayList<>();
        headerValues.add("Experiment/Run");
        headerValues.add("Lab");
        headerValues.add("Item Name");
        headerValues.add("Item ID");
        headerValues.add("Status");
        ValueRange vr = DictionaryAccessor.writeRowText("Mapping Failures!2:2",
                                                        headerValues);
        vrList.add(vr);

        BatchUpdateValuesRequest req = new BatchUpdateValuesRequest();
        req.setData(vrList);
        req.setValueInputOption("RAW");

        execute(sheetsService.spreadsheets().values().batchUpdate(sheetId, req));

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
        execute(sheetsService.spreadsheets().batchUpdate(sheetId, update_sheet_request));
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
            Map<String, Integer> headerMap =
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

    public boolean colorsEqual(Color c1, Color c2) {
        return MaintainDictionary.colorsEqual(c1, c2);
    }

    @Test
    public void testEntries() throws Exception {
        // Add Mapping Failures tab
        InputStream mappingFailureData =
            DictionaryAccessor.class.getResourceAsStream("/mapping_failures.csv");

        DictionaryAccessor.importTabFromCSV("Mapping Failures", mappingFailureData);

        // Extract some lab ids
        ValueRange mappingFailureIds = DictionaryAccessor.getTabData("Mapping Failures!D:D");
        List<List<Object>> mappingFailureIdValues = mappingFailureIds.getValues();

        // This set will contain the UIDs that are "resolved" from the
        // Mapping Failures tab, that is, there are the UIDs thare are
        // added to the UID column of one of the tabs.  They should be
        // subsequently removed from the Mapping Failures tab
        Set<String> resolvedUIDs = new TreeSet<>();

        assert(mappingFailureIdValues != null);

        // Populate each tab with objects
        List<ValueRange> valueUpdates = new ArrayList<ValueRange>();
        URI definitionURI = new URI("https://www.media.com/");

        for (String tab : MaintainDictionary.tabs()) {

            // Form entries
            List<List<Object>> values = new ArrayList<List<Object>>();

            Map<String, Integer> header_map = DictionaryAccessor.getDictionaryHeaders(tab);

            // Populate a dummy object for each allowed type
            int itemIdIndex = 10;
            for (String type : MaintainDictionary.getAllowedTypesForTab(tab)) {
                for(int i=0; i<2; ++i) {
                    boolean doBreak = true;

                    String[] row_entries = new String[header_map.keySet().size()];
                    for (String header : header_map.keySet()) {
                        String entry = null;
                        if (header.equals("Type")) {
                            entry = type;
                            if(type.equals("CHEBI")) {
                                doBreak = false;
                                if(i == 1) {
                                    row_entries[header_map.get("Definition URI / CHEBI ID")] = "1234";
                                }
                            } else if(type.equals("Media")) {
                                row_entries[header_map.get("Definition URI / CHEBI ID")] =
                                    definitionURI.toString();
                            }
                        } else if (header.equals("Common Name")) {
                            entry = UUID.randomUUID().toString().substring(0,6);
                        } else if (header.equals("BioFAB UID") && tab.equals("Reagent")) {
                            entry = UUID.randomUUID().toString().substring(0,6);
                            String resolvedUID = (String)mappingFailureIdValues.get(itemIdIndex).get(0);
                            if(!resolvedUIDs.contains(resolvedUID)) {
                                entry += ", " + resolvedUID;
                                resolvedUIDs.add(resolvedUID);

                                itemIdIndex += 2;
                            }
                        } else {
                            entry = "";  // Fill empty cells, null value won't work
                        }
                        row_entries[header_map.get(header)] = entry;
                    }
                    List<Object> row = new ArrayList<Object>(Arrays.asList(row_entries));
                    values.add(row);

                    if(doBreak) {
                        break;
                    }
                }
            }

            // Write entries to spreadsheet range
            String target_range = tab + "!A3";
            ValueRange body = new ValueRange()
                .setValues(values).setRange(target_range);
            valueUpdates.add(body);
        }

        // Update the spreadsheet
        DictionaryAccessor.batchUpdateValues(valueUpdates);

        testCopyTab();

        // Run the dictionary application
        DictionaryTestShared.initializeTestEnvironment(sheetId);

        // Read entries from the Reagent tab
        List<DictionaryEntry> reagentEntries =
            DictionaryAccessor.snapshotCurrentDictionary("Reagent");

        // Find the CHEBI entries
        List<DictionaryEntry> CHEBIEntries = new ArrayList<>();
        DictionaryEntry mediaEntry = null;
        DictionaryEntry solutionEntry = null;
        for(DictionaryEntry rEntry : reagentEntries) {
            if(rEntry.type.equals("CHEBI")) {
                CHEBIEntries.add(rEntry);
            }

            if(rEntry.type.equals("Media")) {
                mediaEntry = rEntry;
            }

            if(rEntry.type.equals("Solution")) {
                solutionEntry = rEntry;
            }
        }

        assert(mediaEntry != null);
        assert(CHEBIEntries.size() == 2);

        // Log into SynBioHub
        DictionaryTestShared.synBioHubLogin();

        // Check type of first CHEBI entry in SynBioHub
        URI expectedCHEBIURI = new URI(MaintainDictionary.CHEBIPrefix +
                                       "24431");
        assert(CHEBIEntries.get(0).attributeDefinition.equals(expectedCHEBIURI));
        URI uri = CHEBIEntries.get(0).uri;
        SBOLDocument document = SynBioHubAccessor.retrieve(uri, false);
        URI local_uri = SynBioHubAccessor.translateURI(uri);
        TopLevel entity = document.getTopLevel(local_uri);
        URI chebiURI = MaintainDictionary.getCHEBIURI(entity);
        assert(chebiURI.equals(expectedCHEBIURI));

        // Check type of second CHEBI entry in SynBioHub
        expectedCHEBIURI = new URI(MaintainDictionary.CHEBIPrefix +
                                   "1234");
        assert(CHEBIEntries.get(1).attributeDefinition.equals(expectedCHEBIURI));
        uri = CHEBIEntries.get(1).uri;
        document = SynBioHubAccessor.retrieve(uri, false);
        local_uri = SynBioHubAccessor.translateURI(uri);
        entity = document.getTopLevel(local_uri);
        chebiURI = MaintainDictionary.getCHEBIURI(entity);
        assert(chebiURI.equals(expectedCHEBIURI));

        // Check Definition URI
        uri = mediaEntry.uri;
        document = SynBioHubAccessor.retrieve(uri, false);
        local_uri = SynBioHubAccessor.translateURI(uri);
        entity = document.getTopLevel(local_uri);
        Set<URI> derivations = entity.getWasDerivedFroms();
        assert(derivations.size() > 0);
        URI derivationURI = derivations.iterator().next();
        assert(derivationURI.equals(definitionURI));

        // Check mapping failures
        ValueRange mappingFailureData1 = DictionaryAccessor.getTabData("Mapping Failures!D:E");
        mappingFailureIdValues = mappingFailureData1.getValues();
        assert(mappingFailureIdValues.size() == 174);
        for(int i=2; i<mappingFailureIdValues.size(); ++i) {
            List<Object> rowData = mappingFailureIdValues.get(i);
            String labId = (String)rowData.get(0);
            String status = (String)rowData.get(1);

            assert(!resolvedUIDs.contains(labId));
            assert(status.startsWith("Notification sent at "));
        }

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

        // Update an entry in SynBioHub
        uri = solutionEntry.uri;
        document = SynBioHubAccessor.retrieve(uri, false);

        // Translate the URI
        local_uri = SynBioHubAccessor.translateURI(uri);

        // Fetch the SBOL Document from SynBioHub
        entity = document.getTopLevel(local_uri);

        // Update Name
        entity.setName("New Name");

        QName labQKey = new QName("http://sd2e.org#", "BioFAB_UID", "sd2");
        entity.createAnnotation(labQKey, "newLabId");

        // Add a definition URI
        derivations = entity.getWasDerivedFroms();
        try {
            derivations.add(new URI("http://www.test.com"));
        } catch(Exception exception) {
        }
        entity.setWasDerivedFroms(derivations);


        // Update Last Modified Date
        QName MODIFIED = new QName("http://purl.org/dc/terms/","modified","dcterms");
        SimpleDateFormat sdfDate = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
        String modifiedDate = sdfDate.format( new Date() );

        entity.removeAnnotation(entity.getAnnotation(MODIFIED));
        entity.createAnnotation(MODIFIED, modifiedDate);

        SynBioHubAccessor.update(document);

        // Run the Dictionary
        DictionaryTestShared.initializeTestEnvironment(sheetId);

        // Fetch the entries from the Attribute tab
        List<DictionaryEntry> attributeEntries =
            DictionaryAccessor.snapshotCurrentDictionary("Attribute");

        // Fetch then entries from the Gentic Construct tab
        List<DictionaryEntry> geneticConstructEntries =
            DictionaryAccessor.snapshotCurrentDictionary("Genetic Construct");

        // Fetch then entries from the Reagent tab
        reagentEntries =
            DictionaryAccessor.snapshotCurrentDictionary("Reagent");

        // Make sure the log message records that email notifications
        // were sent for the invalid entries.  The notification times
        // are saved so to make sure no more notifications are sent
        // out the next time the dictionary is processed
        DictionaryEntry entry1 = attributeEntries.get(0);
        long notifyTime1 = entry1.lastNotifyTime.getTime();

        assert(notifyTime1 > 0);

        DictionaryEntry entry2 = geneticConstructEntries.get(0);
        long notifyTime2 = entry2.lastNotifyTime.getTime();

        assert(notifyTime2 > 0);

        // Check mapping failures.  Make sure status column did not change
        ValueRange mappingFailureData2 = DictionaryAccessor.getTabData("Mapping Failures!E:E");
        List<List<Object>> mappingFailureIdValues2 = mappingFailureData2.getValues();
        assert(mappingFailureIdValues.size() == 174);
        for(int i=2; i<mappingFailureIdValues.size(); ++i) {
            List<Object> rowData1 = mappingFailureIdValues.get(i);
            List<Object> rowData2 = mappingFailureIdValues2.get(i);
            String status1 = (String)rowData1.get(1);
            String status2 = (String)rowData2.get(0);

            assert(status1.equals(status2));
        }

        // Verify that SynBioHub updates are reflected in the spreadsheet
        DictionaryEntry reagentEntry = reagentEntries.get(9);
        assert(reagentEntry.name.equals("New Name"));
        Set<String> bioFABUIDs = reagentEntry.labUIDs.get("BioFAB_UID");
        assert(bioFABUIDs.contains("newLabId"));
        assert(reagentEntry.attributeDefinition.toString().equals("http://www.test.com/"));
        assert(reagentEntry.getModifiedDate().equals(modifiedDate));

        Color red = MaintainDictionary.redColor();
        Color green = MaintainDictionary.greenColor();

        // Retrieve formatting of the Status column in the Attribute tab
        List<CellFormat> cellFormatList = DictionaryAccessor.getColumnFormatting("Genetic Construct", "Status");
        assert(cellFormatList.size() >= 4);

        // The text in row 3 of the attribute tab should be red since the common name is empty
        Color textColor = cellFormatList.get(2).getTextFormat().getForegroundColor();
        assert(colorsEqual(textColor, red));

        // The text in row 4 of the attribute tab should be green
        textColor = cellFormatList.get(3).getTextFormat().getForegroundColor();
        assert(colorsEqual(textColor, green));

        // Retrieve formatting of the Status column in the Genetic Construct tab
        cellFormatList = DictionaryAccessor.getColumnFormatting("Attribute", "Status");
        assert(cellFormatList.size() >= 3);

        // The text in row 3 of the attribute tab should be red since the type
        // value is an illegal value
        textColor = cellFormatList.get(2).getTextFormat().getForegroundColor();
        assert(colorsEqual(textColor, red));

        // Check the protections
        validateProtections();

        // Log into SynBioHub
        DictionaryTestShared.synBioHubLogin();

        // Translate the URI
        local_uri = SynBioHubAccessor.translateURI(new URI(updateUri));

        // Fetch the SBOL Document from SynBioHub
        document = SynBioHubAccessor.retrieve(new URI(updateUri), false);
        entity = document.getTopLevel(local_uri);

        // Make sure name was updated in SynBioHub
        if(!entity.getName().equals(updatedName)) {
            throw new Exception("Update Value Test Failed");
        }

        // Delete a cell
        String deleteColumn = "BioFAB UID";

        DictionaryAccessor.deleteCellShiftUp("Reagent", deleteColumn, 5);

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

        // Make sure the entry error notification time was not
        // updated.  This shows that another email message was not
        // generated.
        // Fetch the entries from the Attribute tab
        attributeEntries =
            DictionaryAccessor.snapshotCurrentDictionary("Attribute");

        DictionaryEntry entry1_run2 = attributeEntries.get(0);
        long notifyTime1_run2 = entry1_run2.lastNotifyTime.getTime();

        assert(notifyTime1 == notifyTime1_run2);

        DictionaryEntry entry2_run2 = geneticConstructEntries.get(0);
        long notifyTime2_run2 = entry2_run2.lastNotifyTime.getTime();

        assert(notifyTime2 == notifyTime2_run2);
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
                execute(service.files().delete(sheetId));
                log.info("Successfully deleted scratch sheet " + sheetId);
            } catch (IOException e) {
                log.info("An error tearing down scratch sheet occurred: " + e);
            }
        }
        SynBioHubAccessor.logout();
    }
}
