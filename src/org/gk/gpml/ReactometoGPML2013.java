package org.gk.gpml;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.bridgedb.DataSource;
import org.bridgedb.DataSource.Builder;
import org.bridgedb.bio.BioDataSource;
import org.bridgedb.bio.DataSourceTxt;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.render.HyperEdge;
import org.gk.render.Node;
import org.gk.render.Note;
import org.gk.render.ProcessNode;
import org.gk.render.Renderable;
import org.gk.render.RenderableChemical;
import org.gk.render.RenderableCompartment;
import org.gk.render.RenderableComplex;
import org.gk.render.RenderableEntity;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableProtein;
import org.gk.render.RenderableRNA;
import org.gk.schema.InvalidAttributeException;
import org.jdom.Document;
import org.pathvisio.core.biopax.BiopaxElement;
import org.pathvisio.core.biopax.PublicationXref;
import org.pathvisio.core.model.ConnectorType;
import org.pathvisio.core.model.DataNodeType;
import org.pathvisio.core.model.GroupStyle;
import org.pathvisio.core.model.LineStyle;
import org.pathvisio.core.model.LineType;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.model.PathwayElement.MAnchor;
import org.pathvisio.core.model.PathwayElement.MPoint;
import org.pathvisio.core.model.ShapeType;
import org.pathvisio.core.model.StaticProperty;
import org.pathvisio.core.model.ValignType;
import org.pathvisio.core.view.MIMShapes;
import org.reactome.convert.common.AbstractConverterFromReactome;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Converts Reactome pathways to GPML 2013a format. Both DataNodes and
 * Interactions are annotated. Complexes are added as groups of nodes to the
 * bottom of the pathway diagram.
 * 
 * @author anwesha
 * 
 */

public class ReactometoGPML2013 extends AbstractConverterFromReactome {

	private static final int INITIAL_WIDTH = 100;
	private static final int INITIAL_HEIGHT = 20;
	private static final int MEDIUM_INITIAL_HEIGHT = 40;
	private static final int COMPONENTS_ROWS = 20;
	private static final int NO_OF_COMPLEXES = 10;
	private static final int LONG_INITIAL_HEIGHT = 50;
	private static final int COL_GAP = 40;
	private static int NEW_COMPLEX_GAP = 40;

	String humanReactomeURLpre = "\n View original pathway at [http://www.reactome.org/PathwayBrowser/#DIAGRAM=";
	String humanReactomeURLpost = " Reactome].";
	String plantReactomeURLpre = "\nSource: [http://www.reactome.org/"
			+ "PathwayBrowser/#DB=gk_current&FOCUS_SPECIES_ID=48887&FOCUS_"
			+ "PATHWAY_ID=";
	String plantReactomeURLpost = " Plant Reactome].";

	private final String COMPLEX_ID = "complex_id";
	private final String COPIES_NUM = "copies_num";
	private final String LOCATION = "cellular_location";

	HashMap<Long, ArrayList<String>> r2gNodeList = new HashMap<Long, ArrayList<String>>();
	List<HyperEdge> edges = new ArrayList<HyperEdge>();
	List<RenderableCompartment> compartments = new ArrayList<RenderableCompartment>();

	List<Note> notes = new ArrayList<Note>();
	ArrayList<String> complexComponentIDs = new ArrayList<String>();
	ArrayList<PathwayElement> pwyEleList = new ArrayList<PathwayElement>();
	HashMap<String, Integer> complexComponentIDMap = new HashMap<String, Integer>();

	double y = 0;
	Pathway gpmlpathway;
	BiopaxElement elementManager;
	PathwayElement mappInfo = null;
	PathwayElement infoBox = null;
	PathwayElement parentComplex = null;
	PathwayElement pwyelement = null;
	PathwayElement datanode = null;
	PathwayElement interaction = null;
	PathwayElement label = null;
	PathwayElement shape = null;
	final String DATA_SOURCE = "Reactome - http://www.reactome.org";
	final String PLANT_DATA_SOURCE = "Plant Reactome - http://plantreactome.gramene.org";
	final String PLANT_VERSION = "43";

	/*
	 * Adding comments
	 */
	private void addComments(GKInstance instance, PathwayElement pwyele,
			boolean mainTag, boolean isPlantReactome) {
		String reactomeString = "";
		String wpcomment = "";
		String reactomeID = "";

		if (instance.getSchemClass().isValidAttribute(
				ReactomeJavaConstants.summation)) {
			List<GKInstance> summations;
			try {
				summations = instance
						.getAttributeValuesList(ReactomeJavaConstants.summation);
				if (summations != null && summations.size() > 0) {
					for (GKInstance summation : summations) {
						wpcomment = (String) summation
								.getAttributeValue(ReactomeJavaConstants.text);

					}
				}
				if (mainTag) {
					if (isPlantReactome) {
						reactomeString = plantReactomeURLpre
								+ instance.getDBID() + plantReactomeURLpost;
					} else {
						reactomeString = humanReactomeURLpre
								+ instance.getDBID() + humanReactomeURLpost;
					}

					wpcomment = wpcomment + reactomeString;
					reactomeID = "Pathway is converted from Reactome ID: " 
							+ instance.getDBID();
					
					
					pwyele.addComment(wpcomment, "WikiPathways-description");
					
					pwyele.addComment(reactomeID, "Reactome-Converter");
//					pwyele.addComment("61", "Version");
					
				} else {
					if (wpcomment != null && wpcomment.length() > 0) {
						pwyele.addComment(wpcomment, "Reactome");
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}
	
	
	private void addCommentsVersion(String version, PathwayElement pwyele,
			boolean mainTag, boolean isPlantReactome) {

		String reactomeVersion = "";

					reactomeVersion = "Reactome version: " 
							+ version;
								
//					pwyele.addComment(wpcomment + "\n" + "Pathway is converted from "
//							+ "Reactome ID: " 
//							+ instance.getDBID(), "WikiPathways-description");
					
					pwyele.addComment(reactomeVersion, "Reactome-version");

	}

	private void addCommentsAuthor(String authors, PathwayElement pwyele,
			boolean mainTag, boolean isPlantReactome) {

		String reactomeAuthor = "";

					reactomeAuthor = "Reactome Author: " 
							+ authors;
								
//					pwyele.addComment(wpcomment + "\n" + "Pathway is converted from "
//							+ "Reactome ID: " 
//							+ instance.getDBID(), "WikiPathways-description");
					
					pwyele.addComment(reactomeAuthor, "Reactome Author");

	}
	
		

	private PathwayElement addGraphicsElm(Node node, PathwayElement pwyele) {
		Rectangle bounds = node.getBounds();
		PathwayElement pwyElement = pwyele;
		pwyele.setMCenterX(bounds.getCenterX());
		pwyele.setMCenterY(bounds.getCenterY());
		pwyele.setMWidth(bounds.getWidth());
		pwyele.setMHeight(bounds.getHeight());
		pwyele.setValign(ValignType.MIDDLE);
		return pwyElement;
	}

	/*
	 * Adding literature references
	 */
	private void addLitRef(GKInstance instance, PathwayElement pwyele) {
		if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.literatureReference)) {
			List<GKInstance> litRefs;
			try {
				litRefs = instance.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
				if (litRefs != null && litRefs.size() > 0) {
					for (GKInstance litRef : litRefs)
						if (litRef.getSchemClass().isValidAttribute(
								ReactomeJavaConstants.pubMedIdentifier)
								&& litRef
										.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier) != null) {
							String pubId = litRef.getAttributeValue(
									ReactomeJavaConstants.pubMedIdentifier)
									.toString();
							PublicationXref pubref = new PublicationXref();
							pubref.setPubmedId(pubId);

							/*
							 * Getting title and author using PMC Rest
							 */
							DocumentBuilderFactory dbf = DocumentBuilderFactory
									.newInstance();
							DocumentBuilder db = dbf.newDocumentBuilder();
							XPath xPath = XPathFactory.newInstance().newXPath();
							String urlString = "https://www.ebi.ac.uk/europepmc/webservices/rest/search?query=ext_id:"
									+ pubId + "%20src:med";
							org.w3c.dom.Document publication = db
									.parse(new URL(urlString).openStream());

							/*
							 * Get and set Publication title
							 */
							String xpathExpression = "responseWrapper/resultList/result/title";
							String pubTitle = xPath.compile(xpathExpression)
									.evaluate(publication);
							pubref.setTitle(pubTitle);

							/*
							 * Get and set Authors
							 */
							String xpathExpression2 = "responseWrapper/resultList/result/authorString";
							String authors = xPath.compile(xpathExpression2)
									.evaluate(publication);
							pubref.setAuthors(authors);

							elementManager.addElement(pubref);
							pwyele.addBiopaxRef(pubref.getId());
						}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

	}

	/*
	 * Adding multiple graphids for corresponding Reactome ids
	 */
	private void addValues(Long key, String value) {
		ArrayList<String> tempList = null;
		if (r2gNodeList.containsKey(key)) {
			tempList = r2gNodeList.get(key);
			if (tempList == null) {
				tempList = new ArrayList<String>();
			}
			tempList.add(value);
		} else {
			tempList = new ArrayList<String>();
			tempList.add(value);
		}
		r2gNodeList.put(key, tempList);
	}

	private void addXref(PathwayElement pwyele, Long rId, GKInstance instance)
			throws Exception {
		/*
		 * Try to get ReferenceEntity
		 */
		GKInstance referenceEntity = null;
		if (instance != null
				&& instance.getSchemClass().isValidAttribute(
						ReactomeJavaConstants.referenceEntity)) {
			referenceEntity = (GKInstance) instance
					.getAttributeValue(ReactomeJavaConstants.referenceEntity);
		}

		if (referenceEntity == null) {
			/*
			 * Use Reactome as default if no reference entity can be found
			 */

			pwyele.setDataSource(DataSource.getExistingBySystemCode("Re"));
			String id = instance == null ? rId.toString()
					: getReactomeId(instance);
			if (id == null) {
				id = instance.getDBID().toString();
			}
			/*
			 * Patch version 53 onwards, stable identifiers have been changed in
			 * reactome
			 */
			// if (id.contains("REACT_")) {
			pwyele.setElementID(id);
			// } else {
			// pwyele.setElementID("REACT_" + id);
			// }

		} else {
			GKInstance db = (GKInstance) referenceEntity
					.getAttributeValue(ReactomeJavaConstants.referenceDatabase);

			/*
			 * ChEBI and uniprot give errors while searching by full name
			 * This also happens for PubChem Compound (should be PubChem-Compound)
			 * And NCBI Nucleotide (which should link to NCBI Protein).
			 * New addition: "IUPHAR" for drugs
			 */
			String identifier = getIdentifierFromReferenceEntity(referenceEntity);
			if (db.getDisplayName().equalsIgnoreCase("chebi")) {
				pwyele.setDataSource(DataSource.getExistingBySystemCode("Ce"));
				identifier = "CHEBI:" + identifier;
			} else if (db.getDisplayName().equalsIgnoreCase("uniprot")) {
				pwyele.setDataSource(DataSource.getExistingBySystemCode("S"));
			} else if (db.getDisplayName().equalsIgnoreCase("ENSEMBL")) {
				pwyele.setDataSource(DataSource.getExistingBySystemCode("En"));
			} else if (db.getDisplayName().equalsIgnoreCase("miRBase")) {
				pwyele.setDataSource(DataSource.getExistingBySystemCode("Mbm"));
			} else if (db.getDisplayName().equalsIgnoreCase("PubChem Compound")) {
				pwyele.setDataSource(DataSource.getExistingBySystemCode("Cpc"));
			} else if (db.getDisplayName().equalsIgnoreCase("IUPHAR")) {
				pwyele.setDataSource(DataSource.getExistingBySystemCode("Gpl"));
			} else if (db.getDisplayName().equalsIgnoreCase("NCBI Nucleotide")) {
				pwyele.setDataSource(DataSource.getExistingBySystemCode("Np"));
			} else {
				pwyele.setDataSource(DataSource.getExistingByFullName(db
						.getDisplayName()));
			}
			pwyele.setElementID(identifier);
		}

	}

	private void convertCompartment(RenderableCompartment compartment,
			PathwayElement groupElm) {
		// System.out.println("Converting Compartments to Shapes ...");
		shape = PathwayElement.createPathwayElement(ObjectType.SHAPE);
		gpmlpathway.add(shape);
		shape.setGeneratedGraphId();
		shape.setGroupRef(groupElm.getGroupId());
		shape.setShapeType(ShapeType.ROUNDED_RECTANGLE);
		shape.setLineStyle(LineStyle.SOLID);
		shape.setLineThickness(3);
		shape.setTransparent(true);
		shape.setColor(new Color(192, 192, 192));
		addGraphicsElm(compartment, shape);

	}

	private ArrayList<String> createComplexMemberList(GKInstance inst,
			Boolean subpart) {
		try {
			String type = parseIdentifier(inst.getExtendedDisplayName())[0];
//			System.out.println("complex :"+type);
			if (type.equalsIgnoreCase("complex")) {
				List<GKInstance> complexComponentList = inst
						.getAttributeValuesList(ReactomeJavaConstants.hasComponent);
				for (int i = 0; i < complexComponentList.size(); i++) {
					GKInstance complexComponent = complexComponentList.get(i);
					type = parseIdentifier(complexComponent
							.getExtendedDisplayName())[0];
//					System.out.println("within complex : "+type);
					if (type.equalsIgnoreCase("complex")) {
						createComplexMemberList(complexComponent, true);
					} else if (type.equalsIgnoreCase("definedset")
							|| type.equalsIgnoreCase("candidateset")
							|| type.equalsIgnoreCase("openset")) {
						createEntitySetMemberList(complexComponent, true);
					} else {
						complexComponentIDs.add(complexComponent.getDBID()
								.toString());
					}
				}
			}
			 else if
			 (type.equalsIgnoreCase("definedset")||type.equalsIgnoreCase("candidateset")||type.equalsIgnoreCase("openset")){
			 createEntitySetMemberList(inst, true);
			 }
			else {
				complexComponentIDs.add(inst.getDBID()
						.toString());
			}

		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return complexComponentIDs;

	}

	private ArrayList<String> convertComplexWS(Long rId, Boolean subpart) {
		String cp = "mainCom";
		if (subpart) {
			cp = "subCom";
		}
		String urlString = "http://reactomews.oicr.on.ca:8080/ReactomeRESTfulAPI/"
				+ "RESTfulWS/queryById/Complex/" + rId;
		URL url;
		try {
			url = new URL(urlString);
			URLConnection conn = url.openConnection();
			conn.setRequestProperty("Accept", "application/xml");
			DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
			DocumentBuilder db = dbf.newDocumentBuilder();
			org.w3c.dom.Document reactomeComplex = db.parse(conn
					.getInputStream());
			XPath xPath = XPathFactory.newInstance().newXPath();

			if (reactomeComplex.getDocumentElement().getNodeName()
					.equalsIgnoreCase("complex")
					|| reactomeComplex.getDocumentElement().getNodeName()
							.equalsIgnoreCase("definedSet")
					|| reactomeComplex.getDocumentElement().getNodeName()
							.equalsIgnoreCase("candidateSet")) {
				if (reactomeComplex.getDocumentElement().getNodeName()
						.equalsIgnoreCase("complex")) {
					NodeList reactomeComplexCompartmentNodes = (NodeList) xPath
							.compile("/complex/hasComponent").evaluate(
									reactomeComplex, XPathConstants.NODESET);
					String complexId = xPath.compile("/complex/dbId").evaluate(
							reactomeComplex);
					complexComponentIDs.add(cp + complexId);

					for (int a = 0; a < reactomeComplexCompartmentNodes
							.getLength(); a++) {
						/*
						 * Getting child components
						 */
						String memId = reactomeComplexCompartmentNodes.item(a)
								.getFirstChild().getTextContent();
						Long memIdlong = Long.parseLong(memId, 10);
						convertComplexWS(memIdlong, true);

					}
				} else if (reactomeComplex.getDocumentElement().getNodeName()
						.equalsIgnoreCase("candidateSet")) {
					NodeList reactomeComplexCompartmentNodes = (NodeList) xPath
							.compile("/candidateSet/hasMember").evaluate(
									reactomeComplex, XPathConstants.NODESET);
					NodeList reactomeCCNodes = (NodeList) xPath.compile(
							"/candidateSet/hasCandidate").evaluate(
							reactomeComplex, XPathConstants.NODESET);

					String complexId = xPath.compile("/candidateSet/dbId")
							.evaluate(reactomeComplex);
					complexComponentIDs.add(cp + complexId);

					for (int a = 0; a < reactomeComplexCompartmentNodes
							.getLength(); a++) {
						/*
						 * Getting child components
						 */
						String memId = reactomeComplexCompartmentNodes.item(a)
								.getFirstChild().getTextContent();
						Long memIdlong = Long.parseLong(memId, 10);
						convertComplexWS(memIdlong, true);

					}
					for (int a = 0; a < reactomeCCNodes.getLength(); a++) {
						/*
						 * Getting child components
						 */
						String memId = reactomeCCNodes.item(a).getFirstChild()
								.getTextContent();
						Long memIdlong = Long.parseLong(memId, 10);
						convertComplexWS(memIdlong, true);

					}
				} else if (reactomeComplex.getDocumentElement().getNodeName()
						.equalsIgnoreCase("definedSet")) {
					NodeList definedSetCompartments = (NodeList) xPath.compile(
							"/definedSet/hasMember").evaluate(reactomeComplex,
							XPathConstants.NODESET);
					String complexId = xPath.compile("/definedSet/dbId")
							.evaluate(reactomeComplex);

					complexComponentIDs.add(cp + complexId);

					for (int a = 0; a < definedSetCompartments.getLength(); a++) {
						/*
						 * Getting child components
						 */
						String memId = definedSetCompartments.item(a)
								.getFirstChild().getTextContent();
						Long memIdlong = Long.parseLong(memId, 10);
						convertComplexWS(memIdlong, true);
					}
				}
			} else if (reactomeComplex.getDocumentElement().getNodeName()
					.equalsIgnoreCase("entityWithAccessionedSequence")) {
				complexComponentIDs.add("pro" + rId.toString());
			} else if (reactomeComplex.getDocumentElement().getNodeName()
					.equalsIgnoreCase("simpleEntity")) {
				complexComponentIDs.add("met" + rId.toString());
			} else if (reactomeComplex.getDocumentElement().getNodeName()
					.equalsIgnoreCase("genomeEncodedEntity")) {
				complexComponentIDs.add("pro" + rId.toString());
			}
		} catch (MalformedURLException e) {
			System.out
					.println("Error getting complex components using Reactome Webservice ...");
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (XPathExpressionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SAXException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return complexComponentIDs;
	}

	private void convertEdge(HyperEdge edge) throws Exception {
		if (edge.getReactomeId() != null) {
			GKInstance inst;
			try {

				List<Point> points = edge.getBackbonePoints();

				inst = dbAdaptor.fetchInstance(edge.getReactomeId());
				PathwayElement backboneInteraction = createSegmentedLine(edge,
						"Line", points, false);
				if (backboneInteraction != null) {
					gpmlpathway.add(backboneInteraction);
					backboneInteraction.setGeneratedGraphId();
					addComments(inst, backboneInteraction, false, false);
					addLitRef(inst, backboneInteraction);
				}
				handleInputs(edge, backboneInteraction);
				handleOutputs(edge, backboneInteraction);
				handleCatalysts(edge, backboneInteraction);
				handleInhibitors(edge, backboneInteraction);
				handleActivators(edge, backboneInteraction);
			} catch (Exception e) {
				System.out
						.println("Error converting Hyperedges to Interactions ...");
				e.printStackTrace();
			}
		}
	}

	private ArrayList<String> createEntitySetMemberList(GKInstance inst,
			boolean subpart) {
		try {
			String setType = parseIdentifier(inst.getExtendedDisplayName())[0];
//			System.out.println("entityset : "+setType);
			if (setType.equalsIgnoreCase("CandidateSet")
					|| setType.equalsIgnoreCase("DefinedSet")
					|| setType.equalsIgnoreCase("OpenSet")) {
				List<GKInstance> setComponentList = inst
						.getAttributeValuesList(ReactomeJavaConstants.hasMember);
				if (setType.equalsIgnoreCase("CandidateSet")) {
					setComponentList
							.addAll(inst
									.getAttributeValuesList(ReactomeJavaConstants.hasCandidate));
				}
				for (int i = 0; i < setComponentList.size(); i++) {
					GKInstance setComponent = setComponentList.get(i);
					setType = parseIdentifier(setComponent
							.getExtendedDisplayName())[0];
					if (setType.equalsIgnoreCase("Complex")) {
//						System.out.println(setType+" found");
						createComplexMemberList(setComponent, true);
					} else if (setType.equalsIgnoreCase("EntitySet")) {
//						System.out.println(setType+" found in es");
						createEntitySetMemberList(setComponent, true);
					} else {
						complexComponentIDs.add(setComponent.getDBID()
								.toString());
					}
				}
			} 
			else if (setType.equalsIgnoreCase("Complex")) {
				System.out.println(setType+ " : found");
//				System.out.println("complex found");
				createComplexMemberList(inst, true);
			} 
			else {
				System.out.println(setType + " not converted");
				complexComponentIDs.add(inst.getDBID()
						.toString());
			}
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return complexComponentIDs;
	}

	private PathwayElement convertNode(Node node) {
		if (node.getReactomeId() != null) {
			GKInstance inst;
			try {
				inst = dbAdaptor.fetchInstance(node.getReactomeId());
				datanode = PathwayElement
						.createPathwayElement(ObjectType.DATANODE);
				gpmlpathway.add(datanode);
				datanode.setGeneratedGraphId();
				addValues(node.getReactomeId(), datanode.getGraphId());
				// System.out.println(node.getType());
				if (inst != null)
//					System.out.println("nodes in the pathway "+node.getType());
				if (node.getType().equalsIgnoreCase("Gene")) {
					datanode.setDataNodeType(DataNodeType.GENEPRODUCT);
				} else if (node.getType().equalsIgnoreCase("Protein")) {
						datanode.setDataNodeType(DataNodeType.PROTEIN);
					} else if (node instanceof RenderableRNA) {
						/*
						 * Changed RNA colour to purple
						 */
						datanode.setDataNodeType(DataNodeType.RNA);
						datanode.setColor(new Color(153, 0, 153));
					} else if (node.getType().equalsIgnoreCase("Compound")) {
						datanode.setDataNodeType(DataNodeType.METABOLITE);
						datanode.setColor(Color.BLUE);
					} 
					else if (node.getType().equalsIgnoreCase("Complex")) {
						/*
						 * Changed Complex colour to brown and shape to hexagon
						 */
						complexComponentIDs.clear();
						datanode.setDataNodeType(DataNodeType.COMPLEX);
						datanode.setColor(new Color(165, 42, 42));
						datanode.setShapeType(ShapeType.HEXAGON);
						// Long rId = node.getReactomeId();
						// ArrayList<String> complexComponentIDList =
						// convertComplex(
						// rId, false);
						ArrayList<String> complexComponentIDList = createComplexMemberList(
								inst, false);
						complexComponentIDList = countCopies(complexComponentIDList);
						drawComplexMembers(complexComponentIDList,
								getReactomeId(inst), true);
					}
					else if (node.getType().equalsIgnoreCase("EntitySet")) {
						complexComponentIDs.clear();
						datanode.setDataNodeType(DataNodeType.COMPLEX);
						datanode.setColor(new Color(165, 42, 42));
						datanode.setShapeType(ShapeType.ROUNDED_RECTANGLE);
						// Long rId = node.getReactomeId();
						// ArrayList<String> complexComponentIDList =
						// convertComplex(
						// rId, false);
						ArrayList<String> complexComponentIDList = createEntitySetMemberList(
								inst, false);

						complexComponentIDList = countCopies(complexComponentIDList);
						drawComplexMembers(complexComponentIDList,
								getReactomeId(inst), false);
					} 
					else if (node instanceof ProcessNode) {
						datanode.setDataNodeType(DataNodeType.PATHWAY);
						datanode.setColor(new Color(20, 150, 30));
						datanode.setLineThickness(0);
						datanode.setLineStyle(LineStyle.DOUBLE);
					} else {
						datanode.setDataNodeType(DataNodeType.UNKOWN);
					}
				addXref(datanode, node.getReactomeId(), inst);
				addComments(inst, datanode, false, false);
				
				addLitRef(inst, datanode);
				datanode.setDynamicProperty(LOCATION,
						(parseLocation(inst.getDisplayName())[1]));
				datanode.setTextLabel(refineDisplayNames(node.getDisplayName()));
				addGraphicsElm(node, datanode);
			} catch (Exception e) {
				System.out
						.println("Error with converting Nodes to Datanodes ...");
				e.printStackTrace();
			}
		}
		return datanode;
	}

	@Override
	public Document convertPathway(GKInstance pathway) throws Exception {
		return null;
	}

	/*
	 * Adding complex compartments to the pathway
	 */
	// private void createComplexMembers(
	// ArrayList<String> complexComponentIDString, String parentComplexId,
	// boolean isComplex) {
	// for (int count = 0; count < complexComponentIDString.size(); count++) {
	// String complexname = complexComponentIDString.get(count);
	// String unparsedcomplexname = complexname;
	// GKInstance inst;
	// try {
	// if (complexname.startsWith("mainCom")) {
	// complexname = complexname.replaceFirst("mainCom", "");
	// Long rId = Long.parseLong(complexname);
	// inst = dbAdaptor.fetchInstance(rId);
	//
	// // Creating Group Element for grouping complex components
	// complexmem = PathwayElement
	// .createPathwayElement(ObjectType.GROUP);
	//
	// gpmlpathway.add(complexmem);
	// complexmem.setGeneratedGraphId();
	// complexmem.setGroupId(gpmlpathway.getUniqueGroupId());
	// if(isComplex){
	// complexmem.setGroupStyle(GroupStyle.COMPLEX);
	// }else{
	// complexmem.setGroupStyle(GroupStyle.NONE);
	// }
	//
	// complexmem.setDynamicProperty(COMPLEX_ID,
	// getReactomeId(inst));
	//
	// } else
	// /*
	// * Creating Complex member DataNodes
	// */
	// {
	// pwyelement = PathwayElement
	// .createPathwayElement(ObjectType.DATANODE);
	// if (complexname.startsWith("subCom")) {
	// // complexname = complexname.replaceFirst("subCom", "");
	// // pwyelement.setDataNodeType(DataNodeType.COMPLEX);
	// // pwyelement.setColor(new Color(165, 42, 42));
	// // pwyelement.setShapeType(ShapeType.HEXAGON);
	// } else {
	// if (complexname.startsWith("pro")) {
	// complexname = complexname.replaceFirst("pro", "");
	// pwyelement.setDataNodeType(DataNodeType.PROTEIN);
	// } else if (complexname.startsWith("met")) {
	// complexname = complexname.replaceFirst("met", "");
	// pwyelement.setDataNodeType(DataNodeType.METABOLITE);
	// pwyelement.setColor(Color.BLUE);
	// }
	// gpmlpathway.add(pwyelement);
	// pwyelement.setGeneratedGraphId();
	//
	// Long rId = Long.parseLong(complexname);
	// inst = dbAdaptor.fetchInstance(rId);
	// String[] location = parseLocation(inst.getDisplayName());
	// String displayname = refineDisplayNames(location[0]);
	//
	// pwyelement.setTextLabel(displayname);
	//
	// pwyelement.setGroupRef(complexmem.getGroupId());
	// /*
	// * Adding Dynamic properties COMPLEX_ID : for connecting
	// * complex and components COPIES_NUM : for counting copies
	// * of a complex component LOCATION : for cellular location
	// */
	// pwyelement.setDynamicProperty(COMPLEX_ID,
	// complexmem.getDynamicProperty(COMPLEX_ID));
	// pwyelement.setDynamicProperty(COPIES_NUM, String
	// .valueOf(complexComponentIDMap
	// .get(unparsedcomplexname)));
	// pwyelement.setDynamicProperty(LOCATION, location[1]);
	//
	// addXref(pwyelement, rId, inst);
	// addComments(inst, pwyelement, false, false);
	// addLitRef(inst, pwyelement);
	// pwyEleList.add(pwyelement);
	// }
	// }
	// } catch (Exception e) {
	// e.printStackTrace();
	// }
	// }
	// }

	@SuppressWarnings("unchecked")
	public Boolean convertPathway(GKInstance reactomePathway, Long dbID,
			File gpmlfilename, String version) {
		try {
			RenderablePathway rePathway = queryPathwayDiagram(reactomePathway);
			// RenderablePathway rePathway = queryRePathway(reactomePathway);
			/*
			 * A virtual drawing is done to get correct dimensions
			 */

			PathwayEditor editor = new PathwayEditor();
			editor.setRenderable(rePathway);
			Dimension size = editor.getPreferredSize();
			BufferedImage image = new BufferedImage(size.width, size.height,
					BufferedImage.TYPE_3BYTE_BGR);
			/*
			 * Location to start layout of complex components
			 */
			y = size.height;

			Graphics g = image.createGraphics();
			Rectangle clip = new Rectangle(size);
			g.setClip(clip);
			editor.paint(g);

			/*
			 * Create new pathway
			 */
			gpmlpathway = new Pathway();
			System.out.println("Creating new GPML pathway "
					+ rePathway.getReactomeDiagramId());
			/*
			 * Clear element lists
			 */
			pwyEleList.clear();
			edges.clear();
			compartments.clear();
			notes.clear();
			r2gNodeList.clear();

			/*
			 * Create new mappInfo and Infobox, every pathway needs to have one
			 */
			mappInfo = PathwayElement.createPathwayElement(ObjectType.MAPPINFO);
			gpmlpathway.add(mappInfo);

			infoBox = PathwayElement.createPathwayElement(ObjectType.INFOBOX);
			gpmlpathway.add(infoBox);

			/*
			 * Create new biopax element for pathway to store literature
			 * references
			 */
			elementManager = gpmlpathway.getBiopax();
			
					/*
			 * Set species
			 */
			GKInstance species = (GKInstance) reactomePathway
					.getAttributeValue(ReactomeJavaConstants.species);
			if (species != null) {
				mappInfo.setStaticProperty(StaticProperty.ORGANISM,
						species.getDisplayName());
			}
			/*
			 * Set pathway information
			 */
			mappInfo.setStaticProperty(StaticProperty.MAPINFONAME,
					reactomePathway.getDisplayName());
			/**
			 * set reactome id as dynamic property
			 */
			mappInfo.setDynamicProperty("reactome_id", String.valueOf(dbID));
			if (species.getDBID().equals(48887L)) {
//				mappInfo.setMapInfoDataSource(DATA_SOURCE);
//				mappInfo.setVersion(version);
				addComments(reactomePathway, mappInfo, true, false);	
				addCommentsVersion(version, mappInfo, false, false);
				
			} else {
//				mappInfo.setMapInfoDataSource(PLANT_DATA_SOURCE);
//				mappInfo.setVersion(version);
				addComments(reactomePathway, mappInfo, true, true);
				addCommentsVersion(version, mappInfo, false, true);
			}

			addLitRef(reactomePathway, mappInfo);

			/*
			 * Add authors
			 */
			GKInstance authored = (GKInstance) reactomePathway
					.getAttributeValue(ReactomeJavaConstants.authored);
			List<GKInstance> values = null;
			if (authored != null) {
				values = authored
						.getAttributeValuesList(ReactomeJavaConstants.author);
				String authors = joinDisplayNames(values);

				if (authors != null) {
//					mappInfo.setStaticProperty(StaticProperty.AUTHOR, authors);
					addCommentsAuthor(authors, mappInfo, false, false);
				}
			}
			/*
			 * Edited is converted to Maintainer
			 */
			GKInstance edited = (GKInstance) reactomePathway
					.getAttributeValue(ReactomeJavaConstants.edited);
			if (edited != null) {
				values = edited
						.getAttributeValuesList(ReactomeJavaConstants.author);
				String maintainer = joinDisplayNames(values);
				if (maintainer != null) {
//					mappInfo.setStaticProperty(StaticProperty.MAINTAINED_BY,
//							maintainer);
				}
				// Get email from editors
				/* 	if (values != null && values.size() > 0) {
					StringBuilder builder = new StringBuilder();
					for (GKInstance person : values) {
						String email = (String) person
								.getAttributeValue(ReactomeJavaConstants.eMailAddress);
//						System.out.println("***********xxxxxxxxxx: " + email );
						if (email == null || email.length() == 0) {
							continue;
						}
						if (builder.length() > 0) {
							builder.append(", ");
						}
						builder.append(email); 
					}
					if (builder.length() > 0) {
						mappInfo.setDynamicProperty("Maintainer", String.valueOf(mappInfo.getEmail()));
//						System.out.println("OOOOOOOOOOOOOOOOOOO: "+String.valueOf(mappInfo.getEmail()));
						
//						mappInfo.setStaticProperty(StaticProperty.EMAIL,
//								builder.toString());
					}
				} */
			}

			if (targetDir == null) {
				targetDir = new File(".");
			}

			/*
			 * Pathway Conversion
			 */
			List<Renderable> objects = rePathway.getComponents();

			if (objects == null) {
				/*
				 * avoid errors on empty diagrams
				 */
				objects = new ArrayList<Renderable>();
			}

			for (Renderable r : objects)
				if (r instanceof HyperEdge) {
					edges.add((HyperEdge) r);
				} else if (r instanceof RenderableCompartment) {
					compartments.add((RenderableCompartment) r);
				} else if (r instanceof Note) {
					notes.add((Note) r);
				} else if (r instanceof Node) {
					convertNode((Node) r);
				}

			/*
			 * Converting reactions
			 */
			for (HyperEdge edge : edges) {
				convertEdge(edge);
			}

			/*
			 * Converting notes to labels
			 */
			for (Note note : notes) {
				createLabelForNote(note);
			}

			/*
			 * Converting compartment Two steps are needed for converting
			 * compartments: 1). Converting the names to labels 2). Converting
			 * the compartments to rectangles
			 */
			for (RenderableCompartment compartment : compartments) {
				PathwayElement groupElm = PathwayElement.createPathwayElement(ObjectType.GROUP);
				gpmlpathway.add(groupElm);
				groupElm.setGeneratedGraphId();
				groupElm.setGroupId(gpmlpathway.getUniqueGroupId());
				groupElm.setGroupStyle(GroupStyle.GROUP);
				createLabelForCompartment(compartment, groupElm);
				convertCompartment(compartment, groupElm);
			}
			if (!pwyEleList.isEmpty()) {
				layoutComplexComponents(pwyEleList, y);
			}

			gpmlpathway.fixReferences();

			// File outputFile = new File(gpmlfilename);
			/*
			 * Setting to false for checking if process nodes work when set to
			 * false
			 */
			gpmlpathway.writeToXml(gpmlfilename, false);

		} catch (Exception e) {
			e.printStackTrace(System.out);
			System.out.println("No diagram available");
			return false;
		}
		return true;
	}

	@Override
	public Document convertPathways(List<GKInstance> pathways) throws Exception {
		return null;
	}

	private ArrayList<String> countCopies(ArrayList<String> strings) {
		// HashMap<String, Integer> componentCopies = new HashMap<String,
		// Integer>();
		String complexComponent;
		int copies = 1;
		int size = strings.size();
		for (int i = 0; i < size; i++) {
			complexComponent = strings.get(i);
			// System.out.println("present : " + complexComponent);
			for (int j = i + 1; j < size; j++) {
				if (!strings.get(j).equals(strings.get(i))) {
					continue;
				}
				copies++; // counting copies
				strings.remove(j); // removing duplicates
				j--; // decrease j because the array got re-indexed
				size--;// decrease the size of the array
			}
			complexComponentIDMap.put(complexComponent, copies);
			// System.out.println(complexComponent + " : " + copies);
			// componentCopies.put(complexComponent, copies);
		}

		return strings;
	}

	private void drawComplexMembers(ArrayList<String> complexComponentIDString,
			String parentComplexId, boolean isComplex) {
		GKInstance inst;
		try {
			// Creating Group Element for grouping complex components
			parentComplex = PathwayElement
					.createPathwayElement(ObjectType.GROUP);

			gpmlpathway.add(parentComplex);
			parentComplex.setGeneratedGraphId();
			parentComplex.setGroupId(gpmlpathway.getUniqueGroupId());
			if (isComplex) {
				parentComplex.setGroupStyle(GroupStyle.COMPLEX);
			} else {
				parentComplex.setGroupStyle(GroupStyle.NONE);
			}

			for (int count = 0; count < complexComponentIDString.size(); count++) {

				String identifier = complexComponentIDString.get(count);
				// System.out.println(identifier);
				inst = dbAdaptor.fetchInstance(Long.parseLong(identifier));
				pwyelement = PathwayElement
						.createPathwayElement(ObjectType.DATANODE);
				String nodeType = parseIdentifier(inst.getExtendedDisplayName())[0];

				// System.out.println("members : " + nodeType);

				if (nodeType.equalsIgnoreCase("EntityWithAccessionedSequence")) {
					pwyelement.setDataNodeType(DataNodeType.PROTEIN);
				} else if (nodeType.equalsIgnoreCase("SimpleEntity")) {
					pwyelement.setDataNodeType(DataNodeType.METABOLITE);
					pwyelement.setColor(Color.BLUE);
				}

				parentComplex.setDynamicProperty(COMPLEX_ID, parentComplexId);

				gpmlpathway.add(pwyelement);
				pwyelement.setGeneratedGraphId();
				pwyelement
						.setTextLabel(parseLocation(inst.getDisplayName())[0]);
				pwyelement.setGroupRef(parentComplex.getGroupId());
				/*
				 * Adding Dynamic properties COMPLEX_ID : for connecting complex
				 * and components COPIES_NUM : for counting copies of a complex
				 * component LOCATION : for cellular location
				 */
				pwyelement.setDynamicProperty(COMPLEX_ID,
						parentComplex.getDynamicProperty(COMPLEX_ID));
				pwyelement.setDynamicProperty(COPIES_NUM,
						String.valueOf(complexComponentIDMap.get(identifier)));
				pwyelement.setDynamicProperty(LOCATION,
						(parseLocation(inst.getDisplayName())[1]));

				addXref(pwyelement, Long.parseLong(identifier), inst);
				addComments(inst, pwyelement, false, false);
				addLitRef(inst, pwyelement);
				pwyEleList.add(pwyelement);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	private void createLabelForCompartment(RenderableCompartment compt,
			PathwayElement groupElm) {
		label = PathwayElement.createPathwayElement(ObjectType.LABEL);
		gpmlpathway.add(label);
		label.setGeneratedGraphId();
		label.setTextLabel(compt.getDisplayName());
		label.setGroupRef(groupElm.getGroupId());
		Rectangle textRect = compt.getTextBounds();
		setGraphicsElmAttributes(label, textRect);
	}

	private void createLabelForNote(Note note) {
		// System.out.println("Converting Notes to Labels ...");
		if (!note.isPrivate()) // Private notes should not be converted
		{
			label = PathwayElement.createPathwayElement(ObjectType.LABEL);
			gpmlpathway.add(label);
			label.setGeneratedGraphId();
			label.setTextLabel(note.getDisplayName());
			addGraphicsElm(note, label);
		}
	}

	private PathwayElement createSegmentedLine(HyperEdge edge,
			String arrowType, List<Point> points, boolean isOutput)
			throws Exception {

		interaction = PathwayElement.createPathwayElement(ObjectType.LINE);
		gpmlpathway.add(interaction);
		interaction.setGeneratedGraphId();

		MIMShapes.registerShapes();

		interaction.setLineStyle(LineStyle.SOLID);
		interaction.setEndLineType(LineType.fromName(arrowType));
		interaction.setColor(Color.BLACK);
		interaction.setConnectorType(ConnectorType.SEGMENTED);

		if (isOutput) {
			for (int i = 0; i < points.size(); i++) {
				Point point = points.get(i);
				if (i == 0) {
					interaction.setMEndX(point.getX());
					interaction.setMEndY(point.getY());
				} else if (i == points.size() - 1) {
					interaction.setMStartX(point.getX());
					interaction.setMStartY(point.getY());

				}
			}
		} else {
			for (int i = 0; i < points.size(); i++) {
				Point point = points.get(i);
				if (i == 0) {
					interaction.setMStartX(point.getX());
					interaction.setMStartY(point.getY());
				} else if (i == points.size() - 1) {
					interaction.setMEndX(point.getX());
					interaction.setMEndY(point.getY());
				}
			}
		}

		if (edge.getReactomeId() != null) {
			GKInstance rxt = dbAdaptor.fetchInstance(edge.getReactomeId());
			addXref(interaction, edge.getReactomeId(), rxt);

		}
		return interaction;
	}

	/*
	 * Find the point in the node closest to the backbone interaction
	 */
	private Point getclosestPoint(Node node, Point relpoint) {
		Point point = new Point();
		Point north = new Point();
		Point south = new Point();
		Point east = new Point();
		Point west = new Point();
		int linex = node.getPosition().x;
		int liney = node.getPosition().y + node.getBounds().height / 2;
		north.setLocation(linex, liney);
		liney = node.getPosition().y - node.getBounds().height / 2;
		south.setLocation(linex, liney);
		liney = node.getPosition().y;
		linex = node.getPosition().x + node.getBounds().width / 2;
		west.setLocation(linex, liney);
		linex = node.getPosition().x - node.getBounds().width / 2;
		east.setLocation(linex, liney);
		double minDist = relpoint.distance(north);
		point.setLocation(north);
		if (relpoint.distance(south) < minDist) {
			point.setLocation(south);
			minDist = relpoint.distance(south);
		}
		if (relpoint.distance(east) < minDist) {
			point.setLocation(east);
			minDist = relpoint.distance(east);
		}
		if (relpoint.distance(west) < minDist) {
			point.setLocation(west);
		}
		return point;
	}

	/*
	 * Get correct referenced node
	 */
	private PathwayElement getConnectedNode(Long reactomeId,
			PathwayElement edgeElem, Boolean startside) {
		PathwayElement relevantNode = null;
		double minDist = 500.0;
		ArrayList<String> graphidlist = r2gNodeList.get(reactomeId);
		for (String graphid : graphidlist) {
			PathwayElement nodElem = gpmlpathway.getElementById(graphid);
			Point nodeElemPos = new Point();
			nodeElemPos.setLocation(nodElem.getMCenterX(),
					nodElem.getMCenterY());
			Point edgeElemPos = new Point();
			if (startside) {
				edgeElemPos.setLocation(edgeElem.getMStartX(),
						edgeElem.getMStartY());
			} else {
				edgeElemPos.setLocation(edgeElem.getMEndX(),
						edgeElem.getMEndY());
			}
			if (edgeElemPos.distance(nodeElemPos) <= minDist) {
				minDist = edgeElemPos.distance(nodeElemPos);
				relevantNode = nodElem;
			}

		}
		return relevantNode;
	}

	private void handleActivators(HyperEdge edge,
			PathwayElement backboneInteraction) throws Exception {
		List<Node> activators = edge.getActivatorNodes();
		handleHelperNodes(edge, backboneInteraction, "Arrow", activators);
	}

	private void handleCatalysts(HyperEdge edge,
			PathwayElement backboneInteraction) throws Exception {
		List<Node> catalysts = edge.getHelperNodes();
		handleHelperNodes(edge, backboneInteraction, "mim-catalysis", catalysts);
	}

	private void handleHelperNodes(HyperEdge edge,
			PathwayElement backboneInteraction, String arrowType,
			List<Node> helperNodes) throws Exception {
		List<Point> points = new ArrayList<Point>();
		List<Point> backbone = edge.getBackbonePoints();

		if (helperNodes == null || helperNodes.size() == 0)
			return;
		if (helperNodes.size() >= 1) {
			MAnchor anchor = backboneInteraction.addMAnchor(0.5);

			for (Node helperNode : helperNodes) {
				Point point = getclosestPoint(helperNode, backbone.get(0));
				points.add(point);
				if (backboneInteraction.getConnectorType() == ConnectorType.SEGMENTED
						|| backboneInteraction.getConnectorType() == ConnectorType.STRAIGHT) {
					points.add(backbone.get(backbone.size() / 2));
				} else {
					/*
					 * fix for unconnected interactions when the line is curved
					 * often catalysts look unconnected in that case we connect
					 * the catalysts to the beginning
					 */
					MPoint mid = backboneInteraction.getMPoints().get(
							backboneInteraction.getMPoints().size() / 2);
					Point midpoint = new Point();
					midpoint.setLocation(mid.getX(), mid.getY());
					points.add(midpoint);
				}

				PathwayElement intElem = createSegmentedLine(edge, arrowType,
						points, false);
				intElem.getMEnd().linkTo(anchor);

				PathwayElement nodElem = getConnectedNode(
						helperNode.getReactomeId(), intElem, true);

				try {
					intElem.getMStart().linkTo(nodElem);
				} catch (Exception e) {
					System.out.println("node missing");
				}
			}
		}

	}

	private void handleInhibitors(HyperEdge edge,
			PathwayElement backboneInteraction) throws Exception {
		List<Node> inhibitors = edge.getInhibitorNodes();
		handleHelperNodes(edge, backboneInteraction, "TBar", inhibitors);
	}

	private void handleInputs(HyperEdge edge, PathwayElement backboneInteraction)
			throws Exception {
		List<Node> inputs = edge.getInputNodes();
		List<Point> points = new ArrayList<Point>();
		List<Point> backbone = edge.getBackbonePoints();

		if (inputs != null) {
			MAnchor anchor = backboneInteraction.addMAnchor(0.0);
			if (inputs.size() >= 1) {
				for (Node input : inputs) {
					points.clear();
					Point point = getclosestPoint(input, backbone.get(0));
					points.add(point);
					points.add(backbone.get(0));
					PathwayElement intElem = createSegmentedLine(edge, "line",
							points, false);
					intElem.getMEnd().linkTo(anchor);
					PathwayElement nodElem = getConnectedNode(
							input.getReactomeId(), intElem, true);
					try {
						intElem.getMStart().linkTo(nodElem);
					} catch (Exception e) {
						System.out.println("node missing");
					}
				}
			}
		}
	}

	private void handleOutputs(HyperEdge edge,
			PathwayElement backboneInteraction) throws Exception {
		List<Point> points = new ArrayList<Point>();
		List<Node> outputs = edge.getOutputNodes();
		List<Point> backbone = edge.getBackbonePoints();

		if (outputs != null) {
			MAnchor anchor = backboneInteraction.addMAnchor(0.99);

			for (Node output : outputs) {
				points.clear();
				Point point = getclosestPoint(output,
						backbone.get(backbone.size() - 1));
				points.add(point);
				points.add(backbone.get(backbone.size() - 1));
				PathwayElement intElem = createSegmentedLine(edge, "Arrow",
						points, true);
				intElem.getMStart().linkTo(anchor);

				PathwayElement nodElem = getConnectedNode(
						output.getReactomeId(), intElem, false);

				try {
					intElem.getMEnd().linkTo(nodElem);
				} catch (Exception e) {
					System.out.println("node missing");
				}
			}
		}
	}

	private String joinDisplayNames(List<GKInstance> instances) {
		if (instances == null || instances.size() == 0)
			return null;
		StringBuilder builder = new StringBuilder();
		for (GKInstance inst : instances) {
			if (builder.length() > 0) {
				builder.append(", ");
			}
			builder.append(inst.getDisplayName());
		}
		return builder.toString();
	}

	private void layoutComplexComponents(List<PathwayElement> pweList, double y2) {
		int col = 0;
		int row = 0;
		double cy = y2;
		int complexCount = 1;
		String groupRef = pweList.get(0).getGroupRef();
		for (int i = 0; i < pweList.size(); i++) {
			String newgroupRef = pweList.get(i).getGroupRef();
			if (!newgroupRef.equalsIgnoreCase(groupRef)) {
				complexCount++;
			}
		}
		for (int i = 0; i < pweList.size(); i++) {
			String newgroupRef = pweList.get(i).getGroupRef();
			if (!newgroupRef.equalsIgnoreCase(groupRef)) {
				NEW_COMPLEX_GAP = 40;
				groupRef = newgroupRef;
				if (complexCount < NO_OF_COMPLEXES || row >= COMPONENTS_ROWS) {
					cy = y2;
					col++;
					row = 0;
				}
			} else {
				if (i != 0) {
					NEW_COMPLEX_GAP = 0;
				}
			}
			double cx = 100 + col * (INITIAL_WIDTH + COL_GAP);

			if (pweList.get(i).getTextLabel().length() <= 20) {
				cy = cy + INITIAL_HEIGHT + NEW_COMPLEX_GAP;
				pweList.get(i).setMHeight(INITIAL_HEIGHT);
			} else {
				if (pweList.get(i).getTextLabel().length() >= 50) {
					cy = cy + LONG_INITIAL_HEIGHT + NEW_COMPLEX_GAP;
					pweList.get(i).setMHeight(LONG_INITIAL_HEIGHT);
				} else {
					cy = cy + MEDIUM_INITIAL_HEIGHT + NEW_COMPLEX_GAP;
					pweList.get(i).setMHeight(MEDIUM_INITIAL_HEIGHT);
				}
			}
			pweList.get(i).setMCenterX(cx);
			pweList.get(i).setMCenterY(cy);
			pweList.get(i).setMWidth(INITIAL_WIDTH);
			row++;

			gpmlpathway.add(pweList.get(i));
		}

	}

	/*
	 * Parse the displayname to get Identifier and Type returns : [0] = Type [1]
	 * = Identifier
	 */
	private String[] parseIdentifier(String displayName) {
		String[] loc = new String[2];
		if (displayName.contains("]")) {
			String[] firstSplit = displayName.split(Pattern.quote("]"));
			if (firstSplit[0].contains("[")) {
				String firstSplitText = firstSplit[0].replace("[", "");
				loc = firstSplitText.split(":");
			}
		}
		return loc;
	}

	/*
	 * Parse the displayname to get location returns : [0] = text label [1] =
	 * cellular location
	 */
	private String[] parseLocation(String displayName) {
		String[] loc = new String[2];
		if (displayName.contains("[")) {
			String[] firstSplit = displayName.split(Pattern.quote("["));
			loc[0] = firstSplit[0];
			if (firstSplit[1].contains("]")) {
				String secondSplit = firstSplit[1].replace("]", "");
				loc[1] = secondSplit;
			}
		}
		return loc;
	}

	// private String[] parseLocation(String displayName) {
	// String[] loc = new String[2];
	// if (displayName.contains("]")) {
	// String[] firstSplit = displayName.split(Pattern.quote("]"));
	// if (firstSplit[2].contains("[")) {
	// loc = firstSplit[2].split(Pattern.quote("["));
	// }
	// }
	// return loc;
	// }

	private String refineDisplayNames(String text) {
		StringBuilder sb = new StringBuilder(text);
		int i = 0;
		while (i + 20 < sb.length() && (i = sb.lastIndexOf(" ", i + 20)) != -1) {
			sb.replace(i, i + 1, "\n");
		}
		return sb.toString();
	}

	private PathwayElement setGraphicsElmAttributes(PathwayElement pwyele,
			Rectangle bounds) {
		pwyele.setMCenterX(bounds.getCenterX());
		pwyele.setMCenterY(bounds.getCenterY());
		pwyele.setMWidth(bounds.getWidth());
		pwyele.setMHeight(bounds.getHeight());
		pwyele.setValign(ValignType.MIDDLE);
		return pwyele;
	}
}
