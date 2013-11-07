package org.gk.gpml;

import java.util.Collection;

import org.gk.render.Renderable;
import org.gk.render.RenderablePathway;
import org.jdom.Element;

/**
 * Converts Elements of a GPML to Renderable. Classes with this interface must implement
 * correct equals() behavior: a.equals(b) is true if and only if calls to a.convert() and
 * b.convert() produce consistent identical result.
 * @author leon
 *
 */

public interface ElementToRenderableConverter {
    /**
     * 
     * @param e the XML element to convert to its corresponding Renderable. It is not required
     * that a Renderable corresponds to one element (the method can use DOM operations on e).
     * @param collection add new Renderables to this collection
     */
    void convert(Element e, RenderablePathway diagram) throws ConverterException;
}
