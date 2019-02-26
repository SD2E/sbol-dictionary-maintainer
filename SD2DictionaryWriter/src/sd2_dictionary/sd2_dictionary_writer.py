from sd2_dictionary.dictionary_accessor import DictionaryAccessor

program_dictionary_id = '1oLJTTydL_5YPyk-wY-dspjIw_bPZ3oCiWiK0xtG8t3g'


class SD2DictionaryWriter:
    def __init__(self, *, spreadsheet_id=program_dictionary_id):
        self.dictionary = DictionaryAccessor.create(
            spreadsheet_id=spreadsheet_id)

        # Mapping failure tab column headers
        self.mfExperimentRunKey = 'Experiment/Run'
        self.mfLabKey = 'Lab'
        self.mfItemNameKey = 'Item Name'
        self.mfItemIdKey = 'Item ID'
        self.mfItemTypeKey = 'Item Type (Strain or Reagent Tab)'
        self.MAPPING_FAILURES = self.dictionary.MAPPING_FAILURES

        self.mappingFailureKeys = [self.mfExperimentRunKey,
                                   self.mfLabKey,
                                   self.mfItemNameKey,
                                   self.mfItemIdKey,
                                   self.mfItemTypeKey]

        # Inverse map of typeTabs
        self.type2tab = {}
        for tab_name in self.dictionary.type_tabs.keys():
            for type_name in self.dictionary.type_tabs[tab_name]:
                self.type2tab[type_name] = tab_name

        # Dictionary spreadsheet column header names
        self.commonNameKey = 'Common Name'
        self.typeKey = 'Type'
        self.definitionURIKey = 'Definition URI'
        self.uriKey = 'SynBioHub URI'
        self.statusKey = 'Status'

    # Add an row to the Mapping Failures tab in the Dictionary spreadsheet
    def add_mapping_failure(self, *,
                            experimentRun: str = None,
                            lab: str = None,
                            itemName: str = None, itemId: str = None,
                            itemType: str = None):
        """Add a row to the Mapping Failures tab in the Dictionary spreadsheet

          Arguments:

            experimentRun -- the value for the "Experiment/Run" column
            lab           -- the value for the "Lab" column
            itemName      -- the value for the "Item Name" column
            itemId        -- the value for the "Item ID" column
            itemType      -- the value for the "Item Type" column

        """
        entry = self.__genMappingFailureEntry(experimentRun, lab,
                                              itemName, itemId, itemType)

        sheetEntries = self.dictionary.get_row_data(tab=self.MAPPING_FAILURES)

        for sheetEntry in sheetEntries:
            if self.__rowsEqual(entry, sheetEntry, self.mappingFailureKeys):
                return

        entry['row'] = len(sheetEntries) + 3

        self.dictionary.set_row_data(entry)

    # By default the main dictionary spreadsheet is used.  This allows
    # the user to user specify a different spreadsheet
    def set_spreadsheet_id(self, spreadsheetId: str):
        """Set the Google spreadsheet id

          Arguments:

            spreadsheetId  -- the Google spreadsheet id

        """
        self.dictionary.set_spreadsheet_id(spreadsheetId)

    # Update the lab id of an entry in the dictionary.  A new row is
    # created if the entry does not already exist
    def add_dictionary_entry(self, commonName, entryType, lab, labId):
        """Add a lab id for a dictionary entry.  If the entry does not
        exist it will be created

          Arguments:

            commonName    -- the "Common Name" column of the entry
            entryType     -- the "Type" column of the entry
            lab           -- the name of the lab
            labId         -- the new lab id

        """
        if entryType not in self.type2tab:
            raise Exception('Unrecognized type: ' + entryType)

        tab = self.type2tab[entryType]

        sheetEntries = self.dictionary.get_row_data(tab=tab)

        # Make sure the lab has a column in the spreadsheet
        labKey = lab + ' UID'
        tabHeaders = self.dictionary.get_tab_headers(tab)
        if labKey not in tabHeaders.keys():
            raise Exception('No "' + labKey + '" column in tab "' +
                            tab + '"')

        # Generate a map from lab ids to the corresponding entries
        labNameMap = self.__genValueNameMap(sheetEntries, labKey)

        if labId in labNameMap:
            entry = labNameMap[labId]
            if self.commonNameKey in entry:
                raise Exception(
                    'Id "{}" is already assigned to "{}" (row {})'.
                    format(labId, entry[self.commonNameKey], entry['row']))
            else:
                raise Exception('Id "{}" is already assigned on row {}'.
                                format(labId, entry['row']))

        # Check to see if the common name exists
        commonNameMap = self.__genValueNameMap(
            sheetEntries, self.commonNameKey)
        if commonName in commonNameMap:
            # Entry already exists
            entry = commonNameMap[commonName]

            if entry[self.typeKey] != entryType:
                if (entry[self.typeKey] is None
                        or len(entry[self.typeKey]) == 0):
                    entry[self.typeKey] = entryType
                    self.dictionary.set_row_value(
                        entry=entry, column=self.typeKey)
                else:
                    raise Exception('Type of "{}" on row {} of tab "{}" is {}'.
                                    format(commonName, entry['row'], tab,
                                           entry[self.typeKey]))
        else:
            # New Entry
            entry = {}
            entry[self.commonNameKey] = commonName
            entry[self.typeKey] = entryType
            entry['row'] = len(sheetEntries) + 3
            entry['tab'] = tab
            self.dictionary.set_row_value(
                entry=entry, column=self.commonNameKey)
            self.dictionary.set_row_value(
                entry=entry, column=self.typeKey)

        # Add Lab ID
        if labKey not in entry or len(entry[labKey]) == 0:
            entry[labKey] = labId
        else:
            entry[labKey] = entry[labKey] + "," + labId

        self.dictionary.set_row_value(entry=entry, column=labKey)

    ############################
    #
    # Internal methods
    #
    ############################

    # Compares two rows
    def __rowsEqual(self, row1, row2, keys):
        for key in keys:
            if key not in row1:
                if key in row2:
                    return False
            elif key not in row2:
                if key in row1:
                    return False

            elif row1[key] != row2[key]:
                return False

        return True

    # Construct a mapping failure entry
    def __genMappingFailureEntry(self, experimentRun, lab, itemName,
                                 itemId, itemType):
        entry = {}
        if experimentRun is not None:
            entry[self.mfExperimentRunKey] = experimentRun

        if lab is not None:
            entry[self.mfLabKey] = lab

        if itemName is not None:
            entry[self.mfItemNameKey] = itemName

        if itemId is not None:
            entry[self.mfItemIdKey] = itemId

        if itemType is not None:
            entry[self.mfItemTypeKey] = itemType

        entry['tab'] = self.MAPPING_FAILURES

        return entry

    # Generate a map with keys are the values from a column, and
    # values are the corresponding row entries
    def __genValueNameMap(self, sheetEntries, valueKey):
        valueNameMap = {}
        for sheetEntry in sheetEntries:
            if valueKey not in sheetEntry:
                continue
            valueNames = sheetEntry[valueKey]
            if len(valueNames) == 0:
                continue
            valueNameList = list(
                map(lambda x: x.strip(), valueNames.split(',')))
            for valueName in valueNameList:
                valueNameMap[valueName] = sheetEntry

        return valueNameMap
