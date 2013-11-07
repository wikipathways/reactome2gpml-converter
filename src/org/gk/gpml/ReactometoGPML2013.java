package org.gk.gpml;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
import org.pathvisio.core.model.ConnectorType;
import org.pathvisio.core.model.DataNodeType;
import org.pathvisio.core.model.LineStyle;
import org.pathvisio.core.model.LineType;
import org.pathvisio.core.model.ObjectType;
import org.pathvisio.core.model.Pathway;
import org.pathvisio.core.model.PathwayElement;
import org.pathvisio.core.model.ShapeType;
import org.pathvisio.core.model.StaticProperty;
import org.pathvisio.core.model.ValignType;
import org.pathvisio.core.view.MIMShapes;
import org.reactome.convert.common.AbstractConverterFromReactome;

/**
 * Convert reactome pathways to gpml 2013a
 * 
 * @author anwesha
 * 
 */

public class ReactometoGPML2013 extends AbstractConverterFromReactome {

	private Pathway gpmlpathway;
	private PathwayElement mappInfo = null;
	private PathwayElement infoBox = null;
	private PathwayElement pwyelement = null;
	private PathwayElement datanode = null;
	private PathwayElement interaction = null;
	private PathwayElement label = null;
	private PathwayElement shape = null;

	private final String DATA_SOURCE = "Reactome - http://www.reactome.org";
	private final String VERSION = "release 35";
	private final int COORDINATE_SCALE_TO_GPML = 1; // Very strange. Not sure
	// why!
	private final Font DEFAULT_FONT = new Font("Dialog", Font.PLAIN, 12);

	// Used to handle id
	private GPMLIdHandler idHandler;

	public ReactometoGPML2013() {
		idHandler = new GPMLIdHandler();
	}

	private void reset() {
		idHandler.reset();

	}

	public void convertPathway(GKInstance pathway, String outputFileName)
			throws Exception {
		RenderablePathway diagram = queryPathwayDiagram(pathway);
		if (diagram == null) {
			throw new IllegalArgumentException(
					pathway
							+ " has no diagram available in the database, and cannot be converted to GPML at this time.");
		}
		reset();
		// Do a virtual drawing to make all dimensions correct
		PathwayEditor editor = new PathwayEditor();
		editor.setRenderable(diagram);
		Dimension size = editor.getPreferredSize();
		BufferedImage image = new BufferedImage(size.width, size.height,
				BufferedImage.TYPE_3BYTE_BGR);
		Graphics g = image.createGraphics();
		g.setFont(DEFAULT_FONT);
		// Need to set clip with the whole size so that everything can be drawn
		Rectangle clip = new Rectangle(size);
		g.setClip(clip);
		editor.paint(g);

		// Create new pathway
		gpmlpathway = new Pathway();

		mappInfo = PathwayElement.createPathwayElement(ObjectType.MAPPINFO);
		mappInfo.setStaticProperty(StaticProperty.MAPINFONAME,
				pathway.getDisplayName());
		// mappInfo.setStaticProperty(StaticProperty.COMMENTS, DATA_SOURCE);
		mappInfo.setStaticProperty(StaticProperty.VERSION, VERSION);

		GKInstance species = (GKInstance) pathway
				.getAttributeValue(ReactomeJavaConstants.species);
		if (species != null) {
			mappInfo.setStaticProperty(StaticProperty.ORGANISM,
					species.getDisplayName());
		}
		gpmlpathway.add(mappInfo);

		infoBox = PathwayElement.createPathwayElement(ObjectType.INFOBOX);
		gpmlpathway.add(infoBox);
		if (targetDir == null) {
			targetDir = new File(".");
		}

		// Do converting for this pathway
		// Convert entities first
		List<Renderable> objects = diagram.getComponents();
		if (objects == null) {
			// avoid errors on empty diagrams
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
				PathwayElement nodeEle = convertNode((Node) r, datanode);
				if (nodeEle != null) {
					gpmlpathway.add(nodeEle);
				}

			}

		}

		// Convert reactions
		for (HyperEdge edge : edges) {
			handleEdge(edge);

		}
		// Convert notes to labels: order is important labels should be placed
		// together here.
		for (Note note : notes) {
			label = PathwayElement.createPathwayElement(ObjectType.LABEL);
			PathwayElement labelElm = createLabelForNote(note, label);
			if (labelElm != null)
				gpmlpathway.add(labelElm);
		}
		// Convert compartment
		// Two steps are needed for converting compartments:
		// 1). Converting the names to labels
		for (RenderableCompartment compartment : compartments) {
			PathwayElement labelElm = createLabelForCompartment(compartment);
			if (labelElm != null)
				gpmlpathway.add(labelElm);
		}
		// 2). Converting the compartments to rectangles
		for (RenderableCompartment compartment : compartments) {
			PathwayElement compartElm = convertCompartment(compartment);
			if (compartElm != null)
				gpmlpathway.add(compartElm);
		}

		gpmlpathway.fixReferences();
		gpmlpathway.writeToXml(new File(outputFileName), true);

	}

	private PathwayElement convertCompartment(RenderableCompartment compartment) {
		shape = PathwayElement.createPathwayElement(ObjectType.SHAPE);
		shape.setGraphId("comp_" + compartment.getID());
		shape.setGroupRef("group_comp_" + compartment.getID());
		addGraphicsElm(compartment, shape);
		shape.setShapeType(ShapeType.RECTANGLE);
		shape.setLineThickness(2.0);
		return shape;
	}

	private PathwayElement convertNode(Node node, PathwayElement datanode2) {

		if (node.getReactomeId() != null) {
			GKInstance inst;
			try {
				inst = dbAdaptor.fetchInstance(node.getReactomeId());

				if (inst != null) {
					if (node instanceof RenderableProtein) {
						datanode2.setDataNodeType(DataNodeType.PROTEIN);
						datanode2.setShapeType(ShapeType.ROUNDED_RECTANGLE);
						datanode2.setFillColor(new Color(204, 255, 204));
					} else if (node instanceof RenderableRNA)
						datanode2.setDataNodeType(DataNodeType.RNA);
					else if (node instanceof RenderableChemical) {
						datanode2.setDataNodeType(DataNodeType.METABOLITE);
						datanode2.setShapeType(ShapeType.OVAL);
						datanode2.setFillColor(new Color(204, 255, 204));
					} else if (node instanceof RenderableComplex) {
						datanode2.setDataNodeType(DataNodeType.COMPLEX);
						datanode2.setShapeType(ShapeType.ROUNDED_RECTANGLE);
						datanode2.setFillColor((new Color(204, 255, 255)));
						datanode2.setLineThickness(2);
					} else if (node instanceof ProcessNode)
						datanode2.setDataNodeType(DataNodeType.PATHWAY);
					else
						datanode2.setDataNodeType(DataNodeType.UNKOWN);
				}
				addXref(datanode2, node, inst);
				datanode2.setTextLabel(node.getDisplayName());
				addGraphicsNode(node, datanode2);
				String id = "n" + node.getID() + "";
				datanode2.setGraphId(id);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		return datanode2;
	}

	private void handleEdge(HyperEdge edge) throws Exception {
		Color color = edge.getForegroundColor();
		if (color == null)
			color = Color.black;

		String graphId = "e_" + edge.getID();
		List<Point> points = edge.getBackbonePoints();
		createSegmentedLine(edge, graphId, "solid", "Line", color, points,
				false);

		handleInputs(edge, Color.RED);
		handleOutputs(edge, Color.GREEN);
//		handleCatalysts(edge, Color.BLUE);
//		handleInhibitors(edge, Color.CYAN);
//		handleActivators(edge, Color.YELLOW);
	}

	private void handleInputs(HyperEdge edge, Color color) throws Exception {
		int index = 0;
		int ind = 0;
		List<Node> inputs = edge.getInputNodes();
		List<List<Point>> inputBranches = edge.getInputPoints();
		List<Point> backbone = edge.getBackbonePoints();
		if (inputBranches != null && inputBranches.size() > 0) {

			for (List<Point> points : inputBranches) {
				points.add(backbone.get(0));
				String graphId = "e_" + edge.getID() + "_a_i" + index;
				createSegmentedLine(edge, graphId, "solid", "line", color,
						points, true);
				index++;
			}

			for (Node input : inputs) {
				String graphId = "e_" + edge.getID() + "_a_i" + ind;
				PathwayElement intElem = gpmlpathway.getElementById(graphId);
				PathwayElement nodElem = gpmlpathway.getElementById("n"
						+ input.getID() + "");
				intElem.getMStart().linkTo(nodElem);
				ind++;
			}

		}
	}

	private void handleOutputs(HyperEdge edge, Color color) throws Exception {
		int index = 0;
		int ind = 0;
		List<Node> outputs = edge.getOutputNodes();
		List<Point> backbone = edge.getBackbonePoints();
		List<List<Point>> outputBranches = edge.getOutputPoints();
		if (outputBranches != null && outputBranches.size() > 0) {

			for (List<Point> points : outputBranches) {
				points.add(backbone.get(backbone.size() - 1));
				String graphId = "e_" + edge.getID() + "_o" + index;
				createSegmentedLine(edge, graphId, "solid", "Arrow", color,
						points, true);
				index++;
			}
			for (Node output : outputs) {
				String graphId = "e_" + edge.getID() + "_o" + ind;
				PathwayElement intElem = gpmlpathway.getElementById(graphId);
				PathwayElement nodElem = gpmlpathway.getElementById("n"
						+ output.getID() + "");
				intElem.getMStart().linkTo(nodElem);
				ind++;
			}
		}

	}

	private void handleHelperNodes(HyperEdge edge, Color color, String style,
			String arrowType, String graphId, List<Node> helperNodes,
			List<List<Point>> branches) throws Exception {
		String intId = graphId;
		int index = 0;
		int ind = 0;
		if (helperNodes == null || helperNodes.size() == 0)
			return;
		if (branches != null && branches.size() > 0) {

			for (List<Point> points : branches) {
				graphId = graphId + index;
				createSegmentedLine(edge, graphId, style, arrowType, color,
						points, true);
			}
			for (int i = 0; i < helperNodes.size(); i++) {
				intId = "e_" + edge.getID() + "_o" + ind;
				System.out.println(intId);
				Node catalyst = helperNodes.get(i);
				PathwayElement intElem = gpmlpathway.getElementById(intId);
				PathwayElement nodElem = gpmlpathway.getElementById("n"
						+ catalyst.getID() + "");
				intElem.getMStart().linkTo(nodElem);

			}

		}

	}

	private void handleActivators(HyperEdge edge, Color color) throws Exception {
		List<Node> activators = edge.getActivatorNodes();
		List<List<Point>> activatorBranches = edge.getActivatorPoints();
		handleHelperNodes(edge, color, "solid", "Arrow", "e_" + edge.getID()
				+ "_a_", activators, activatorBranches);
	}

	private void handleInhibitors(HyperEdge edge, Color color) throws Exception {
		List<Node> inhibitors = edge.getInhibitorNodes();
		List<List<Point>> inhibitorBranches = edge.getInhibitorPoints();
		handleHelperNodes(edge, color, "solid", "TBar", "e_" + edge.getID()
				+ "_i_", inhibitors, inhibitorBranches);
	}

	private void handleCatalysts(HyperEdge edge, Color color) throws Exception {
		List<Node> catalysts = edge.getHelperNodes();
		List<List<Point>> catalystBranches = edge.getHelperPoints();
		handleHelperNodes(edge, color, "broken", "mim-catalysis",
				"e_" + edge.getID() + "_c_", catalysts, catalystBranches);
	}

	private void addGraphicsNode(Node node, PathwayElement pwyele) {
		// Graphics
		Color fgColor = node.getForegroundColor();
		if (fgColor == null)
			fgColor = Color.black;
		Rectangle bounds = node.getBounds();

		pwyele.setColor(fgColor);
		pwyele.setMCenterX(COORDINATE_SCALE_TO_GPML * bounds.getCenterX());
		pwyele.setMCenterY(COORDINATE_SCALE_TO_GPML * bounds.getCenterY());
		pwyele.setMWidth(COORDINATE_SCALE_TO_GPML * bounds.getWidth());
		pwyele.setMHeight(COORDINATE_SCALE_TO_GPML * bounds.getHeight());
	}

	private void createSegmentedLine(HyperEdge edge, String graphId,
			String style, String arrowType, Color color, List<Point> points,
			boolean needArrow) throws Exception {

		interaction = PathwayElement.createPathwayElement(ObjectType.LINE);
		MIMShapes.registerShapes();

		interaction.setGraphId(graphId);
		if (style.equalsIgnoreCase("broken"))
			interaction.setLineStyle(LineStyle.DASHED);
		else
			interaction.setLineStyle(LineStyle.SOLID);
		interaction.setStartLineType(LineType.fromName(arrowType));
		interaction.setColor(color);
		interaction.setConnectorType(ConnectorType.SEGMENTED);

		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			if (i == 0) {
				interaction.setMStartX(point.getX() * COORDINATE_SCALE_TO_GPML);
				interaction.setMStartY(point.getY() * COORDINATE_SCALE_TO_GPML);
			} else if (i == points.size() - 1) {
				interaction.setMEndX(point.getX() * COORDINATE_SCALE_TO_GPML);
				interaction.setMEndY(point.getY() * COORDINATE_SCALE_TO_GPML);
			}
//			if (needArrow && i == 0)
//				interaction.setEndLineType(LineType.ARROW);
		}
		if (edge.getReactomeId() != null) {
			GKInstance rxt = dbAdaptor.fetchInstance(edge.getReactomeId());
			addXref(interaction, edge, rxt);
		}
		gpmlpathway.add(interaction);

	}

	private void addXref(PathwayElement pwyele, Renderable render,
			GKInstance instance) throws Exception {

		// Try to get ReferenceEntity
		GKInstance referenceEntity = null;
		if (instance != null
				&& instance.getSchemClass().isValidAttribute(
						ReactomeJavaConstants.referenceEntity))
			referenceEntity = (GKInstance) instance
					.getAttributeValue(ReactomeJavaConstants.referenceEntity);
		if (referenceEntity == null) {
			// Use Reactome as default if no reference entity can be found
			pwyele.setDataSource(BioDataSource.REACTOME);

			String id = instance == null ? render.getReactomeId().toString()
					: getReactomeId(instance);
			if (id == null) {
				id = instance.getDBID().toString();
			}
			pwyele.setElementID(id);
		} else {
			GKInstance db = (GKInstance) referenceEntity
					.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
			// System.out.println(db.getDisplayName());

			// Chebi and uniprot give errors while searching by full name
			if (db.getDisplayName().equalsIgnoreCase("chebi")) {
				pwyele.setDataSource(BioDataSource.CHEBI);
			} else if (db.getDisplayName().equalsIgnoreCase("uniprot")) {
				pwyele.setDataSource(BioDataSource.UNIPROT);
			} else {
				pwyelement.setDataSource(DataSource.getByFullName(db
						.getDisplayName()));
			}
			// pwyele.setDataSource(BioDataSource.CHEBI);
			String identifier = getIdentifierFromReferenceEntity(referenceEntity);
			pwyele.setElementID(identifier);
		}

	}

	private PathwayElement createLabelForNote(Note note, PathwayElement label) {
		if (note.isPrivate()) // Private note should not be converted
			return null;
		label.setGraphId(gpmlpathway.getUniqueGraphId());
		label.setTextLabel(note.getDisplayName());
		addGraphicsElm(note, label);
		return label;
	}

	private void addGraphicsElm(Node node, PathwayElement pwyele) {
		// Create color element
		Color fgColor = node.getForegroundColor();
		if (fgColor == null)
			fgColor = Color.black;
		Rectangle bounds = node.getBounds();
		setGraphicsElmAttributes(pwyele, fgColor, bounds);
	}

	private void setGraphicsElmAttributes(PathwayElement pwyele, Color fgColor,
			Rectangle bounds) {
		pwyele.setColor(fgColor);
		pwyele.setMCenterX(COORDINATE_SCALE_TO_GPML * bounds.getCenterX());
		pwyele.setMCenterY(COORDINATE_SCALE_TO_GPML * bounds.getCenterY());
		pwyele.setMWidth(COORDINATE_SCALE_TO_GPML * bounds.getWidth());
		pwyele.setMHeight(COORDINATE_SCALE_TO_GPML * bounds.getHeight());
		pwyele.setValign(ValignType.MIDDLE);
	}

	private PathwayElement createLabelForCompartment(RenderableCompartment compt) {
		label = PathwayElement.createPathwayElement(ObjectType.LABEL);
		label.setGraphId("comp_text_" + compt.getID());
		label.setGroupRef("group_comp_" + compt.getID());
		label.setTextLabel(compt.getDisplayName());

		// Create graphics for this compartment
		Rectangle textRect = compt.getTextBounds();
		Color color = compt.getForegroundColor();
		if (color == null)
			color = Color.black;
		setGraphicsElmAttributes(label, color, textRect);
		// Because of a bug in the original code, have to specifiy the font size
		// graphicElm.setAttribute(GPMLConstants.FontSize,
		// COORDINATE_SCALE_TO_GPML * 12 + "");
		return label;
	}

	private void ensureTwoPointsNotSame(List<Point> points) {
		for (int i = 0; i < points.size() - 1; i++) {
			Point p1 = points.get(i);
			Point p2 = points.get(i + 1);
			if (p1.equals(p2)) {
				p2.y += 1; // This is rather random. Just break even.
			}
		}
	}

	// protected void linkPointToNode(String intId, Node node) {
	// datanode = gpmlpathway.getElementById("n" + node.getID() + "");
	// System.out.println(intId);
	// interaction = gpmlpathway.getElementById(intId);
	// interaction.getMStart().linkTo(datanode);
	// }

	/**
	 * Need to generate relative coordinate for node that is linked to a line
	 * element. Otherwise, the default, 0, 0 is used, which is the center of the
	 * node.
	 * 
	 * @param p
	 * @param node
	 * @return
	 */
	protected double[] generateRelCoordinate(Point p, Node node) {
		// There are only 8 points can be used.
		double[] rtn = new double[2];
		Point pos = node.getPosition();
		// the whole bounds have been divided into four parts relative to the
		// center.
		rtn[0] = 2.0d * (p.x - pos.x) / node.getBounds().getWidth();
		rtn[1] = 2.0d * (p.y - pos.y) / node.getBounds().getHeight();
		return rtn;
	}

	@Override
	public Document convertPathways(List<GKInstance> pathways) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Document convertPathway(GKInstance pathway) throws Exception {
		// TODO Auto-generated method stub
		return null;
	}

}
