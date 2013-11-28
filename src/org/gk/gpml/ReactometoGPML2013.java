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
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.bridgedb.DataSource;
import org.bridgedb.bio.BioDataSource;
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
import org.pathvisio.core.model.LineStyle;
import org.pathvisio.core.model.LineType;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
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

	ArrayList<Long> complexComponentIDs = new ArrayList<Long>();

	int complexCount = 0;
	double y = 0;

	MAnchor anchor1;
	MAnchor anchor2;
	MAnchor anchor3;
	Pathway gpmlpathway;
	BiopaxElement elementManager;
	PathwayElement mappInfo = null;
	PathwayElement infoBox = null;
	PathwayElement pwyelement = null;
	PathwayElement datanode = null;
	PathwayElement interaction = null;
	PathwayElement label = null;
	PathwayElement shape = null;

	final String DATA_SOURCE = "Reactome - http://www.reactome.org";
	final String VERSION = "release 46";

	@Override
	public Document convertPathway(GKInstance pathway) throws Exception {
		return null;
	}

	@SuppressWarnings("unchecked")
	public void convertPathway(GKInstance pathway, String outputFileDir)
			throws Exception {
		RenderablePathway diagram = queryPathwayDiagram(pathway);
		if (diagram == null) {
			throw new IllegalArgumentException(
					pathway
							+ " has no diagram available in the database, and cannot be converted to GPML at this time.");
		}

		/*
		 * A virtual drawing is done to get correct dimensions
		 */
		PathwayEditor editor = new PathwayEditor();
		editor.setRenderable(diagram);
		Dimension size = editor.getPreferredSize();
		BufferedImage image = new BufferedImage(size.width, size.height,
				BufferedImage.TYPE_3BYTE_BGR);

		y = size.height + 20;

		Graphics g = image.createGraphics();
		Rectangle clip = new Rectangle(size);
		g.setClip(clip);
		editor.paint(g);

		/*
		 * Create new pathway
		 */
		gpmlpathway = new Pathway();
		System.out.println("Creating new GPML pathway "+diagram.getDisplayName());
		complexCount = 0;
		/*
		 * Create new mappInfo and Infobox, every pathway needs to have one
		 */
		mappInfo = PathwayElement.createPathwayElement(ObjectType.MAPPINFO);
		infoBox = PathwayElement.createPathwayElement(ObjectType.INFOBOX);

		/*
		 * Create new biopax element for pathway to store literature references
		 */
		elementManager = gpmlpathway.getBiopax();

		/*
		 * Set pathway information
		 */
		mappInfo.setStaticProperty(StaticProperty.MAPINFONAME,
				pathway.getDisplayName());
		mappInfo.setMapInfoDataSource(DATA_SOURCE);
		mappInfo.setVersion(VERSION);

		/*
		 * Set species
		 */
		GKInstance species = (GKInstance) pathway
				.getAttributeValue(ReactomeJavaConstants.species);
		if (species != null) {
			mappInfo.setStaticProperty(StaticProperty.ORGANISM,
					species.getDisplayName());
		}

		/*
		 * Adding authors
		 */
		GKInstance authored = (GKInstance) pathway
				.getAttributeValue(ReactomeJavaConstants.authored);
		List<GKInstance> values = null;
		if (authored != null) {
			values = authored
					.getAttributeValuesList(ReactomeJavaConstants.author);
			String authors = joinDisplayNames(values);

			if (authors != null)
				mappInfo.setStaticProperty(StaticProperty.AUTHOR, authors);
		}
		/*
		 * Edited is converted to Maintainer
		 */
		GKInstance edited = (GKInstance) pathway
				.getAttributeValue(ReactomeJavaConstants.edited);
		if (edited != null) {
			values = edited
					.getAttributeValuesList(ReactomeJavaConstants.author);
			String maintainer = joinDisplayNames(values);
			if (maintainer != null)
				mappInfo.setStaticProperty(StaticProperty.MAINTAINED_BY,
						maintainer);
			// Get email from editors
			if (values != null && values.size() > 0) {
				StringBuilder builder = new StringBuilder();
				for (Iterator<GKInstance> it = values.iterator(); it.hasNext();) {
					GKInstance person = it.next();
					String email = (String) person
							.getAttributeValue(ReactomeJavaConstants.eMailAddress);
					if (email == null || email.length() == 0)
						continue;
					if (builder.length() > 0)
						builder.append(", ");
					builder.append(email);
				}
				if (builder.length() > 0)
					mappInfo.setStaticProperty(StaticProperty.EMAIL,
							builder.toString());
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

		List<HyperEdge> edges = new ArrayList<HyperEdge>();
		List<RenderableCompartment> compartments = new ArrayList<RenderableCompartment>();
		List<Note> notes = new ArrayList<Note>();

		for (Renderable r : objects) {
			if (r instanceof HyperEdge) {
				edges.add((HyperEdge) r);
			} else if (r instanceof RenderableCompartment)
				compartments.add((RenderableCompartment) r);
			else if (r instanceof Note) {
				notes.add((Note) r);
			} else if (r instanceof Node) {
				datanode = PathwayElement
						.createPathwayElement(ObjectType.DATANODE);
				PathwayElement nodeEle = convertNode((Node) r, datanode,
						diagram, y);
				if (nodeEle != null) {
					gpmlpathway.add(nodeEle);
				}

			}

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
			label = PathwayElement.createPathwayElement(ObjectType.LABEL);
			PathwayElement labelElm = createLabelForNote(note, label);
			if (labelElm != null)
				gpmlpathway.add(labelElm);
		}

		/*
		 * Converting compartment Two steps are needed for converting
		 * compartments: 1). Converting the names to labels
		 */
		for (RenderableCompartment compartment : compartments) {
			PathwayElement labelElm = createLabelForCompartment(compartment);
			if (labelElm != null)
				gpmlpathway.add(labelElm);
		}
		/*
		 * 2). Converting the compartments to rectangles
		 */
		for (RenderableCompartment compartment : compartments) {
			PathwayElement compartElm = convertCompartment(compartment);
			if (compartElm != null)
				gpmlpathway.add(compartElm);
		}

		gpmlpathway.add(mappInfo);
		gpmlpathway.add(infoBox);
		gpmlpathway.fixReferences();
		
		String pathwayname = gpmlpathway.getMappInfo().getMapInfoName();
		pathwayname = pathwayname.replaceAll("/", "_");
		pathwayname = pathwayname.replaceAll(" ", "-");
		
		File outputFile = new File(outputFileDir+pathwayname+".gpml");
		gpmlpathway.writeToXml(outputFile, true);
		System.out.println("Pathway file created at: "+outputFile.getAbsolutePath());

	}

	@Override
	public Document convertPathways(List<GKInstance> pathways) throws Exception {
		return null;
	}

	@SuppressWarnings("deprecation")
	private PathwayElement convertNode(Node node, PathwayElement dNode,
			RenderablePathway diagram, double y2) {

		if (node.getReactomeId() != null) {
			GKInstance inst;
			try {
				inst = dbAdaptor.fetchInstance(node.getReactomeId());
				System.out.println("Converting Nodes to Datanodes ...");
				if (inst != null) {
					if (node instanceof RenderableProtein)
						dNode.setDataNodeType(DataNodeType.PROTEIN);
					else if (node instanceof RenderableRNA)
						dNode.setDataNodeType(DataNodeType.RNA);
					else if (node instanceof RenderableChemical) {
						dNode.setDataNodeType(DataNodeType.METABOLITE);
						dNode.setColor(Color.BLUE);
					} else if (node instanceof RenderableComplex) {
						complexComponentIDs.clear();
						dNode.setDataNodeType(DataNodeType.COMPLEX);
						Long rId = node.getReactomeId();
						convertComplex(rId, y2);
						complexCount++;

					} else if (node instanceof ProcessNode) {
						dNode.setDataNodeType(DataNodeType.PATHWAY);
						dNode.setColor(new Color(20, 150, 30));
						dNode.setLineThickness(0);
						dNode.setLineStyle(LineStyle.DOUBLE);
					} else
						dNode.setDataNodeType(DataNodeType.UNKOWN);
				}
				addXrefnLitRef(dNode, node.getReactomeId(), inst);
				dNode.setTextLabel(node.getDisplayName());
				addGraphicsElm(node, dNode);
				dNode.setGraphId("n" + node.getID() + "");
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return dNode;
	}

	private void convertEdge(HyperEdge edge) throws Exception {
		System.out.println("Converting Hyperedges to Interactions ...");
		String graphId = "e_" + edge.getID();
		List<Point> points = edge.getBackbonePoints();

		interaction = createSegmentedLine(edge, graphId, "Line", points, false);

		List<List<Point>> inputBranches = edge.getInputPoints();
		if (inputBranches != null && inputBranches.size() > 1) {
			anchor1 = interaction.addMAnchor(0.0);
		}
		handleInputs(edge, anchor1);

		List<List<Point>> outputBranches = edge.getOutputPoints();
		if (outputBranches != null && outputBranches.size() > 1) {
			anchor2 = interaction.addMAnchor(0.99);
		}
		handleOutputs(edge, anchor2);

		List<List<Point>> helperBranches = edge.getHelperPoints();
		if (helperBranches != null && helperBranches.size() >= 1) {
			anchor3 = interaction.addMAnchor(0.5);
		}
		handleCatalysts(edge, anchor3);
		handleInhibitors(edge, anchor3);
		handleActivators(edge, anchor3);
	}

	/*
	 * Getting components of Reactome Complexes
	 */
	private void convertComplex(Long rId, double y2) throws IOException,
			ParserConfigurationException, SAXException,
			XPathExpressionException {
		System.out.println("Getting Complex components using Reactome Webservice ...");
		String urlString = "http://reactomews.oicr.on.ca:8080/ReactomeRESTfulAPI/"
				+ "RESTfulWS/queryById/Complex/" + rId;
		URL url = new URL(urlString);
		URLConnection conn = url.openConnection();
		conn.setRequestProperty("Accept", "application/xml");
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		org.w3c.dom.Document reactomeComplex = db.parse(conn.getInputStream());
		XPath xPath = XPathFactory.newInstance().newXPath();

		if (reactomeComplex.getDocumentElement().getNodeName()
				.equalsIgnoreCase("complex")) {
			NodeList reactomeComplexCompartmentNodes = (NodeList) xPath
					.compile("/complex/hasComponent").evaluate(reactomeComplex,
							XPathConstants.NODESET);
			
			for (int a = 0; a < reactomeComplexCompartmentNodes.getLength(); a++) {
				/*
				 * Getting child components
				 */
				String memId = reactomeComplexCompartmentNodes.item(a)
						.getFirstChild().getTextContent();
				Long memIdlong = Long.parseLong(memId, 10);
				convertComplex(memIdlong, y2);

			}
		} else if (reactomeComplex.getDocumentElement().getNodeName()
				.equalsIgnoreCase("definedSet")) {
			NodeList definedSetCompartments = (NodeList) xPath.compile(
					"/definedSet/hasMember").evaluate(reactomeComplex,
					XPathConstants.NODESET);
			
			for (int a = 0; a < definedSetCompartments.getLength(); a++) {
				/*
				 * Getting child components
				 */
				String memId = definedSetCompartments.item(a).getFirstChild()
						.getTextContent();
				Long memIdlong = Long.parseLong(memId, 10);
				convertComplex(memIdlong, y2);

			}
		} else {
			complexComponentIDs.add(rId);
			}
		
		createMembers(complexComponentIDs, reactomeComplex, y2);
		
	}

	/*
	 * Adding complex compartment to the pathway
	 */
	private void createMembers(ArrayList<Long> rIdList,
			org.w3c.dom.Document reactomeComplex,double y2)
			throws XPathExpressionException {

		for (int count = 0; count < rIdList.size(); count++) {
			double cx = 100 + (complexCount * 100);
			double cy = y2 + (count * 30);
			GKInstance inst;
			try {
				Long rId = (Long) rIdList.get(count);
				inst = dbAdaptor.fetchInstance(rId);
				PathwayElement newDN = PathwayElement
						.createPathwayElement(ObjectType.DATANODE);

				if (reactomeComplex.getDocumentElement().getNodeName()
						.equalsIgnoreCase("entityWithAccessionedSequence")) {
					newDN.setDataNodeType(DataNodeType.PROTEIN);
										
				} else if (reactomeComplex.getDocumentElement().getNodeName()
						.equalsIgnoreCase("simpleEntity")) {
					newDN.setDataNodeType(DataNodeType.METABOLITE);
								
				}
				
				String[] names = inst.getDisplayName().split("\\[");
				newDN.setTextLabel(names[0]);
				
				newDN.setMCenterX(cx);
				newDN.setMCenterY(cy);
				newDN.setInitialSize();

				addXrefnLitRef(newDN, rId, inst);

				gpmlpathway.add(newDN);

			} catch (Exception e) {
				e.printStackTrace();
			}
		}

	}
	
	private PathwayElement convertCompartment(RenderableCompartment compartment) {
		System.out.println("Converting Compartments to Shapes ...");
		shape = PathwayElement.createPathwayElement(ObjectType.SHAPE);
		shape.setGraphId("comp_" + compartment.getID());
		shape.setGroupRef("group_comp_" + compartment.getID());
		shape.setShapeType(ShapeType.ROUNDED_RECTANGLE);
		shape.setLineStyle(LineStyle.DOUBLE);
		shape.setLineThickness(3);
		shape.setTransparent(true);
		shape.setColor(new Color(192, 192, 192));
		addGraphicsElm(compartment, shape);
		return shape;
	}

	private PathwayElement createLabelForCompartment(RenderableCompartment compt) {
		label = PathwayElement.createPathwayElement(ObjectType.LABEL);
		label.setGraphId("comp_text_" + compt.getID());
		label.setGroupRef("group_comp_" + compt.getID());

		label.setTextLabel(compt.getDisplayName());
		Rectangle textRect = compt.getTextBounds();
		setGraphicsElmAttributes(label, textRect);
		return label;
	}

	private PathwayElement createLabelForNote(Note note, PathwayElement label) {
		System.out.println("Converting Notes to Labels ...");
		if (note.isPrivate()) // Private notes should not be converted
			return null;
		label.setGraphId(gpmlpathway.getUniqueGraphId());
		label.setTextLabel(note.getDisplayName());
		addGraphicsElm(note, label);
		return label;
	}

	private PathwayElement createSegmentedLine(HyperEdge edge, String graphId,
			String arrowType, List<Point> points, boolean isOutput)
			throws Exception {

		PathwayElement newInteraction = PathwayElement
				.createPathwayElement(ObjectType.LINE);
		MIMShapes.registerShapes();

		newInteraction.setGraphId(graphId);
		newInteraction.setLineStyle(LineStyle.SOLID);
		newInteraction.setEndLineType(LineType.fromName(arrowType));
		newInteraction.setColor(Color.BLACK);
		newInteraction.setConnectorType(ConnectorType.SEGMENTED);
		if (isOutput) {
			for (int i = 0; i < points.size(); i++) {
				Point point = points.get(i);
				if (i == 0) {
					newInteraction.setMEndX(point.getX());
					newInteraction.setMEndY(point.getY());
				} else if (i == points.size() - 1) {
					newInteraction.setMStartX(point.getX());
					newInteraction.setMStartY(point.getY());

				}
			}
		} else {
			for (int i = 0; i < points.size(); i++) {
				Point point = points.get(i);
				if (i == 0) {
					newInteraction.setMStartX(point.getX());
					newInteraction.setMStartY(point.getY());
				} else if (i == points.size() - 1) {
					newInteraction.setMEndX(point.getX());
					newInteraction.setMEndY(point.getY());
					newInteraction.setMEndX(point.getX());
					newInteraction.setMEndY(point.getY());
				}
			}
		}

		if (edge.getReactomeId() != null) {
			GKInstance rxt = dbAdaptor.fetchInstance(edge.getReactomeId());
			addXrefnLitRef(newInteraction, edge.getReactomeId(), rxt);
		}
		gpmlpathway.add(newInteraction);
		return newInteraction;
	}

	private void handleHelperNodes(HyperEdge edge, MAnchor anchor,
			String style, String arrowType, String graphId,
			List<Node> helperNodes, List<List<Point>> branches)
			throws Exception {
		String intId = graphId;
		int index = 0;
		int ind = 0;
		List<Point> backbone = edge.getBackbonePoints();
		if (helperNodes == null || helperNodes.size() == 0)
			return;
		if (branches != null && branches.size() > 0) {
			for (List<Point> points : branches) {
				points.add(backbone.get((backbone.size()) / 2));
				graphId = graphId + index;
				createSegmentedLine(edge, graphId, arrowType, points, false);
			}
			for (int i = 0; i < helperNodes.size(); i++) {
				intId = intId + ind;
				Node catalyst = helperNodes.get(i);
				PathwayElement intElem = gpmlpathway.getElementById(intId);
				PathwayElement nodElem = gpmlpathway.getElementById("n"
						+ catalyst.getID() + "");
				intElem.getMEnd().linkTo(anchor);
				intElem.getMStart().linkTo(nodElem);

			}

		}

	}

	private void handleInputs(HyperEdge edge, MAnchor anchor) throws Exception {
		int index = 0;
		int ind = 0;
		List<Node> inputs = edge.getInputNodes();
		List<List<Point>> inputBranches = edge.getInputPoints();
		List<Point> backbone = edge.getBackbonePoints();
		if (inputBranches != null && inputBranches.size() > 1) {
			for (List<Point> points : inputBranches) {
				points.add(backbone.get(0));
				String graphId = "e_" + edge.getID() + "_a_i" + index;

				createSegmentedLine(edge, graphId, "line", points, false);
				index++;
			}

			for (Node input : inputs) {
				String graphId = "e_" + edge.getID() + "_a_i" + ind;
				PathwayElement intElem = gpmlpathway.getElementById(graphId);
				PathwayElement nodElem = gpmlpathway.getElementById("n"
						+ input.getID() + "");
				intElem.getMEnd().linkTo(anchor);
				intElem.getMStart().linkTo(nodElem);
				ind++;
			}

		} else if (edge.getInputNodes().size() == 1) {
			Node input = edge.getInputNode(0);
			String graphId = "e_" + edge.getID() + "";
			PathwayElement intElem = gpmlpathway.getElementById(graphId);
			PathwayElement nodElem = gpmlpathway.getElementById("n"
					+ input.getID() + "");
			intElem.getMStart().linkTo(nodElem);
		}
	}

	private void handleOutputs(HyperEdge edge, MAnchor anchor) throws Exception {
		int index = 0;
		int ind = 0;
		List<Node> outputs = edge.getOutputNodes();
		List<Point> backbone = edge.getBackbonePoints();
		List<List<Point>> outputBranches = edge.getOutputPoints();

		if (outputBranches != null && outputBranches.size() > 0) {

			for (List<Point> points : outputBranches) {
				points.add(backbone.get(backbone.size() - 1));
				String graphId = "e_" + edge.getID() + "_o" + index;
				createSegmentedLine(edge, graphId, "Arrow", points, true);
				index++;
			}
			for (Node output : outputs) {
				String graphId = "e_" + edge.getID() + "_o" + ind;
				PathwayElement intElem = gpmlpathway.getElementById(graphId);
				PathwayElement nodElem = gpmlpathway.getElementById("n"
						+ output.getID() + "");
				intElem.getMEnd().linkTo(nodElem);
				intElem.getMStart().linkTo(anchor);
				ind++;
			}
		} else if (edge.getOutputNodes().size() == 1) {
			Node output = edge.getOutputNode(0);
			String graphId = "e_" + edge.getID() + "";
			PathwayElement intElem = gpmlpathway.getElementById(graphId);
			intElem.setEndLineType(LineType.ARROW);
			PathwayElement nodElem = gpmlpathway.getElementById("n"
					+ output.getID() + "");
			intElem.getMEnd().linkTo(nodElem);
		}

	}

	private void handleActivators(HyperEdge edge, MAnchor anchor)
			throws Exception {
		List<Node> activators = edge.getActivatorNodes();
		List<List<Point>> activatorBranches = edge.getActivatorPoints();
		handleHelperNodes(edge, anchor, "solid", "Arrow", "e_" + edge.getID()
				+ "_a_", activators, activatorBranches);
	}

	private void handleInhibitors(HyperEdge edge, MAnchor anchor)
			throws Exception {
		List<Node> inhibitors = edge.getInhibitorNodes();
		List<List<Point>> inhibitorBranches = edge.getInhibitorPoints();
		handleHelperNodes(edge, anchor, "solid", "TBar", "e_" + edge.getID()
				+ "_i_", inhibitors, inhibitorBranches);
	}

	private void handleCatalysts(HyperEdge edge, MAnchor anchor)
			throws Exception {
		List<Node> catalysts = edge.getHelperNodes();
		List<List<Point>> catalystBranches = edge.getHelperPoints();
		handleHelperNodes(edge, anchor, "solid", "mim-catalysis",
				"e_" + edge.getID() + "_c_", catalysts, catalystBranches);
	}

	private String joinDisplayNames(List<GKInstance> instances) {
		if (instances == null || instances.size() == 0)
			return null;
		StringBuilder builder = new StringBuilder();
		for (GKInstance inst : instances) {
			if (builder.length() > 0)
				builder.append(", ");
			builder.append(inst.getDisplayName());
		}
		return builder.toString();
	}

	private void addGraphicsElm(Node node, PathwayElement pwyele) {
		Rectangle bounds = node.getBounds();
		setGraphicsElmAttributes(pwyele, bounds);

	}

	@SuppressWarnings("unchecked")
	private void addXrefnLitRef(PathwayElement pwyele, Long rId,
			GKInstance instance) throws Exception {
		System.out.println("Annotating elements ...");
		/*
		 * Try to get ReferenceEntity
		 */
		GKInstance referenceEntity = null;
		if (instance != null
				&& instance.getSchemClass().isValidAttribute(
						ReactomeJavaConstants.referenceEntity))
			referenceEntity = (GKInstance) instance
					.getAttributeValue(ReactomeJavaConstants.referenceEntity);

		if (referenceEntity == null) {
			/*
			 * Use Reactome as default if no reference entity can be found
			 */
			pwyele.setDataSource(BioDataSource.REACTOME);

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
			if (db.getDisplayName().equalsIgnoreCase("chebi")) {
				pwyele.setDataSource(BioDataSource.CHEBI);
			} else if (db.getDisplayName().equalsIgnoreCase("uniprot")) {
				pwyele.setDataSource(BioDataSource.UNIPROT);
			} else {
				pwyele.setDataSource(DataSource.getByFullName(db
						.getDisplayName()));
			}
			String identifier = getIdentifierFromReferenceEntity(referenceEntity);
			if (pwyele.getDataSource().getFullName().equalsIgnoreCase("chebi")) {
				identifier = "CHEBI:" + identifier;
			}
			pwyele.setElementID(identifier);
		}

		/*
		 * Adding comments
		 */
		if (instance.getSchemClass().isValidAttribute(
				ReactomeJavaConstants.summation)) {
			List<GKInstance> summations = instance
					.getAttributeValuesList(ReactomeJavaConstants.summation);
			if (summations != null && summations.size() > 0) {
				for (GKInstance summation : summations) {
					String text = (String) summation
							.getAttributeValue(ReactomeJavaConstants.text);
					if (text != null && text.length() > 0) {
						pwyele.addComment(text, "Reactome");

					}
				}
			}
		}

		/*
		 * Adding literature references
		 */
		if (instance.getSchemClass().isValidAttribute(
				ReactomeJavaConstants.literatureReference)) {
			List<GKInstance> litRefs = instance
					.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
			if (litRefs != null && litRefs.size() > 0) {

				for (GKInstance litRef : litRefs) {
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
						org.w3c.dom.Document publication = db.parse(new URL(
								urlString).openStream());

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

			}

		}
	}

	private void setGraphicsElmAttributes(PathwayElement pwyele,
			Rectangle bounds) {
		pwyele.setMCenterX(bounds.getCenterX());
		pwyele.setMCenterY(bounds.getCenterY());
		pwyele.setMWidth(bounds.getWidth());
		pwyele.setMHeight(bounds.getHeight());
		pwyele.setValign(ValignType.MIDDLE);
	}

}
