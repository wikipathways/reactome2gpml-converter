/*
 * This command line interface is used to convert a pathway that has a diagram inside the reactome
 * database to GPML format.
 */

package org.gk.gpml;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.reactome.convert.common.AbstractConverterFromReactome;

/**
 * The main class for the Reactome Converter
 * 
 * @author anwesha
 * 
 */
public class CLIConverter {

	public static void main(String[] args) throws Exception {
		if (args.length < 7) {
			// printUsage();
			System.err
					.println("Please provide the following parameters in order: dbhost dbName dbUser dbPwd dbPort outputDir species");
			System.exit(1);
		}

		MySQLAdaptor adaptor = new MySQLAdaptor(args[0], args[1], args[2],
				args[3], Integer.parseInt(args[4]));

		File dir = new File(args[5]);
		String species = args[6];

		dir.mkdirs();

		CLIConverter converter = new CLIConverter(adaptor);
		/*
		 * Boolean true to save ATXML files
		 */

		/*
		 * Abacavir transport (Test pathway)
		 */
//		 converter.convertReactomeToGPMLByID((long) 73884, dir, false);
				
		//converter.convertReactomeToGPMLByID((long) 5602358, dir, false);
		//converter.convertReactomeToGPMLByID((long) 73857, dir, false);
		//converter.convertReactomeToGPMLByID((long) 5602358, dir, false);
	

//		for (int i : ids3) {
//			converter.convertReactomeToGPMLByID((long) i, dir, false);
//		}
		//
		
//		 converter.convertPathwayDiagrams(dir, species, true);
		 
//		 converter.convertReactomeToGPMLByID((long) 975155, dir, false);
//		 converter.convertReactomeToGPMLByID((long) 5602358, dir, false);
//		 converter.convertPlantPathwayDiagrams(dir, false);
//		 converter.getSpeciesDbID();
		converter.getReactomeDbID(species);
	}

	private long speciescode;

	private void convertPathwayDiagrams(File dir, String species, boolean b) {
		if(species.equalsIgnoreCase("Human")){
			speciescode = 48887L;
		}else{
			if(species.equalsIgnoreCase("Rice")){
				speciescode = 186860;
			}else{
				if(species.equalsIgnoreCase("Maize")){
					speciescode = 5402224;
				}else{
					if(species.equalsIgnoreCase("Arabidopsis")){
						speciescode = 5398000;
					}else{
						if(species.equalsIgnoreCase("All")){
							 convertPlantPathwayDiagrams(dir, false);
						}
					}
				}
			}
		}
		dumpPathwayDiagrams(dir, speciescode, b);
		
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

	private void convertReactomeToGPML(GKInstance pathway, File gpmlfilename) {
		Long dbID = pathway.getDBID();
		System.out.println("converting pathway #" + dbID + " "
				+ pathway.getDisplayName() + "...");
		if (!r2g3Converter.convertPathway(pathway, dbID, gpmlfilename)) {
			notRenderable.put(dbID, pathway.getDisplayName());
			gpmlfilename.delete();
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
	public void convertReactomeToGPMLByID(Long dbID, File dir, Boolean saveatxml) {

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

	public void dumpPathwayDiagrams(File dir, long l, Boolean saveatxml) {
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
				if (species.getDBID().equals(l)) {
					String fileName = AbstractConverterFromReactome
							.getFileName(pathway);
					String gpmlfile = fileName + ".gpml";
					File[] listOfFiles = dir.listFiles();
					boolean convert = true;
					for (File listOfFile : listOfFiles)
						if (gpmlfile.equalsIgnoreCase(listOfFile.getName())) {
							System.out.println("Skipping  "+gpmlfile);
							convert = false;
						}
					if (convert) {
						Long id = pathway.getDBID();
						convertReactomeToGPMLByID(id, dir, saveatxml);
					}
				}
			}
			} catch (Exception e) {
			e.printStackTrace();
		}
//		System.out.println("Not rendered" + notRenderable);
	}

	/**
	 * This method gets the DB id for the species
	 * 
	 * @throws Exception
	 */

	public void getSpeciesDbID() {
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
					System.out.println(species.getDBID() + "\t"
							+ species.getDisplayName());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}
private Long getSpeciesCode(String species){
	if(species.equalsIgnoreCase("Human")){
		speciescode = 48887L;
	}else{
		if(species.equalsIgnoreCase("Rice")){
			speciescode = 186860;
		}else{
			if(species.equalsIgnoreCase("Maize")){
				speciescode = 5402224;
			}else{
				if(species.equalsIgnoreCase("Arabidopsis")){
					speciescode = 5398000;
				}
			}
		}
	}
	return speciescode;
}
	/**
	 * This method gets the DB id for all pathways
	 * @param species 
	 * 
	 * @throws Exception
	 */

	public void getReactomeDbID(String speciesName) {
		int count = 0;
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
				String fileName = AbstractConverterFromReactome
						.getFileName(pathway);
				if (pathway != null) {
					if (species.getDBID().equals(getSpeciesCode(speciesName))) {
						System.out.println(pathway.getDBID()+"\t"+fileName);	
						count++;
					}
					
				}
				
				}
			} catch (Exception e) {
			e.printStackTrace();
		}
		System.out.println(count);
		System.out.println("Done!");
	}
}
