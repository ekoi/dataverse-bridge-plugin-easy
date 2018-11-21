bridge-plugin-easy
==================

This is the implementation of a [bridge-plugin](https://github.com/DANS-KNAW/dataverse-bridge-plugin-lib) for ingesting data to the [EASY](https://easy.dans.knaw.nl/ui/home) archive. 
This plugin transforms the dataverse metadata file in JSON format into the required metadata files by EASY; ‘dataset.xml’ and ‘files.xml’. 
This is done according to the requirements described in this document: '[Depositing in EASY with SWORD v2.0](https://easy.dans.knaw.nl/doc/sword2.html)' requirements document.

Building from source
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* [bridge-plugin-lib](https://github.com/DANS-KNAW/dataverse-bridge-plugin-lib)

Steps:

        git clone https://github.com/DANS-KNAW/dataverse-bridge-plugin-easy.git
        cd dataverse-bridge-plugin-easy
        mvn clean install

Install
-------

Copy the jar file into the plugin directory of the bridge service. 
Also copy the plugin configuration file (easy.json). 
Make sure that this plugin and the service are based on the same version of the plugin lib. 

<!--- TODO Add more documenation -->
