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
                    name='Dictionary Writer Test')['spreadsheetId']
        self.dictionary_accessor.set_spreadsheet_id(self.spreadsheet_id)
        self.dictionary_accessor.create_dictionary_sheets()


    def test_add_dictionary_entry(self):
        dictionaryWriter = SD2DictionaryWriter(
            spreadsheet_id=self.spreadsheet_id
        )

        dictionaryWriter.add_dictionary_entry(
            'myChemical1', 'Solution', 'Ginkgo', 'label1')

        dictionaryWriter.add_dictionary_entry(
            'myChemical2', 'Solution', 'Ginkgo', 'label2')

    @classmethod
    def tearDownClass(self):
        if self.spreadsheet_id is not None:
            self.dictionary_accessor.delete_spreadsheet(
                self.spreadsheet_id
            )

if __name__ == '__main__':
    unittest.main()
