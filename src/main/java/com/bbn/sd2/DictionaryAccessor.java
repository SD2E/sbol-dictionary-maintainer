package com.bbn.sd2;

import com.bbn.sd2.DictionaryEntry.StubStatus;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.client.googleapis.services.AbstractGoogleClientRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.Base64;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.sheets.v4.Sheets;
import com.google.api.services.sheets.v4.SheetsScopes;
import com.google.api.services.sheets.v4.model.AddProtectedRangeRequest;
import com.google.api.services.sheets.v4.model.AddProtectedRangeResponse;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateSpreadsheetResponse;
import com.google.api.services.sheets.v4.model.CopySheetToAnotherSpreadsheetRequest;
import com.google.api.services.sheets.v4.model.DeleteProtectedRangeRequest;
import com.google.api.services.sheets.v4.model.DeleteRangeRequest;
import com.google.api.services.sheets.v4.model.DeleteSheetRequest;
import com.google.api.services.sheets.v4.model.Editors;
import com.google.api.services.sheets.v4.model.GridData;
import com.google.api.services.sheets.v4.model.GridRange;
import com.google.api.services.sheets.v4.model.ProtectedRange;
import com.google.api.services.sheets.v4.model.RepeatCellRequest;
import com.google.api.services.sheets.v4.model.Request;
import com.google.api.services.sheets.v4.model.Response;
import com.google.api.services.sheets.v4.model.RowData;
import com.google.api.services.sheets.v4.model.Sheet;
import com.google.api.services.sheets.v4.model.SheetProperties;
import com.google.api.services.sheets.v4.model.Spreadsheet;
import com.google.api.services.sheets.v4.model.TextFormat;
import com.google.api.services.sheets.v4.Sheets.Spreadsheets.Values;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesRequest;
import com.google.api.services.sheets.v4.model.BatchUpdateValuesResponse;
import com.google.api.services.sheets.v4.model.CellData;
import com.google.api.services.sheets.v4.model.CellFormat;
import com.google.api.services.sheets.v4.model.Color;
import com.google.api.services.sheets.v4.model.ValueRange;
import com.google.api.services.sheets.v4.model.UpdateSheetPropertiesRequest;
import com.google.api.services.sheets.v4.model.UpdateValuesResponse;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.GmailScopes;
import com.google.api.services.gmail.model.Message;
import com.google.api.services.gmail.model.Profile;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Logger;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.util.ByteArrayDataSource;

import org.apache.commons.cli.CommandLine;

public class DictionaryAccessor {
    private static Logger log = Logger.getGlobal();

    private static String spreadsheetId = null;

    private static Sheets sheetsService = null;

    private static Gmail gmailService = null;

    private DictionaryAccessor() {} // static-only class

    private static Map<String, Sheet> cachedSheetProperties = null;

    private static Map< String, Map<String, Integer> > tab_headers = new TreeMap<>();

    private static String loggedInUser = null;

    /** Configure from command-line arguments */
    public static void configure(CommandLine cmd) {
        spreadsheetId = cmd.getOptionValue("gsheet_id", MaintainDictionary.defaultSpreadsheet());
    }

    /** Make a clean boot, tearing down old instance if needed */
    public static void restart() {
        sheetsService = null;
        ensureSheetsService();
        ensureGmailService();
    }

    // GSheets Variables:
    private static final String APPLICATION_NAME = "SD2 Maintain Dictionary";
    private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
    private static final String TOKENS_DIRECTORY_PATH = "tokens";

    /**
     * Global instance of the scopes required by this application
     * If modifying these scopes, delete your previously saved credentials/ folder.
     */
    private static final List<String> SCOPES =
        Arrays.asList(SheetsScopes.SPREADSHEETS, GmailScopes.GMAIL_COMPOSE,
                      GmailScopes.GMAIL_SEND);

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
        GoogleAuthorizationCodeFlow flow =
            new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();
        return new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("user");
    }

    private static void ensureSheetsService() {
        if(sheetsService!=null) return;

        try {
            // Build a new authorized API client service.
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            sheetsService =
                new Sheets.Builder(HTTP_TRANSPORT,
                                   JSON_FACTORY,
                                   getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

            log.info("Successfully logged into Google Sheets");
        } catch(Exception e) {
            e.printStackTrace();
            log.severe("Google Sheets connection failed");
        }
    }

    /**
     * Sends an email message using the parameters provided.
     *
     * @param to email address of the receiver
     * @param from email address of the sender, the mailbox account
     * @param subject subject of the email
     * @param bodyText body text of the email
     * @return the MimeMessage to be used to send email
     * @throws MessagingException
     */
    public static void sendEmail(String to,
                                 String cc,
                                 String subject,
                                 String bodyText,
                                 byte[] attachmentData)
        throws MessagingException, IOException {
        Properties props = new Properties();
        Session session = Session.getDefaultInstance(props, null);

        MimeMessage email = new MimeMessage(session);

        for(String recipient : to.split(";")) {
            recipient = recipient.trim();
            email.addRecipient(javax.mail.Message.RecipientType.TO,
                               new InternetAddress(recipient));
        }

        if(cc != null) {
            for(String recipient : cc.split(";")) {
                recipient = recipient.trim();
                email.addRecipient(javax.mail.Message.RecipientType.CC,
                                   new InternetAddress(recipient));
            }
        }

        email.setSubject(subject);

        BodyPart messageBodyPart = new MimeBodyPart();
        messageBodyPart.setText(bodyText);
        Multipart multipart = new MimeMultipart();
        multipart.addBodyPart(messageBodyPart);

        if(attachmentData != null) {
            messageBodyPart = new MimeBodyPart();

            DataSource source = new ByteArrayDataSource(attachmentData, "application/json");
            messageBodyPart.setDataHandler( new DataHandler(source));
            messageBodyPart.setFileName("dictionaryEntries.json");
            multipart.addBodyPart(messageBodyPart);
        }

        email.setContent(multipart);

        Message message = createMessageWithEmail(email);
        execute(gmailService.users().messages().send("me", message));
    }

    public static Profile getProfile() throws IOException {
        Gmail.Users.GetProfile getProfile = gmailService.users().getProfile("me");
        return getProfile.execute();
    }

    /**
     * Create a message from an email.
     *
     * @param emailContent Email to be set to raw of message
     * @return a message containing a base64url encoded email
     * @throws IOException
     * @throws MessagingException
     */
    private static Message createMessageWithEmail(MimeMessage emailContent)
        throws MessagingException, IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        emailContent.writeTo(buffer);
        byte[] bytes = buffer.toByteArray();
        String encodedEmail = Base64.encodeBase64URLSafeString(bytes);
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }

    private static void ensureGmailService() {
        if(gmailService!=null) return;

        try {
            // Build a new authorized API client service.
            final NetHttpTransport HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
            gmailService = new Gmail.Builder(HTTP_TRANSPORT, JSON_FACTORY, getCredentials(HTTP_TRANSPORT))
                .setApplicationName(APPLICATION_NAME)
                .build();

            Profile profile = getProfile();

            loggedInUser = profile.getEmailAddress();

            log.info("Successfully logged into Google Gmail");
        } catch(Exception e) {
            e.printStackTrace();
            log.severe("Google Gmail connection failed");
        }
    }

    // TODO: generalize the readRange
    private final static int row_offset = 2; // number of header rows
    public static List<DictionaryEntry> snapshotCurrentDictionary(String tab) throws Exception {
        log.info("Taking snapshot");
        ensureSheetsService();
        // Go to each tab in turn, collecting entries
        List<DictionaryEntry> entries = new ArrayList<>();
        log.info("Scanning tab " + tab);
        Map<String, Integer> header_map = getDictionaryHeaders(tab);

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
        ValueRange response = getTabData(readRange);
        if(response.getValues()==null) {
            log.info("No entries found on this tab");
            return entries;
        }
        int row_index = row_offset;

        for(List<Object> value : response.getValues()) {
            entries.add(new DictionaryEntry(tab, header_map, ++row_index, value));
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
        GoogleAuthorizationCodeFlow flow =
            new GoogleAuthorizationCodeFlow.Builder(HTTP_TRANSPORT, JSON_FACTORY, clientSecrets,
                                                    Arrays.asList(DriveScopes.DRIVE_FILE))
            .setDataStoreFactory(new FileDataStoreFactory(new java.io.File(TOKENS_DIRECTORY_PATH)))
            .setAccessType("offline")
            .build();
        Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver()).authorize("owner");
        Drive drive = new Drive.Builder(HTTP_TRANSPORT, JSON_FACTORY, credential)
            .setApplicationName(APPLICATION_NAME)
            .build();
        try {
            String backup_filename = execute(drive.files().get(spreadsheetId)).getName();
            backup_filename += "_backup_" + MaintainDictionary.xmlDateTimeStamp();
            com.google.api.services.drive.model.File copiedFile = new com.google.api.services.drive.model.File();
            copiedFile.setName(backup_filename);
            copiedFile.setParents(Collections.singletonList(GDRIVE_BACKUP_FOLDER));
            execute(drive.files().copy(spreadsheetId, copiedFile));
            System.out.println("Successfully wrote back-up to " + backup_filename);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void exportCSV() throws IOException {
        for(String tab : MaintainDictionary.tabs()) {
            String readRange = tab;
            ValueRange response = getTabData(readRange);
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


    public static void cacheTabHeaders(String tab) throws IOException {
        Map<String, Integer> header_map = new TreeMap<>();
        String headerRange = tab + "!" + row_offset + ":" + row_offset;
        ValueRange response = getTabData(headerRange);

        if(response.getValues()==null) {
            return; // skip empty sheets
        }

        List<Object> headers = response.getValues().get(0);
        // TODO: validate required headers Type, Common Name, etc.
        // TODO: if header cells aren't locked, might need to check for duplicate header entries
        for(int i_h = 0; i_h < headers.size(); ++i_h) {
            String header = headers.get(i_h).toString();
            if (MaintainDictionary.headers().contains(header)) {
                header_map.put(header, i_h);
            }
        }

        tab_headers.put(tab, header_map);
    }

    public static Map<String, Integer> getDictionaryHeaders(String tab) {
        return tab_headers.get(tab);
    }

    /**
     * Checks if entries under the given header are unique across all sheets in the Dictionary
     * @param header_name The spreadsheet column in which to look
     * @param entries A snapshot of the dictionary
     * @throws IOException
     * @throw GeneralSecurityException
     */
    public static void validateUniquenessOfEntries(String header_name, List<DictionaryEntry> entries) {
        final Map<String, String> uidMap = DictionaryMaintainerApp.labUIDMap;
        Map<String, DictionaryEntry> nameToEntry = new TreeMap<String, DictionaryEntry>();
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
            List<String> vals = new ArrayList<>();

            if(entry_i.statusCode != StatusCode.VALID) {
                continue;
            }

            if(commonName) {
                vals.add(entry_i.name);
            } else {
                Set<String> nameSet = entry_i.labUIDs.get(uidTag);

                if(nameSet != null) {
                    vals.addAll(nameSet);
                }
            }

            for(String val_i : vals) {
                val_i = val_i.trim();
                if(val_i.isEmpty()) {
                    continue;
                }

                DictionaryEntry entry_j = nameToEntry.get(val_i);
                if(entry_j != null) {
                    entry_i.statusLog =
                        "Duplicate entry. Found " + val_i + " in row " + entry_j.row_index +
                        " of " + entry_j.tab + " and row " + entry_i.row_index + " of " +
                        entry_i.tab;
                    entry_i.statusCode = StatusCode.DUPLICATE_VALUE;
                }
                nameToEntry.put(val_i, entry_i);
            }
        }
    }

    /**
     * Write text to an arbitrary single cell
     * @param writeRange location of cell
     * @param value Text to be written
     * @return The ValueRange json object to send to the Spreadsheets server
     * @throws IOException
     */
    public static ValueRange writeLocationText(String writeRange, String value) throws IOException {
        List<Object> row = new ArrayList<>();
        row.add(value);

        List<List<Object>> values = new ArrayList<>();
        values.add(row);

        return new ValueRange().setRange(writeRange).setValues(values);
    }

    /**
     * Write text to a row of cells
     * @param writeRange location of the row
     * @param values to be written
     * @return The ValueRange json object to send to the Spreadsheets server
     * @throws IOException
     */
    public static ValueRange writeRowText(String writeRange, List<String> rowValues) throws IOException {
        List<Object> row = new ArrayList<>();
        for(String value: rowValues) {
            row.add(value);
        }

        List<List<Object>> values = new ArrayList<>();
        values.add(row);

        return new ValueRange().setRange(writeRange).setValues(values);
    }

    public static BatchUpdateValuesResponse batchUpdateValues(List<ValueRange> values) throws IOException {
        BatchUpdateValuesRequest req = new BatchUpdateValuesRequest();
        req.setData(values);
        req.setValueInputOption("RAW");

        return execute(sheetsService.spreadsheets().values().batchUpdate(spreadsheetId, req));
    }

    public static BatchUpdateSpreadsheetResponse batchUpdateRequests(List<Request> requestList) throws IOException {
        BatchUpdateSpreadsheetRequest breq = new BatchUpdateSpreadsheetRequest();
        breq.setRequests(requestList);
        return execute(sheetsService.spreadsheets().batchUpdate(spreadsheetId, breq));
    }

    public static List<ValueRange> batchGet(List<String> ranges) throws IOException {
        Values.BatchGet request = sheetsService.spreadsheets().values().batchGet(spreadsheetId);
        request.setSpreadsheetId(spreadsheetId);
        request.setRanges(ranges);

        return execute(request).getValueRanges();
    }

    private static char columnNameToIndex(String tab, String colName) throws IOException {
        Map<String, Integer> header_map = getDictionaryHeaders(tab);

        Integer colVal = header_map.get(colName);
        if(colVal == null) {
            throw new IOException("Column " + colName + " not found on tab " + tab);
        }

        return (char)('A' + colVal);
    }

    public static void deleteCellShiftUp(String tab, String colName, int row) throws Exception  {
        char col = columnNameToIndex(tab, colName);

        String readRange = tab + "!" + col + row + ":" + col;
        ValueRange response = getTabData(readRange);

        List<List<Object>> values = response.getValues();
        if(values == null) {
            throw new IOException("No data in column \"" + colName + "\" starting at row " + row
                                  + " in tab " + tab);
        }

        for(int i=0; i<values.size()-1; ++i) {
            // Copy value from row below
            values.get(i).set(0, values.get(i+1).get(0));
        }

        values.get(values.size() - 1).set(0,  "");

        int lastRow = row + values.size() - 1;

        String writeRange = tab + "!" + col + row +
            ":" + col + lastRow;

        response.setRange(writeRange);
        setTabData(writeRange, response);
    }

    public static String getCellData(String tab, String colName, int row) throws IOException {
        char col = columnNameToIndex(tab, colName);

        String readRange = tab + "!" + col + row;

        ValueRange response = getTabData(readRange);

        List<List<Object>> values = response.getValues();

        if(values == null) {
            return "";
        }

        if(values.size() == 0) {
            return "";
        }

        List<Object> rowValues = values.get(0);

        if(rowValues.size() == 0) {
            return "";
        }

        return (String)rowValues.get(0);
    }

    public static void setCellData(String tab, String colName, int row, String value) throws IOException {
        char col = columnNameToIndex(tab, colName);

        String writeRange = tab + "!" + col + row;

        ValueRange updatedValue = new ValueRange();
        updatedValue.setRange(writeRange);

        List<Object> rowValues = new ArrayList<>();
        rowValues.add(value);

        List<List<Object>> values = new ArrayList<>();
        values.add(rowValues);

        updatedValue.setValues(values);

        setTabData(writeRange, updatedValue);
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
     * Write the last updated time stamp
     * @param e  entry to be written
     * @param time stamp string
     * @throws IOException
     */
    public static ValueRange writeLastUpdated(DictionaryEntry e, String timeStamp) throws IOException {
        String location = getCellLocation(e, "Last Updated");

        if(location == null) {
            return null;
        }

        return writeLocationText(location, timeStamp);
    }

    /**
     * Read the last updated time stamp
     * @param e  entry to be written
     * @throws IOException
     */
    public static String readLastUpdated(DictionaryEntry e) throws IOException {
        String location = getCellLocation(e, "Last Updated");

        if(location == null) {
            return null;
        }

        return getCellData(e.tab, "Last Updated", e.row_index);
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
     * Queue an update of a cell
     * @param e  entry to be written
     * @param columnTitle the column to update
     * @param cellData the new cell contents
     * @return A ValueRange object that updates the specified cell
     * @throws IOException
     */
    public static ValueRange writeCellData(DictionaryEntry e, String columnTitle,
                                           String cellData) throws IOException {
        String location = getCellLocation(e, columnTitle);

        return writeLocationText(location, cellData);
    }

    /**
     * Write the URI of the entry
     * @param e  entry to be written
     * @param uri definitive location for ontology source definition
     * @throws IOException
     */
    public static ValueRange writeEntryDefinition(DictionaryEntry e,
                                                  URI attributeDefinition) throws IOException {
        String location = getCellLocation(e, "Definition URI");

        return writeLocationText(location, attributeDefinition.toString());
    }

    /**
     * Write the Defintion URI of the entry
     * @param e  entry to be written
     * @param uri definitive location for ontology source definition
     * @throws IOException
     */
    public static ValueRange writeDefinitionImport(DictionaryEntry e,
                                                   URI attributeDefinition) throws IOException {
        final String columnHeader = "Definition Import";

        if(!e.header_map.containsKey(columnHeader)) {
            return null;
        }

        String attributeString = "";

        if(attributeDefinition != null) {
            attributeString = attributeDefinition.toString();
        }

        if(attributeString.equals(e.definitionImport)) {
            return null;
        }

        e.dictionaryEntryChanged = true;

        String location = getCellLocation(e, columnHeader);

        return writeLocationText(location, attributeString);
    }

    /**
     * Write the URI of the entry
     * @param e  entry to be written
     * @param uri definitive location for ontology source definition
     * @throws IOException
     */
    public static ValueRange writeDefinitionOrCHEBIURI(DictionaryEntry e,
                                                       URI definitionURI) throws IOException {
        String location = getCellLocation(e, "Definition URI / CHEBI ID");

        return writeLocationText(location, definitionURI.toString());
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
            Map<String, Integer> header_map;
            header_map = getDictionaryHeaders(tab);

            int colInt = header_map.get("Status");
            char col = (char) ('A' + (char)colInt);

            String location = tab + "!"  + col + "1";

            updates.add(writeLocationText(location, status));
        }

        batchUpdateValues(updates);
    }

    /**
     * Write the status at the end of each round
     * @param status string to be written
     * @throws IOException
     */
    public static void writeStatusUpdate(String tab, String status) throws IOException {
        List<ValueRange> updates = new ArrayList<ValueRange>();

        Map<String, Integer> header_map;
        header_map = getDictionaryHeaders(tab);

        int colInt = header_map.get("Status");
        char col = (char) ('A' + (char)colInt);

        String location = tab + "!"  + col + "1";

        updates.add(writeLocationText(location, status));

        batchUpdateValues(updates);
    }

    public static Request deleteRowRequest(String tab, int row) throws IOException {
        DeleteRangeRequest deleteRange = new DeleteRangeRequest();

        Sheet sheet = getCachedSheetProperties(tab);

        GridRange range = new GridRange();

        range.setStartRowIndex(row);
        range.setEndRowIndex(row + 1);
        range.setSheetId(sheet.getProperties().getSheetId());

        deleteRange.setRange(range);
        deleteRange.setShiftDimension("ROWS");

        Request req = new Request();
        req.setDeleteRange(deleteRange);

        return req;
    }

    public static void copyTabsFromOtherSpreadSheet(String srcSpreadsheetId, String dstSpreadsheetId,
                                                    Set<String> tabList) throws IOException {
        // Create request body object that specifies the destination spreadsheet id
        CopySheetToAnotherSpreadsheetRequest requestBody = new CopySheetToAnotherSpreadsheetRequest();
        requestBody.setDestinationSpreadsheetId(dstSpreadsheetId);

        // List of requests to send to Google
        ArrayList<Request> reqList = new ArrayList<Request>();

        // Lookup the sheet properties on the destination spreadsheet
        Sheets.Spreadsheets.Get get = sheetsService.spreadsheets().get(dstSpreadsheetId).setFields("sheets.properties");
        Spreadsheet s = execute(get);
        List<Sheet> sheets = s.getSheets();

        // Loop the sheets on the source source spreadsheet
        for(Sheet sheet: sheets) {
            // Get the sheet properties
            SheetProperties dstProperties = sheet.getProperties();

            // Make sure the title is in the list to be copied
            String dstSheetTitle = dstProperties.getTitle();
            if(!tabList.contains(dstSheetTitle)) {
                continue;
            }

            // This sheet on the destination spreadsheet needs to be replaced, so
            // create a request to delete it
            DeleteSheetRequest deleteSheet = new DeleteSheetRequest();
            deleteSheet.setSheetId(sheet.getProperties().getSheetId());
            Request req = new Request().setDeleteSheet(deleteSheet);
            reqList.add(req);
        }

        // Lookup the sheet properties on the source spreadsheet
        get = sheetsService.spreadsheets().get(srcSpreadsheetId).setFields("sheets.properties");
        s = execute(get);
        sheets = s.getSheets();

        // Loop the sheets on the source source spreadsheet

        for(Sheet sheet: sheets) {
            // Get the sheet properties
            SheetProperties srcProperties = sheet.getProperties();

            // Make sure the title is in the list to be copied
            String srcSheetTitle = srcProperties.getTitle();
            if(!tabList.contains(srcSheetTitle)) {
                continue;
            }

            // Copy the sheet from the source spreadsheet to the destination spreadsheet
            SheetProperties sp =
                execute(sheetsService.spreadsheets().sheets().copyTo(srcSpreadsheetId,
                                                                     srcProperties.getSheetId(),
                                                                     requestBody));

            // At this point the sheet as been copied, but the title is prepended with
            // "Copy of ".  It needs to be restored to the original title

            // Set the original title
            sp.setTitle(srcSheetTitle);

            // Create a request object to update the sheet properties
            UpdateSheetPropertiesRequest changeTitle = new UpdateSheetPropertiesRequest();
            changeTitle.setProperties(sp);
            changeTitle.setFields("Title");

            // Create a higher level request object
            Request req = new Request().setUpdateSheetProperties(changeTitle);

            // Queue the lower level request object
            reqList.add(req);
        }

        // Execute queued up sheet requests
        if(!reqList.isEmpty()) {
            BatchUpdateSpreadsheetRequest breq = new BatchUpdateSpreadsheetRequest();
            breq.setRequests(reqList);
            execute(sheetsService.spreadsheets().batchUpdate(dstSpreadsheetId, breq));
        }
    }

    // Generates a list of all the protected ranges that need to be added to this sheet
    private static void updateProtectedRanges(List<ProtectedRange> ranges,
                                              SheetProperties sheetProperties,
                                              List<Request> updateRangeRequests) throws Exception {
        Set<String> columnsToProtect =
            new TreeSet<String>(MaintainDictionary.getProtectedHeaders());

        String sheetTitle = sheetProperties.getTitle();

        // This returns a returns a map that maps a column header to a
        // column index
        Map<String, Integer> headers = getDictionaryHeaders(sheetTitle);

        // This maps a column index to a header name
        Map<Integer, String> reverseLookup = new TreeMap<Integer, String>();
        for(String header : headers.keySet()) {
            reverseLookup.put(headers.get(header), header);
        }

        Set<String> editorSet = new TreeSet<>();
        editorSet.addAll(DictionaryMaintainerApp.editors);
        editorSet.add(loggedInUser);

        for(ProtectedRange range : ranges) {
            // Extract the protection range
            Integer startColumnIndex = range.getRange().getStartColumnIndex();
            Integer endColumnIndex = range.getRange().getEndColumnIndex();
            Integer startRowIndex = range.getRange().getStartRowIndex();
            Integer endRowIndex = range.getRange().getEndRowIndex();
            String columnTitle = null;

            boolean unexpectedProtection = false;


            // First, make sure that the protection does not span multiple rows
            if((startColumnIndex == null) || (endColumnIndex == null)) {
                unexpectedProtection = true;
            } else if(endColumnIndex != (startColumnIndex + 1)) {
                unexpectedProtection = true;
            } else if(!reverseLookup.containsKey(startColumnIndex)) {
                unexpectedProtection = true;
            } else {
                columnTitle = reverseLookup.get(startColumnIndex);
                if(!columnsToProtect.contains(columnTitle) ||
                   endRowIndex != null) {
                    unexpectedProtection = true;
                }
            }

            if( unexpectedProtection ) {
                String quotedSheetTitle = "\"" + sheetTitle + "\"";
                if(endColumnIndex == null) {
                    if(startColumnIndex == null) {
                        if(startRowIndex == null) {
                            log.warning("Deleting unexpected protection on sheet " +
                                        sheetTitle);
                        } else {
                            log.warning("Deleting unexpected protection from on row " +
                                        (startRowIndex + 1) + " on sheet " + quotedSheetTitle);
                        }
                    } else {
                        char startColumn = (char) ('A' + startColumnIndex);

                        log.warning("Deleting unexpected protection from on row " +
                                    (startRowIndex + 1) + " starting from column " + startColumn +
                                    " on sheet " + quotedSheetTitle);
                    }
                } else if(endColumnIndex < 26) {
                    char startColumn = (char) ('A' + startColumnIndex);
                    char endColumn;

                    if(endColumnIndex > startColumnIndex) {
                        endColumn = (char) ('A' + endColumnIndex - 1);
                    } else {
                        endColumn = startColumn;
                    }

                    if(endRowIndex == null) {
                        if(startRowIndex == null) {
                            if(startColumn == endColumn) {
                                log.warning("Deleting unexpected protection on column " +
                                            startColumn + " on sheet " + sheetTitle);
                            } else {
                                log.warning("Deleting unexpected protection from column " +
                                            startColumn + " to column " + endColumn + " on sheet " +
                                            sheetTitle);
                            }
                        } else {
                            log.warning("Deleting unexpected protection from on column " +
                                        startColumn + " starting from row " + (startRowIndex + 1) +
                                        " on sheet " + sheetTitle);
                        }
                    } else {
                        log.warning("Deleting unexpected protection from " +
                                    startColumn + (startRowIndex + 1) +
                                    " to " + endColumn + endRowIndex +
                                    " on sheet " + sheetTitle);
                    }
                } else {
                    log.warning("Deleting unexpected protection on sheet " +
                                sheetTitle);
                }

                // Create a request to delete the protected range
                DeleteProtectedRangeRequest deleteRequest = new DeleteProtectedRangeRequest();
                deleteRequest.setProtectedRangeId(range.getProtectedRangeId());
                updateRangeRequests.add(new Request().setDeleteProtectedRange(deleteRequest));
                continue;
            }

            // Remove column from list of columns to protect since it
            // is already protected
            String columnName = reverseLookup.get(startColumnIndex);
            columnsToProtect.remove(columnName);

            // Make sure the editors list is correct
            Editors editors = range.getEditors();
            if(editors == null) {
                log.warning("Failed to get editors for sheet " + sheetTitle);
                continue;
            }

            // List of users that can edit this protected region
            List<String> columnEditors = editors.getUsers();

            // The document owner seems to be the last one in the list
            String documentOwner = columnEditors.get( columnEditors.size() - 1);
            editorSet.add(documentOwner);

            // Create a set of the current editors
            Set<String> currentEditors = new TreeSet<>();
            currentEditors.addAll( columnEditors );

            if(!currentEditors.equals(editorSet)) {
                // The editor list is not correct.  Ideally we would
                // just updated it, however we the Google request that
                // updates the editors does not seem to work properly
                // if the current user is not the editor. Therefore we
                // remove the protection and add then add it back
                // again.  This will cause the current user to
                // automatically be added to the protection.
                DeleteProtectedRangeRequest deleteRequest = new DeleteProtectedRangeRequest();
                deleteRequest.setProtectedRangeId(range.getProtectedRangeId());
                updateRangeRequests.add(new Request().setDeleteProtectedRange(deleteRequest));

                // This causes a new protection to be added
                columnsToProtect.add(columnName);
            }
        }

        List<String> editorList = new ArrayList<>();
        editorList.addAll(editorSet);

        // Create a list of all the protected ranges that need to be
        // added to this document
        for(String column : columnsToProtect) {
            ProtectedRange newRange = new ProtectedRange();

            GridRange gridRange = new GridRange();

            Integer startColumnIndex = headers.get(column);
            if(startColumnIndex == null) {
                // Don't try to protect a column that is not present in the sheet
                continue;
            }

            log.info("Adding protection to column \"" + column + "\" on tab \"" + sheetTitle + "\"");
            gridRange.setStartColumnIndex(startColumnIndex);
            gridRange.setEndColumnIndex(startColumnIndex + 1);
            gridRange.setSheetId(sheetProperties.getSheetId());
            newRange.setRange(gridRange);

            Editors editors = new Editors();
            editors.setUsers(editorList);
            newRange.setEditors(editors);

            AddProtectedRangeRequest addProtectedRangeRequest =
                new AddProtectedRangeRequest();
            addProtectedRangeRequest.setProtectedRange(newRange);

            Request request = new Request();
            request.setAddProtectedRange(addProtectedRangeRequest);
            updateRangeRequests.add(request);
        }
    }

    public static int protectTab(String tab) throws IOException {
        Sheet sheet = getCachedSheetProperties(tab);
        SheetProperties sheetProperties = sheet.getProperties();
        ProtectedRange newRange = new ProtectedRange();

        GridRange gridRange = new GridRange();

        gridRange.setSheetId(sheetProperties.getSheetId());
        newRange.setRange(gridRange);

        AddProtectedRangeRequest addProtectedRangeRequest =
            new AddProtectedRangeRequest();
        addProtectedRangeRequest.setProtectedRange(newRange);

        Request request = new Request();
        request.setAddProtectedRange(addProtectedRangeRequest);

        List<Request> requestList = new ArrayList<>();
        requestList.add( request );

        BatchUpdateSpreadsheetResponse batchResponse =
            batchUpdateRequests(requestList);

        Response response = batchResponse.getReplies().get(0);

        AddProtectedRangeResponse pResponse =
            response.getAddProtectedRange();

        ProtectedRange range = pResponse.getProtectedRange();

        return range.getProtectedRangeId();
    }

    public static void unprotectRange(int rangeId) throws IOException {
        DeleteProtectedRangeRequest deleteRequest = new DeleteProtectedRangeRequest();
        deleteRequest.setProtectedRangeId(rangeId);

        Request request = new Request();
        request.setDeleteProtectedRange(deleteRequest);

        List<Request> requestList = new ArrayList<>();
        requestList.add( request );

        batchUpdateRequests(requestList);
    }

    public static void cacheSheetProperties() throws IOException {
        // Lookup the sheet properties and protected ranges on the source spreadsheet
        Sheets.Spreadsheets.Get get = sheetsService.spreadsheets().get(spreadsheetId);
        get.setFields("sheets.properties");
        Spreadsheet s = execute(get);
        List<Sheet> sheets = s.getSheets();

        if(cachedSheetProperties == null) {
            cachedSheetProperties = new TreeMap<String, Sheet>();
        } else {
            cachedSheetProperties.clear();
        }

        for(Sheet sheet: sheets) {
            // Get the sheet properties
            SheetProperties sheetProperties = sheet.getProperties();

            // Make sure the title is in the list of sheets we process
            String sheetTitle = sheetProperties.getTitle();
            cachedSheetProperties.put(sheetTitle, sheet);
        }
    }

    public static Sheet getSheetProperties(String tab) throws IOException {
        cacheSheetProperties();

        return cachedSheetProperties.get(tab);
    }

    public static Sheet getCachedSheetProperties(String tab) {
        return cachedSheetProperties.get(tab);
    }

    public static List<CellFormat> getColumnFormatting(String tab, String columnName) throws IOException {
        char column = columnNameToIndex(tab, columnName);
        Sheets.Spreadsheets.Get get = sheetsService.spreadsheets().get(spreadsheetId);
        get.setFields("sheets.data.rowData.values.userEnteredFormat");
        get.setRanges(new ArrayList<String>(Arrays.asList(tab + "!" + column + ":" + column)));
        Spreadsheet s = execute(get);
        List<Sheet> sheets = s.getSheets();

        // Only one sheet should be returned since the specified range
        // only included one sheet
        if(sheets.size() == 0) {
            throw new IOException("Failed to retrieve formatting for " +
                                  get.getRanges().get(0));
        }

        Sheet sheet = sheets.get(0);

        // There should be at most one set of grid data
        List<GridData> sheetData = sheet.getData();
        if(sheetData == null) {
            throw new IOException("No sheet data in request for " +
                                  get.getRanges().get(0));
        }

        if(sheetData.size() == 0) {
            throw new IOException("No grid data in request for " +
                                  get.getRanges().get(0));
        }

        List<CellFormat> formatList = new ArrayList<CellFormat>();

        do {
            GridData gridData = sheetData.get(0);

            if(gridData.size() == 0) {
                // No data was returned
                break;
            }

            List<RowData> rowDataList = gridData.getRowData();

            if(rowDataList == null) {
                // No data was returned
                break;
            }

            for(RowData rowData : rowDataList) {
                List<CellData> values = rowData.getValues();

                if(values == null) {
                    formatList.add(null);
                    continue;
                }

                formatList.add(values.get(0).getUserEnteredFormat());
            }
        } while( false );

        return formatList;
    }

    public static List<ProtectedRange> getProtectedRanges(String tab) throws IOException {
        // Lookup the sheet properties and protected ranges on the source spreadsheet
        Sheets.Spreadsheets.Get get = sheetsService.spreadsheets().get(spreadsheetId);
        get.setFields("sheets.protectedRanges");
        get.setRanges(new ArrayList<String>(Arrays.asList(tab)));
        Spreadsheet s = execute(get);
        List<Sheet> sheets = s.getSheets();

        if(sheets == null) {
            return null;
        }

        if(sheets.size() < 1) {
            return null;
        }

        return sheets.get(0).getProtectedRanges();
    }

    public static Request setStatusColor(int row, char column, Integer sheetId, Color color) {
        Request req = new Request();
        RepeatCellRequest repeatCellRequest = new RepeatCellRequest();
        CellData cellData = new CellData();
        CellFormat cellFormat = new CellFormat();
        TextFormat textFormat = new TextFormat();
        GridRange range = new GridRange();

        textFormat.setForegroundColor(color);
        textFormat.setItalic(true);
        textFormat.setFontSize(8);

        cellFormat.setTextFormat(textFormat);

        cellData.setUserEnteredFormat(cellFormat);
        range.setStartColumnIndex((int)column - (int)'A');
        range.setEndColumnIndex((int)column - (char)'A' + 1);
        range.setStartRowIndex(row);
        range.setEndRowIndex(row + 1);
        range.setSheetId(sheetId);

        repeatCellRequest.setCell(cellData);
        repeatCellRequest.setFields("userEnteredFormat(textFormat)");
        repeatCellRequest.setRange(range);

        req.setRepeatCell(repeatCellRequest);

        return req;
    }

    public static long checkProtections() throws Exception {
        long requestCount = 1;

        // Get the list of tabs to process
        Set<String> tabList = new TreeSet<>();

        tabList.addAll(MaintainDictionary.tabs());
        tabList.add("Mapping Failures");

        // Lookup the sheet properties and protected ranges on the source spreadsheet
        Sheets.Spreadsheets.Get get = sheetsService.spreadsheets().get(spreadsheetId);
        get.setFields("sheets.protectedRanges,sheets.properties");
        Spreadsheet s = execute(get);
        List<Sheet> sheets = s.getSheets();

        // This list will contain requests to add new protected ranges to the
        // spreadsheet
        List<Request> updateRangeRequests = new ArrayList<Request>();

        // Loop the sheets on the source source spreadsheet
        for(Sheet sheet: sheets) {
            // Get the sheet properties
            SheetProperties sheetProperties = sheet.getProperties();

            // Make sure the title is in the list of sheets we process
            String sheetTitle = sheetProperties.getTitle();
            if(!tabList.contains(sheetTitle)) {
                continue;
            }

            // Get a list of existing protected ranges on this sheet
            List<ProtectedRange> ranges = sheet.getProtectedRanges();
            if(ranges == null) {
                ranges = new ArrayList<ProtectedRange>();
            }

            // Determine the protected ranges that need to be added
            // to the sheet
            updateProtectedRanges(ranges, sheetProperties, updateRangeRequests);
        }

        if(!updateRangeRequests.isEmpty()) {
            // At this point we have a list of protected ranges.
            // Create a request with all these protected ranges
            BatchUpdateSpreadsheetRequest breq = new BatchUpdateSpreadsheetRequest();
            breq.setRequests(updateRangeRequests);

            try {
                // Execute request to add new protected ranges
                execute(sheetsService.spreadsheets().batchUpdate(spreadsheetId, breq));
                requestCount += (long)updateRangeRequests.size();
            } catch(Exception e) {
                e.printStackTrace();
            }
        }

        return requestCount;
    }

    public static void importTabFromCSV(String tab, InputStream csvData) throws IOException {
        BufferedReader reader = new BufferedReader( new InputStreamReader(csvData) );

        List<List<Object>> values = new ArrayList<>();

        String csvLine = reader.readLine();
        while(csvLine != null) {
            String[] rowStringValues = csvLine.split(",");

            List<Object> rowValues = new ArrayList<>(Arrays.asList(rowStringValues));
            values.add(rowValues);

            csvLine = reader.readLine();
        }

        ValueRange sheetData = new ValueRange()
            .setValues(values).setRange(tab);

        List<ValueRange> valueRangeUpdates = new ArrayList<>();

        valueRangeUpdates.add(sheetData);

        batchUpdateValues(valueRangeUpdates);
    }

    public static ValueRange getTabData(String tab) throws IOException {
        return execute(sheetsService.spreadsheets().values().get(spreadsheetId, tab));
    }

    public static UpdateValuesResponse setTabData(String tab, ValueRange update) throws IOException {
        Values.Update updateRequest =
            sheetsService.spreadsheets().values().update(spreadsheetId, tab, update);

        updateRequest.setValueInputOption("RAW");

        return execute(updateRequest);
    }

    public static Values.Get getTabDataRequest(String tab) throws IOException {
        return sheetsService.spreadsheets().values().get(spreadsheetId, tab);
    }

    public static final String getSpreadsheetId() {
        return spreadsheetId;
    }

    public static <T> T execute(AbstractGoogleClientRequest<T> request) throws IOException {
        long delayExtraMS = 60000;
        long delayBaseMS = 60000;
        int retriesLeft = 4;

        while(true) {
            try {
                return request.execute();
            } catch(GoogleJsonResponseException e) {
                if(retriesLeft == 0) {
                    throw e;
                }

                GoogleJsonError err = e.getDetails();
                if(err == null) {
                    throw e;
                }

                // If the error code is 429 it means that
                // there were too man requests to Google
                if(err.getCode() != 429) {
                    throw e;
                }

                long delayMS = delayExtraMS + delayBaseMS;

                // Wait a bit and try again
                try {
                    log.warning("Too many Google requests.  Re-trying in "
                                + (delayMS / 1000L) + " seconds ...");
                    Thread.sleep(delayMS);
                } catch(InterruptedException e2) {
                    // Might as well retry request
                }

                --retriesLeft;
                delayExtraMS *= 2;
            } catch(SocketTimeoutException e) {
                if(retriesLeft == 0) {
                    throw e;
                }

                long delayMS = delayExtraMS + delayBaseMS;

                // Wait a bit and try again
                try {
                    log.warning("Google request timed out.  Re-trying in "
                                + (delayMS / 1000L) + " seconds ...");
                    Thread.sleep(delayMS);
                } catch(InterruptedException e2) {
                    // Might as well retry request
                }

                --retriesLeft;
                delayExtraMS *= 2;
            }
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
