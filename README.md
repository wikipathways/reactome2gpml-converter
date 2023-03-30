[![build](https://github.com/wikipathways/reactome2gpml-converter/actions/workflows/build.yml/badge.svg)](https://github.com/wikipathways/reactome2gpml-converter/actions/workflows/build.yml)

# Reactome2GPML Converter

This converter converts Reactome pathways to GPML 2013a format.

The class `ReactometoGPML2013.java` does the actual conversion and class CLIConverter provides command line access to the converter.

Eclipse is used as Java IDE, and the project is built using ant. 

After setting up the project in Eclipse or as a local .jar file, testing can be performed on a local Reactome MySQL database; download at https://reactome.org/download-data.

The conversion is performed by the convertPathway method in class org.reactome.sgml.ReactomeToGPML2013Converter. 
You will have to provide your correct database connection information for class MySQLAdaptor().

For more information, read the publication on this project titled:
"Reactome from a WikiPathways Perspective" by Anwesha Bohler , Guanming Wu, Martina Kutmon, Leontius Adhika Pradhana, Susan L. Coort, Kristina Hanspers, Robin Haw, Alexander R. Pico, Chris T. Evelo. https://doi.org/10.1371/journal.pcbi.1004941

Recent conversions for WikiPathways and news can be found at:
https://classic.wikipathways.org/index.php/Portal:Reactome
