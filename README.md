# SBOL Dictionary Maintainer

The purpose of this utility is to simplify the definition and linking of names and metadata across different parts of a complex synthetic biology project, such as the DARPA SD2 program.

In particular, this application assists in maintaining a dictionary that links three classes of names:

* "Common names" used colloqially by human researchers refer to particular substances, ideas, or constructs (e.g. "E. coli MG1655", "LB media", "Fluorescent bead control")
* Shared formal definitions stored in SynBioHub or elsewhere (e.g., [https://hub.sd2e.org/user/sd2e/design/LB_Broth/1](), [https://hub.sd2e.org/user/sd2e/design/spherotech_rainbow_beads/1]())
* Internal names used by specific tools or laboratories.

SBOL Dictionary Maintainer supports this curation activity by linking together a Google Sheet, with columns for each class of names, with a SynBioHub metadata store.  New entries are turned automatically into SynBioHub "stub" objects of the appropriate type, and annotated with names in order to allow any tool that needs to translate between name collections to do so.

## Running SBOL Dictionary Maintainer

SBOL Dictionary Maintainer is a Java/Gradle project.

To build:

* Open in Eclipse 
* Select the project, and run "Gradle > Refresh Gradle Project"
* Test by running com.bbn.sd2.DictionaryMaintainerApp
* Export a jar, which should default to "sd2-dictionary-maintainer.jar" with the right preferences.

To run, execute:

````
java -jar sd2-dictionary-maintainer.jar -p {SynBioHub password}
````

Note that on the first run, the dictionary will need to log into its Google account and store credentials.
These credentials will also occasionally expire (which will be indicated by an error 400 login failure), in which case you should delete the current credentials, allowing them to be replaced.


## Setting up a Dictionary
To set up a new dictionary, you will need to perform the following steps:
- Create a shared SynBioHub user linked to a Gmail account and a collection for the dictionary.
- Set up a Google Sheet following the Dictionary template, and share it with the SynBioHub user's Gmail
- Configure your sheet ID, your server, and your collection:
  - The sheet ID is the big block of random characters in the Google Sheet URL: (template: https://docs.google.com/spreadsheets/d/[sheetID]/[otherstuff])
  - The server is the SynBioHub instance that you are using
  - For the collection, use the URL stem shared by all collection members, _not_ the URL (template: https://[server]/user/[shareduser]/[dictionarycollection])

## Standard Instances for DARPA SD2

In the SD2 project, all work should be tested with the staging copy before being deployed live:

* Staging:
	* Sheet ID: `1xyFH-QqYzoswvI3pPJRlBqw9PQdlp91ds3mZoPc3wCU`
	* Server: `-S "https://hub-staging.sd2e.org/" -f "https://hub.sd2e.org/"`
* Deployment:
   * Sheet ID: `1oLJTTydL_5YPyk-wY-dspjIw_bPZ3oCiWiK0xtG8t3g`
	* Server: `-S "https://hub.sd2e.org/"`

## Tests

Tests are run in an isolated testing environment. A scratch Google spreadsheet will be constructed and a scratch SBOL Collection object will be created on the staging instance of SD2 SynBioHub. Running tests requires passing the password argument to the JUnit test runner through a Java VM command line variable, as well as a pointer to the test config file. In Eclipse, these can be configured in Run > Run Configurations > JUnit > TestMaintainDictionary under Arguments tab and VM arguments field.
````
-ea -Dp=password -Dc=true -Dconfig=testConfig.json
````
The clean (c) argument will destroy the Google spreadsheet after tests complete. By default the spreadsheet is not destroyed so the developer can inspect its contents.

## Development Notes

Note that Java 1.8 is currently required, per https://github.com/SynBioHub/synbiohub/issues/594 : do not upgrade

