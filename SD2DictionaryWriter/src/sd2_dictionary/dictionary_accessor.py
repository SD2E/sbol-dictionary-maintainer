from __future__ import print_function
import pickle
import os.path
from googleapiclient.discovery import build
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request

# If modifying these scopes, delete the file token.pickle.
SCOPES = ['https://www.googleapis.com/auth/spreadsheets']


class DictionaryAccessor:

    # Constructor
    def __init__(self, *, service, spreadsheet_id: str):
        self._spreadsheet_id = spreadsheet_id
        self._sheet_service = service
        self._tab_headers = dict()
        self._inverse_tab_headers = dict()

        self.type_tabs = {
            'Attribute': ['Attribute'],
            'Reagent': ['Bead', 'CHEBI', 'DNA', 'Protein',
                        'RNA', 'Media', 'Stain', 'Buffer',
                        'Solution'],
            'Genetic Construct': ['DNA', 'RNA'],
            'Strain': ['Strain'],
            'Protein': ['Proteins'],
            'Collections': ['Challenge Problem']
        }

    @staticmethod
    def create(*, spreadsheet_id):
        """
        Ensures that the user is logged in and returns a `DictionaryAccessor`.

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
                creds = flow.run_local_server()
            # Save the credentials for the next run
            with open('token.pickle', 'wb') as token:
                pickle.dump(creds, token)

        return DictionaryAccessor(
            spreadsheet_id=spreadsheet_id,
            service=build('sheets', 'v4', credentials=creds)
        )

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
        return get.execute()

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
        update_request.execute()

    def _cache_tab_headers(self, tab):
        """
        Cache the headers (and locations) in a tab
        returns a map that maps headers to column indexes
        """
        headerValues = self._get_tab_data(tab + "!2:2")['values'][0]
        headerMap = {}
        for index in range(len(headerValues)):
            headerMap[headerValues[index]] = index

        inverseHeaderMap = {}
        for key in headerMap.keys():
            inverseHeaderMap[headerMap[key]] = key

        self._tab_headers[tab] = headerMap
        self._inverse_tab_headers[tab] = inverseHeaderMap

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

        values = self._get_tab_data(value_range)['values']
        row_data = []
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
        row_data = [''] * max(headers.keys())

        for index in headers.keys():
            header = headers[index]
            if header not in entry:
                continue
            row_data[index] = entry[header]

        return row_data
