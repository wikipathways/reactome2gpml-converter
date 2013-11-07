package org.gk.gpml;

import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.sql.ResultSet;
import java.util.ArrayList;

import org.gk.model.GKInstance;
import org.gk.persistence.MySQLAdaptor;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

/**
 * A script to extract ATXML pathway diagrams in the database to file system, check their validity,
 * converts them to GPML, and validates the generated GPML.
 * 
 * The script behavior (whether to extract or validate, etc.) can be specified at compile time by modifying
 * the variables at main() (isWriting, isValidating, checkATXML, checkGPML).
 * 
 * @author leon
 *
 */
class TestPathways {
    
    private static void writeToFile(String s, String fn) {
        try {
            BufferedWriter out = new BufferedWriter(new FileWriter(fn));
        out.write(s);
        out.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void log(String s) {
        System.out.println(s);
    }
    

    private static SAXBuilder getValidatingBuilder(String xsdUri, String fileLocation) {
        SAXBuilder builder = new SAXBuilder(true);
        String attr = xsdUri + " " + fileLocation;
        builder.setFeature("http://apache.org/xml/features/validation/schema", true);
        builder.setProperty(
                "http://apache.org/xml/properties/schema/external-noNamespaceSchemaLocation",
                attr);
        builder.setProperty(
                "http://apache.org/xml/properties/schema/external-schemaLocation",
                attr);
        return builder;
    }
    
    private static interface DiagramTester {
        public String fetchDiagram(GKInstance instance) throws Exception;
        /*
         * Injects extraAttributes to the first element of the XML diagram.
         * Validation only works when the first element of the XML has certain
         * string so it is hacked into the XML using regular expression.
         */
        public String inject(String diagram, String extraAttributes);
        public void validate(String diagram) throws Exception;
        public String getFileNameExtension();
        public String getXSDURI();
        public boolean isXMLNamespaced();
    }
    
    
    private class ATXMLDiagramTester implements DiagramTester {
        private ResultSet rs;
        private int rsIndex;
        private SAXBuilder validator;
        private String xsdUri = "";
        
        /*
         * rs is the SQL ResultSet while index is the row number that contains the diagram.
         */
        public ATXMLDiagramTester(ResultSet rs, int index) {
            this.rs = rs;
            this.rsIndex = index;
            
            File atxmlFile = new File("atxml.xsd");
            if (!atxmlFile.exists()) {
                log("Error: ATXML XSD not found.");
                //System.exit(1);
            }
            xsdUri = "file://" + atxmlFile.getAbsolutePath();
            validator = getValidatingBuilder(xsdUri, xsdUri);
        }
        public String fetchDiagram(GKInstance instance) throws Exception {
            return rs.getString(rsIndex);
        }
        public String inject(String diagram, String extraAttributes) {
            return diagram.replaceFirst("\\<Process ", "<Process "+extraAttributes+" ");
        }
        public void validate(String diagram) throws Exception {
            validator.build(new ByteArrayInputStream(diagram.getBytes("UTF-8")));
        }
        public String getFileNameExtension() {
            return "xml";
        }
        public String getXSDURI() {
            return xsdUri;
        }
        public boolean isXMLNamespaced() {
            return false;
        }
    }
    
    private class GPMLDiagramTester implements DiagramTester {
        private SAXBuilder validator;
        private String xsdUri = "http://genmapp.org/GPML/2010a";
        private ReactomeToGPMLConverter r2gConverter;
        private MySQLAdaptor adaptor = null;
        
        public GPMLDiagramTester(MySQLAdaptor adaptor) {
            File atxmlFile = new File("GPML2010a.xsd");
            if (!atxmlFile.exists()) {
                log("Error: GPML2010a XSD not found.");
                //System.exit(1);
            }
            String schemaLocation = "file://" + atxmlFile.getAbsolutePath();
            validator = getValidatingBuilder(xsdUri, schemaLocation);
            r2gConverter = new ReactomeToGPMLConverter();
            this.adaptor = adaptor;
            r2gConverter.setMySQLAdaptor(adaptor);
        }
        public String fetchDiagram(GKInstance instance) throws Exception {
            Document doc = r2gConverter.convertPathway(instance);
            XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
            ByteArrayOutputStream oStream = new ByteArrayOutputStream();
            outputter.output(doc, oStream);
            return oStream.toString("UTF-8");
        }
        public String inject(String diagram, String extraAttributes) {
            return diagram.replaceFirst(
                    "\\<Pathway ",
                    "<Pathway "+extraAttributes+" ");
        }
        public void validate(String diagram) throws Exception {
            validator.build(new ByteArrayInputStream(diagram.getBytes("UTF-8")));
        }
        public String getFileNameExtension() {
            return "gpml";
        }
        public String getXSDURI() {
            return xsdUri;
        }
        public boolean isXMLNamespaced() {
            return true;
        }
    }
    
    private static void printUsage() {
        System.out.println("Usage: java org.gk.gpml.TestPathways dbhost dbName user pwd port");
    }
    
    public static void main(String[] args) {
        TestPathways testPathways = new TestPathways();
        boolean isWriting = true; // write the XML files?
        boolean isValidating = true; // validate the XML files?
        boolean checkATXML = true; // validate ATXML?
        boolean checkGPML = true; // validate checkGPML?
        
        final String DIAGRAM_DIRECTORY = "diagrams/";
        if (args.length != 5) {
            printUsage();
            System.exit(1);
        }
        
        new File(DIAGRAM_DIRECTORY).mkdir();
        try {
            
            MySQLAdaptor adaptor = new MySQLAdaptor(args[0],
                    args[1],
                    args[2], 
                    args[3],
                    Integer.parseInt(args[4]));
            
            
            ResultSet rs = adaptor.executeQuery("SELECT p.DB_ID, CONVERT(pd.storedATXML using utf8)" +
                    " FROM PathwayDiagram pd INNER JOIN Pathway p" +
                    " ON pd.representedPathway = p.DB_ID", null);
            
            ArrayList<DiagramTester> testers = new ArrayList<DiagramTester>();
            if (checkATXML) {
                testers.add(testPathways.new ATXMLDiagramTester(rs, 2));
            }
            if (checkGPML) {
                testers.add(testPathways.new GPMLDiagramTester(adaptor));
            }
            
            while (rs.next()) {
                Long dbID = rs.getLong(1);
                GKInstance instance = adaptor.fetchInstance(dbID);
                String diagramName = dbID + " "  + instance.getDisplayName();
                for (DiagramTester tester : testers) {
                    try {
                        //TODO: OS-agnostic directories & auto-create
                        String diagramFileName =
                            DIAGRAM_DIRECTORY
                            + diagramName.replaceAll("[^0-9A-Za-z()_-]+", " ")
                            + "."
                            + tester.getFileNameExtension();
                        String diagram = tester.fetchDiagram(instance);
                        if (isWriting) {
                            writeToFile(diagram, diagramFileName);
                        }
                        if (isValidating) {
                            log("Validating " + tester.getFileNameExtension()
                                    + " " + diagramName + " (" + diagram.length() + " bytes)... ");
                            String extraAttributes = "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                            + "xsi:"
                            + (tester.isXMLNamespaced() ? "schemaLocation" : "noNamespaceSchemaLocation")
                            + "=\""+tester.getXSDURI()+"\"";
                            String injectedDiagram = tester.inject(diagram, extraAttributes);
                            //writeToFile(injectedDiagram, diagramFileName);
                            tester.validate(injectedDiagram);
                        }
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        continue;
                    }
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
}


