/*
 * This command line interface is used to convert a pathway that has a diagram inside the reactome
 * database to GPML format.
 */

package org.gk.gpml;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.reactome.convert.common.AbstractConverterFromReactome;

public class CLIConverter {

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

		converter.convertReactomeToGPMLByID((long) 69620, dir, false);
		// converter.convertReactomeToGPMLByID((long) 73857, dir, false);
		// converter.convertReactomeToGPMLByID((long) 2032785, dir, false);

		// converter.dumpHumanPathwayDiagrams(dir, false);
		// converter.convertPlantPathwayDiagrams(dir, false);
		// converter.getSpeciesDbID();
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

	private final Map<Long, String> notRenderable;
	private final Map<Long, String> Renderable;

	private CLIConverter(MySQLAdaptor adaptor) {
		this(adaptor, new ReactometoGPML2013());
	}

	private CLIConverter(MySQLAdaptor adaptor, ReactometoGPML2013 r2g3Converter) {
		notRenderable = new HashMap<Long, String>();
		Renderable = new HashMap<Long, String>();
		this.adaptor = adaptor;
		this.r2g3Converter = r2g3Converter;
		this.r2g3Converter.setMySQLAdaptor(adaptor);
	}

	private void convertPlantPathwayDiagrams(File dir, boolean saveatxml) {
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
				} else {

					try {
						String fileName = AbstractConverterFromReactome
								.getFileName(pathway);
						if (saveatxml) {
							File atxmlfile = new File(dir, fileName + ".atxml");
							atxmlfile.createNewFile();
							r2g3Converter.queryATXML(pathway, atxmlfile);
						}
						File gpmlfile = new File(dir, fileName + ".gpml");
						gpmlfile.createNewFile();
						convertReactomeToGPML(pathway, gpmlfile);
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void convertReactomeToGPML(GKInstance pathway,
			File gpmlfilename) {
		Long dbID = pathway.getDBID();
		System.out.println("converting pathway #" + dbID + " "
				+ pathway.getDisplayName() + "...");
		if (!r2g3Converter.convertPathway(pathway, gpmlfilename)) {
			notRenderable.put(dbID, pathway.getDisplayName());
		} else {
			Renderable.put(dbID, pathway.getDisplayName());
		}
		System.out.println("Not Rendered " + notRenderable);
		System.out.println("Rendered " + Renderable);

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
			if (saveatxml) {
				File atxmlfile = new File(dir, fileName + ".atxml");
				atxmlfile.createNewFile();
				r2g3Converter.queryATXML(pathway, atxmlfile);
			}
			File gpmlfile = new File(dir, fileName + ".gpml");
			gpmlfile.createNewFile();
			convertReactomeToGPML(pathway, gpmlfile);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void dumpHumanPathwayDiagrams(File dir, Boolean saveatxml)
	{
		notRenderable.clear();
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
				// for (int i = 0; i <= 5; i++) {
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
								saveatxml);
					}
				}
			}
			// }

		} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println("Not rendered" + notRenderable);
	}

	/**
	 * This method gets the DB id for the species
	 * 
	 * @throws Exception
	 */

	public void getSpeciesDbID()
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
				else {
					System.out.println(species.getDBID() + "\t"
							+ species.getDisplayName());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

}

