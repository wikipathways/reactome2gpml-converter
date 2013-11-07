/*
 * Created on Dec 20, 2010
 *
 */
package org.reactome.biopax;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.jdom.Element;
import org.jdom.Namespace;

/**
 * This helper class is used to convert publication from Reactome to BioPAX (level 2 and 3).
 * @author wgm
 *
 */
public class ReactomeToBioPAXPublicationConverter {
    private String PublicationXref;
    private String id;
    private String XSD_STRING;
    private String db;
    private String XSD_INT;
    private String year;
    private String title;
    private String author;
    private String source;
    private String url;
    
    public ReactomeToBioPAXPublicationConverter() {
    }
    
    public void setIsForLevel2(boolean isLevel2) {
        if (isLevel2) {
            PublicationXref = BioPAXJavaConstants.publicationXref;
            id = BioPAXJavaConstants.ID;
            XSD_STRING = BioPAXJavaConstants.XSD_STRING;
            db = BioPAXJavaConstants.DB;
            XSD_INT = BioPAXJavaConstants.XSD_INT;
            year = BioPAXJavaConstants.YEAR;
            title = BioPAXJavaConstants.TITLE;
            author = BioPAXJavaConstants.AUTHORS;
            source = BioPAXJavaConstants.SOURCE;
            url = BioPAXJavaConstants.URL;
        }
        else {
            PublicationXref = BioPAX3JavaConstants.PublicationXref;
            id = BioPAX3JavaConstants.id;
            XSD_STRING = BioPAX3JavaConstants.XSD_STRING;
            db = BioPAX3JavaConstants.db;
            XSD_INT = BioPAX3JavaConstants.XSD_INT;
            year = BioPAX3JavaConstants.year;
            title = BioPAX3JavaConstants.title;
            author = BioPAX3JavaConstants.author;
            source = BioPAX3JavaConstants.source;
            url = BioPAX3JavaConstants.url;
        }
    }
    
    public Element convertPublication(GKInstance publication,
                                      BioPAXOWLIDGenerator idGenerator,
                                      String id,
                                      Namespace bpNS,
                                      Namespace rdfNS,
                                      Element rootElm) throws Exception {
        Element rtn = null;
        if (publication.getSchemClass().isa(ReactomeJavaConstants.LiteratureReference)) {
            rtn = convertLiteratureReference(publication, 
                                             idGenerator, 
                                             id,
                                             bpNS, 
                                             rdfNS,
                                             rootElm);
        }
        else if (publication.getSchemClass().isa(ReactomeJavaConstants.Book)) {
            rtn = convertBookReference(publication,
                                       idGenerator,
                                       id,
                                       bpNS,
                                       rdfNS,
                                       rootElm);
        }
        else if (publication.getSchemClass().isa(ReactomeJavaConstants.URL)) {
            rtn = convertURL(publication,
                              idGenerator,
                              id,
                              bpNS,
                              rdfNS,
                              rootElm);
        }
        return rtn;
    }
    
    private Element convertBookReference(GKInstance publication,
                                         BioPAXOWLIDGenerator idGenerator,
                                         String id,
                                         Namespace bpNS,
                                         Namespace rdfNS,
                                         Element rootElm) throws Exception {
        if (id == null) {
            id = idGenerator.generateOWLID(PublicationXref);
        }
        Element pubXrefIndividual = ReactomeToBioPAXUtilities.createIndividualElm(PublicationXref,
                                                                                  id,
                                                                                  bpNS,
                                                                                  rdfNS,
                                                                                  rootElm);
        String isbn = (String) publication.getAttributeValue(ReactomeJavaConstants.ISBN);
        if (isbn != null) {
            // ID is a String in BioPAX. Need to convert to String.
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.id,
                                                        this.XSD_STRING,
                                                        isbn,
                                                        bpNS,
                                                        rdfNS);
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.db,
                                                        this.XSD_STRING,
                                                        "ISBN",
                                                        bpNS,
                                                        rdfNS);
        }
        Integer year = (Integer) publication.getAttributeValue(ReactomeJavaConstants.year);
        if (year != null) {
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.year,
                                                        this.XSD_INT,
                                                        year,
                                                        bpNS,
                                                        rdfNS);
        }
        // Title
        String title = (String) publication.getAttributeValue(ReactomeJavaConstants.chapterTitle);
        if (title == null)
            title = (String) publication.getAttributeValue(ReactomeJavaConstants.title);
        if (title != null) {
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.title,
                                                        this.XSD_STRING,
                                                        title,
                                                        bpNS,
                                                        rdfNS);
        }
        // Authors
        List<?> authors = publication.getAttributeValuesList(ReactomeJavaConstants.chapterAuthors);
        if (authors == null || authors.size() == 0)
            authors = publication.getAttributeValuesList(ReactomeJavaConstants.author);
        if (authors != null && authors.size() > 0) {
            // Two persons might have the same display name. E.g. pmid: 15070733.
            List<String> names = new ArrayList<String>();
            for (Iterator<?> it = authors.iterator(); it.hasNext();) {
                GKInstance person = (GKInstance) it.next();
                String displayName = person.getDisplayName();
                if (displayName == null)
                    displayName = person.toString();
                //if (person.getDisplayName() == null) {
                //    System.out.println("Person has null displayName: " + person.toString());
                //    continue;
                //}
                names.add(displayName);
            }
            //TODO: Check what occurs if two names are the same?
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.author,
                                                        this.XSD_STRING,
                                                        names,
                                                        bpNS,
                                                        rdfNS);
        }
        // Source is from Journal title, volume and page
        StringBuffer source = new StringBuffer();
        String journal = (String) publication.getAttributeValue(ReactomeJavaConstants.title);
        if (journal != null) 
            source.append(journal).append(" (Book)");
        String page = (String) publication.getAttributeValue(ReactomeJavaConstants.pages);
        if (page != null)
            source.append(": ").append(page);
        if (source.length() > 0) {
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.source,
                                                        this.XSD_STRING,
                                                        source.toString(),
                                                        bpNS,
                                                        rdfNS);
        }
        return pubXrefIndividual;
    }
    
    private Element convertURL(GKInstance publication,
                               BioPAXOWLIDGenerator idGenerator,
                               String id,
                               Namespace bpNS,
                               Namespace rdfNS,
                               Element rootElm) throws Exception {
        if (id == null) {
            id = idGenerator.generateOWLID(PublicationXref);
        }
        Element pubXrefIndividual = ReactomeToBioPAXUtilities.createIndividualElm(PublicationXref,
                                                                                  id,
                                                                                  bpNS,
                                                                                  rdfNS,
                                                                                  rootElm);
        String url = (String) publication.getAttributeValue(ReactomeJavaConstants.uniformResourceLocator);
        if (url != null) {
            // ID is a String in BioPAX. Need to convert to String.
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.url,
                                                        this.XSD_STRING,
                                                        url,
                                                        bpNS,
                                                        rdfNS);
        }
        // Title
        String title = (String) publication.getAttributeValue(ReactomeJavaConstants.title);
        if (title != null) {
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.title,
                                                        this.XSD_STRING,
                                                        title,
                                                        bpNS,
                                                        rdfNS);
        }
        // Authors
        List<?> authors = publication.getAttributeValuesList(ReactomeJavaConstants.author);
        if (authors != null && authors.size() > 0) {
            // Two persons might have the same display name. E.g. pmid: 15070733.
            List<String> names = new ArrayList<String>();
            for (Iterator<?> it = authors.iterator(); it.hasNext();) {
                GKInstance person = (GKInstance) it.next();
                String displayName = person.getDisplayName();
                if (displayName == null)
                    displayName = person.toString();
                //if (person.getDisplayName() == null) {
                //    System.out.println("Person has null displayName: " + person.toString());
                //    continue;
                //}
                names.add(displayName);
            }
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.author,
                                                        this.XSD_STRING,
                                                        names,
                                                        bpNS,
                                                        rdfNS);
        }
        return pubXrefIndividual;
    }

    private Element convertLiteratureReference(GKInstance publication,
                                               BioPAXOWLIDGenerator idGenerator,
                                               String id,
                                               Namespace bpNS, 
                                               Namespace rdfNS,
                                               Element rootElm) throws Exception {
        Integer pmid = (Integer) publication.getAttributeValue(ReactomeJavaConstants.pubMedIdentifier);
        if (id == null) {
            id = idGenerator.generateOWLID(PublicationXref);
        }
        Element pubXrefIndividual = ReactomeToBioPAXUtilities.createIndividualElm(PublicationXref,
                                                                                  id,
                                                                                  bpNS,
                                                                                  rdfNS,
                                                                                  rootElm);
        if (pmid != null) {
            // ID is a String in BioPAX. Need to convert to String.
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.id,
                                                        this.XSD_STRING,
                                                        pmid.toString(),
                                                        bpNS,
                                                        rdfNS);
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.db,
                                                        this.XSD_STRING,
                                                        "Pubmed",
                                                        bpNS,
                                                        rdfNS);
        }
        Integer year = (Integer) publication.getAttributeValue(ReactomeJavaConstants.year);
        if (year != null) {
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.year,
                                                        this.XSD_INT,
                                                        year,
                                                        bpNS,
                                                        rdfNS);
        }
        // Title
        String title = (String) publication.getAttributeValue(ReactomeJavaConstants.title);
        if (title != null) {
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.title,
                                                        this.XSD_STRING,
                                                        title,
                                                        bpNS,
                                                        rdfNS);
        }
        // Authors
        List<?> authors = publication.getAttributeValuesList(ReactomeJavaConstants.author);
        if (authors != null && authors.size() > 0) {
            // Two persons might have the same display name. E.g. pmid: 15070733.
            List<String> names = new ArrayList<String>();
            for (Iterator<?> it = authors.iterator(); it.hasNext();) {
                GKInstance person = (GKInstance) it.next();
                String displayName = person.getDisplayName();
                if (displayName == null)
                    displayName = person.toString();
                //if (person.getDisplayName() == null) {
                //    System.out.println("Person has null displayName: " + person.toString());
                //    continue;
                //}
                names.add(displayName);
            }
            //TODO: Check what occurs if two names are the same?
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.author,
                                                        this.XSD_STRING,
                                                        names,
                                                        bpNS,
                                                        rdfNS);
        }
        // Source is from Journal title, volume and page
        StringBuffer source = new StringBuffer();
        String journal = (String) publication.getAttributeValue(ReactomeJavaConstants.journal);
        if (journal != null) 
            source.append(journal);
        Integer volume = (Integer) publication.getAttributeValue(ReactomeJavaConstants.volume);
        if (volume != null)
            source.append(" ").append(volume).append(":");
        String page = (String) publication.getAttributeValue(ReactomeJavaConstants.pages);
        if (page != null)
            source.append(page);
        if (source.length() > 0) {
            ReactomeToBioPAXUtilities.createDataPropElm(pubXrefIndividual,
                                                        this.source,
                                                        this.XSD_STRING,
                                                        source.toString(),
                                                        bpNS,
                                                        rdfNS);
        }
        return pubXrefIndividual;
    }
    
}
