/*
 * This command line interface is used to convert a pathway that has a diagram inside the reactome
 * database to GPML format.
 */

package org.gk.gpml;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.reactome.convert.common.AbstractConverterFromReactome;

public class CLIConverter {
	static File dir = new File(
			"/home/anwesha/ReactomeDatabase/NotFinalReactome48");
	public static void main(String[] args) throws Exception {
		if (args.length < 6) {
			// printUsage();
			System.err
			.println("Please provide the following parameters in order: dbhost dbName dbUser dbPwd dbPort outputDir");
			System.exit(1);
		}

		MySQLAdaptor adaptor = new MySQLAdaptor(args[0], args[1], args[2],
				args[3], Integer.parseInt(args[4]));

		File dir = new File(args[5]);

		dir.mkdirs();

		CLIConverter converter = new CLIConverter(adaptor);
		/*
		 * Boolean true to save ATXML files
		 */

		// converter.convertReactomeToGPMLByID((long) 983231, dir, false);

		converter.dumpHumanPathwayDiagrams(dir, true);

	}

	private static void printUsage() throws Exception {
		System.out
		.println("Usage: java org.gk.gpml.CLIConverter dbhost dbName user pwd port DB_ID [outputfile]");
		System.out.println();
		System.out
		.println("DB_ID is the Reactome ID of a pathway that has a diagram inside the database.");
	}

	private final MySQLAdaptor adaptor;

	private final ReactometoGPML2013 r2g3Converter;

	private final ArrayList<Long> notRenderable;

	private CLIConverter(MySQLAdaptor adaptor) {
		this(adaptor, new ReactometoGPML2013());
	}

	private CLIConverter(MySQLAdaptor adaptor, ReactometoGPML2013 r2g3Converter) {
		notRenderable = new ArrayList<Long>();
		this.adaptor = adaptor;
		this.r2g3Converter = r2g3Converter;
		this.r2g3Converter.setMySQLAdaptor(adaptor);
	}

	private void convertReactomeToGPML(GKInstance pathway,
			File gpmlfilename) {
		Long dbID = pathway.getDBID();
		System.out.println("converting pathway #" + dbID + " "
				+ pathway.getDisplayName() + "...");

		r2g3Converter.convertPathway(pathway, gpmlfilename);

	}

	/**
	 * Convert Reactome pathways using their IDs
	 * 
	 * @param dbID
	 *            Stable ID of the pathway
	 * @param dir
	 *            Directory to save converted gpml file
	 * @param saveatxml
	 *            Boolean true if atxml files should be saved as well
	 */
	public void convertReactomeToGPMLByID(Long dbID, File dir,
			Boolean saveatxml) {

		GKInstance pathway;
		try {
			pathway = adaptor.fetchInstance(dbID);
			String fileName = AbstractConverterFromReactome
					.getFileName(pathway);
			File atxmlfile = new File(dir, fileName + ".atxml");
			atxmlfile.createNewFile();
			if (saveatxml) {
				r2g3Converter.queryATXML(pathway, atxmlfile);
			}
			File gpmlfile = new File(dir, fileName + ".gpml");
			gpmlfile.createNewFile();
			convertReactomeToGPML(pathway, gpmlfile);
		} catch (Exception e1) {
			notRenderable.add(dbID);
			System.out.println("Not renderable... moving on");
		}
	}

	/**
	 * This method is used to dump all human pathway diagrams into a specified
	 * directory.
	 * 
	 * @param dir
	 *            Folder to save result gpml files
	 * @param saveatxml
	 *            true if atxml files should be saved as well
	 * @throws Exception
	 */

	public void dumpHumanPathwayDiagrams(File dir, Boolean saveatxml)
	{


		Collection<?> diagrams;
		try {
			diagrams = adaptor
					.fetchInstancesByClass(ReactomeJavaConstants.PathwayDiagram);
			SchemaClass cls = adaptor.fetchSchema().getClassByName(
					ReactomeJavaConstants.PathwayDiagram);
			SchemaAttribute att = cls
					.getAttribute(ReactomeJavaConstants.representedPathway);
			adaptor.loadInstanceAttributeValues(diagrams, att);
			// Group all human pathways
			for (Object name : diagrams) {
				GKInstance diagram = (GKInstance) name;
				GKInstance pathway = (GKInstance) diagram
						.getAttributeValue(ReactomeJavaConstants.representedPathway);
				GKInstance species = (GKInstance) pathway
						.getAttributeValue(ReactomeJavaConstants.species);
				if (species == null) {
					continue;
				}
				if (species.getDBID().equals(48887L)) {
					String fileName = AbstractConverterFromReactome
							.getFileName(pathway);
					String gpmlfile = fileName + ".gpml";
					File[] listOfFiles = dir.listFiles();
					boolean convert = true;
					for (File listOfFile : listOfFiles)
						if (gpmlfile.equalsIgnoreCase(listOfFile.getName())) {
							convert = false;
						}
					if (convert) {

						Long id = pathway.getDBID();
						convertReactomeToGPMLByID(id, dir,
								true);
					}
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Not rendered" + notRenderable);
	}

}

