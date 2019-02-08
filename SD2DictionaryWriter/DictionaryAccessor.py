from __future__ import print_function
import pickle
import os.path
from googleapiclient.discovery import build
from google_auth_oauthlib.flow import InstalledAppFlow
from google.auth.transport.requests import Request

# If modifying these scopes, delete the file token.pickle.
SCOPES = ['https://www.googleapis.com/auth/spreadsheets']
ProgramDictionary = '1oLJTTydL_5YPyk-wY-dspjIw_bPZ3oCiWiK0xtG8t3g'

class DictionaryAccessor:

    # Constructor
    def __init__(self):
        self.typeTabs = {'Attribute'         : ['Attribute'],
                         'Reagent'           : ['Bead', 'CHEBI', 'DNA', 'Protein',
                                                'RNA', 'Media', 'Stain', 'Buffer',
                                                'Solution'],
                         'Genetic Construct' : ['DNA', 'RNA'],
                         'Strain'            : ['Strain'],
                         'Protein'           : ['Proteina'],
                         'Collections'       : ['Challenge Problem']

                         }

        self.login()

    def login(self):
        creds = None
        # The file token.pickle stores the user's access and refresh tokens, and is
        # created automatically when the authorization flow completes for the first
        # time.
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

        self.sheetsService = build('sheets', 'v4', credentials=creds)
        self.spreadsheetId = ProgramDictionary
        self.tabHeaders = {}
        self.inverseTabHeaders = {}

    # Select the spreadsheet that is being operated on
    def setSpreadsheetId(self, spreadsheetId):
        self.spreadsheetId = spreadsheetId
        self.clearTabHeaderCache()

    # Retrieve all the data from a spreadsheet tab
    def getTabData(self, tab):
        values = self.sheetsService.spreadsheets().values()
        get = values.get(spreadsheetId=self.spreadsheetId, range=tab)
        return get.execute()

    # Write data to a spreadsheet tab
    def setTabData(self, tab, values):
        body = {}
        body['values'] = values
        body['range'] = tab
        body['majorDimension'] = "ROWS"

        values = self.sheetsService.spreadsheets().values()
        updateRequest = values.update(spreadsheetId=self.spreadsheetId,
                                      range=tab, body=body,
                                      valueInputOption='RAW')
        updateRequest.execute();

    # Cache the headers (and locations) in a tab
    # returns a map that maps headers to column indicies
    def cacheTabHeaders(self, tab):
        headerValues = self.getTabData(tab + "!2:2")['values'][0]
        headerMap = {}
        for index in range( len(headerValues) ):
            headerMap[ headerValues[ index ] ] = index

        inverseHeaderMap = {}
        for key in headerMap.keys():
            inverseHeaderMap[ headerMap[ key ] ] = key

        self.tabHeaders[tab] = headerMap
        self.inverseTabHeaders[tab] = inverseHeaderMap


    def clearTabHeaderCache(self):
        self.tabHeaders.clear()
        self.inverseTabHeaders.clear()

    # Get the headers (and locations) in a tab
    # returns a map that maps headers to column indicies
    def getTabHeaders(self, tab):
        if tab not in self.tabHeaders.keys():
            self.cacheTabHeaders(tab)

        return self.tabHeaders[tab]

    # Get the headers (and locations) in a tab
    # returns a map that maps column indicies to headers
    def getTabInverseHeaders(self, tab):
        if tab not in self.inverseTabHeaders.keys():
            self.cacheTabHeaders(tab)

        return self.inverseTabHeaders[tab]

    # Retreive data in a tab.  Returns a list of maps, where each list
    # element maps a header name to the corresponding row value.  If
    # no row is specified all rows are returned
    def getRowData(self, tab, row=None):
        if tab not in self.tabHeaders.keys():
            self.cacheTabHeaders(tab)

        headerValue = self.inverseTabHeaders[tab]
        headers = self.tabHeaders[tab]

        if row is None:
            valueRange=tab + '!3:9999'
        else:
            valueRange=tab + '!' + str(row) + ":" + str(row)

        values = self.getTabData(valueRange)['values']
        rowData = []
        rowIndex =  3
        for rowValues in values:
            thisRowData = {}
            for i in range( len(headerValue) ):
                if i >= len(rowValues):
                    break;

                header = headerValue[ i ]
                value = rowValues[ i ]

                if value is not None:
                    thisRowData[ header ] = value

            if len(thisRowData) > 0:
                thisRowData['row'] = rowIndex
                thisRowData['tab'] = tab
                rowData.append(thisRowData)

            rowIndex += 1

        return rowData

    # Write a row to the spreadsheet.  The entry is a map that maps
    # column headers to the corresponding values, with an additional
    # set of keys that specify the tab and the spreadsheet row
    def setRowData(self, entry):
        tab = entry['tab']
        row = entry['row']
        rowData = self.genRowData(entry, tab)
        rowRange = '{}!{}:{}'.format(tab, row, row)
        self.setTabData(rowRange, [rowData])

    def setRowValue(self, entry, column):
        return self.setCellValue(entry['tab'], entry['row'],
                                 column, entry[column])

    def setCellValue(self, tab, row, column, value):
        headers = self.getTabHeaders(tab)
        if column not in headers:
            raise Exception('No column "{}" on tab "{}"'.
                            format(column, tab))

        col = chr ( ord('A') + headers[ column ] )
        rowRange = tab + '!' + col + str(row)
        self.setTabData(rowRange, [[value]])

    def genRowData(self, entry, tab):
        headers = self.getTabInverseHeaders(tab)
        rowData = [''] * max(headers.keys())

        for index in headers.keys():
            header = headers[index]
            if header not in entry:
                continue
            rowData[index] = entry[header]

        return rowData
