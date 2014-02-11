package org.gk.gpml;

/*
 * Created on Oct 19, 2009
 *
 */

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.font.LineBreakMeasurer;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.text.AttributedCharacterIterator;
import java.text.AttributedString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.*;
import org.gk.util.GKApplicationUtilities;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.junit.Test;
import org.reactome.biopax.BioPAXJavaConstants;
import org.reactome.biopax.ReactomeToBioPAXPublicationConverter;
import org.reactome.convert.common.AbstractConverterFromReactome;

/**
 * This class is used to convert a reactome pathway diagram to GPML. To make this conversion, it is required 
 * that a pathway should have a pathway diagram. All converting is done via database. 
 * @author wgm
 *
 */
public class ReactomeToGPMLConverter extends AbstractConverterFromReactome {
    private final String DATA_SOURCE = "Reactome - http://www.reactome.org";
    private final String VERSION = "release 35";
    private final Namespace GPML_NS = Namespace.getNamespace(GPMLConstants.GPML_NS);
    private final Namespace RDF_NS = Namespace.getNamespace("rdf", 
                                                            BioPAXJavaConstants.RDF_NS);
    // GPML2010a requires BioPAX level 3. Instead of upgrading the whole BioPAX
    // converter we just specify the level 3 namespace here. BioPAX level 3 is a 
    // superset of BioPAX level 2.
    private final Namespace BP_NS = Namespace.getNamespace("bp",
                                                           GPMLConstants.BIOPAX_NS);
    private final int COORDINATE_SCALE_TO_GPML = 1; // Very strange. Not sure why!
    private final Font DEFAULT_FONT = new Font("Dialog", Font.PLAIN, 12);
    // The root element for pathway
    private Element pathwayElm;
    private Element biopaxElm;
    // Used to handle id
    private GPMLIdHandler idHandler;
    
    public ReactomeToGPMLConverter() {
        idHandler = new GPMLIdHandler();
    }
    
    public Document convertPathways(List<GKInstance> pathways) throws Exception {
        throw new IllegalStateException("This method is not supported. Use convertPathway(GKInstance) instead.");
    }
    
    private void reset() {
        idHandler.reset();
        pathwayElm = null;
        biopaxElm = null;
    }
    
    public Document convertPathway(GKInstance pathway) throws Exception {
        if (targetDir == null) {
            targetDir = new File(".");
        }
        // Check if we can find a pathway diagram for the specified pathway. If not,
        // we cannot convert it.
        RenderablePathway diagram = queryPathwayDiagram(pathway);
        if (diagram == null) {
            throw new IllegalArgumentException(pathway + " has no diagram available in the database, and cannot be converted to GPML at this time.");
        }
        reset();
        // Do a virtual drawing to make all dimensions correct
        PathwayEditor editor = new PathwayEditor();
        editor.setRenderable(diagram);
        Dimension size = editor.getPreferredSize();
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = image.createGraphics();
        g.setFont(DEFAULT_FONT);
        // Need to set clip with the whole size so that everything can be drawn
        Rectangle clip = new Rectangle(size);
        g.setClip(clip);
        editor.paint(g);
        Document document = new Document();
        // Root element is pathway
        pathwayElm = createPathwayElm(pathway);
        document.setRootElement(pathwayElm);
        // Do converting for this pathway
        // Convert entities first
        List<Renderable> objects = diagram.getComponents();
        if (objects == null) {
            // avoid errors on empty diagrams
            objects = new ArrayList<Renderable>();
        }
        List<HyperEdge> edges = new ArrayList<HyperEdge>();
        List<RenderableCompartment> compartments = new ArrayList<RenderableCompartment>();
        Map<Renderable, String> rToGraphId = new HashMap<Renderable, String>();
        // The following order (nodes, lines, compartments) should not be changed based on XML Schema
        List<Note> notes = new ArrayList<Note>();
        for (Renderable r : objects) {
            if (r instanceof HyperEdge) {
                edges.add((HyperEdge)r);
            }
            else if (r instanceof RenderableCompartment)
                compartments.add((RenderableCompartment)r);
            else if (r instanceof Note) {
                notes.add((Note)r);
            }
            else if (r instanceof Node) {
                Element dataNode = createNode((Node) r);
                if (dataNode != null) {
                    pathwayElm.addContent(dataNode);
                    if (r.getReactomeId() != null) {
                        GKInstance inst = dbAdaptor.fetchInstance(r.getReactomeId());
                        if (inst != null) {
                            addSemanticContent(dataNode, 
                                               inst,
                                               true);
                            addCosmetics(dataNode, inst);
                            wrapLabels(dataNode, ((Graphics2D)g).getFontRenderContext());
                        }
                    }
                    String gpmlId = dataNode.getAttributeValue(GPMLConstants.GraphId);
                    rToGraphId.put(r, gpmlId);
                }
            }
        }
        // Convert reactions
        for (HyperEdge edge : edges) {
            handleEdge(edge, 
                       pathwayElm, 
                       rToGraphId);
        }
        // Convert notes to labels: order is important labels should be placed together here.
        for (Note note : notes) {
            Element labelElm = createLabelForNote(note);
            if (labelElm != null)
                pathwayElm.addContent(labelElm);   
        }
        // Convert compartment
        // Two steps are needed for converting compartments:
        // 1). Converting the names to labels
        for (RenderableCompartment compartment : compartments) {
            Element labelElm = createLabelForCompartment(compartment);
            pathwayElm.addContent(labelElm);
        }
        // 2). Converting the compartments to rectangles
        for (RenderableCompartment compartment : compartments) {
            Element compartElm = convertCompartment(compartment);
            pathwayElm.addContent(compartElm);
            String gpmlId = compartElm.getAttributeValue(GPMLConstants.GraphId);
            rToGraphId.put(compartment, gpmlId);
        }
        // Create groups
//        for (HyperEdge edge : edges) {
//            String groupId = createGraphId(edge);
//            Element groupElm = createGroup(groupId);
//            // Show display name as text label for group
//            String name = edge.getDisplayName();
//            if (name != null)
//                groupElm.setAttribute(GPMLConstants.TextLabel, name);
//            // Add semantics to edges displayed as groups
//            if (edge.getReactomeId() != null) {
//                GKInstance rxt = dbAdaptor.fetchInstance(edge.getReactomeId());
//                addSemanticContent(groupElm, rxt, false);
//            }
//            pathwayElm.addContent(groupElm);
//        }
        // Disable creating group for compartment. It is pretty disturbing to have too many
        // grouping layers.
        // Link compartment and text label together
//        for (RenderableCompartment compartment : compartments) {
//            String groupId = "group_comp_" + compartment.getID();
//            Element groupElm = createGroup(groupId);
//            pathwayElm.addContent(groupElm);
//        }
        // InfoBox is required
        Element infoBoxElm = new Element(GPMLConstants.InfoBox, GPML_NS);
        infoBoxElm.setAttribute(GPMLConstants.CenterX, "3872.5");
        infoBoxElm.setAttribute(GPMLConstants.CenterY, "6350.0");
        pathwayElm.addContent(infoBoxElm);
        // Biopax element is the last element
        if (biopaxElm != null)
            pathwayElm.addContent(biopaxElm);
        return document;
    }
    
    private String createGraphId(HyperEdge edge) {
        String id = "group_e_" + edge.getID();
        if (edge.getReactomeId() != null) {
            id = id + "_REACTOME_" + edge.getReactomeId();
        }
        return id;
    }

    private Element createGroup(String groupId) {
        Element groupElm = new Element(GPMLConstants.Group, GPML_NS);
        // Note: group id format for compartment
        groupElm.setAttribute(GPMLConstants.GroupId, 
                              groupId);
        // The default style is for "complex" actually.
        groupElm.setAttribute(GPMLConstants.Style,
                              GPMLConstants.Group);
        return groupElm;
    }
    
    private Element createLabelForCompartment(RenderableCompartment compt) {
        Element label = new Element(GPMLConstants.Label, GPML_NS);
        label.setAttribute(GPMLConstants.GraphId, "comp_text_" + compt.getID());
        label.setAttribute(GPMLConstants.GroupRef, "group_comp_" + compt.getID());
        label.setAttribute(GPMLConstants.TextLabel, compt.getDisplayName());
        // Create graphics for this compartment
        Rectangle textRect = compt.getTextBounds();
        Element graphicElm = new Element(GPMLConstants.Graphics, GPML_NS);
        Color color = compt.getForegroundColor();
        if (color == null)
            color = Color.black;
        setGraphicsElmAttributes(graphicElm, 
                                 color, 
                                 textRect);
        // Because of a bug in the original code, have to specifiy the font size
        graphicElm.setAttribute(GPMLConstants.FontSize, 
                                COORDINATE_SCALE_TO_GPML * 12 + "");
        label.addContent(graphicElm);
        return label;
    }
    
    private void handleEdge(HyperEdge edge,
                            Element pathwayElm,
                            Map<Renderable, String> rToGraphId) throws Exception {
        // Do a validation to make all coordinates correct
        //edge.validateConnectInfo();
        // Color to be used by all lines converted from this edge
        Color color = edge.getForegroundColor();
        if (color == null)
            color = Color.black;
        // These point element will be used to connect to nodes
        Map<Point, Element> pointToElm = new HashMap<Point, Element>();
        // Handle the backbone
        Element lineElm = createLineElmForBackbone(edge,
                                                   color,
                                                   pointToElm);
        //        String groupId = createGraphId(edge);
        //        for (Element elm : lineElms) {
        //            elm.setAttribute(GPMLConstants.GroupRef, groupId);
        //        }
        pathwayElm.addContent(lineElm);
        handleInputs(edge, 
                     color, 
                     pointToElm,
                     rToGraphId,
                     pathwayElm);
        handleOutputs(edge,
                      color, 
                      pointToElm,
                      rToGraphId,
                      pathwayElm);
        handleCatalysts(edge, 
                        color,
                        pointToElm,
                        rToGraphId,
                        pathwayElm);
        handleInhibitors(edge, 
                         color, 
                         pointToElm,
                         rToGraphId, 
                         pathwayElm);
        handleActivators(edge, 
                         color,
                         pointToElm,
                         rToGraphId,
                         pathwayElm);
        if (edge.getReactomeId() != null) {
            GKInstance rxt = dbAdaptor.fetchInstance(edge.getReactomeId());
            addSemanticContent(lineElm, rxt, true);
        }
    }
    
    private void handleHelperNodes(HyperEdge edge,
                                   Color color, 
                                   String style,
                                   String arrowType,
                                   String graphId,
                                   Map<Point, Element> pointToElm,
                                   Map<Renderable, String> rToGraphId,
                                   Element pathwayElm, 
                                   List<Node> helperNodes,
                                   List<List<Point>> branches) {
        if (helperNodes == null || helperNodes.size() == 0)
            return;
        if (branches != null && branches.size() > 0) {
            String anchorId = "e_" + edge.getID() + "_a_p"; // p is for position
            Point anchor = edge.getPosition();
            int index = 0;
            for (List<Point> points : branches) {
                Element elm = createLineElmForBranch(points,
                                                     anchor,
                                                     anchorId,
                                                     false, 
                                                     graphId + "_" + index, 
                                                     color,
                                                     pointToElm);
                // Want to use dashed line for catalysts
                Element graphics = getGraphicsChild(elm);
                graphics.setAttribute(GPMLConstants.LineStyle,
                                      style);
                index ++;
                pathwayElm.addContent(elm);
            }
            // Get the last point for arrow head
            Element anchorElm = pointToElm.get(anchor);
            anchorElm.setAttribute(GPMLConstants.ArrowHead, 
                                   arrowType);
        }
        
        // Link edge to nodes
        for (int i = 0; i < helperNodes.size(); i ++) {
            Node catalyst = helperNodes.get(i);
            List<Point> points = branches.get(i);
            Point point = points.get(0);
            linkPointToNode(point, 
                            pointToElm,
                            rToGraphId,
                            catalyst);
        }
    }
    private Element getGraphicsChild(Element elm) {
        Element graphics = (Element) elm.getChild("Graphics", GPML_NS);
        return graphics;
    }
    private void handleCatalysts(HyperEdge edge,
                                 Color color,
                                 Map<Point, Element> pointToElm,
                                 Map<Renderable, String> rToGraphId,
                                 Element pathwayElm) {
        List<Node> catalysts = edge.getHelperNodes();
        List<List<Point>> catalystBranches = edge.getHelperPoints();
        String style = GPMLConstants.Broken;
        String arrowType = "Line";
        handleHelperNodes(edge, 
                          color,
                          style,
                          arrowType,
                          "e_" + edge.getID() + "_c_",
                          pointToElm,
                          rToGraphId,
                          pathwayElm,
                          catalysts,
                          catalystBranches);
    }
    
    private void handleActivators(HyperEdge edge,
                                  Color color,
                                  Map<Point, Element> pointToElm,
                                  Map<Renderable, String> rToGraphId,
                                  Element pathwayElm) {
        List<Node> activators = edge.getActivatorNodes();
        List<List<Point>> activatorBranches = edge.getActivatorPoints();
        String style = GPMLConstants.Solid;
        handleHelperNodes(edge, 
                          color,
                          style,
                          "Arrow",
                          "e_" + edge.getID() + "_a_",
                          pointToElm,
                          rToGraphId,
                          pathwayElm,
                          activators,
                          activatorBranches);
    }
    
    private void handleInhibitors(HyperEdge edge,
                                  Color color,
                                  Map<Point, Element> pointToElm,
                                  Map<Renderable, String> rToGraphId,
                                  Element pathwayElm) {
        List<Node> inhibitors = edge.getInhibitorNodes();
        List<List<Point>> inhibitorBranches = edge.getInhibitorPoints();
        String style = GPMLConstants.Solid;
        handleHelperNodes(edge, 
                          color,
                          style,
                          "TBar",
                          "e_" + edge.getID() + "_i_",
                          pointToElm,
                          rToGraphId,
                          pathwayElm,
                          inhibitors,
                          inhibitorBranches);
    }
    
    private void handleInputs(HyperEdge edge,
                              Color color,
                              Map<Point, Element> pointToElm,
                              Map<Renderable, String> rToGraphId,
                              Element pathwayElm) {
        List<List<Point>> inputBranches = edge.getInputPoints();
        List<Point> backbone = edge.getBackbonePoints();
        if (inputBranches != null && inputBranches.size() > 0) {
            // This id should be used by the method createLineElmForBackbone()
            String anchorId = "e_" + edge.getID() + "_a_i";
            Point anchor = backbone.get(0);
            int index = 0;
            for (List<Point> points : inputBranches) {
                Element lineElm = createLineElmForBranch(points,
                                                         anchor,
                                                         anchorId,
                                                         false, 
                                                         "e_" + edge.getID() + "_i" + index, // i for input
                                                         color,
                                                         pointToElm);
                index ++;
                pathwayElm.addContent(lineElm);
            }
        }
        
        // Link edge to nodes
        List<Node> inputs = edge.getInputNodes();
        if (inputs.size() == 1) {
            Point p = backbone.get(0);
            Node node = inputs.get(0);
            linkPointToNode(p, pointToElm, rToGraphId, node);
        }
        else if (inputs.size() > 1) {
            for (int i = 0; i < inputs.size(); i ++) {
                Node input = inputs.get(i);
                List<Point> points = inputBranches.get(i);
                Point point = points.get(0);
                linkPointToNode(point, pointToElm, rToGraphId, input);
            }
        }
    }
    
    private void handleOutputs(HyperEdge edge,
                               Color color,
                               Map<Point, Element> pointToElm,
                               Map<Renderable, String> rToGraphId,
                               Element pathwayElm) {
        List<Point> backbone = edge.getBackbonePoints();
        List<List<Point>> outputBranches = edge.getOutputPoints();
        if (outputBranches != null && outputBranches.size() > 0) {
            String anchorId = "e_" + edge.getID() + "_a_o";
            Point anchor = backbone.get(backbone.size() - 1);
            int index = 0;
            for (List<Point> points : outputBranches) {
                Element lineElm = createLineElmForBranch(points,
                                                         anchor,
                                                         anchorId,
                                                         true, 
                                                         "e_" + edge.getID() + "_o" + index, // O for output
                                                         color,
                                                         pointToElm);
                index ++;
                pathwayElm.addContent(lineElm);
            }
        }
        List<Node> outputs = edge.getOutputNodes();
        if (outputs.size() == 1) {
            Point p = backbone.get(backbone.size() - 1);
            Node node = outputs.get(0);
            linkPointToNode(p, 
                            pointToElm,
                            rToGraphId,
                            node);
            Element pointElm = pointToElm.get(p);
            pointElm.setAttribute(GPMLConstants.ArrowHead, "Arrow");
        }
        else if (outputs.size() > 1) {
            for (int i = 0; i < outputs.size(); i ++) {
                Node output = outputs.get(i);
                List<Point> points = outputBranches.get(i);
                Point point = points.get(0);// The first point is used to link to node
                linkPointToNode(point, pointToElm, rToGraphId, output);
            }
        }
    }
    
    protected void linkPointToNode(Point point,
                                 Map<Point, Element> pointToElm,
                                 Map<Renderable, String> rToGraphId,
                                 Node node) {
        Element pointElm = pointToElm.get(point);
        pointElm.setAttribute(GPMLConstants.GraphRef,
                              rToGraphId.get(node));
        // Specify the relative X, y to avoid the default: 0, 0
        double[] relativeXY = generateRelCoordinate(point, node);
        pointElm.setAttribute(GPMLConstants.relX, relativeXY[0] + "");
        pointElm.setAttribute(GPMLConstants.relY, relativeXY[1] + "");
    }
    
    /**
     * Need to generate relative coordinate for node that is linked to a line element. Otherwise,
     * the default, 0, 0 is used, which is the center of the node.
     * @param p
     * @param node
     * @return
     */
    protected double[] generateRelCoordinate(Point p, Node node) {
        // There are only 8 points can be used.
        double[] rtn = new double[2];
        Point pos = node.getPosition();
        // the whole bounds have been divided into four parts relative to the center.
        rtn[0] = 2.0d * (p.x - pos.x) / node.getBounds().getWidth();
        rtn[1] = 2.0d * (p.y - pos.y) / node.getBounds().getHeight();
        return rtn;
    }
    
    private Element createSegmentedLine(String graphId,
                                        Color color,
                                        List<Point> points,
                                        Map<Point, Element> pointToElm,
                                        boolean needArrow) {
        Element lineElm = new Element(GPMLConstants.Line, GPML_NS);
        lineElm.setAttribute(GPMLConstants.GraphId, 
                             graphId);  
        Element graphicElm = new Element(GPMLConstants.Graphics, GPML_NS);
        lineElm.addContent(graphicElm);
        graphicElm.setAttribute(GPMLConstants.LineStyle,
                                GPMLConstants.Solid);
        graphicElm.setAttribute(GPMLConstants.Color, 
                                GKApplicationUtilities.getHexForColor(color));
        graphicElm.setAttribute(GPMLConstants.ConnectorType,
                                GPMLConstants.Segmented);
        for (int i = 0; i < points.size(); i++) {
            Point point = points.get(i);
            Element pointElm = convertPointToElement(point,
                                                     graphId + "_" + i);
            pointToElm.put(point, pointElm);
            graphicElm.addContent(pointElm);
            if (needArrow && i == 0)
                pointElm.setAttribute(GPMLConstants.ArrowHead, "Arrow");
        }
        return lineElm;
    }
        
    /**
     * It seems there is a problem to render in pathvisio if two consecutive points are the same.
     * This method is used to make sure two points are not the same.
     * @param points
     */
    protected void ensureTwoPointsNotSame(List<Point> points) {
        for (int i = 0; i < points.size() - 1; i++) {
            Point p1 = points.get(i);
            Point p2 = points.get(i + 1);
            if (p1.equals(p2)) {
                p2.y += 1; // This is rather random. Just break even.
            }
        }
    }
                   
    private Element createLineElmForBackbone(HyperEdge edge,
                                             Color color,
                                             Map<Point, Element> pointToElm) {
        String graphId = "e_" + edge.getID();
        List<Point> points = edge.getBackbonePoints();
        ensureTwoPointsNotSame(points);
        Element lineElm = createSegmentedLine(graphId, 
                                          color,
                                          points,
                                          pointToElm,
                                          false);
        // anchor for input
        if (edge.getInputPoints() != null) {
            // Get the first line
            String id = "e_" + edge.getID() + "_a_i";
            addAnchorToLineElm(lineElm, 
                               0.0d, 
                               id);
        }
        // anchor for position
        if (points.size() > 2) { // There should be an anchor point
            Point pos = edge.getPosition();
            // Need to find the anchor for the position
            int index = points.indexOf(pos);
            // Get the total length of line segments
            double total = 0.0d;
            double total1 = 0.0d;
            for (int i = 0; i < points.size() - 1; i++) {
                Point p1 = points.get(i);
                Point p2 = points.get(i + 1);
                double dist = Point2D.distance(p1.x, p1.y, p2.x, p2.y);
                total += dist;
                if (i <= index - 1) {
                    total1 += dist;
                }
            }
            // Just another anchor
            String id = "e_" + edge.getID() + "_a_p";
            addAnchorToLineElm(lineElm, 
                               total1 / total,
                               id);
        }
        // anchor for output
        if (edge.getOutputPoints() != null) {
            // Get the last line
            String id = "e_" + edge.getID() + "_a_o";
            addAnchorToLineElm(lineElm, 
                               1.0d, 
                               id);
        }
        return lineElm;
    }
    
    Element createLineElmForBranch(List<Point> points,
                                           Point anchor,
                                           String anchorId,
                                           boolean isOutput,
                                           String graphId,
                                           Color color,
                                           Map<Point, Element> pointToElm) {
        // The lines will be converted by line element one by one 
        List<Point> pointsCopy = new ArrayList<Point>(points);
        pointsCopy.add(anchor);
        Element lineElm = createSegmentedLine(graphId,
                                              color,
                                              pointsCopy,
                                              pointToElm, 
                                              isOutput);
        Element anchorPoint = pointToElm.get(anchor);
//        int index1 = anchorId.indexOf("_");
//        int index2 = anchorId.indexOf("_", index1 + 1);
//        int number = new Integer(anchorId.substring(index1 + 1, index2));
//        if (anchorId.endsWith("_o")) {
            anchorPoint.setAttribute(GPMLConstants.GraphRef, anchorId);
//            System.out.println(anchorId);
//        }
        return lineElm;
    }

//    private void createLineElements(String graphId, 
//                                   Color color,
//                                   List<Element> rtn, 
//                                   List<Point> points,
//                                   boolean needArrow,
//                                   Map<Point, Element> pointToElm) {
//        Element preLineElm = null;
//        for (int i = 0; i < points.size() - 1; i++) {
//            Point first = points.get(i);
//            Point second = points.get(i + 1);
//            // Create a line element between these two points
//            Element lineElm = new Element(GPMLConstants.Line, GPML_NS);
//            lineElm.setAttribute(GPMLConstants.GraphId, 
//                                 graphId + "_" + i);  
//            Element graphicElm = new Element(GPMLConstants.Graphics, GPML_NS);
//            lineElm.addContent(graphicElm);
//            graphicElm.setAttribute(GPMLConstants.LineStyle,
//                                    GPMLConstants.Solid);
//            graphicElm.setAttribute(GPMLConstants.Color, 
//                                    GKApplicationUtilities.getHexForColor(color));
//            Element firstElm = convertPointToElement(first,
//                                                     graphId + "_" + i + "_1");
//            Element secondElm = convertPointToElement(second,
//                                                      graphId + "_" + i + "_2");
//            graphicElm.addContent(firstElm);
//            graphicElm.addContent(secondElm);
//            pointToElm.put(first, firstElm);
//            pointToElm.put(second, secondElm);
//            rtn.add(lineElm);
//            // Need to link to previous element
//            if (preLineElm != null) {
//                // Linked to this element for the firstElm
//                // Add a anchor point first in the preSecondElm
//                String id = preLineElm.getAttributeValue(GPMLConstants.GraphId) + "_a";
//                addAnchorToLineElm(preLineElm, 1.0d, id);
////                firstElm.setAttribute(GPMLConstants.GraphRef, 
////                                      id);
//            }
//            preLineElm = lineElm;
//            if (needArrow && i == 0)
//                firstElm.setAttribute(GPMLConstants.ArrowHead, "Arrow");
//        }
//    }

    private void addAnchorToLineElm(Element lineElm,
                                    Double pos,
                                    String id) {
        // Linked to this element for the firstElm
        // Add a anchor point first in the preSecondElm
        Element anchorElm = new Element(GPMLConstants.Anchor, GPML_NS);
        anchorElm.setAttribute(GPMLConstants.Position, pos + "");
        anchorElm.setAttribute(GPMLConstants.GraphId, 
                               id);
        // Anchor should be attached to Graphics elements
        lineElm.getChild(GPMLConstants.Graphics, GPML_NS).addContent(anchorElm);
    }
    private Element convertPointToElement(Point p, String graphId) {
        Element pointElm = new Element(GPMLConstants.Point, GPML_NS);
        pointElm.setAttribute(GPMLConstants.x, 
                              p.getX() * COORDINATE_SCALE_TO_GPML + "");
        pointElm.setAttribute(GPMLConstants.y,
                              p.getY() * COORDINATE_SCALE_TO_GPML + "");
        pointElm.setAttribute(GPMLConstants.GraphId, 
                              graphId);
        return pointElm;
    }
    
    private Element convertCompartment(RenderableCompartment compartment) {
        Element shape = new Element(GPMLConstants.Shape, GPML_NS);
        
        shape.setAttribute(GPMLConstants.GraphId, 
                           "comp_" + compartment.getID());
        shape.setAttribute(GPMLConstants.GroupRef,
                           "group_comp_" + compartment.getID());
        addGraphicsElm(compartment, shape);
        Element graphics = getGraphicsChild(shape);
        graphics.setAttribute(GPMLConstants.ShapeType, 
                GPMLConstants.Rectangle);
        graphics.setAttribute(GPMLConstants.LineThickness, "2.0");
        return shape;
    }
    
    private Element createNode(Node node) throws Exception {
        Element nodeElm = new Element(GPMLConstants.DataNode,
                                      GPML_NS);
        // Convert attributes
        // Integer cannot be used for xsd:ID. Adding "n" for "Node" to create a valid id
        String id = "n" + node.getID() + "";
        nodeElm.setAttribute(GPMLConstants.GraphId, id);
        nodeElm.setAttribute(GPMLConstants.TextLabel,
                             node.getDisplayName());
        String type = null;
        if (node instanceof RenderableProtein)
            type = GPMLConstants.Protein;
        else if (node instanceof RenderableRNA)
            type = GPMLConstants.Rna;
        else if (node instanceof RenderableChemical)
            type = GPMLConstants.Metabolite;
        else if (node instanceof RenderableComplex)
            type = GPMLConstants.Complex;
        else if (node instanceof ProcessNode)
            type = GPMLConstants.Pathway;
        else
            type = GPMLConstants.Unknown;
        nodeElm.setAttribute(GPMLConstants.Type,
                             type);
        addGraphicsElm(node, nodeElm);
        // Xref is required except for labels
        if (type != GPMLConstants.Label) {
            Element xrefElm = createXrefForNode(node);
            nodeElm.addContent(xrefElm);
        }
        return nodeElm;
    }
    
    private Element createLabelForNote(Note note) {
        if (note.isPrivate()) // Private note should not be converted
            return null;
        Element label = new Element(GPMLConstants.Label, GPML_NS);
        // Convert attributes
        // Integer cannot be used for xsd:ID. Adding "n" for "Node" to create a valid id
        String id = "n" + note.getID() + "";
        label.setAttribute(GPMLConstants.GraphId, id);
        label.setAttribute(GPMLConstants.TextLabel,
                             note.getDisplayName());
        addGraphicsElm(note, label);
        return label;
    }

    private void addGraphicsElm(Node node, Element nodeElm) {
        // Graphic stuff
        Element graphicElm = new Element(GPMLConstants.Graphics,
                                         GPML_NS);
        nodeElm.addContent(graphicElm);
        // Create color element
        Color fgColor = node.getForegroundColor();
        if (fgColor == null)
            fgColor = Color.black;
        Rectangle bounds = node.getBounds();
        setGraphicsElmAttributes(graphicElm, fgColor, bounds);
    }

    private void setGraphicsElmAttributes(Element graphicElm, Color fgColor,
                                          Rectangle bounds) {
        graphicElm.setAttribute(GPMLConstants.Color, 
                                GKApplicationUtilities.getHexForColor(fgColor));
        graphicElm.setAttribute(GPMLConstants.CenterX, 
                                COORDINATE_SCALE_TO_GPML * bounds.getCenterX() + "");
        graphicElm.setAttribute(GPMLConstants.CenterY, 
                                COORDINATE_SCALE_TO_GPML * bounds.getCenterY() + "");
        graphicElm.setAttribute(GPMLConstants.Width, 
                                COORDINATE_SCALE_TO_GPML * bounds.getWidth() + "");
        graphicElm.setAttribute(GPMLConstants.Height, 
                                COORDINATE_SCALE_TO_GPML * bounds.getHeight() + "");
        graphicElm.setAttribute(GPMLConstants.Valign, "Middle");
    }
    
    private Element createXrefForNode(Node node) throws Exception {
        Element xrefElm = new Element(GPMLConstants.Xref, GPML_NS);
        // Try to get ReferenceEntity
        GKInstance instance = node.getInstance();
        if (instance == null) {
        	Long id = node.getReactomeId();
        	if (id != null) {
                instance = dbAdaptor.fetchInstance(id);
                node.setInstance(instance);
        	}
        }
        GKInstance referenceEntity = null;
        if (instance != null && instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.referenceEntity)) 
            referenceEntity = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.referenceEntity);
        if (referenceEntity == null) {
            // Use Reactome as default if no reference entity can be found
            xrefElm.setAttribute(GPMLConstants.Database,
                                 "Reactome");
            String id = instance == null ? node.getReactomeId().toString() : getReactomeId(instance);
            if (id == null) {
            	id = instance.getDBID().toString();
            }
            xrefElm.setAttribute(GPMLConstants.ID, id);
        }
        else {
            GKInstance db = (GKInstance) referenceEntity.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
            xrefElm.setAttribute(GPMLConstants.Database, 
                                 db.getDisplayName());
            String identifier = getIdentifierFromReferenceEntity(referenceEntity);
            xrefElm.setAttribute(GPMLConstants.ID,
                                 identifier);
        }
        return xrefElm;
    }
    
    private Element createPathwayElm(GKInstance pathway) throws Exception {
        // Root element is pathway
        Element pathwayElm = new Element(GPMLConstants.Pathway, GPML_NS);
        pathwayElm.addNamespaceDeclaration(GPML_NS);
        
        pathwayElm.setAttribute(GPMLConstants.Name,
                                pathway.getDisplayName());
        // Get species: a pathway may use several species. Want to list them all.
        //List values = pathway.getAttributeValuesList(ReactomeJavaConstants.species);
        // Wikipathways can support only one species now. 
        GKInstance species = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
        //String species = joinDisplayNames(values);
        if (species != null) {
            pathwayElm.setAttribute(GPMLConstants.Organism,
                                    species.getDisplayName());
        }
        pathwayElm.setAttribute(GPMLConstants.Data_Source,
                                DATA_SOURCE);
        pathwayElm.setAttribute(GPMLConstants.Version,
                                VERSION);
        // Handle authors and other related stuff.
        GKInstance authored = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.authored);
        List values = null;
        if (authored != null) {
            // Get authors
            values = authored.getAttributeValuesList(ReactomeJavaConstants.author);
            String authors = joinDisplayNames(values);
            if (authors != null)
                pathwayElm.setAttribute(GPMLConstants.Author,
                                        authors);
        }
        // Edited will be converted to Maintainer
        GKInstance edited = (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.edited);
        if (edited != null) {
            values = edited.getAttributeValuesList(ReactomeJavaConstants.author);
            String maintainer = joinDisplayNames(values);
            if (maintainer != null)
                pathwayElm.setAttribute(GPMLConstants.Maintainer,
                                        maintainer);
            // Get email from editors
            if (values != null && values.size() > 0) {
                StringBuilder builder = new StringBuilder();
                for (Iterator it = values.iterator(); it.hasNext();) {
                    GKInstance person = (GKInstance) it.next();
                    String email = (String) person.getAttributeValue(ReactomeJavaConstants.eMailAddress);
                    if (email == null || email.length() == 0)
                        continue;
                    if (builder.length() > 0)
                        builder.append(", ");
                    builder.append(email);
                }
                if (builder.length() > 0)
                    pathwayElm.setAttribute(GPMLConstants.Email,
                                            builder.toString());
            }
        }
        addSemanticContent(pathwayElm, pathway, false);
        return pathwayElm;
    }
    
    private void addSemanticContent(Element elm,
                                    GKInstance inst,
                                    boolean needAtTop) throws Exception {
        List<Element> children = new ArrayList<Element>();
        // Add summation as text
        if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.summation)) {
            List<GKInstance> summations = inst.getAttributeValuesList(ReactomeJavaConstants.summation);
            if (summations != null && summations.size() > 0) {
                for (GKInstance summation : summations) {
                    String text = (String) summation.getAttributeValue(ReactomeJavaConstants.text);
                    if (text != null && text.length() > 0) {
                        Element commentElm = new Element(GPMLConstants.Comment, GPML_NS);
                        //commentElm.setAttribute(GPMLConstants.Source,
                        //                        "Reactome summation");
                        // Per suggestion from Kastina in wikipathways, change the source name
                        // from "Reactome summation" to "WikiPathways-description" so that these 
                        // comments can be listed correctly.
                        commentElm.setAttribute(GPMLConstants.Source,
                                                "WikiPathways-description");
                        commentElm.setText(text);
                        children.add(commentElm);
                    }
                }
            }
        }
        // Add literature references
        if (inst.getSchemClass().isValidAttribute(ReactomeJavaConstants.literatureReference)) {
            List<GKInstance> litRefs = inst.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
            if (litRefs != null && litRefs.size() > 0) {
                for (GKInstance litRef : litRefs) {
                    Element biopaxElm = idHandler.getLitRefElement(litRef);
                    if (biopaxElm == null) {
                        // Need to create a biopax element for this literature
                        biopaxElm = convertToBPPublicationXRef(litRef);
                        addToBiopaxElm(biopaxElm);
                        idHandler.addLitRefElement(litRef, biopaxElm);
                    }
                    //elm.setAttribute(GPMLConstants.BiopaxRef, 
                    //                 idHandler.getIdForLitRefInst(litRef));
                    // Need to create an element for biopaxref. Attribute cannot work for pathvisio
                    Element biopaxRefElm = new Element(GPMLConstants.BiopaxRef, GPML_NS);
                    biopaxRefElm.setText(idHandler.getIdForLitRefInst(litRef));
                    children.add(biopaxRefElm);
                }
            }
        }
        if (children.size() > 0){
            if (needAtTop)
                elm.addContent(0, children);
            else
                elm.addContent(children);
        }
        //Put DB_ID for future reference when converting back to ATXML
        String graphId = elm.getAttributeValue(GPMLConstants.GraphId);
        if (graphId != null) {
            elm.setAttribute(GPMLConstants.GraphId, graphId + "_REACTOME_" + inst.getDBID());
        }
    }
    
    private void addToBiopaxElm(Element childElm) {
        if (biopaxElm == null) {
            biopaxElm = new Element(GPMLConstants.Biopax, GPML_NS);
        }
        biopaxElm.addContent(childElm);
    }
    
    private Element convertToBPPublicationXRef(GKInstance litRef) throws Exception {
        ReactomeToBioPAXPublicationConverter helper = new ReactomeToBioPAXPublicationConverter();
        // A bug for GPML: level 3 namespace is used. However, attribute names are still based on level2.
        helper.setIsForLevel2(true);
        Namespace rdfNs = Namespace.getNamespace("rdf",
                                                 BioPAXJavaConstants.RDF_NS);
        String id = idHandler.getIdForLitRefInst(litRef);
        Element pubXrefIndividual = helper.convertPublication(litRef, 
                                                              null,
                                                              id,
                                                              BP_NS,
                                                              rdfNs,
                                                              null);
        // There is an error in GPML regarding ID: "id" used by GPML should be "ID".
        // Here is a workaround
        pubXrefIndividual.setAttribute("id", id, rdfNs);
        return pubXrefIndividual;
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
    
    String getHexString(Color c) {
        return Integer.toHexString(c.getRGB() & 0x00ffffff);
    }
    
    private void addCosmetics(Element dataNode, GKInstance inst) {
        String type = dataNode.getAttributeValue(GPMLConstants.Type);
        Element graphics = getGraphicsChild(dataNode);
        //System.out.println("::"+type+"::"+dataNode.getAttributeValue("TextLabel"));
        graphics.setAttribute(GPMLConstants.FillColor, getHexString(DefaultRenderConstants.DEFAULT_BACKGROUND));
        if (type.equals(GPMLConstants.Complex)) {
            graphics.setAttribute(GPMLConstants.ShapeType, GPMLConstants.RoundedRectangle);
            graphics.setAttribute(GPMLConstants.FillColor, getHexString(new Color(204, 255, 255)));
            graphics.setAttribute(GPMLConstants.LineThickness, "2");
        }
        else if (type.equals(GPMLConstants.Protein)) {
            graphics.setAttribute(GPMLConstants.ShapeType, GPMLConstants.RoundedRectangle);
        }
        else if (type.equals(GPMLConstants.Metabolite)) {
            graphics.setAttribute(GPMLConstants.ShapeType, GPMLConstants.Oval);
        }
    }

    private String wrapLines(String s, float wrappingWidth, FontRenderContext frc, String lineBreak) {
        AttributedCharacterIterator aci = new AttributedString(s).getIterator();
        int end = aci.getEndIndex();
        LineBreakMeasurer measurer = new LineBreakMeasurer(aci, frc);
        StringBuffer output = new StringBuffer();
        int position = 0;
        int lastPosition = 0;
        do {
            measurer.nextLayout(wrappingWidth);
            position = measurer.getPosition();
            output.append(s, lastPosition, position);
            output.append(lineBreak);
            lastPosition = position;
        } while (position < end);
        return output.toString();
    }
    
    private void wrapLabels(Element dataNode, FontRenderContext frc) {
        String label = dataNode.getAttributeValue("TextLabel");
        Float width = new Float(getGraphicsChild(dataNode).getAttributeValue("Width"));
        //System.out.println(label);
        // XML serialisation automatically converts "\n" to "&#xA;"
        String wrappedLabel = wrapLines(label, width, frc, "\n");
        dataNode.setAttribute("TextLabel", wrappedLabel);
    }
    
    @Test
    public void testConvert() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "gk_central_100509",
                                            "",
                                            "",
                                            3306);
        setMySQLAdaptor(dba);
        // Get the list of files from Peter's list
        String fileName = "../../gkteam/peter/chicken_flat_pathways_v2.txt";
        List<GKInstance> pathways = loadPathways(fileName, dba);
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        for (GKInstance pathway : pathways) {
            System.out.println("converting " + pathway.getDisplayName() + "...");
            //if (pathway.getDisplayName().equals("DNA repair")) {
                Document doc = convertPathway(pathway);
                fileName = "tmp/" + pathway.getDisplayName() + ".gpml";
                outputter.output(doc, new FileOutputStream(fileName));
                // Test loading using JDOM and validation
                SAXBuilder builder = new SAXBuilder(true);
                builder.setFeature("http://apache.org/xml/features/validation/schema", true);
                builder.setProperty("http://apache.org/xml/properties/schema/external-schemaLocation",
                                    "http://genmapp.org/GPML/2008a http://svn.bigcat.unimaas.nl/pathvisio/trunk/GPML.xsd");
                doc = builder.build(new File(fileName));
            //}
        }
        System.out.println("Total pathways: " + pathways.size());
    }
    
    @Test
    public void testSingleConvert() throws Exception {
//        MySQLAdaptor dba = new MySQLAdaptor("localhost",
//                                            "gk_current_ver42",
//                                            "",
//                                            "");
    	MySQLAdaptor dba = new MySQLAdaptor("localhost",
              "reactome_convert",
              "anwesha",
              "RConver!a");
    	
        setMySQLAdaptor(dba);
        // Use human Cell Cycle Check Points
//        Long dbId = 69620L;
        // Human Apoptosis
//        Long dbId = 109581L;
        // Fatty acis, triacylglycerol and ketone metabolism
        //Long dbId = 535734L;
        // Signaling by NGF: cannot be opened
//        Long dbId = 166520L;
        // Nucleotide excision repair
//        Long dbId = 73885L;
        // Aquaporin-mediated transprot to test notes used in reactome
        Long dbId = 445717L;
        GKInstance pathway = dba.fetchInstance(dbId);
        Document doc = convertPathway(pathway);
        String fileName = "tmp/" + pathway.getDisplayName() + ".gpml";
        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        outputter.output(doc, new FileOutputStream(fileName));
    }
    
 }
