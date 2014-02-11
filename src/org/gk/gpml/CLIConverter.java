/*
 * This command line interface is used to convert a pathway that has a diagram inside the reactome
 * database to GPML format.
 */

package org.gk.gpml;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Collection;
import java.util.Iterator;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.jdom.Document;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

public class CLIConverter {

	private MySQLAdaptor adaptor;
	private ReactometoGPML2013 r2g3Converter;

	private CLIConverter(MySQLAdaptor adaptor) {
		this(adaptor, new ReactometoGPML2013());
	}

	private CLIConverter(MySQLAdaptor adaptor, ReactometoGPML2013 r2g3Converter) {
		this.adaptor = adaptor;
		this.r2g3Converter = r2g3Converter;
		this.r2g3Converter.setMySQLAdaptor(adaptor);
	}

	private void convertReactomeToGPML(Long dbID, String outputFileName)
			throws Exception {
		GKInstance pathway = adaptor.fetchInstance(dbID);
		convertReactomeToGPML(pathway, outputFileName);
	}

	private void convertReactomeToGPML(GKInstance pathway, String outputFileName)
			throws Exception {
		Long dbID = pathway.getDBID();
		System.out.println("converting pathway #" + dbID + " "
				+ pathway.getDisplayName() + "...");
		// Document doc = r2gConverter.convertPathway(pathway);
//		r2g3Converter.convertPathway(pathway, outputFileName);
		r2g3Converter.convertPathway(pathway, outputFileName);
		// XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
		// outputter.output(doc, new FileOutputStream(outputFileName));
		/*
		 * // Test loading using JDOM and validation SAXBuilder builder = new
		 * SAXBuilder(true);
		 * builder.setFeature("http://apache.org/xml/features/validation/schema"
		 * , true); builder.setProperty(
		 * "http://apache.org/xml/properties/schema/external-schemaLocation",
		 * "http://genmapp.org/GPML/2008a http://svn.bigcat.unimaas.nl/pathvisio/trunk/GPML2008a.xsd"
		 * ); doc = builder.build(new File(outputFileName));
		 */
	}

	private static void printUsage() throws Exception {
		System.out
				.println("Usage: java org.gk.gpml.CLIConverter dbhost dbName user pwd port DB_ID [outputfile]");
		System.out.println();
		System.out
				.println("DB_ID is the Reactome ID of a pathway that has a diagram inside the database.");
	}

	/**
	 * This method is used to dump all human pathway diagrams into a specified
	 * directory.
	 * 
	 * @param dir
	 * @throws Exception
	 */
	public void dumpHumanPathwayDiagrams(File dir) throws Exception {
		File log = new File(dir,"log.txt");
		Collection<?> diagrams = adaptor
				.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
		SchemaClass cls = adaptor.fetchSchema().getClassByName(
				ReactomeJavaConstants.PathwayDiagram);
		SchemaAttribute att = cls
				.getAttribute(ReactomeJavaConstants.representedPathway);
		adaptor.loadInstanceAttributeValues(diagrams, att);
		// Group all human pathways
		for (Iterator<?> it = diagrams.iterator(); it.hasNext();) {
			GKInstance diagram = (GKInstance) it.next();
			GKInstance pathway = (GKInstance) diagram
					.getAttributeValue(ReactomeJavaConstants.representedPathway);
			GKInstance species = (GKInstance) pathway
					.getAttributeValue(ReactomeJavaConstants.species);
			if (species == null)
				continue;
			if (species.getDBID().equals(48887L)) {
				String fileName = getFileName(pathway);
				File[] listOfFiles = dir.listFiles();
				boolean convert = true;
				for (int i = 0; i < listOfFiles.length; i++) {
					
					if((fileName.equalsIgnoreCase(listOfFiles[i].getName()))){
						convert = false;
					}
				}
				if(convert){
					System.out.println(fileName+ "to be converted");
					File file = new File(dir, fileName);
					convertReactomeToGPML(pathway, file.getAbsolutePath());
				}
			}
		}
	}

	/**
	 * A simple helper method to get a file name for gpml output.
	 * 
	 * @param pathway
	 * @return
	 */
	private String getFileName(GKInstance pathway) {
		return pathway.getDisplayName().replaceAll("[^0-9A-Za-z()_-]+", " ")
				+ ".gpml";
	}

	public static void main(String[] args) throws Exception {
//		if (args.length < 6) {
//			// printUsage();
//			System.err
//					.println("Please provide the following parameters in order: dbhost dbName dbUser dbPwd dbPort outputDir");
//			System.exit(1);
//		}
//		// MySQLAdaptor adaptor = new MySQLAdaptor(args[0],
//		// args[1],
//		// args[2],
//		// args[3],
//		// Integer.parseInt(args[4]));
//
//		System.out.println("in");
		MySQLAdaptor adaptor = new MySQLAdaptor("localhost",
				"reactome_convert", "anwesha", "RConver!a",
				Integer.parseInt("3306"));

		CLIConverter converter = new CLIConverter(adaptor);

		File dir = new File("/home/anwesha/ReactomeDatabase/FinalHumanPathways/");
				
		
		    
//		dir.mkdirs();
//		System.out.println("created folder");

//		converter.convertReactomeToGPML((long) 451927, dir.getAbsolutePath());
		
		converter.dumpHumanPathwayDiagrams(dir);
	}
}
