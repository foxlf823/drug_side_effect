package utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CtdEntry {
	public String name;
	public String type;
	public String id;
	public List<String> altIds;
	public List<String> parentIds;
	public List<String> synonyms;
	public 	Map<String, List<String>> idToSynonyms;
	
	public CtdEntry(){
		altIds = new ArrayList<String>();
		parentIds = new ArrayList<String>();
		synonyms = new ArrayList<String>();
		idToSynonyms = new HashMap<String, List<String>>();
		
	}
	public CtdEntry(String name,String id){
		altIds = new ArrayList<String>();
		parentIds = new ArrayList<String>();
		synonyms = new ArrayList<String>();	
		idToSynonyms = new HashMap<String, List<String>>();
		
	}
}
