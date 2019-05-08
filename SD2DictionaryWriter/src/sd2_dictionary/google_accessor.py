from __future__ import print_function
import pickle
import os.path
from googleapiclient.discovery import build
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request
import time

# If modifying these scopes, delete the file token.pickle.
SCOPES = ['https://www.googleapis.com/auth/spreadsheets',
          'https://www.googleapis.com/auth/drive.file',
          'https://www.googleapis.com/auth/documents',]


REQUESTS_PER_SEC = 0.5

class GoogleAccessor:

    # Constructor
    def __init__(self, *, spreadsheet_id: str, credentials):
        self._sheet_service = build('sheets', 'v4',
                                    credentials=credentials)
        self._drive_service = build('drive', 'v3',
                                    credentials=credentials)
        self._docs_service = build('docs', 'v1',
                                   credentials=credentials)
        self._spreadsheet_id = spreadsheet_id
        self._tab_headers = dict()
        self._inverse_tab_headers = dict()
        self.MAPPING_FAILURES = 'Mapping Failures'

        self.type_tabs = {
            'Attribute': ['Attribute'],
            'Reagent': ['Bead', 'CHEBI', 'DNA', 'Protein',
                        'RNA', 'Media', 'Stain', 'Buffer',
                        'Solution'],
            'Genetic Construct': ['DNA', 'RNA'],
            'Strain': ['Strain'],
            'Protein': ['Protein'],
            'Collections': ['Challenge Problem']
        }

        self._dictionary_headers = ['Common Name',
                                    'Type',
                                    'SynBioHub URI',
                                    'Stub Object?',
                                    'Definition URI',
                                    'Definition URI / CHEBI ID',
                                    'Status']

        self.mapping_failures_headers = [
            'Experiment/Run',
	    'Lab',
            'Item Name',
            'Item ID',
            'Item Type (Strain or Reagent Tab)',
            'Status'
            ]

        # Lab Names
        self.labs = ['BioFAB', 'Ginkgo',
                     'Transcriptic', 'LBNL', 'EmeraldCloud']

    @staticmethod
    def create(*, spreadsheet_id=None, console=False):
        """
        Ensures that the user is logged in and returns a `GoogleAccessor`.

        Credentials are initially read from the `credentials.json` file, and
        are subsequently stored in the file `token.pickle` that stores the
        user's access and refresh tokens.
        The file `token.pickle` is created automatically when the authorization
        flow completes for the first time.
        """
        creds = None
        #
        if os.path.exists('token.pickle'):
            with open('token.pickle', 'rb') as token:
                creds = pickle.load(token)
        # If there are no (valid) credentials available, let the user log in.
        if not creds or not creds.valid:
            if creds and creds.expired and creds.refresh_token:
                creds.refresh(Request())
            else:
                flow = InstalledAppFlow.from_client_secrets_file(
                    'credentials.json', SCOPES)
                if console:
                    creds = flow.run_console()
                else:
                    creds = flow.run_local_server()
            # Save the credentials for the next run
            with open('token.pickle', 'wb') as token:
                pickle.dump(creds, token)

        return GoogleAccessor(
            spreadsheet_id=spreadsheet_id, credentials=creds
        )

    def create_new_spreadsheet(self, name: str):
        """Creates a new spreadsheet and return the spreadsheet id
          Arguements:

            name - Name of the new spreadsheet

        """
        spreadsheet = {
            'properties': {
                'title': name
                }
            }
        spreadsheets = self._sheet_service.spreadsheets()
        create_sheets_request = spreadsheets.create(body=spreadsheet,
                                                    fields='spreadsheetId')
        return self._execute_request(create_sheets_request)

    def delete_spreadsheet(self, spreadsheet_id: str):
        """Delete an existing spreadsheet
          Arguements:

            spreadsheet_id - the spreadsheet to delete

        """
        files = self._drive_service.files()
        request = files.delete(fileId=spreadsheet_id)
        return self._execute_request(request)

    def create_dictionary_sheets(self):
        """ Creates the standard tabs on the current spreadsheet.
            The tabs are not popluated with any data
        """
        add_sheet_requests = list(map(lambda x: self.add_sheet_request(x),
                                    list(self.type_tabs.keys())))
        # Mapping Failures tab
        add_sheet_requests.append(
            self.add_sheet_request( self.MAPPING_FAILURES )
        )
        self._execute_requests(add_sheet_requests)

        # Add sheet column headers
        headers = self._dictionary_headers
        headers += list(map(lambda x: x + ' UID', self.labs))

        for tab in self.type_tabs.keys():
            self._set_tab_data(tab=tab + '!2:2', values=[headers])

        self._set_tab_data(tab=self.MAPPING_FAILURES + '!2:2',
                           values=[self.mapping_failures_headers])

    def add_sheet_request(self, sheet_title: str):
        """ Creates a Google request to add a tab to the current spreadsheet

          Arguments:

            sheet_title: name of the new tab
        """

        request = {
            'addSheet': {
                'properties': {
                    'title': sheet_title
                    }
                }
            }
        return request

    def _execute_requests(self, requests: []):
        body = {
            'requests': requests
            }
        batch_request = self._sheet_service.spreadsheets().batchUpdate(
            spreadsheetId=self._spreadsheet_id,
            body=body)
        time.sleep(len(requests) / REQUESTS_PER_SEC)
        return batch_request.execute()

    def _execute_request(self, request):
        time.sleep(1.0 / REQUESTS_PER_SEC)
        return request.execute()

    def set_spreadsheet_id(self, spreadsheet_id: str):
        """
        Select the spreadsheet that is being operated on.
        """
        self._spreadsheet_id = spreadsheet_id
        self._clear_tab_header_cache()

    def _get_tab_data(self, tab):
        """
        Retrieve all the data from a spreadsheet tab.
        """
        values = self._sheet_service.spreadsheets().values()
        get = values.get(spreadsheetId=self._spreadsheet_id, range=tab)
        return self._execute_request(get)

    def _set_tab_data(self, *, tab, values):
        """
        Write data to a spreadsheet tab.
        """
        body = {}
        body['values'] = values
        body['range'] = tab
        body['majorDimension'] = "ROWS"

        values = self._sheet_service.spreadsheets().values()
        update_request = values.update(spreadsheetId=self._spreadsheet_id,
                                       range=tab, body=body,
                                       valueInputOption='RAW')
        self._execute_request(update_request)

    def _cache_tab_headers(self, tab):
        """
        Cache the headers (and locations) in a tab
        returns a map that maps headers to column indexes
        """
        tab_data = self._get_tab_data(tab + "!2:2")

        if 'values' not in tab_data:
            raise Exception('No header values found in tab "' +
                            tab + '"')

        header_values = tab_data['values'][0]
        header_map = {}
        for index in range(len(header_values)):
            header_map[header_values[index]] = index

        inverse_header_map = {}
        for key in header_map.keys():
            inverse_header_map[header_map[key]] = key

        self._tab_headers[tab] = header_map
        self._inverse_tab_headers[tab] = inverse_header_map

    def _clear_tab_header_cache(self):
        self._tab_headers.clear()
        self._inverse_tab_headers.clear()

    def get_tab_headers(self, tab):
        """
        Get the headers (and locations) in a tab
        returns a map that maps headers to column indexes
        """
        if tab not in self._tab_headers.keys():
            self._cache_tab_headers(tab)

        return self._tab_headers[tab]

    def _get_tab_inverse_headers(self, tab):
        """
        Get the headers (and locations) in a tab
        returns a map that maps column indexes to headers
        """
        if tab not in self._inverse_tab_headers.keys():
            self._cache_tab_headers(tab)

        return self._inverse_tab_headers[tab]

    def get_row_data(self, *, tab, row=None):
        """
        Retrieve data in a tab.  Returns a list of maps, where each list
        element maps a header name to the corresponding row value.  If
        no row is specified all rows are returned
        """
        if tab not in self._tab_headers.keys():
            self._cache_tab_headers(tab)

        header_value = self._inverse_tab_headers[tab]

        if row is None:
            value_range = tab + '!3:9999'
        else:
            value_range = tab + '!' + str(row) + ":" + str(row)

        tab_data = self._get_tab_data(value_range)
        row_data = []
        if 'values' not in tab_data:
            return row_data

        values = tab_data['values']
        row_index = 3
        for row_values in values:
            this_row_data = {}
            for i in range(len(header_value)):
                if i >= len(row_values):
                    break

                header = header_value[i]
                value = row_values[i]

                if value is not None:
                    this_row_data[header] = value

            if len(this_row_data) > 0:
                this_row_data['row'] = row_index
                this_row_data['tab'] = tab
                row_data.append(this_row_data)

            row_index += 1

        return row_data

    def set_row_data(self, entry):
        """
        Write a row to the spreadsheet.  The entry is a map that maps
        column headers to the corresponding values, with an additional
        set of keys that specify the tab and the spreadsheet row
        """
        tab = entry['tab']
        row = entry['row']
        row_data = self.gen_row_data(entry=entry, tab=tab)
        row_range = '{}!{}:{}'.format(tab, row, row)
        self._set_tab_data(tab=row_range, values=[row_data])

    def set_row_value(self, *, entry, column):
        """
        Write a single cell value, given an entry, and the column name
        of the entry to be written
        """
        return self.set_cell_value(
            tab=entry['tab'],
            row=entry['row'],
            column=column,
            value=entry[column]
        )

    def set_cell_value(self, *, tab, row, column, value):
        """
        Write a single cell value, given an tab, row, column name, and value.
        """
        headers = self.get_tab_headers(tab)
        if column not in headers:
            raise Exception('No column "{}" on tab "{}"'.
                            format(column, tab))

        col = chr(ord('A') + headers[column])
        row_range = tab + '!' + col + str(row)
        self._set_tab_data(tab=row_range, values=[[value]])

    def gen_row_data(self, *, entry, tab):
        """
        Generate a list of spreadsheet row value given a map the maps
        column headers to values
        """
        headers = self._get_tab_inverse_headers(tab)
        row_data = [''] * (max(headers.keys()) + 1)

        for index in headers.keys():
            header = headers[index]
            if header not in entry:
                continue
            row_data[index] = entry[header]

        return row_data

    def get_document(self, *, document_id):
        return self._docs_service.documents().get(documentId=document_id).execute()
