//// PathVisio,
//// a tool for data visualization and analysis using Biological Pathways
//// Copyright 2006-2009 BiGCaT Bioinformatics
////
//// Licensed under the Apache License, Version 2.0 (the "License");
//// you may not use this file except in compliance with the License.
//// You may obtain a copy of the License at
////
//// http://www.apache.org/licenses/LICENSE-2.0
////
//// Unless required by applicable law or agreed to in writing, software
//// distributed under the License is distributed on an "AS IS" BASIS,
//// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//// See the License for the specific language governing permissions and
//// limitations under the License.
////
//package org.gk.gpml;
//
//import java.util.ArrayList;
//import java.util.Collection;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.LinkedList;
//import java.util.List;
//import java.util.Map;
//import java.util.Set;
//
//import org.pathvisio.debug.Logger;
//import org.pathvisio.model.LineStyle;
//import org.pathvisio.model.LineType;
//import org.pathvisio.model.ObjectType;
//import org.pathvisio.model.Pathway;
//import org.pathvisio.model.PathwayElement;
//import org.pathvisio.model.GraphLink.GraphRefContainer;
//import org.pathvisio.model.PathwayElement.MAnchor;
//import org.pathvisio.model.PathwayElement.MPoint;
//
///**
// * This class is modified from org.pathvisio.util.Relation.
// * 
// * Class to parse a relation between GPML objects, e.g. a biochemical reaction.
// * A relation can be created from a line that connects two objects
// * of type datanode, shape or label.
// * The following fields will be created:
// * - LEFT: an element that acts on the left side of an interaction (i.e. inputs)
// * - RIGHT: an element that acts on the right side of an interaction (i.e. outputs)
// * - MEDIATOR: an element that acts as mediator of an interaction (i.e. activators, inhibitors)
// * 
// * Once a line is fed into this class, all line segments connecting to it will be evaluated; therefore
// * one instance of ReactomeRelation corresponds to one Reactome reaction (RenderableReaction).
// * 
// * See evaluateMPoint() method for the heuristics employed to differentiate between lefts, rights,
// * and mediators.
// * 
// *
// * Additionally, if the element to be added is a group, all nested elements will
// * be added recursively.
// *
// * So in the example, the following fields will be created:
// * A: LEFT
// * D: LEFT
// * F: MEDIATOR
// * C: MEDIATOR
// * C1:MEDIATOR
// * C2:MEDIATOR
// * E: RIGHT
// * B: RIGHT
// *
// * @author thomas
// */
//public class ReactomeRelation {
//    private Set<PathwayElement> lefts = new HashSet<PathwayElement>();
//    private Set<PathwayElement> rights = new HashSet<PathwayElement>();
//    private Set<PathwayElement> mediators = new HashSet<PathwayElement>();
//    private Set<PathwayElement> traversedLines = new HashSet<PathwayElement>();
//    
//    private Map<PathwayElement, List<PathwayElement>> lines = new HashMap<PathwayElement, List<PathwayElement>>();
//    
//    public Map<PathwayElement, List<PathwayElement>> getLines() {
//        return lines;
//    }
//    
//    /* code for debugging purpose
//    private class CustomLinkedList extends LinkedList<PathwayElement> {
//        public void addLast(PathwayElement e) {
//            for (int i = 0; i < this.size(); i++) System.out.print(" ");
//            super.addLast(e);
//            System.out.println("add "+e.getGraphId());
//        }
//        public PathwayElement removeLast() {
//            PathwayElement e = super.removeLast();
//            for (int i = 0; i < this.size(); i++) System.out.print(" ");
//            System.out.println("rem "+e.getGraphId());
//            return e;
//        }
//    }
//    private LinkedList<PathwayElement> lineStack = new CustomLinkedList();
//    */
//    
//    private LinkedList<PathwayElement> lineStack = new LinkedList<PathwayElement>();
//    
//    public ReactomeRelation() {
//        
//    }
//    
//    /**
//     * Parse a relation.
//     * @param relationLine The line that defines the relation.
//     */
//    public ReactomeRelation(PathwayElement relationLine) {
//        buildRelations(relationLine);
//    }
//    
//    public void buildRelations(PathwayElement relationLine) {
//        if(relationLine.getObjectType() != ObjectType.LINE) {
//            throw new IllegalArgumentException("Object type should be line!");
//        }
//        Pathway pathway = relationLine.getParent();
//        if(pathway == null) {
//            throw new IllegalArgumentException("Object has no parent pathway");
//        }
//        if(traversedLines.contains(relationLine)) {
//            // line has been traversed before so do not process again
//            //System.out.println("x "+relationLine.getGraphId());
//            return;
//        }
//        traversedLines.add(relationLine);
//        //System.out.println(relationLine.getGraphId() + "::" + relationLine.getStartGraphRef() + ":"+pathway.getReferringObjects(relationLine.getStartGraphRef())+":"+relationLine.getEndGraphRef() + " ");
//        
//        Set<GraphRefContainer> grc = new HashSet<GraphRefContainer>();
//        
//        //Find all connecting lines (via anchors)
//        for(MAnchor ma : relationLine.getMAnchors()) {
//            //evaluateGRCList(pathway, ma.getReferences());
//            grc.addAll(ma.getReferences());
//        }
//        grc.add(relationLine.getMStart());
//        grc.add(relationLine.getMEnd());
//        evaluateGRCList(pathway, grc, relationLine);
//    }
//    
//    private void evaluateGRCList(Pathway pathway, Collection<GraphRefContainer> grcCollection, PathwayElement line) {
//        for(GraphRefContainer grc : grcCollection) {
//            if(grc instanceof MPoint) {
//                MPoint mp = (MPoint)grc;
//                //System.out.println("anchor "+ma.getGraphId()+" " + ma.getPosition()+" mp "+mp.getX()+" "+mp.getY()+" "+mp.getGraphId());
//                evaluateMPoint(pathway, mp);
//            } else {
//                Logger.log.warn("unsupported GraphRefContainer: " + grc);
//            }
//        }
//    }
//    
//    private void evaluateMPoint(Pathway pathway, MPoint mp) {
//        PathwayElement line = mp.getParent();
//        lineStack.addLast(line);
//        
//        if(line.getMStart().isLinked()) {
//            if (line.getStartLineType() == LineType.ARROW) {
//                addRight(pathway.getElementById(line.getMStart().getGraphRef()));
//            }
//            else if (line.getLineStyle() == LineStyle.DASHED) {
//                addMediator(pathway.getElementById(line.getMStart().getGraphRef()));
//            }
//            else {
//                addLeft(pathway.getElementById(line.getMStart().getGraphRef()));
//            }
//        }
//        
//        if(line.getMEnd().isLinked()) {
//            if (line.getEndLineType() == LineType.ARROW) {
//                addRight(pathway.getElementById(line.getMEnd().getGraphRef()));
//            }
//            else if (line.getLineStyle() == LineStyle.DASHED) {
//                addMediator(pathway.getElementById(line.getMEnd().getGraphRef()));
//            }
//            else {
//                addLeft(pathway.getElementById(line.getMEnd().getGraphRef()));
//            }
//        }
//        
//        evaluatePrePost(pathway, pathway.getReferringObjects(line.getStartGraphRef()));
//        evaluatePrePost(pathway, pathway.getReferringObjects(line.getEndGraphRef()));
//        lineStack.removeLast();
//    }
//    
//    private void evaluatePrePost(Pathway pathway, Collection<GraphRefContainer> grcCollection) {
//        for(GraphRefContainer grc : grcCollection) {
//            if(grc instanceof MPoint) {
//                MPoint mp = (MPoint)grc;
//                //System.out.println("anchor "+ma.getGraphId()+" " + ma.getPosition()+" mp "+mp.getX()+" "+mp.getY()+" "+mp.getGraphId());
//                // build relation for a line that is referred by this point
//                //System.out.print(mp.getGraphId()+ "***"+mp.getGraphRef()+"---"+pathway.getElementById(mp.getGraphRef())+" ");
//                Object anchorCandidate = pathway.getGraphIdContainer(mp.getGraphRef());
//                //System.out.println(anchorCandidate);
//                if (anchorCandidate instanceof MAnchor) {
//                    MAnchor anchor = (MAnchor) anchorCandidate;
//                    //System.out.println(anchor);
//                    buildRelations(anchor.getParent());
//                }
//                
//                
//            } else {
//                Logger.log.warn("unsupported GraphRefContainer: " + grc);
//            }
//        }
//    }
//    
//    
//    void addLeft(PathwayElement pwe) {
//        addElement(pwe, lefts);
//    }
//
//    void addRight(PathwayElement pwe) {
//        addElement(pwe, rights);
//    }
//
//    void addMediator(PathwayElement pwe) {
//        addElement(pwe, mediators);
//    }
//
//    void addElement(PathwayElement pwe, Set<PathwayElement> set) {
//        if(pwe != null) {
//            //If it's a group, add all subelements
//            if(pwe.getObjectType() == ObjectType.GROUP) {
//                for(PathwayElement ge : pwe.getParent().getGroupElements(pwe.getGroupId())) {
//                    addElement(ge, set);
//                }
//            }
//            //System.out.print("*"+pwe.getGraphId()+"*");
//            // Because we want to take the shortest path, ignore subsequent findings of the same nodes.
//            // This should not affect output at all because there is no cycles in reaction graphs (i.e.
//            // all reaction graphs are bidirected acyclic graph).
//            if (!set.contains(pwe)) {
//                set.add(pwe);
//                List<PathwayElement> branchLines = new ArrayList<PathwayElement>();
//                for (PathwayElement p : lineStack) {
//                    if (!backboneLines.contains(p)) {
//                        branchLines.add(p);
//                    }
//                }
//                lines.put(pwe, branchLines);
//                //System.out.println("- put "+pwe.getGraphId());
//            }
//            /*
//            else {
//                System.out.println("- dup "+pwe.getGraphId());
//            }
//            */
//        }
//    }
//
//    public Set<PathwayElement> getLefts() { return lefts; }
//    public Set<PathwayElement> getRights() { return rights; }
//    public Set<PathwayElement> getMediators() { return mediators; }
//    
//    private PathwayElement backboneGroup;
//    private List<PathwayElement> backboneLines = new ArrayList<PathwayElement>();
//    
//    public void setBackboneGroup(PathwayElement backboneGroup) {
//        this.backboneGroup = backboneGroup;
//        Pathway pwy = backboneGroup.getParent();
//        // register all points in the backbone so that it can be filtered off when getting branch points
//        backboneLines = new ArrayList<PathwayElement>();
//        for (PathwayElement pe : pwy.getGroupElements(backboneGroup.getGroupId())) {
//            if (pe.getObjectType() == ObjectType.LINE) {
//                backboneLines.add(pe);
//            }
//            
//        }
//    }
//}
