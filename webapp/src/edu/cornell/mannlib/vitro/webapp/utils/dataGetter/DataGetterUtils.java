/* $This file is distributed under the terms of the license in /doc/license.txt$ */
package edu.cornell.mannlib.vitro.webapp.utils.dataGetter;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.QuerySolutionMap;
import com.hp.hpl.jena.query.ResultSet;
import com.hp.hpl.jena.rdf.model.Model;
import com.hp.hpl.jena.rdf.model.Resource;
import com.hp.hpl.jena.rdf.model.ResourceFactory;
import com.hp.hpl.jena.vocabulary.OWL;

import edu.cornell.mannlib.vitro.webapp.dao.DisplayVocabulary;
import edu.cornell.mannlib.vitro.webapp.dao.VitroVocabulary;
import edu.cornell.mannlib.vitro.webapp.utils.pageDataGetter.PageDataGetterUtils;

public class DataGetterUtils {
    
    final static Log log = LogFactory.getLog(DataGetterUtils.class);

    /**
     * Get a list of DataGetter objects that are associated with a page.
     * This should not return PageDataGetters and should not throw an 
     * exception if a page has PageDataGetters.  
     */
    public static List<DataGetter> getDataGettersForPage( Model displayModel, String pageURI) 
    throws InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException, SecurityException, InvocationTargetException, NoSuchMethodException{
        //get data getter uris for pageURI
        List<String> dgUris = getDataGetterURIsForPageURI( displayModel, pageURI);
        
        List<DataGetter> dgList = new ArrayList<DataGetter>();
        for( String dgURI: dgUris){
            DataGetter dg =dataGetterForURI(displayModel, dgURI) ;
            if( dg != null )
                dgList.add(dg); 
        }
        return dgList;
    }

    /**
     * Tests if classInQuestion implements interFace.
     */
    public static boolean isInstanceOfInterface( Class classInQuestion, Class interFace){
        if( classInQuestion == null || interFace == null )
            throw new IllegalAccessError("classInQuestion or interFace must not be null"); 
        
        //figure out if it implements interface         
        Class[] interfaces = classInQuestion.getInterfaces();
        if( interfaces == null )
            return false;
        boolean foundIface = false;
        for( Class cz : interfaces ){
            if( interFace.equals( cz ) ){
                return true;                
            }
        }
        return false;
    }
    
    /**
     * Returns a DataGetter using information in the 
     * displayModel for the individual with the URI given by dataGetterURI
     * to configure it. 
     * 
     * May return null.
     * This should not throw an exception if the URI exists and has a type
     * that does not implement the DataGetter interface.
     */
    public static DataGetter dataGetterForURI(Model displayModel, String dataGetterURI) 
    throws InstantiationException, IllegalAccessException, ClassNotFoundException, IllegalArgumentException, InvocationTargetException, SecurityException, NoSuchMethodException 
    {
        
        //get java class for dataGetterURI
        String dgClassName = getJClassForDataGetterURI(displayModel, dataGetterURI);
        
        //figure out if it implements interface DataGetter
        Class dgClass = Class.forName(dgClassName);
        if( ! isInstanceOfInterface( dgClass, DataGetter.class) ){
            return null;
        }
        
        //try to run constructor with (Model, String) parameters
        Class partypes[] = { Model.class , String.class };        
        Constructor ct = dgClass.getConstructor( partypes );
        
        Object obj = null;
        if( ct != null ){
            Object[] initargs = new Object[2];
            initargs[0]= displayModel;
            initargs[1] = dataGetterURI;
            obj = ct.newInstance(initargs);
        } else {
            log.debug("no constructor with signature " +
            		"(Model displayModel,String URI) found, trying empty constructor");                        
            obj =  dgClass.newInstance();
        }
        
        if( !(obj instanceof DataGetter) ){
            log.debug("For <" + dataGetterURI + "> the class " +
                    "for its rdf:type " + dgClassName + " does not implement the interface DataGetter.");
            return null;
        }
        
        return (DataGetter)obj;                
    }

    public static String getJClassForDataGetterURI(Model displayModel, String dataGetterURI) throws IllegalAccessException {
        String query = prefixes +
        "SELECT ?type WHERE { ?dgURI rdf:type ?type } ";
        Query dgTypeQuery = QueryFactory.create(query);
        
        QuerySolutionMap initialBindings = new QuerySolutionMap();
        initialBindings.add("dgURI", ResourceFactory.createResource( dataGetterURI ));
        
        List<String> types = new ArrayList<String>();         
        displayModel.enterCriticalSection(false);
        try{
            QueryExecution qexec = QueryExecutionFactory.create(dgTypeQuery,displayModel,initialBindings );
            try{                                                    
                ResultSet results = qexec.execSelect();                
                while (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    Resource type = soln.getResource("type");
                    if( type != null && type.getURI() != null){
                        types.add( PageDataGetterUtils.getClassNameFromUri( type.getURI() ));
                    }
                }
            }finally{ qexec.close(); }
        }finally{ displayModel.leaveCriticalSection(); }
        
        
        return chooseType( types, displayModel, dataGetterURI);
    }
    
    
    private static List<String> getDataGetterURIsForPageURI(Model displayModel, String pageURI) {
        String query = prefixes + 
             "SELECT ?dataGetter WHERE { ?pageURI display:hasDataGetter ?dataGetter }";
        Query dgForPageQuery = QueryFactory.create(query);
        
        QuerySolutionMap initialBindings = new QuerySolutionMap();
        initialBindings.add("pageURI", ResourceFactory.createResource( pageURI ));
        
        List<String> dgURIs = new ArrayList<String>();
        displayModel.enterCriticalSection(false);
        try{
            QueryExecution qexec = QueryExecutionFactory.create(dgForPageQuery,displayModel,initialBindings );
            try{                                                    
                ResultSet results = qexec.execSelect();                
                while (results.hasNext()) {
                    QuerySolution soln = results.nextSolution();
                    Resource dg = soln.getResource("dataGetter");
                    if( dg != null && dg.getURI() != null){
                        dgURIs.add( dg.getURI());
                    }
                }
            }finally{ qexec.close(); }
        }finally{ displayModel.leaveCriticalSection(); }
                
        return dgURIs;
    }
    
    private static String chooseType(List<String> types, Model displayModel, String dataGetterURI) throws IllegalAccessException {
        //currently just get the first one that is not owl:Thing
        for(String type : types){
            if( ! StringUtils.isEmpty( type ) && !type.equals( OWL.Thing.getURI() ))
                return type;
        }
        throw new IllegalAccessException("No useful type defined for <" + dataGetterURI + ">");        
    }

    static final String prefixes = 
        "PREFIX rdf:   <" + VitroVocabulary.RDF +"> \n" +
        "PREFIX rdfs:  <" + VitroVocabulary.RDFS +"> \n" + 
        "PREFIX xsd:  <http://www.w3.org/2001/XMLSchema#> \n" +
        "PREFIX display: <" + DisplayVocabulary.DISPLAY_NS +"> \n";


    
}