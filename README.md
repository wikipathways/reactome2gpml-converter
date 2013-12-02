reactome2gpml-converter
=======================

This converter converts Reactome pathways to GPML 2013a format.

The class ReatometoGPML2013.java does the actual conversion and class CLICOnverter provides command line access to the converter.

Eclipse is used as Java IDE

After set up the project in Eclipse, you need to install a local Reactome database for easy test. You can download a public release database from http://www.reactome.org/download.

Start testing with these two methods in class org.reactome.sgml.ReactomeToGPML2013Converter. You may have to provide your correct database connection information for class MySQLAdaptor().

      ** testSingleConvert()

      ** testConvert()


