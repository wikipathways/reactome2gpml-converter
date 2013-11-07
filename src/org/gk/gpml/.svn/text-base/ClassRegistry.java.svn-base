package org.gk.gpml;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ClassRegistry registers singleton classes and provides a list of instances (singletons) of the classes registered.
 * 
 * @author leon
 *
 */
public class ClassRegistry {
    private List<Class> classes = new ArrayList<Class>();
    private List<Object> singletons = null;
    private static Map<Class, ClassRegistry> registry = new HashMap<Class, ClassRegistry>();
    
    //To access the class, use getRegistry(Class) method.
    private ClassRegistry() {
        
    }
    
    public static ClassRegistry getRegistry(Class c) {
        ClassRegistry r = registry.get(c);
        if (r == null) {
            ClassRegistry newr = new ClassRegistry();
            registry.put(c, newr);
            return newr;
        }
        return r;
    }
    
    public void register(Class c) {
        classes.add(c);
    }
    
    public List<Object> getSingletons() {
        if (singletons == null) {
            singletons = new ArrayList<Object>();
            for (Class c : classes) {
                try {
                    singletons.add(c.newInstance());
                }
                catch (IllegalAccessException e) {
                    
                }
                catch (InstantiationException e) {
                    
                }
            }
        }
        return singletons;
    }
    
}
