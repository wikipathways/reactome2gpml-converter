package org.gk.gpml;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.apache.commons.lang.WordUtils;
import org.bridgedb.DataSource;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.render.HyperEdge;
import org.gk.render.Node;
import org.gk.render.Note;
import org.gk.render.ProcessNode;
import org.gk.render.Renderable;
import org.gk.render.RenderableChemical;
import org.gk.render.RenderableCompartment;
import org.gk.render.RenderableComplex;
import org.gk.render.RenderablePathway;
import org.gk.render.RenderableProtein;
import org.gk.render.RenderableRNA;
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
import org.pathvisio.core.model.PathwayElement.Comment;
import org.pathvisio.core.model.PathwayElement.MAnchor;
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
	private static final int MEDIUM_INITIAL_HEIGHT = 30;
	private static final int COMPONENTS_ROWS = 20;
	private static final int LONG_INITIAL_HEIGHT = 50;
	private static final int COL_GAP = 40;
	private static final int TOP_GAP = 5;

	private static ArrayList<String> removeDuplicates(ArrayList<String> strings) {

		int size = strings.size();

		for (int i = 0; i < size - 1; i++) {
			for (int j = i + 1; j < size; j++) {

				if (!strings.get(j).equals(strings.get(i))) {
					continue;
				}

				strings.remove(j);
				// decrease j because the array got re-indexed
				j--;
				// decrease the size of the array
				size--;
			}
		}

		return strings;

	}

	String reactomeURL = "Original Pathway at Reactome: http://www.reactome.org/"
			+ "PathwayBrowser/#DB=gk_current&FOCUS_SPECIES_ID=48887&FOCUS_"
			+ "PATHWAY_ID=";
	private final String REACTOME_ID = "reactome_id";
	// HashMap<Long, String> nonRenderable = new HashMap<Long, String>();
	// HashMap<Long, String> r2gEdgeList = new HashMap<Long, String>();
	HashMap<Long, ArrayList<String>> r2gNodeList = new HashMap<Long, ArrayList<String>>();
	List<HyperEdge> edges = new ArrayList<HyperEdge>();
	List<Node> nodes = new ArrayList<Node>();
	List<RenderableCompartment> compartments = new ArrayList<RenderableCompartment>();

	List<Note> notes = new ArrayList<Note>();
	ArrayList<String> complexComponentIDs = new ArrayList<String>();
	ArrayList<Long> complexIDs = new ArrayList<Long>();
	ArrayList<String> complexnames = new ArrayList<String>();
	ArrayList<PathwayElement> pwyEleList = new ArrayList<PathwayElement>();
	// int complexCount = 0;

	double y = 0;
	List<Comment> commentList;
	Comment comment;
	GKInstance newInstance;
	Pathway gpmlpathway;
	BiopaxElement elementManager;
	PathwayElement mappInfo = null;
	PathwayElement infoBox = null;
	PathwayElement complexmem = null;
	PathwayElement pwyelement = null;
	PathwayElement datanode = null;
	PathwayElement interaction = null;
	PathwayElement label = null;

	PathwayElement shape = null;
	final String DATA_SOURCE = "Reactome - http://www.reactome.org";

	final String VERSION = "48";

	/*
	 * Adding comments
	 */
	private void addComments(GKInstance instance, PathwayElement pwyele,
			boolean mainTag) {
		String reactomeString = "";
		String wpcomment = "";

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
						if (mainTag) {
							/*
							 * Adding summation
							 */
							wpcomment = wpcomment + reactomeString;
						} else if (wpcomment != null && wpcomment.length() > 0) {
							pwyele.addComment(wpcomment, "Reactome");
						}
					}
				}
				if (mainTag) {
					/*
					 * adding original pathway url
					 */
					reactomeString = reactomeURL + instance.getDBID();
					pwyele.addComment(wpcomment + reactomeString,
							"WikiPathways-description");
				}

				/*
				 * Adding the Reactome ID as a Dynamic Property
				 */
				pwyele.setDynamicProperty(REACTOME_ID,
						String.valueOf(instance.getDBID()));
			} catch (Exception e) {
				e.printStackTrace();
			}

		}

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
		if (instance.getSchemClass().isValidAttribute(
				ReactomeJavaConstants.literatureReference)) {
			List<GKInstance> litRefs;
			try {
				litRefs = instance
						.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
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
							String urlString = "http://www.ebi.ac.uk/europepmc/webservices/rest/search/query=ext_id:"
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
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

	}

	/*
	 * Adding comments
	 */
	private void addReactomePathwayId(GKInstance instance) {
		if (instance != null
				&& instance.getSchemClass().isa(
						ReactomeJavaConstants.PathwayDiagram)) {
			String reactomepathwayid = "http://www.reactome.org/PathwayBrowser/"
					+ "#DB=gk_current&FOCUS_SPECIES_ID=48887&FOCUS_PATHWAY_ID="
					+ instance.getDBID();
			infoBox.addComment(reactomepathwayid, "WikiPathways-description");
		}

	}

	/*
	 * Adding multiple graphids for corresponding Reactome ids
	 */
	private void addValues(Long key, String value) {
		ArrayList<String> tempList = null;
		if (r2gNodeList.containsKey(key)) {
			tempList = r2gNodeList.get(key);
			if(tempList == null) {
				tempList = new ArrayList<String>();
			}
			tempList.add(value);
		} else {
			tempList = new ArrayList<String>();
			tempList.add(value);
		}
		r2gNodeList.put(key,tempList);
	}

	//
	// private void checkingCatalysts(GKInstance inst) {
	// if (inst.getSchemClass().isValidAttribute(
	// ReactomeJavaConstants.CatalystActivity))
	// try {
	// List processes = inst
	// .getAttributeValuesList(ReactomeJavaConstants.CatalystActivity);
	// System.out.println("works" + processes);
	// } catch (Exception e) {
	// System.out.println("damn");
	// }
	// else
	// System.out.println("not valid for pathways");
	// }

	private void addXref(PathwayElement pwyele, Long rId, GKInstance instance)
			throws Exception {
		// System.out.println("Annotating elements ...");
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
			pwyele.setDataSource(DataSource.getBySystemCode("Re"));

			String id = instance == null ? rId.toString()
					: getReactomeId(instance);
			if (id == null) {
				id = instance.getDBID().toString();
			}
			pwyele.setElementID(id);
		} else {
			GKInstance db = (GKInstance) referenceEntity
					.getAttributeValue(ReactomeJavaConstants.referenceDatabase);

			/*
			 * ChEBI and uniprot give errors while searching by full name
			 */
			String identifier = getIdentifierFromReferenceEntity(referenceEntity);
			if (db.getDisplayName().equalsIgnoreCase("chebi")) {
				pwyele.setDataSource(DataSource.getBySystemCode("Ce"));
				identifier = "CHEBI:" + identifier;
			} else if (db.getDisplayName().equalsIgnoreCase("uniprot")) {
				pwyele.setDataSource(DataSource.getBySystemCode("S"));
			} else {
				pwyele.setDataSource(DataSource.getByFullName(db
						.getDisplayName()));
			}
			pwyele.setElementID(identifier);
		}

	}

	private void convertCompartment(RenderableCompartment compartment,
			PathwayElement groupElm) {
		System.out.println("Converting Compartments to Shapes ...");
		shape = PathwayElement.createPathwayElement(ObjectType.SHAPE);
		gpmlpathway.add(shape);
		shape.setGeneratedGraphId();
		shape.setGroupRef(groupElm.getGroupId());
		shape.setShapeType(ShapeType.ROUNDED_RECTANGLE);
		shape.setLineStyle(LineStyle.DOUBLE);
		shape.setLineThickness(3);
		shape.setTransparent(true);
		shape.setColor(new Color(192, 192, 192));
		addGraphicsElm(compartment, shape);

	}

	/*
	 * Getting components of Reactome Complexes
	 */
	private ArrayList<String> convertComplex(Long rId) throws IOException,
	ParserConfigurationException, SAXException,
	XPathExpressionException {

		System.out
		.println("Getting Complex components using Reactome Webservice ...");
		String urlString = "http://reactomews.oicr.on.ca:8080/ReactomeRESTfulAPI/"
				+ "RESTfulWS/queryById/Complex/" + rId;
		// System.out.println(urlString);
		URL url = new URL(urlString);
		URLConnection conn = url.openConnection();
		conn.setRequestProperty("Accept", "application/xml");
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		org.w3c.dom.Document reactomeComplex = db.parse(conn.getInputStream());
		XPath xPath = XPathFactory.newInstance().newXPath();

		if (reactomeComplex.getDocumentElement().getNodeName()
				.equalsIgnoreCase("complex")
				|| reactomeComplex.getDocumentElement().getNodeName()
				.equalsIgnoreCase("definedSet")) {
			if (reactomeComplex.getDocumentElement().getNodeName()
					.equalsIgnoreCase("complex")) {
				NodeList reactomeComplexCompartmentNodes = (NodeList) xPath
						.compile("/complex/hasComponent").evaluate(
								reactomeComplex, XPathConstants.NODESET);
				String complexId = xPath.compile("/complex/dbId").evaluate(
						reactomeComplex);
				complexComponentIDs.add("mainCom" + complexId);

				for (int a = 0; a < reactomeComplexCompartmentNodes.getLength(); a++) {
					/*
					 * Getting child components
					 */
					String memId = reactomeComplexCompartmentNodes.item(a)
							.getFirstChild().getTextContent();
					Long memIdlong = Long.parseLong(memId, 10);
					convertComplex(memIdlong);

				}
			} else if (reactomeComplex.getDocumentElement().getNodeName()
					.equalsIgnoreCase("definedSet")) {
				NodeList definedSetCompartments = (NodeList) xPath.compile(
						"/definedSet/hasMember").evaluate(reactomeComplex,
								XPathConstants.NODESET);
				String complexId = xPath.compile("/definedSet/dbId").evaluate(
						reactomeComplex);

				complexComponentIDs.add("subCom" + complexId);

				for (int a = 0; a < definedSetCompartments.getLength(); a++) {
					/*
					 * Getting child components
					 */
					String memId = definedSetCompartments.item(a)
							.getFirstChild().getTextContent();
					Long memIdlong = Long.parseLong(memId, 10);
					convertComplex(memIdlong);

				}
				// complexComponentIDs.add("gap");
			}
		} else if (reactomeComplex.getDocumentElement().getNodeName()
				.equalsIgnoreCase("entityWithAccessionedSequence")) {
			complexComponentIDs.add("pro" + rId.toString());
		} else if (reactomeComplex.getDocumentElement().getNodeName()
				.equalsIgnoreCase("simpleEntity")) {
			complexComponentIDs.add("met" + rId.toString());
		}
		return complexComponentIDs;
	}

	private void convertEdge(HyperEdge edge) throws Exception {
		if (edge.getReactomeId() != null) {
			GKInstance inst;
			try {
				System.out.println("Converting Hyperedges to Interactions ...");
				// r2gEdgeList.put(edge.getReactomeId(), edge.getDisplayName());
				List<Point> points = edge.getBackbonePoints();

				inst = dbAdaptor.fetchInstance(edge.getReactomeId());
				PathwayElement backboneInteraction = createSegmentedLine(edge,
						"Line", points, false);
				if (backboneInteraction != null) {
					gpmlpathway.add(backboneInteraction);
					backboneInteraction.setGeneratedGraphId();
					addComments(inst, backboneInteraction, false);
					addLitRef(inst, backboneInteraction);
				}
				// handleProcessNodes(inst, edge, backboneInteraction);
				handleInputs(edge, backboneInteraction);
				handleOutputs(edge, backboneInteraction);
				handleCatalysts(edge, backboneInteraction);
				handleInhibitors(edge, backboneInteraction);
				handleActivators(edge, backboneInteraction);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("deprecation")
	private PathwayElement convertNode(Node node) {
		if (node.getReactomeId() != null) {
			GKInstance inst;
			try {

				inst = dbAdaptor.fetchInstance(node.getReactomeId());
				System.out.println("Converting Nodes to Datanodes ...");
				datanode = PathwayElement
						.createPathwayElement(ObjectType.DATANODE);
				gpmlpathway.add(datanode);
				datanode.setGeneratedGraphId();
				addValues(node.getReactomeId(), datanode.getGraphId());
				// r2gNodeList.put(node.getReactomeId(), datanode.getGraphId());
				// System.out.println(datanode.getGraphId());
				if (inst != null)
					if (node instanceof RenderableProtein) {
						datanode.setDataNodeType(DataNodeType.PROTEIN);
					} else if (node instanceof RenderableRNA) {
						datanode.setDataNodeType(DataNodeType.RNA);
					} else if (node instanceof RenderableChemical) {
						datanode.setDataNodeType(DataNodeType.METABOLITE);
						datanode.setColor(Color.BLUE);
					} else if (node instanceof RenderableComplex) {
						complexComponentIDs.clear();
						datanode.setDataNodeType(DataNodeType.COMPLEX);
						Long rId = node.getReactomeId();
						ArrayList<String> complexComponentIDList = convertComplex(rId);
						complexComponentIDList = removeDuplicates(complexComponentIDList);
						fetchComplexMembers(complexComponentIDList);
						// complexCount++;
					} else if (node instanceof ProcessNode) {
						datanode.setDataNodeType(DataNodeType.PATHWAY);
						datanode.setColor(new Color(20, 150, 30));
						datanode.setLineThickness(0);
						datanode.setLineStyle(LineStyle.DOUBLE);
					} else {
						datanode.setDataNodeType(DataNodeType.UNKOWN);
					}
				addXref(datanode, node.getReactomeId(), inst);
				addComments(inst, datanode, false);
				addLitRef(inst, datanode);
				datanode.setTextLabel(
						refineDisplayNames(node.getDisplayName()));
				addGraphicsElm(node, datanode);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return datanode;
	}
	@Override
	public Document convertPathway(GKInstance pathway) throws Exception {
		return null;
	}

	@SuppressWarnings("unchecked")
	public Boolean convertPathway(GKInstance reactomePathway,
			File gpmlfilename)
	{
		try {
			RenderablePathway diagram = queryPathwayDiagram(reactomePathway);

			// if (diagram == null)
			// throw new IllegalArgumentException(
			// reactomePathway
			// +
			// " has no diagram available in the database, and cannot be converted to GPML at this time.");

			/*
			 * A virtual drawing is done to get correct dimensions
			 */
			PathwayEditor editor = new PathwayEditor();
			editor.setRenderable(diagram);
			Dimension size = editor.getPreferredSize();
			BufferedImage image = new BufferedImage(size.width, size.height,
					BufferedImage.TYPE_3BYTE_BGR);
			/*
			 * Location to start layout of complex components
			 */
			y = size.height + TOP_GAP;

			Graphics g = image.createGraphics();
			Rectangle clip = new Rectangle(size);
			g.setClip(clip);
			editor.paint(g);

			/*
			 * Create new pathway
			 */
			gpmlpathway = new Pathway();
			System.out.println("Creating new GPML pathway "
					+ diagram.getReactomeDiagramId());
			// complexCount = 0;
			complexComponentIDs.clear();
			pwyEleList.clear();
			/*
			 * Create new mappInfo and Infobox, every pathway needs to have one
			 */
			mappInfo = PathwayElement.createPathwayElement(ObjectType.MAPPINFO);
			gpmlpathway.add(mappInfo);

			infoBox = PathwayElement.createPathwayElement(ObjectType.INFOBOX);
			gpmlpathway.add(infoBox);

			/*
			 * Create new biopax element for pathway to store literature references
			 */
			elementManager = gpmlpathway.getBiopax();

			/*
			 * Set pathway information
			 */
			mappInfo.setStaticProperty(StaticProperty.MAPINFONAME,
					reactomePathway.getDisplayName());
			mappInfo.setMapInfoDataSource(DATA_SOURCE);
			mappInfo.setVersion(VERSION);
			// checkingCatalysts(reactomePathway);
			addComments(reactomePathway, mappInfo, true);
			addLitRef(reactomePathway, mappInfo);

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
					mappInfo.setStaticProperty(StaticProperty.AUTHOR, authors);
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
					mappInfo.setStaticProperty(StaticProperty.MAINTAINED_BY,
							maintainer);
				}
				// Get email from editors
				if (values != null && values.size() > 0) {
					StringBuilder builder = new StringBuilder();
					for (GKInstance person : values) {
						String email = (String) person
								.getAttributeValue(ReactomeJavaConstants.eMailAddress);
						if (email == null || email.length() == 0) {
							continue;
						}
						if (builder.length() > 0) {
							builder.append(", ");
						}
						builder.append(email);
					}
					if (builder.length() > 0) {
						mappInfo.setStaticProperty(StaticProperty.EMAIL,
								builder.toString());
					}
				}
			}

			if (targetDir == null) {
				targetDir = new File(".");
			}

			/*
			 * Pathway Conversion
			 */
			List<Renderable> objects = diagram.getComponents();

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
			 * compartments: 1). Converting the names to labels 2). Converting the
			 * compartments to rectangles
			 */
			for (RenderableCompartment compartment : compartments) {
				PathwayElement groupElm = PathwayElement
						.createPathwayElement(ObjectType.GROUP);
				gpmlpathway.add(groupElm);
				groupElm.setGeneratedGraphId();
				groupElm.setGroupId(gpmlpathway.getUniqueGroupId());
				groupElm.setGroupStyle(GroupStyle.GROUP);
				createLabelForCompartment(compartment, groupElm);
				convertCompartment(compartment, groupElm);
			}
			layoutComplexComponents(pwyEleList, y);
			gpmlpathway.fixReferences();

			// File outputFile = new File(gpmlfilename);
			/*
			 * Setting to false for checking if process nodes work when set to false
			 */
			gpmlpathway.writeToXml(gpmlfilename, false);
		} catch (Exception e) {
			System.out.println("No diagram available");
			return false;
		}
		return true;
	}

	@Override
	public Document convertPathways(List<GKInstance> pathways) throws Exception {
		return null;
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
		System.out.println("Converting Notes to Labels ...");
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

		// newInteraction.setGraphId(graphId);
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
	 * Adding complex compartments to the pathway
	 */
	private void fetchComplexMembers(
			ArrayList<String> complexComponentIDString) {
		// pwyEleList.clear();
		for (int count = 0; count < complexComponentIDString.size(); count++) {

			// double cx = 0.0;
			// double cy = 0.0;

			String complexname = complexComponentIDString.get(count);
			// System.out.println("complex "+complexname);

			GKInstance inst;
			try {

				if (complexname.startsWith("mainCom")) {
					complexname = complexname.replaceFirst("mainCom", "");
					Long rId = Long.parseLong(complexname);
					inst = dbAdaptor.fetchInstance(rId);
					pwyelement = PathwayElement
							.createPathwayElement(ObjectType.LABEL);
					gpmlpathway.add(pwyelement);
					pwyelement.setGeneratedGraphId();
					pwyelement.setTextLabel(refineDisplayNames(
							inst
							.getDisplayName()));
					/*
					 * Adding Reactome Complex ID as comment to Label for
					 * complex compartment nodes
					 */
					pwyelement.addComment(getReactomeId(inst), "Reactome");

					// Creating Group Element for grouping complex components
					complexmem = PathwayElement
							.createPathwayElement(ObjectType.GROUP);
					gpmlpathway.add(complexmem);
					complexmem.setGeneratedGraphId();
					complexmem.setGroupId(gpmlpathway.getUniqueGroupId());
					complexmem.setGroupStyle(GroupStyle.COMPLEX);

				}
				else if (complexname.startsWith("subCom")) {
					complexname = complexname.replaceFirst("subCom", "");
					Long rId = Long.parseLong(complexname);
					inst = dbAdaptor.fetchInstance(rId);
					pwyelement = PathwayElement
							.createPathwayElement(ObjectType.LABEL);
					gpmlpathway.add(pwyelement);
					pwyelement.setGeneratedGraphId();
					String displayname = refineDisplayNames(inst
							.getDisplayName());
					// String displayname = inst.getDisplayName();
					pwyelement.setTextLabel(displayname);

					/*
					 * Adding Reactome Complex ID as comment to Label for
					 * complex compartment nodes
					 */
					pwyelement.addComment(getReactomeId(inst), "Reactome");

				}
				// Creating Complex member DataNodes
				else {

					pwyelement = PathwayElement
							.createPathwayElement(ObjectType.DATANODE);
					gpmlpathway.add(pwyelement);
					pwyelement.setGeneratedGraphId();

					if (complexname.startsWith("pro")) {
						complexname = complexname.replaceFirst("pro", "");
						pwyelement.setDataNodeType(DataNodeType.PROTEIN);
					} else if (complexname.startsWith("met")) {
						complexname = complexname.replaceFirst("met", "");
						pwyelement.setDataNodeType(DataNodeType.METABOLITE);
						pwyelement.setColor(Color.BLUE);
					}

					Long rId = Long.parseLong(complexname);
					inst = dbAdaptor.fetchInstance(rId);
					String displayname = refineDisplayNames(inst
							.getDisplayName());
					pwyelement.setTextLabel(displayname);

					pwyelement.setGroupRef(complexmem.getGroupId());
					addXref(pwyelement, rId, inst);
					addComments(inst, pwyelement, false);
					addLitRef(inst, pwyelement);
				}
				pwyEleList.add(pwyelement);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
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
		liney = node.getPosition().y - node.getBounds().height
				/ 2;
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
		List<List<Point>> activatorBranches = edge.getActivatorPoints();
		handleHelperNodes(edge, backboneInteraction, "Arrow", activators,
				activatorBranches);
	}

	private void handleCatalysts(HyperEdge edge,
			PathwayElement backboneInteraction) throws Exception {
		List<Node> catalysts = edge.getHelperNodes();
		List<List<Point>> catalystBranches = edge.getHelperPoints();
		handleHelperNodes(edge, backboneInteraction, "mim-catalysis",
				catalysts, catalystBranches);
	}

	private void handleHelperNodes(HyperEdge edge,
			PathwayElement backboneInteraction, String arrowType,
			List<Node> helperNodes, List<List<Point>> branches)
					throws Exception {
		List<Point> points = new ArrayList<Point>();
		List<Point> backbone = edge.getBackbonePoints();
		if (helperNodes == null || helperNodes.size() == 0)
			return;
		if (helperNodes.size() >= 1) {
			MAnchor anchor = backboneInteraction.addMAnchor(0.5);
			for (Node helperNode : helperNodes) {
				Point point = getclosestPoint(helperNode, backbone.get(0));
				points.add(point);
				points.add(backbone.get(backbone.size() / 2));
				// PathwayElement nodElem =
				// gpmlpathway.getElementById(r2gNodeList
				// .get(helperNode.getReactomeId()));
				PathwayElement intElem = createSegmentedLine(edge,
						arrowType,
						points, false);
				intElem.getMEnd().linkTo(anchor);

				PathwayElement nodElem = getConnectedNode(
						helperNode.getReactomeId(), intElem, true);

				try {
					intElem.getMStart().linkTo(nodElem);
				} catch (Exception e) {
					System.out.println("node missing");
				}
				// }

			}
		}

	}

	private void handleInhibitors(HyperEdge edge,
			PathwayElement backboneInteraction) throws Exception {
		List<Node> inhibitors = edge.getInhibitorNodes();
		List<List<Point>> inhibitorBranches = edge.getInhibitorPoints();
		handleHelperNodes(edge, backboneInteraction, "TBar", inhibitors,
				inhibitorBranches);
	}

	// TODO : Trying fixes for SBGN interactions
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

					// PathwayElement nodElem = gpmlpathway
					// .getElementById(r2gNodeList.get(input
					// .getReactomeId()));

					PathwayElement intElem = createSegmentedLine(edge, "line",
							points, false);
					intElem.getMEnd().linkTo(anchor);
					PathwayElement nodElem = getConnectedNode(input
							.getReactomeId(), intElem, true);
					try {
						intElem.getMStart().linkTo(nodElem);
					} catch (Exception e) {
						System.out.println("node missing");
					}
				}
			}
		}
	}

	// TODO Testing for SBGN interactions
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
				// PathwayElement nodElem =
				// gpmlpathway.getElementById(r2gNodeList
				// .get(output.getReactomeId()));
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
		double cy = y2 + TOP_GAP;
		for (int i = 0; i < pweList.size(); i++) {
			double cx = 100 + col * (INITIAL_WIDTH + COL_GAP);

			pweList.get(i).setMCenterX(cx);
			pweList.get(i).setMCenterY(cy);
			pweList.get(i).setMWidth(INITIAL_WIDTH);

			if (pweList.get(i).getTextLabel().length() <= 20) {
				cy = cy + INITIAL_HEIGHT;
				pweList.get(i).setMHeight(INITIAL_HEIGHT);
			} else {
				if (pweList.get(i).getTextLabel().length() >= 50) {
					cy = cy + LONG_INITIAL_HEIGHT;
					pweList.get(i).setMHeight(LONG_INITIAL_HEIGHT);
				} else {
					cy = cy + MEDIUM_INITIAL_HEIGHT;
					pweList.get(i).setMHeight(MEDIUM_INITIAL_HEIGHT);
				}

			}
			row++;
			if (pweList.get(i).getObjectType() == ObjectType.LABEL) {
				if (row >= COMPONENTS_ROWS) {
					cy = y2 + TOP_GAP;
					col++;
					row = 0;
				}
			}
			gpmlpathway.add(pweList.get(i));
		}


	}

	private String refineDisplayNames(String text) {
		text = WordUtils.wrap(text, 20);
		return text;
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