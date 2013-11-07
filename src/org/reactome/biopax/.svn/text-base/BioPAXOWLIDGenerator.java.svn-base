/*
 * Created on Feb 1, 2011
 *
 */
package org.reactome.biopax;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.gk.model.GKInstance;

/**
 * This class is used to generate OWL IDs for BioPAX export. It is required that all OWL ids should be
 * unique across the whole name spaces for level 2 and level 3.
 * @author wgm
 *
 */
public class BioPAXOWLIDGenerator {
    // Cache all ids to avoid any duplication
    private Set<String> idSet;
    // Used to limit the id search to speed up the performance
    private GKInstance species;
    
    public BioPAXOWLIDGenerator() {
        idSet = new HashSet<String>();
    }
    
    public void setSpecies(GKInstance species) {
        this.species = species;
    }
    
    public GKInstance getSpecies() {
        return this.species;
    }
    
    /**
     * Reset all cached ids to empty.
     */
    public void reset() {
        idSet.clear();
    }
    
    /**
     * Generate a unique id based on a template.
     * @param idTemplate the id template
     * @return
     */
    public String generateOWLID(String idTemplate) {
        //String tmp = id.replaceAll("[ :,\\(\\)\\[\\]\\\\/]", "_");
        // Replace all non word character by "_"
        String tmp = idTemplate.replaceAll("\\W", "_");
        // Have to make sure digit should not be in the first place
        Pattern pattern = Pattern.compile("^\\d");
        if (pattern.matcher(tmp).find()) {
            tmp = "_" + tmp;
        }
//        String speciesAbb = getSpeciesAbbreviation();
//        if (speciesAbb != null)
//            tmp = tmp + "_" + speciesAbb + "_";
        int c = 1;
        String rtn = tmp + c; // Start with the first one
        // To keep the returned id unique.
        while (idSet.contains(rtn)) {
            // Have to find a new id
            c ++;
            rtn = tmp + c;
        }
        idSet.add(rtn);
        return rtn;        
    }
    
//    private String getSpeciesAbbreviation() {
//        if (species == null)
//            return null;
//        String name = species.getDisplayName();
//        String[] tokens = name.split(" ");
//        if (tokens.length == 1) {
//            // Get the first two letters
//            return tokens[0].substring(0, 2).toLowerCase();
//        }
//        else {
//            StringBuilder builder = new StringBuilder();
//            for (String token : tokens) {
//                builder.append(token.substring(0, 1).toLowerCase());
//            }
//            return builder.toString();
//        }
//    }
    
}
