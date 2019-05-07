import unittest
import warnings

from sd2_dictionary.sd2_dictionary_writer import SD2DictionaryWriter
from sd2_dictionary.google_accessor import GoogleAccessor

class TestSD2DictionaryWriter(unittest.TestCase):

    @classmethod
    def setUpClass(self):
        # The Google API appears to create resource warnings when run
        # from unit test similar to the following:
        #
        # site-packages/googleapiclient/_helpers.py:130:
        #  ResourceWarning: unclosed <ssl.SSLSocket fd=6,
        #                            family=AddressFamily.AF_INET6,
        #                            type=SocketKind.SOCK_STREAM,
        #                            proto=6,
        #                            laddr=('192.168.0.1', 49988, 0, 0),
        #                            raddr=('192.168.0.2', 443, 0, 0)>
        #
        # There is some discussion of similar warnings here:
        #
        #  https://github.com/kennethreitz/requests/issues/3912
        #
        # I am just going ignore these warnings
        #
        warnings.filterwarnings('ignore', message='unclosed <ssl.SSLSocket',
                                category=ResourceWarning)

        self.google_accessor = GoogleAccessor.create(spreadsheet_id="")

        self.spreadsheet_id = self.google_accessor.create_new_spreadsheet(
                    name='Dictionary Writer Test'
        )['spreadsheetId']

        self.google_accessor.set_spreadsheet_id(self.spreadsheet_id)
        self.google_accessor.create_dictionary_sheets()


    def test_add_dictionary_entry(self):
        dictionaryWriter = SD2DictionaryWriter(
            spreadsheet_id=self.spreadsheet_id
        )

        # Add some entries
        for x in range(5):
            dictionaryWriter.add_dictionary_entry(
                common_name='myChemical' + str(x),
                entry_type='Strain',
                lab='Ginkgo',
                lab_id='label' + str(x))

        # Add some more entries
        for x in range(5, 10):
            dictionaryWriter.add_dictionary_entry(
                common_name='myChemical' + str(x),
                entry_type='Solution',
                lab='BioFAB',
                lab_id='label' + str(x))

        # Read back the spreadsheet data
        sheet_entries = self.google_accessor.get_row_data(
            tab='Reagent'
        )

        sheet_entries += self.google_accessor.get_row_data(
            tab='Strain'
        )

        # Crate a map from common names to entries
        entry_map = {}
        for entry in sheet_entries:
            entry_map[ entry['Common Name'] ] = entry

        # Check the entries
        for x in range(10):
            name = 'myChemical' + str(x)
            assert name in entry_map
            entry = entry_map[name]
            label = 'label' + str(x)
            if x < 5:
                assert entry['Type'] == 'Strain'
                assert entry['Ginkgo UID'] == label
            else:
                assert entry['Type'] == 'Solution'
                assert entry['BioFAB UID'] == label

        # Add a label to a different lab
        dictionaryWriter.add_dictionary_entry(
            common_name='myChemical2',
            entry_type='Strain',
            lab='BioFAB',
            lab_id='label11',
            definition_uri='http://www.test.com/')

        # Add an additional label
        dictionaryWriter.add_dictionary_entry(
            common_name='myChemical2',
            entry_type='Strain',
            lab='BioFAB',
            lab_id='label12')

        # Try to add an element with the same name and different type.
        # This should fail
        generated_exception = False
        try:
            dictionaryWriter.add_dictionary_entry(
                common_name='myChemical2',
                entry_type='Solution',
                lab='BioFAB',
                lab_id='label11')
        except:
            generated_exception = True

        assert generated_exception

        # Try to add an element with the same label.  This should
        # fail
        generated_exception = False
        try:
            dictionaryWriter.add_dictionary_entry(
                common_name='myChemical11',
                entry_type='Solution',
                lab='BioFAB',
                lab_id='label7')
        except:
            generated_exception = True

        assert generated_exception


    def test_add_mapping_failures_entry(self):
        dictionaryWriter = SD2DictionaryWriter(
            spreadsheet_id=self.spreadsheet_id
        )

        # Add 5 entries, 2 times
        for i in range(2):
            for x in range(5):
                dictionaryWriter.add_mapping_failure(
                    experiment_run='experiment' + str(x),
                    lab='Transcriptic',
                    item_name='itemName' + str(x),
                    item_id='itemId' + str(x),
                    item_type='Strain'
                )

        # read back tab entries
        sheet_entries = self.google_accessor.get_row_data(
            tab='Mapping Failures'
        )

        assert len(sheet_entries) == 5

        for entry in sheet_entries:
            idx = entry['row'] - 3

            val = 'experiment' + str(idx)
            assert entry['Experiment/Run'] == val

            assert entry['Lab'] == 'Transcriptic'

            val = 'itemName' + str(idx)
            assert entry['Item Name'] == val

            val = 'itemId' + str(idx)
            assert entry['Item ID'] == val

            header = 'Item Type (Strain or Reagent Tab)'
            assert entry[header] == 'Strain'

    @classmethod
    def tearDownClass(self):
        if self.spreadsheet_id is not None:
            self.google_accessor.delete_spreadsheet(
                self.spreadsheet_id
            )

if __name__ == '__main__':
    unittest.main()
