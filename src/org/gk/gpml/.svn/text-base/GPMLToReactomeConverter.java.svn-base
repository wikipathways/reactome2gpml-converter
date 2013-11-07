package org.gk.gpml;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JFrame;

import org.gk.gkEditor.ZoomablePathwayEditor;
import org.gk.graphEditor.PathwayEditor;
import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.gk.render.RenderablePathway;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

public class GPMLToReactomeConverter {
    private MySQLAdaptor adaptor;
    private String gpmlSource;
    private List<ElementGuesser> guessers;
    private String atxml;
    private RenderablePathway renderedDiagram;
    
//    static {
//        //register core ElementGuessers
//        ClassRegistry registry = ClassRegistry.getRegistry(ElementGuesser.class);
//        registry.register(ReactomeElementGuesser.class);
//    }
    
    public GPMLToReactomeConverter() {
        //TODO
    }
    
    public GPMLToReactomeConverter(String src) {
        this();
        setGPMLSource(src);
    }
    
    public GPMLToReactomeConverter(File aFile) throws IOException {
        StringBuilder contents = new StringBuilder();
        //use buffering, reading one line at a time
        //FileReader always assumes default encoding is OK!
        BufferedReader input =  new BufferedReader(new FileReader(aFile));
        try {
            String line = null; //not declared within while loop
            while (( line = input.readLine()) != null){
                contents.append(line);
                contents.append(System.getProperty("line.separator"));
            }
        }
        finally {
            // input.close() also throws IOException, hence the double try block
            input.close();
        }
        
        
        gpmlSource = contents.toString();
    }
    
    public List<ElementGuesser> getGuessers() {
        return guessers;
    }

    public void setGuessers(List<ElementGuesser> guessers) {
        this.guessers = guessers;
    }
    
    public MySQLAdaptor getAdaptor() {
        return adaptor;
    }

    public void setAdaptor(MySQLAdaptor adaptor) {
        this.adaptor = adaptor;
    }
    
    public void setGPMLSource(String src) {
        this.gpmlSource = src;
    }
    
    public String getGPMLSource() {
        return gpmlSource;
    }
    
    public RenderablePathway getRenderedDiagram() {
        return renderedDiagram;
    }
    
    /**
     * Perform conversion to reactome format. Transparently called by public methods such as getEntities() or
     * getATXML(). You may want to call this directly to refresh the internal cache, e.g. if you setGPMLSource()
     * to reuse an instance of this class. At least a call setGPMLSource() or using a proper constructor is needed
     * before calling this method.
     * 
     * The conversion result is stored in an internal cache and can be retrieved using getATXML() and
     * getInstances().
     * 
     * @return true if conversion is a success, false otherwise (empty values will be returned by getATXML() etc.
     * in case of conversion failure)
     */
    public boolean convert() {
        if (gpmlSource == null) {
            return false;
        }
        
        if (guessers == null) {
            guessers = new ArrayList<ElementGuesser>();
            // default to use all guessers available
            for (Object guesserObject : ClassRegistry.getRegistry(ElementGuesser.class).getSingletons()) {
                guessers.add((ElementGuesser) guesserObject);
            }
        }
        
        Document inputDoc;
        try {
            SAXBuilder builder = new SAXBuilder();
            inputDoc = builder.build(new StringReader(gpmlSource));
        } catch (JDOMException e1) {
            e1.printStackTrace();
            return false;
        } catch (IOException e1) {
            e1.printStackTrace();
            return false;
        }
        
        
        Document outputDoc = new Document();
        
        //build a map of ElementGuesser interested in specific Elements
        Map<String, List<ElementGuesser>> interests = new HashMap<String, List<ElementGuesser>>();
        for (ElementGuesser guesser : guessers) {
            for (String elementName : guesser.getElementNamesOfInterest()) {
                if (interests.get(elementName) == null) {
                    interests.put(elementName, new ArrayList<ElementGuesser>());
                }
                interests.get(elementName).add(guesser);
            }
        }
        
        renderedDiagram = new RenderablePathway();
        
        // Iterate through all interesting elements, determine the best converter for the particular element
        // and invoke it. This logic is made to ensure that future implementations will always augment existing
        // conversion code, and a GPML with more than one convention in place can be converted.
        ElementIterator elementIterator = new ElementIterator(inputDoc, interests.keySet());
        while (elementIterator.hasNext()) {
            Element e = elementIterator.next();
            List<ElementGuesser> candidates = interests.get(e.getName());
            if (candidates != null) {
                // determine the best converter for the particular element
                ElementToRenderableConverter converter = getBestRenderableConverter(e, candidates);
                try {
                    converter.convert(e, renderedDiagram);
                }
                catch (ConverterException ex) {
                    ex.printStackTrace();
                }
                
            }
        }
        
        
        return true;
    }
    
    /**
     * Determines the best converter for the particular element.
     * @param e element to match
     * @param candidates ElementGuessers to pick from
     * @return the ElementGuesser that has highest chance to convert e correctly
     */
    private ElementToRenderableConverter getBestRenderableConverter (Element e, List<ElementGuesser> candidates) {
        Map<ElementToRenderableConverter, Double> converterConfidences = new HashMap<ElementToRenderableConverter, Double>();
        Map.Entry<ElementToRenderableConverter, Double> bestEntry = null;
        
        for (ElementGuesser guesser : candidates) {
            Map<ElementToRenderableConverter, Double> returnValue = guesser.guess(e, this);
            for (Map.Entry<ElementToRenderableConverter, Double> entry : returnValue.entrySet()) {
                Double confidenceValue = converterConfidences.get(entry.getKey());
                if (confidenceValue == null) {
                    confidenceValue = entry.getValue();
                }
                else {
                    confidenceValue += entry.getValue();
                }
                converterConfidences.put(entry.getKey(), confidenceValue);
                if ((bestEntry == null) || (confidenceValue > bestEntry.getValue())) {
                    bestEntry = entry;
                }
            }
        }
        //System.out.println(":bestEntry:" + bestEntry.getKey() + ":" + bestEntry.getValue());
        return bestEntry.getKey();
    }
    
    /**
     * Returns the ATXML representation of the GPML format.
     */
    public String getATXML() {
        if (atxml == null) {
            convert();
        }
        return atxml;
    }
    
    /**
     * Returns all reactome instances present in the diagram.
     */
    public List<GKInstance> getInstances() {
        //TODO
        return null;
    }
    
    private static final Font DEFAULT_FONT = new Font("Dialog", Font.PLAIN, 12);
    
    private static void printUsage() {
        System.out.println("Usage: java org.gk.gpml.GPMLToReactomeConverter dbhost dbName user pwd port gpmlfile");
    }
    
    /**
     * This utility script accepts a GPML file and displays the pathway diagram in a JFrame. The pathway diagram is
     * alreadi in Renderable objects format.
     * @param args
     */
    public static void main(String[] args) {
        if (args.length != 6) {
            printUsage();
            System.exit(1);
        }
        
        GPMLToReactomeConverter converter = null;
        
        try {
            converter = new GPMLToReactomeConverter(new File(args[5]));
        } catch (IOException ex) {
            System.err.println(ex.getLocalizedMessage());
            System.exit(1);
        }
        
        try {
            MySQLAdaptor adaptor = new MySQLAdaptor(args[0],
                    args[1],
                    args[2], 
                    args[3],
                    Integer.parseInt(args[4]));
            converter.setAdaptor(adaptor);
        } catch (SQLException ex) {
            System.err.println(ex.getLocalizedMessage());
            System.exit(1);
        }
        converter.convert();
        
        //Create and set up the window.
        ZoomablePathwayEditor zoomableEditor= new ZoomablePathwayEditor();
        PathwayEditor editor = zoomableEditor.getPathwayEditor();
        editor.setRenderable(converter.renderedDiagram);
        Dimension size = editor.getPreferredSize();
        BufferedImage image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_3BYTE_BGR);
        Graphics g = image.createGraphics();
        g.setFont(DEFAULT_FONT);
        // Need to set clip with the whole size so that everything can be drawn
        Rectangle clip = new Rectangle(size);
        g.setClip(clip);
        editor.paint(g);
        
        
        JFrame frame = new JFrame("Converter");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(zoomableEditor);
        //Display the window.
        frame.pack();
        frame.setVisible(true);
        
        
    }
}
