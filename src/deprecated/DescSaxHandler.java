package deprecated;

import java.util.HashMap;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import cn.fox.utils.DTDEntityResolver;

public class DescSaxHandler extends DefaultHandler {
	int maxNumOfWordPerEntry; // The entry whose numbers of words > this will be ignored
	UiNamePair current;
	String lastElement;
	String dtdPath;
	String xmlPath;
	public HashMap<String, String> dict;
	class UiNamePair {
		String ui;
		String name;
	}
	public DescSaxHandler(int maxNumOfWordPerEntry, String dtdPath, String xmlPath) {
		this.maxNumOfWordPerEntry = maxNumOfWordPerEntry;
		this.dtdPath = dtdPath;
		this.xmlPath = xmlPath;
	}
	
	public void read() throws Exception {
		
		SAXParserFactory factory = SAXParserFactory.newInstance();  
        SAXParser parser = factory.newSAXParser();  
        XMLReader reader = parser.getXMLReader();
        reader.setEntityResolver(new DTDEntityResolver(dtdPath));  
        reader.setContentHandler(this);  
        reader.parse(new InputSource(xmlPath)); 

	}
	
	// ignore case
	public boolean matchWholeEntry(String s) {
		return dict.containsValue(s.toLowerCase());
	}
	// ignore case, match at least one whole word of a entry
	public boolean matchPartialEntry(String s) {
		String temp = s.toLowerCase();
		for(String key:dict.keySet()) {
			String[] words = dict.get(key).split(" ++");
			for(String word:words)
				if(word.equals(temp))
					return true;
		}
		return false;
	}
	
	@Override
	public void startDocument() throws SAXException {
		dict = new HashMap<String, String>();
	}
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		if(qName.equals("DescriptorRecord")) {
			current = new UiNamePair();
		}	
		lastElement = qName;
	}
	
	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		if(lastElement.equals("DescriptorUI"))
			current.ui = new String(ch, start, length);
		else if (lastElement.equals("String"))
			current.name = new String(ch, start, length);

	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if(qName.equals("DescriptorRecord") &&  current.name.split(" ++").length<=maxNumOfWordPerEntry) {
			/*current.name = current.name.replaceAll("-", " ");
			current.name = current.name.replaceAll(",", "");*/
			dict.put(current.ui, current.name.trim().toLowerCase());
		}
		lastElement = "";
	}
	
	@Override
	public void endDocument() throws SAXException {
		
	}
}
