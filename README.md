reactome2gpml-converter
=======================

This converter converts Reactome pathways to GPML 2013a format.

The class ReatometoGPML2013.java does the actual conversion and class CLICOnverter provides command line access to the converter.

Eclipse is used as Java IDE

After setting up the project in Eclipse, you need to install a local Reactome database for easy test. You can download a public release database from http://www.reactome.org/download.

The conversion is performed by the convertPathway method in class org.reactome.sgml.ReactomeToGPML2013Converter. 
You will have to provide your correct database connection information for class MySQLAdaptor().

Visit the project website for more information : http://projects.bigcat.unimaas.nl/ReactomeConverter

      
