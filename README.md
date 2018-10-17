reactome2gpml-converter
=======================

This converter converts Reactome pathways to GPML 2013a format.

The class ReactometoGPML2013.java does the actual conversion and class CLIConverter provides command line access to the converter.

Eclipse is used as Java IDE

After setting up the project in Eclipse, you need to install a local Reactome MySQL database for testing. You can download a public release database from https://reactome.org/download-data.

The conversion is performed by the convertPathway method in class org.reactome.sgml.ReactomeToGPML2013Converter. 
You will have to provide your correct database connection information for class MySQLAdaptor().

Visit the project website for more information : http://projects.bigcat.unimaas.nl/ReactomeConverter

The project is built using ant. 
