from sd2_dictionary.sd2_dictionary_writer import SD2DictionaryWriter


def main():
    # Instantiate dictionary writer.
    # If no spreadsheet_id is given, the program dictionary spreadsheet is
    # used.
    dictionaryWrite = SD2DictionaryWriter(
        spreadsheet_id='1xKfTC7Jzj7lXmBCSMlASEbDOolpsFZMPDj47OqFdgmw'
    )

    # Add a dictionary entry.  If it already exists, the id will be
    # added to the existing entry.  Otherwise a new entry will be
    # created.
    dictionaryWrite.add_dictionary_entry(
        'myChemical', 'Solution', 'Ginkgo', 'NewLabel')

    # Add a mapping failures entry
    dictionaryWrite.add_mapping_failure(
        experimentRun='r1bsqh78n5jeq_r1bsxfcwtsbmtxx',
        lab='Transcriptic',
        itemName='water_cal',
        itemId='water_cal',
        itemType='Strain'
    )


if __name__ == "__main__":
    main()
