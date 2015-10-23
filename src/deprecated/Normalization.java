package deprecated;
import java.util.Iterator;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;

import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.Entity;



public class Normalization {
	 Map<String,Set<String>> lexiconIdToNames;
	 Map<String,Map<String,String>> abbreviations;
	
	public Normalization(){
		lexiconIdToNames = new HashMap<String,Set<String>>();
		abbreviations = new HashMap<String,Map<String,String>>();
		
	}
	public void loadLexicon(String filename){
		CTDmedicLexicon ctdMedicLexicon = new CTDmedicLexicon();
		ctdMedicLexicon.loadLexicon(filename);
		lexiconIdToNames = ctdMedicLexicon.getIdToConceptNames();
		
	}
	//load CDRAbbreFile
	public void loadCdrAbbre(String cdrAbbrFile,String cdrOutSf_LfPairs){		
	    
	    AbbreviationResolver cdrAbbrResolver = new AbbreviationResolver();
	    cdrAbbrResolver.loadAbbreFromFile(cdrAbbrFile);
	    cdrAbbrResolver.writeAbberToFile(cdrOutSf_LfPairs);
	    abbreviations = cdrAbbrResolver.getAbbreviations();
		
	}
	public 	 Map<String,Set<String>> getLexiconIdToNames(){
		return this.lexiconIdToNames;
	}
	//处理document中的entity是缩略词的情况
	public String replaceAbbre(Entity entity,BiocDocument document){
		String longForm = null;
		
	     //replace abbreviations in  document
	   
	    Iterator iter = abbreviations.entrySet().iterator();
	    Map<String,String> sfAndLf = new HashMap<String,String>();
	    String docText = null;
	    
	    while(iter.hasNext()){
	    	Map.Entry entry = (Map.Entry) iter.next();
	    	String docId = (String) entry.getKey();
	    	if(!docId.trim().equals(document.id.trim()))
	    		continue;
	    	sfAndLf = (Map)entry.getValue(); 	    	
	    	/* if(docId.equals("10090885"))	    	 
	    		 System.out.println();	    	
	    	 docText = document.title + " " + document.abstractt;
	    		    	*/
	    	 Iterator it = sfAndLf.entrySet().iterator();
	    	 
	    	 while(it.hasNext()){
	    		  
	    		  Map.Entry ent = (Map.Entry) it.next();
	    		  String sf = (String) ent.getKey();
	    		  sf = sf.trim();
	    		  String lf = (String) ent.getValue();
	    		  lf = lf.trim();
	    		  if(sf.equals(entity.text.trim())){
	    			  longForm = lf;	    			       	     	  
	    		 }
	    		}
	    		
	    	 }
	    if(longForm!=null)
	    	return longForm;
	    return entity.text;
	     
	}


   public  static String  replaceStr(String raw , String old,String replace,int start){
	StringBuilder docText = new StringBuilder();
	if(raw != null ){
		docText.append(raw.substring(0, start));
		docText.append(replace);
		docText.append(raw.substring(start+old.length()));
		
	}
	return docText.toString();
   }
	

}
