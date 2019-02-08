from SD2DictionaryWriter import SD2DictionaryWriter

def main():
    # Instantiate dictinoary writer
    dictionaryWrite = SD2DictionaryWriter()

    # Set sheet id.  By default the main dictionary spreadsheet is
    # used.  You only need to do this if you want to use another
    # spreadsheet.
    dictionaryWrite.setSpreadsheetId('1xKfTC7Jzj7lXmBCSMlASEbDOolpsFZMPDj47OqFdgmw')

    # Add a dictionary entry.  If it already exists, the id will be
    # added to the existing entry.  Otherwise a new entry will be
    # created.
    dictionaryWrite.addDictionaryEntry('myChemical', 'Solution', 'Ginkgo', 'NewLabel')

    # Add a mapping failures entry
    dictionaryWrite.addMappingFailure(experimentRun = 'r1bsqh78n5jeq_r1bsxfcwtsbmtxx',
                                      lab='Transcriptic',
                                      itemName='water_cal',
                                      itemId='water_cal',
                                      itemType='Strain')

if __name__ == "__main__":
    main()
