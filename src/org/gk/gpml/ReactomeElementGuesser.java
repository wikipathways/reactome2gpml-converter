//package org.gk.gpml;
//
//import java.awt.Point;
//import java.awt.Rectangle;
//import java.awt.geom.Rectangle2D;
//import java.io.ByteArrayInputStream;
//import java.io.ByteArrayOutputStream;
//import java.io.IOException;
//import java.io.InputStream;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//import java.util.regex.Matcher;
//import java.util.regex.Pattern;
//
//import org.gk.render.HyperEdge;
//import org.gk.render.RenderableEntity;
//import org.gk.render.RenderablePathway;
//import org.gk.render.RenderableReaction;
//import org.jdom.Document;
//import org.jdom.Element;
//import org.jdom.output.XMLOutputter;
//import org.pathvisio.model.GpmlFormat;
//import org.pathvisio.model.ObjectType;
//import org.pathvisio.model.Pathway;
//import org.pathvisio.model.PathwayElement;
//import org.pathvisio.model.PathwayElement.MPoint;
//
//
///**
// * An ElementGuesser that converts elements conforming to Reactome GPML convention (i.e. GPML files converted from
// * org.gk.gpml.ReactomeToGPMLConverter). These elements have "_REACTOME_" inside their graphId or groupId, followed by
// * its corresponding Reactome database ID.
// * @author leon
// *
// */
//public class ReactomeElementGuesser implements ElementGuesser {
//    // use Document.hashCode() for key
//    private static Map<Integer, Pathway> pathwayCache = new HashMap<Integer, Pathway>();
//    
//    public List<String> getElementNamesOfInterest() {
//        return Arrays.asList(GPMLConstants.Group);
//    }
//    
//    public Map<ElementToRenderableConverter, Double> guess(Element e, GPMLToReactomeConverter converter) {
//        String name = e.getName();
//        HashMap<ElementToRenderableConverter, Double> ret = new HashMap<ElementToRenderableConverter, Double>();
//        if (name.equals(GPMLConstants.Group)) {
//            //A reactome reaction
//            double p = 0.0;
//            if (e.getAttributeValue(GPMLConstants.GroupId).contains("_REACTOME_")) {
//                p += 0.5;
//            }
//            if (e.getChild(GPMLConstants.Comment, e.getNamespace()).getAttributeValue(GPMLConstants.Source).equals("Reactome summation")) {
//                p += 0.4;
//            }
//            if (e.getAttributeValue(GPMLConstants.Style).equals(GPMLConstants.Group) && (e.getAttributeValue(GPMLConstants.TextLabel) != null)) {
//                p += 0.1;
//            }
//            ret.put(new GroupToReactionConverter(converter), p);
//            
//        }
//        return ret;
//    }
//    
//    private class GroupToReactionConverter implements ElementToRenderableConverter {
//        GPMLToReactomeConverter converter;
//        
//        public GroupToReactionConverter(GPMLToReactomeConverter converter) {
//            this.converter = converter;
//        }
//        
//        public void convert(Element e, RenderablePathway diagram)
//                throws ConverterException {
//            // note: carefully read the import statements. "Pathway" and "Converter" are terms commonly used in both pathvisio and reactome, so classes may belong to either one
//            Document doc = e.getDocument();
//            Pathway pwy = pathwayCache.get(doc.hashCode());
//            if (pwy == null) {
//                try {
//                    // this Pathway is of pathvisio
//                    Pathway newPwy = new Pathway();
//                    XMLOutputter outputter = new XMLOutputter();
//                    ByteArrayOutputStream oStream = new ByteArrayOutputStream();
//                    outputter.output(doc, oStream);
//                    InputStream inStream = new ByteArrayInputStream(oStream.toByteArray());
//                    GpmlFormat.readFromXml(newPwy, inStream, false);
//                    pathwayCache.put(doc.hashCode(), newPwy);
//                    pwy = newPwy;
//                }
//                catch (IOException ex) {
//                    ex.printStackTrace();
//                    return;
//                }
//                catch (org.pathvisio.model.ConverterException ex) {
//                    ex.printStackTrace();
//                    return;
//                }
//            }
//            
//            
//            String groupId = e.getAttributeValue(GPMLConstants.GroupId);
//            // pv stands for pathvisio
//            PathwayElement pvGroup = pwy.getGroupById(groupId);
//           
//            // get the inputs, outputs, and catalysts for each line in the group, using org.pathvisio.util.Relation
//            
//            ReactomeRelation relation = new ReactomeRelation();
//            relation.setBackboneGroup(pvGroup);
//            
//            for (PathwayElement pvElement : pwy.getGroupElements(groupId)) {
//                if (pvElement.getObjectType() == ObjectType.LINE) {
//                    
//                    relation.buildRelations(pvElement);
//                }
//            }
//            /*
//            System.out.println("Reaction "+pvGroup.getTextLabel() + " has: ");
//            testPrint(relation, relation.getLefts(), "in :");
//            testPrint(relation, relation.getRights(), "out:");
//            testPrint(relation, relation.getMediators(), "cat:");
//            */
//            
//            Set<PathwayElement> nodes = new HashSet<PathwayElement>(relation.getLefts());
//            nodes.addAll(relation.getRights());
//            nodes.addAll(relation.getMediators());
//            for (PathwayElement pe : nodes) {
//                convertToRenderable(pe, diagram);
//            }
//            
//            HyperEdge edge = new RenderableReaction();
//            
//            try {
//                List<Point> pointList = pvLineToPoints(pwy.getGroupElements(groupId));
//                edge.setPosition(pointList.get(0));
//                edge.setBackbonePoints(pointList);
//                diagram.addComponent(edge);
//                edge.setDisplayName(e.getAttributeValue("TextLabel"));
//                
//            }
//            catch (Exception ex) {
//                ex.printStackTrace();
//            }
//            edge.setInputPoints(getBranchPoints(relation.getLefts(), relation.getLines()));
//            edge.setOutputPoints(getBranchPoints(relation.getRights(), relation.getLines()));
//            edge.setActivatorPoints(getBranchPoints(relation.getMediators(), relation.getLines()));
//            
//        }
//        
//        private RenderableEntity convertToRenderable(PathwayElement pe, RenderablePathway diagram) {
//            RenderableEntity entity = new RenderableEntity();
//            entity.setBounds(toRectangle(pe.getMBounds()));
//            entity.setDisplayName(pe.getTextLabel());
//            if (diagram != null) {
//                diagram.addComponent(entity);
//            }
//            return entity;
//        }
//        
//        private Rectangle toRectangle(Rectangle2D r) {
//            // TODO Auto-generated method stub
//            return new Rectangle((int)r.getX(), (int)r.getY(), (int)r.getWidth(), (int)r.getHeight());
//        }
//
//        private List<Point> pvLineToPoints(Collection<PathwayElement> lines) {
//            List<Point> out = new ArrayList<Point>();
//            //sort the lines
//            //List<PathwayElement> sortedLines = new ArrayList<PathwayElement>(lines);
//            
//            // Map<current line, next line>
//            Map<PathwayElement, PathwayElement> nextLineMatrix = new HashMap<PathwayElement, PathwayElement>();
//            Set<PathwayElement> firstLineCandidates = new HashSet<PathwayElement>(lines);
//            for (PathwayElement line : lines) {
//                for (PathwayElement testLine : lines) {
//                    if (isSamePoint(line.getMEnd(), testLine.getMStart()) && line != testLine) {
//                        nextLineMatrix.put(line, testLine);
//                        // because testLine already has a line before it, it can no longer be a "first line"
//                        firstLineCandidates.remove(testLine);
//                        break;
//                    }
//                }
//            }
//            if (firstLineCandidates.size() != 1) {
//                throw new IllegalStateException("Lines are not connected or have branches");
//            }
//            
//            // get the first line as the current line
//            PathwayElement currentLine = firstLineCandidates.iterator().next();
//            out.add(new Point((int) currentLine.getMStartX(), (int)currentLine.getMStartY()));
//            do {
//                //System.out.print(currentLine.getGraphId() + " ");
//                out.add(new Point((int) currentLine.getMEndX(), (int)currentLine.getMEndY()));
//            } while ((currentLine = nextLineMatrix.get(currentLine)) != null);
//            //System.out.println();
//            
//            return out;
//        }
//        
//        
//        
//        private boolean isSamePoint(MPoint a, MPoint b) {
//            return isSameDouble(a.getX(), b.getX()) && isSameDouble(a.getY(), b.getY()); 
//        }
//        
//        private boolean isSameDouble(double a, double b) {
//            return Math.abs(a - b) < 0.0000001;
//        }
//        
//        private List<List<Point>> getBranchPoints (Collection<PathwayElement> nodeSet, Map<PathwayElement, List<PathwayElement>> lineMap){
//            List<List<Point>> masterList = new ArrayList<List<Point>>();
//            for (PathwayElement pe : nodeSet) {
//                try {
//                
//                List<PathwayElement> lines = lineMap.get(pe);
//                List<Point> points = pvLineToPoints(lines);
//                masterList.add(points);
//                }
//                catch (IllegalStateException ex) {
//                    System.err.println(ex.getLocalizedMessage());
//                }
//            }
//            return masterList;
//        }
//        
//        private void testPrint(ReactomeRelation relation, Set<PathwayElement> pvs, String label) {
//            for (PathwayElement pve : pvs) {
//                System.out.print(label+pve.getTextLabel().replace("\n", "")
//                        +" [dbid "+getDbIdFromGraphId(pve.getGraphId())+"] ");
//                for (PathwayElement line : relation.getLines().get(pve)) {
//                    System.out.print(line.getGraphId() + " ");
//                }
//                System.out.println();
//            }
//        }
//        
//        private Long getDbIdFromGraphId(String graphId) {
//            try {
//                Pattern pattern = Pattern.compile("^.*_REACTOME_([0-9]+)$");
//                Matcher matcher = pattern.matcher(graphId);
//                matcher.find();
//                return new Long(matcher.group(1));
//            }
//            catch (IllegalStateException ex) {
//                return null;
//            }
//        }
//    }
//    
//}
//
//

