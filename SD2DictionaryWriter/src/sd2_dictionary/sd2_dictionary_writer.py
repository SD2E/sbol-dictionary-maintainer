from sd2_dictionary.google_accessor import GoogleAccessor

program_dictionary_id = '1oLJTTydL_5YPyk-wY-dspjIw_bPZ3oCiWiK0xtG8t3g'


class SD2DictionaryWriter:
    def __init__(self, *, spreadsheet_id=program_dictionary_id,
                 console=False):
        self.dictionary = GoogleAccessor.create(
            spreadsheet_id=spreadsheet_id, console=console)

        # Mapping failure tab column headers
        self.mf_experiment_run_key = 'Experiment/Run'
        self.mf_lab_key = 'Lab'
        self.mf_item_name_key = 'Item Name'
        self.mf_item_id_key = 'Item ID'
        self.mf_item_type_key = 'Item Type (Strain or Reagent Tab)'
        self.MAPPING_FAILURES = self.dictionary.MAPPING_FAILURES

        self.mapping_failure_keys = [self.mf_experiment_run_key,
                                     self.mf_lab_key,
                                     self.mf_item_name_key,
                                     self.mf_item_id_key,
                                     self.mf_item_type_key]

        # Inverse map of typeTabs
        self.type2tab = {}
        for tab_name in self.dictionary.type_tabs.keys():
            for type_name in self.dictionary.type_tabs[tab_name]:
                self.type2tab[type_name] = tab_name

        # Dictionary spreadsheet column header names
        self.common_name_key = 'Common Name'
        self.type_key = 'Type'
        self.definition_uri_key = 'Definition URI'
        self.definition_chebi_uri_key = 'Definition URI / CHEBI ID'
        self.uri_key = 'SynBioHub URI'
        self.status_key = 'Status'

    # Add an row to the Mapping Failures tab in the Dictionary spreadsheet
    def add_mapping_failure(self, *,
                            experiment_run: str = None,
                            lab: str = None,
                            item_name: str = None,
                            item_id: str = None,
                            item_type: str = None):
        """Add a row to the Mapping Failures tab in the Dictionary spreadsheet

          Arguments:

            experiment_run -- the value for the "Experiment/Run" column
            lab            -- the value for the "Lab" column
            item_name      -- the value for the "Item Name" column
            item_id        -- the value for the "Item ID" column
            item_type      -- the value for the "Item Type" column

        """
        entry = self.__gen_mapping_failure_entry(experiment_run, lab,
                                                 item_name, item_id, item_type)

        sheet_entries = self.dictionary.get_row_data(tab=self.MAPPING_FAILURES)

        for sheet_entry in sheet_entries:
            if self.__rows_equal(entry, sheet_entry, self.mapping_failure_keys):
                return

        entry['row'] = len(sheet_entries) + 3

        self.dictionary.set_row_data(entry)

    # By default the main dictionary spreadsheet is used.  This allows
    # the user to user specify a different spreadsheet
    def set_spreadsheet_id(self, spreadsheet_id: str):
        """Set the Google spreadsheet id

          Arguments:

            spreadsheet_id  -- the Google spreadsheet id

        """
        self.dictionary.set_spreadsheet_id(spreadsheet_id)

    # Update the lab id of an entry in the dictionary.  A new row is
    # created if the entry does not already exist
    def add_dictionary_entry(self, *, common_name, entry_type, lab='', lab_id='',
                             definition_uri=''):
        """Add a lab id for a dictionary entry.  If the entry does not
        exist it will be created

          Arguments:

            common_name    -- the "Common Name" column of the entry
            entry_type     -- the "Type" column of the entry
            lab            -- the name of the lab (optional)
            lab_id         -- the new lab id (optional)
            defintinon_uri -- the Definition URI, or CHEBI URI (optional)

        """
        if entry_type not in self.type2tab:
            raise Exception('Unrecognized type: ' + entry_type)

        tab = self.type2tab[entry_type]

        tab_list = self.dictionary.type_tabs.keys()

        sheet_entries = {}
        all_entries = []
        for tab_name in tab_list:
            sheet_entries[tab_name] = self.dictionary.get_row_data(tab=tab_name)
            all_entries += sheet_entries[tab_name]

        if (len(lab_id) > 0 and len(lab) == 0):
            raise Exception("lab must be specified when lab_id is specified")

        if (len(lab) > 0 and len(lab_id) == 0):
            raise Exception("lab_id must be specified when lab is specified")

        # Make sure the lab has a column in the spreadsheet
        if len(lab_id) > 0:
            lab_key = lab + ' UID'
            tab_headers = self.dictionary.get_tab_headers(tab)
            if lab_key not in tab_headers.keys():
                raise Exception('No "' + lab_key + '" column in tab "' +
                                tab + '"')

            # Generate a map from lab ids to the corresponding entries
            lab_name_map = self.__gen_value_name_map(all_entries, lab_key)

            # Make sure lab id does not already exist
            if lab_id in lab_name_map:
                entry = lab_name_map[lab_id]
                if self.common_name_key in entry:
                    raise Exception(
                        'Id "{}" is already assigned to "{}" (row {} of tab "{}")'.
                        format(lab_id, entry[self.common_name_key], entry['row'],
                               entry['tab']))
                else:
                    raise Exception('Id "{}" is already assigned on row {} of tab "{}"'.
                                    format(lab_id, entry['row'], entry['tab']))

        # Check to see if the common name exists
        common_name_map = self.__gen_value_name_map(
            all_entries, self.common_name_key)
        if common_name in common_name_map:
            # Entry already exists
            entry = common_name_map[common_name]

            if entry[self.type_key] != entry_type:
                if (entry[self.type_key] is None
                        or len(entry[self.type_key]) == 0):
                    entry[self.type_key] = entry_type
                    self.dictionary.set_row_value(
                        entry=entry, column=self.type_key)
                else:
                    raise Exception('Type of "{}" on row {} of tab "{}" is {}'.
                                    format(common_name, entry['row'], entry['tab'],
                                           entry[self.type_key]))
        else:
            # New Entry
            entry = {}
            entry[self.common_name_key] = common_name
            entry[self.type_key] = entry_type
            entry['row'] = len(sheet_entries[tab]) + 3
            entry['tab'] = tab
            self.dictionary.set_row_value(
                entry=entry, column=self.common_name_key)
            self.dictionary.set_row_value(
                entry=entry, column=self.type_key)

        # Add Lab ID
        if len(lab_id) > 0:
            if lab_key not in entry or len(entry[lab_key]) == 0:
                entry[lab_key] = lab_id
            else:
                entry[lab_key] = entry[lab_key] + "," + lab_id

            self.dictionary.set_row_value(entry=entry, column=lab_key)

        if len(definition_uri) > 0:
            if tab == 'Reagent':
                column_key = self.definition_chebi_uri_key
            else:
                column_key = self.definition_uri_key

            entry[column_key] = definition_uri

            self.dictionary.set_row_value(entry=entry, column=column_key)

    ############################
    #
    # Internal methods
    #
    ############################

    # Compares two rows
    def __rows_equal(self, row1, row2, keys):
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
    def __gen_mapping_failure_entry(self, experiment_run, lab, item_name,
                                    item_id, item_type):
        entry = {}
        if experiment_run is not None:
            entry[self.mf_experiment_run_key] = experiment_run

        if lab is not None:
            entry[self.mf_lab_key] = lab

        if item_name is not None:
            entry[self.mf_item_name_key] = item_name

        if item_id is not None:
            entry[self.mf_item_id_key] = item_id

        if item_type is not None:
            entry[self.mf_item_type_key] = item_type

        entry['tab'] = self.MAPPING_FAILURES

        return entry

    # Generate a map with keys are the values from a column, and
    # values are the corresponding row entries
    def __gen_value_name_map(self, sheet_entries, value_key):
        value_name_map = {}
        for sheet_entry in sheet_entries:
            if value_key not in sheet_entry:
                continue
            value_names = sheet_entry[value_key]
            if len(value_names) == 0:
                continue
            value_name_list = list(
                map(lambda x: x.strip(), value_names.split(',')))
            for value_name in value_name_list:
                value_name_map[value_name] = sheet_entry

        return value_name_map
