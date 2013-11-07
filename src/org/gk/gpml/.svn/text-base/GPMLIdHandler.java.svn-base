/*
 * Created on Oct 27, 2009
 *
 */
package org.gk.gpml;

import java.util.HashMap;
import java.util.Map;

import org.gk.model.GKInstance;
import org.jdom.Element;

/**
 * This class is used to handle id related stuff in the GPML conversion.
 * @author wgm
 *
 */
public class GPMLIdHandler {
    // Make sure only one element will be created for one LiteratureReferenceInstance
    private Map<GKInstance, Element> litRefToElement;
    
    public GPMLIdHandler() {
        litRefToElement = new HashMap<GKInstance, Element>();
    }
    
    public void reset() {
        litRefToElement.clear();
    }
    
    public String getIdForLitRefInst(GKInstance litRef) {
        return "lit_" + litRef.getDBID();
    }
    
    public Element getLitRefElement(GKInstance inst) {
        Element elm = litRefToElement.get(inst);
        return elm;
    }
    
    public void addLitRefElement(GKInstance inst, Element elm) {
        litRefToElement.put(inst, elm);
    }
}
