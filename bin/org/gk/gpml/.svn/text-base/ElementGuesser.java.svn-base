package org.gk.gpml;

import java.util.List;
import java.util.Map;

import org.jdom.Element;

public interface ElementGuesser {
    /**
     * Try to provide ElementToRenderableConverter that suits the element conversion best.
     * 
     * @param e one of the elements (specified by getElementNamesOfInterest()) to guess
     * @return a map of guesses. Key is a org.gk.gpml.ElementToRenderableConverter that this class
     * thinks can convert e the best. Value is a confidence value ranging from -1.0 ("This converter
     * definitely does not need to convert e") to 1.0 ("This converter definitely needs to convert e").
     */
    Map <ElementToRenderableConverter, Double> guess(Element e, GPMLToReactomeConverter converter);
    
    /**
     * 
     * @return list of element names that can be processed by guess(Element).
     */
    List <String> getElementNamesOfInterest();
}
