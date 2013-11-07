/*
 * Created on Oct 19, 2009
 *
 */
package org.reactome.convert.common;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.DiagramGKBReader;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.RenderablePathway;
import org.gk.util.FileUtilities;
import org.jdom.Document;

/**
 * This is an abstract class used to group some common variables and methods.
 * @author wgm
 *
 */
public abstract class AbstractConverterFromReactome {

    protected MySQLAdaptor dbAdaptor;
    protected File targetDir;
    protected boolean debug = false;

    public void setTargetDir(File dir) {
        this.targetDir = dir;
    }

    public void setMySQLAdaptor(MySQLAdaptor dba) {
        this.dbAdaptor = dba;
    }
    
    public MySQLAdaptor getMySQLAdaptor() {
        return this.dbAdaptor;
    }
    
    public void setMySQLAdaptor(String dbHost,
                                String dbName,
                                String dbUser,
                                String dbPwd,
                                int dbPort) throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor(dbHost,
                                            dbName,
                                            dbUser,
                                            dbPwd,
                                            dbPort);
        setMySQLAdaptor(dba);
    }
    
    @SuppressWarnings("unchecked")
    protected List<GKInstance> loadFrontPageItems(MySQLAdaptor dba) throws Exception {
        Collection c = dba.fetchInstancesByClass(ReactomeJavaConstants.FrontPage);
        GKInstance frontPageItem = (GKInstance) c.iterator().next();
        List list = frontPageItem.getAttributeValuesList(ReactomeJavaConstants.frontPageItem);
        List<GKInstance> pathways = new ArrayList<GKInstance>(list.size());
        for (Object obj : list)
            pathways.add((GKInstance)obj);
        return pathways;
    }
    
    /**
     * Load pathways from a file. File formata is like the following:
     * header: pathwayName\tPathwayId
     * data: Pathway1\t123345
     * @param fileName
     * @param dba
     * @return
     * @throws Exception
     */
    protected List<GKInstance> loadPathways(String fileName, 
                                            MySQLAdaptor dba) throws Exception {
        // File format as following:
        // PathwayName\tPathwayID
        // Pathway\t12345
        List<GKInstance> pathways = new ArrayList<GKInstance>();
        FileUtilities fu = new FileUtilities();
        fu.setInput(fileName);
        String line = fu.readLine();
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            Long dbId = new Long(tokens[1]);
            GKInstance pathway = dba.fetchInstance(dbId);
            pathways.add(pathway);
        }
        fu.close();
        return pathways;
    }
    
    /**
     * All subclass to this class should implement method to do actual converting. This method is used to convert
     * a list of pathways into a single JDOM document.
     * @param pathways
     * @return
     * @throws Exception
     */
    public abstract Document convertPathways(List<GKInstance> pathways) throws Exception;
    
    /**
     * This method is used to convert a single pathway into a JDOM document.
     * @param pathway
     * @return
     * @throws Exception
     */
    public abstract Document convertPathway(GKInstance pathway) throws Exception;

    /**
     * Query the pathway diagram for the species pathway.
     * @param pathway
     * @return null will be returned if no diagram is available. 
     * @throws Exception
     */
    protected RenderablePathway queryPathwayDiagram(GKInstance pathway) throws Exception {
        // Check pathway diagram
        Collection c = dbAdaptor.fetchInstanceByAttribute(ReactomeJavaConstants.PathwayDiagram,
                                                          ReactomeJavaConstants.representedPathway,
                                                          "=",
                                                          pathway);
        if (c == null || c.size() == 0)
            return null;
        // There should be only one diagram
        GKInstance diagram = (GKInstance) c.iterator().next();
        String xml = (String) diagram.getAttributeValue(ReactomeJavaConstants.storedATXML);
        //System.out.println(xml);
        DiagramGKBReader reader = new DiagramGKBReader();
        RenderablePathway rPathway = reader.openDiagram(xml);
        reader.setDisplayNames(rPathway, 
                               dbAdaptor);
        return rPathway;
    }
    
    /**
     * Get the id used in Reactome for the specified GKInstance. If a stable_id exists, it will be used. Otherwise
     * DB_ID will be used.
     * @param instance
     * @return
     * @throws Exception
     */
    protected String getReactomeId(GKInstance instance) throws Exception {
        if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.stableIdentifier)) {
            GKInstance stableId = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (stableId != null) {
                String identifier = (String) stableId.getAttributeValue(ReactomeJavaConstants.identifier);
                return identifier;
            }
        }
        return instance.getDBID() + "";
    }
    
    /**
     * variantIdentifier will be queried first.
     * @param refEntity
     * @return
     * @throws Exception
     */
    protected String getIdentifierFromReferenceEntity(GKInstance refEntity) throws Exception {
       if (refEntity.getSchemClass().isValidAttribute(ReactomeJavaConstants.variantIdentifier)) { 
           String variantIdentifier = (String) refEntity.getAttributeValue(ReactomeJavaConstants.variantIdentifier);
           if (variantIdentifier != null)
               return variantIdentifier;
       }
       String identifier = (String) refEntity.getAttributeValue(ReactomeJavaConstants.identifier);
       return identifier;
    }

}
