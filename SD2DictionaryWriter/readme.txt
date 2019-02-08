This package requres the following dependencies:

pip install --upgrade google-api-python-client google-auth-httplib2 google-auth-oauthlib

This package contains methods for adding rows to the Dictionary
spreadsheet.  The first time you run you will need to authenticate
with Google via a web browser.  The web browser should automatically
be launched for you.

For testing you can create a copy of the SD2 Program Dictionary
spreadsheet, and then use setSpreadsheetId() method to point to the
copy.  See Sample.py for an example of this.  The obtain the
spreadsheet id, open the spreadsheet in a browser and look at the
URL.  The spreadsheet id is the long sequence of seemingly random
characters between two / characters.  For example, for the following
URL:

  https://docs.google.com/spreadsheets/d/1xKfTC7Jzj7lXmBCSMlASEbDOolpsFZMPDj47OqFdgmw/edit#gid=1729578261

The spreadsheet URL is: 1xKfTC7Jzj7lXmBCSMlASEbDOolpsFZMPDj47OqFdgmw

The credentials.json file needs to be copied to the working directory
where you run your application.

