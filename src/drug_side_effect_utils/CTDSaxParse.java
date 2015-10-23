package drug_side_effect_utils;  
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
	public String type;
	
	public CTDSaxParse(String xmlPath) throws Exception {
		entryList = new ArrayList<CtdEntry>();
		this.xmlPath = xmlPath;
		if(xmlPath.indexOf("chemical") != -1)
			type = "Chemical";
		else if(xmlPath.indexOf("disease") != -1)
			type = "Disease";
		else
			throw new Exception();

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

	@Override 
	public void startDocument() throws SAXException {
		 
	}
	@Override
	public void endDocument() throws SAXException {
		System.out.println(xmlPath+" #Entry Number: "+entryList.size());
	}
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		 currentTag = qName;
		 if(currentTag.equals("Row")) {
			 currentEntry = new CtdEntry();
		 }
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		 
	   if(qName.equals("Row") ) {
		   if(currentEntry != null) {
			   entryList.add(currentEntry);
			   currentEntry = null;		
		   }
		   
	   }	
	   currentTag = null;

	}	
	
	@Override
	public void characters(char[] ch, int start, int length) throws SAXException {
		if(currentTag != null) {
			String currentValues = new String(ch, start, length);
			if((type+"Name").equals(currentTag)) {
				currentEntry.name = currentValues;
			} else if((type+"ID").equals(currentTag)) {
				currentEntry.id = currentValues;
			} else if(("Alt"+type+"IDs").equals(currentTag)){
	        	
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
			
		}
		
		  
        
	}	
	
}

