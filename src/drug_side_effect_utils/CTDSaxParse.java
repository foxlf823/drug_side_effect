package drug_side_effect_utils;  
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.xml.parsers.SAXParser;  
import javax.xml.parsers.SAXParserFactory;  
  
import org.xml.sax.Attributes;   
import org.xml.sax.SAXException;  
import org.xml.sax.XMLReader;  
import org.xml.sax.helpers.DefaultHandler;

import deprecated.CTDdisease;

public class CTDSaxParse extends DefaultHandler {

	public List<CtdEntry> entryList;
	public String xmlPath;
	public int type; //0-Chemical, 1-Disease, 2-chem-disease
	
	public HashSet<CTDRelationEntry> relationSet;
	
	public boolean containRelation(String chemical, String disease) {
		CTDRelationEntry entry = new CTDRelationEntry();
		entry.chemicalName = chemical.toLowerCase();
		entry.diseaseName = disease.toLowerCase();
		return relationSet.contains(entry);
	}
	
	public CTDSaxParse(String xmlPath) throws Exception {
		entryList = new ArrayList<CtdEntry>();
		this.xmlPath = xmlPath;
		if(xmlPath.indexOf("chemicals_diseases") != -1)
			type = 2;
		else if(xmlPath.indexOf("chemical") != -1)
			type = 0;
		else if(xmlPath.indexOf("disease") != -1)
			type = 1; 
		else
			throw new Exception();
		
		relationSet = new HashSet<>();

		SAXParserFactory factory=SAXParserFactory.newInstance();  

        SAXParser sp=factory.newSAXParser();  

        XMLReader reader=sp.getXMLReader();  

        
		reader.setContentHandler(this);
		reader.parse(xmlPath);
		
		
		
	}
	
	
	public Map<String, String> buildIdNameMap() {
		Map<String, String> mapName2Id = new HashMap<>();
		for(CtdEntry entry:entryList) {
			String id = entry.id.substring(entry.id.indexOf(":")+1);
			mapName2Id.put(entry.name.toLowerCase(), id);
			if(entry.synonyms!=null) {
				for(String synonym:entry.synonyms)
					mapName2Id.put(synonym.toLowerCase(), id);
			}
		}
		return mapName2Id;
	}
	
	private String currentTag;  
	private CtdEntry currentEntry;
	private CTDRelationEntry currentRelation;

	@Override 
	public void startDocument() throws SAXException {
		 
	}
	@Override
	public void endDocument() throws SAXException {
		if(type==0 || type==1)
			System.out.println(xmlPath+" #Entry Number: "+entryList.size());
		else
			System.out.println(xmlPath+" #Relation Number: "+relationSet.size());
	}
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		 currentTag = qName;
		 if(currentTag.equals("Row")) {
			 if(type == 0 || type == 1)
				 currentEntry = new CtdEntry();
			 else
				 currentRelation = new CTDRelationEntry();
		 }
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		 
	   if(qName.equals("Row") ) {
		   if(type == 0 || type == 1) {
			   if(currentEntry != null) {
				   entryList.add(currentEntry);
				   currentEntry = null;		
			   }
		   } else {
			   if(currentRelation != null) {
				   relationSet.add(currentRelation);
				   currentRelation = null;		
			   }
		   }
		   
	   }	
	   currentTag = null;

	}	
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		if(currentTag != null) {
			String currentValues = new String(ch, start, length);
			
			
			if(type == 0 || type == 1) {
				String prefix = type == 0 ? "Chemical":"Disease";
				if((prefix+"Name").equals(currentTag)) {
					currentEntry.name = currentValues;
				} else if((prefix+"ID").equals(currentTag)) {
					currentEntry.id = currentValues;
				} else if(("Alt"+prefix+"IDs").equals(currentTag)){
		        	
		        	List<String> altDiseaseIds = new ArrayList<String>();   
		        	String[] alts = currentValues.split("\\|");
		        	if(alts.length > 1){
		        	for(int i = 0; i< alts.length; i++){
		        		altDiseaseIds.add(alts[i]);        			
		        		}
		        	}
		        	else{
		        		altDiseaseIds.add(currentValues);
		        	}
		        	currentEntry.altIds = altDiseaseIds;
		        } else if("ParentIDs".equals(currentTag)){
		        	
		        	List<String> parentIds = new ArrayList<String>();        
		        	String[] ids = currentValues.split("\\|");
		        	if(ids.length >1){
		        		for(int i = 0; i< ids.length ; i++){
		        			parentIds.add(ids[i]);        			
		        		}
		        	}
		        	else{
		        		parentIds.add(currentValues);
		        	}
		        	currentEntry.parentIds = parentIds;
		        } else if("Synonyms".equals(currentTag)){
		        	
		        	List<String> synonyms = new ArrayList<String>();        	
		        	String[] ids = currentValues.split("\\|");
		        	if(ids.length > 1){
		        		for(int i = 0; i< ids.length; i++){
		        			synonyms.add(ids[i]);        			
		        		}
		        	}
		        	else{
		        		synonyms.add(currentValues);
		        	}
		        	
		        	currentEntry.synonyms = synonyms;
		        } 
			} else {
				if("ChemicalName".equals(currentTag)) {
					currentRelation.chemicalName = currentValues.toLowerCase();
				} else if("DiseaseName".equals(currentTag)) {
					currentRelation.diseaseName = currentValues.toLowerCase();
				}
			}
				
			
			
			
		}
		
		  
        
	}	
	
}

