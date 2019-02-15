import unittest
import warnings

from sd2_dictionary.sd2_dictionary_writer import SD2DictionaryWriter
from sd2_dictionary.dictionary_accessor import DictionaryAccessor

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

        self.dictionary_accessor = DictionaryAccessor.create(spreadsheet_id="")

        self.spreadsheet_id = self.dictionary_accessor.create_new_spreadsheet(
                    name='Dictionary Writer Test'
        )['spreadsheetId']

        self.dictionary_accessor.set_spreadsheet_id(self.spreadsheet_id)
        self.dictionary_accessor.create_dictionary_sheets()

    def test_add_dictionary_entry(self):
        dictionaryWriter = SD2DictionaryWriter(
            spreadsheet_id=self.spreadsheet_id
        )

        # Add some entries
        for x in range(5):
            dictionaryWriter.add_dictionary_entry(
                'myChemical' + str(x), 'Solution',
                'Ginkgo', 'label' + str(x))

        # Add some more entries
        for x in range(5, 10):
            dictionaryWriter.add_dictionary_entry(
                'myChemical' + str(x), 'Solution',
                'BioFAB', 'label' + str(x))

        # Read back the spreadsheet data
        sheet_entries = self.dictionary_accessor.get_row_data(
            tab='Reagent'
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
            assert entry['Type'] == 'Solution'
            label = 'label' + str(x)
            if x < 5:
                assert entry['Ginkgo UID'] == label
            else:
                assert entry['BioFAB UID'] == label

        # Add a label to a different lab
        dictionaryWriter.add_dictionary_entry(
            'myChemical2', 'Solution',
            'BioFAB', 'label11')

        # Add an additional label
        dictionaryWriter.add_dictionary_entry(
            'myChemical2', 'Solution',
            'BioFAB', 'label12')

        # Try to add an element with the same name and different type.
        # This should fail
        generated_exception = False
        try:
            dictionaryWriter.add_dictionary_entry(
                    'myChemical2', 'Media',
                    'BioFAB', 'label11')
        except:
            generated_exception = True

        assert generated_exception

        # Try to add an element with the same label.  This should
        # fail
        generated_exception = False
        try:
            dictionaryWriter.add_dictionary_entry(
                    'myChemical11', 'Solution',
                    'BioFAB', 'label7')
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
                    experimentRun='experiment' + str(x),
                    lab='Transcriptic',
                    itemName='itemName' + str(x),
                    itemId='itemId' + str(x),
                    itemType='Strain'
                )

        # read back tab entries
        sheet_entries = self.dictionary_accessor.get_row_data(
            tab='Mapping Failures'
        )

        assert len(sheet_entries) == 5

        for entry in sheet_entries:
            idx = entry['row'] - 3

            print('Compare {} to {}'.format(entry['Experiment/Run'],
                                            'experiment' + str(idx))

            assert entry['Experiment/Run'] == ...
            'experiment' + str(idx)

            assert entry['Lab'] == 'Transcriptic'

            assert entry['Item Name'] == ...
            'itemName' + str(idx)

            assert entry['Item ID'] == ...
            'itemId' + str(idx)

            assert entry['Item Type (Strain or Reagent Tab)'] == ...
            'Strain'

    @classmethod
    def tearDownClass(self):
        if self.spreadsheet_id is not None:
            self.dictionary_accessor.delete_spreadsheet(
                self.spreadsheet_id
            )

if __name__ == '__main__':
    unittest.main()
