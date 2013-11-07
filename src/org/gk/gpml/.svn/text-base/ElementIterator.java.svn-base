package org.gk.gpml;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;

import org.jdom.Document;
import org.jdom.Element;


/**
 * Provides a breadth-first-search (BFS) iterator for elements in a particular Document matching certain element names.
 * @author leon
 *
 */
class ElementIterator implements Iterator<Element> {
    
    private Collection<String> interestingElementNames;
    private Document doc;
    private LinkedList<Element> queue;
    
    public ElementIterator(Document doc, Collection<String> interestingElementNames) {
        this.doc = doc;
        this.interestingElementNames = interestingElementNames;
    }
    
    public boolean hasNext() {
        if (queue == null) {
            return true;
        }
        // process all elements until we get to a valid one
        while (queue.size() > 0 && !isValid(queue.getFirst())) {
            processQueueOnce();
        }
        if (queue.size() > 0) {
            return true;
        }
        // we cannot find any more valid elements
        return false;
    }

    public Element next() {
        if (queue == null) {
            queue = new LinkedList<Element>();
            queue.addLast(doc.getRootElement());
        }
        // call hasNext() to be sure that the next item in queue is really valid
        hasNext();
        return processQueueOnce();
    }
    
    private Element processQueueOnce() {
        Element e = queue.removeFirst();
        for (Object child : e.getChildren()) {
            queue.addLast((Element)child);
        }
        return e;
    }
    
    private boolean isValid(Element e) {
        return interestingElementNames.contains(e.getName());
    }
    
    public void remove() {
        throw new UnsupportedOperationException();
    }
    
}