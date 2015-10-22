package drug_side_effect_utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashSet;

import javax.sql.rowset.spi.XmlReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import cn.fox.utils.DTDEntityResolver;
import cn.fox.utils.IgnoreDTDEntityResolver;
import cn.fox.utils.ObjectSerializer;

public class DictHandler {

	public static void main(String[] args) throws Exception{
		/*stripHumanDO();
		stripDrugBank();*/
		//distributeEntryLengthAndEntryWordNumber("D:\\dict\\drugbank.strip");
		//distributeEntryLengthAndEntryWordNumber("D:\\dict\\HumanDO.strip");
		//SaxParseDesc.begin();
		//SaxParseSupp.begin();
		//buildDictDrugBank(true);
		//buildDictHumanDO(true);
		//buildJochem();
		//buildCTDchem();
		buildCTDmedic();
	}
	
	public static void buildCTDmedic() throws Exception {
		DocumentBuilderFactory dbf ; 
		DocumentBuilder db ;
		Document d;
		dbf = DocumentBuilderFactory.newInstance();
		db  = dbf.newDocumentBuilder();
		db.setEntityResolver(new IgnoreDTDEntityResolver());
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream("F:\\biomedical resource\\CTD\\CTD_diseases.dict"), "utf-8"); 
		
		
		d = db.parse("F:\\biomedical resource\\CTD\\CTD_diseases.xml");
		NodeList rows = d.getElementsByTagName("Row"); ;
		for(int i = 0; i < rows.getLength(); i++) {
			Element row = (Element)rows.item(i); 
			NodeList names = row.getElementsByTagName("DiseaseName");
			osw.write(names.item(0).getFirstChild().getNodeValue().trim().toLowerCase()+"\n");
		}
		osw.close();
	}
	
	public static void buildCTDchem() throws Exception {
		DocumentBuilderFactory dbf ; 
		DocumentBuilder db ;
		Document d;
		dbf = DocumentBuilderFactory.newInstance();
		db  = dbf.newDocumentBuilder();
		db.setEntityResolver(new IgnoreDTDEntityResolver());
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream("F:\\biomedical resource\\CTD\\CTD_chemicals.dict"), "utf-8"); 
		
		
		d = db.parse("F:\\biomedical resource\\CTD\\CTD_chemicals.xml");
		NodeList rows = d.getElementsByTagName("Row"); ;
		for(int i = 0; i < rows.getLength(); i++) {
			Element row = (Element)rows.item(i); 
			NodeList names = row.getElementsByTagName("ChemicalName");
			osw.write(names.item(0).getFirstChild().getNodeValue().trim().toLowerCase()+"\n");
		}
		osw.close();
	}
	
	public static void buildJochem() throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("F:\\biomedical resource\\Jochem\\ChemlistV1_2.ontology"), "utf-8"));
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream("F:\\biomedical resource\\Jochem\\Jochem.dict"), "utf-8");
		String thisLine = null;
		while ((thisLine = br.readLine()) != null ) {
			if(thisLine.isEmpty() || thisLine.length()<4)
				continue;
			if(thisLine.substring(0, 3).equals("NA ")) {
				osw.write(thisLine.substring(3, thisLine.length()).trim().toLowerCase()+"\n");
			}
		}
		br.close();
		osw.close();
	}
	
	
	
	public static void buildDictDrugBank(boolean entryBased) throws Exception {
		DocumentBuilderFactory dbf ; 
		DocumentBuilder db ;
		Document d;
		dbf = DocumentBuilderFactory.newInstance();
		db  = dbf.newDocumentBuilder();
		db.setEntityResolver(new DTDEntityResolver("F:\\biomedical resource\\drugbank.xsd"));
		
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream("F:\\biomedical resource\\drugbank.dict"), "utf-8");
		d = db.parse("F:\\biomedical resource\\drugbank.xml");
		NodeList drugBanks = d.getElementsByTagName("drugbank"); 
		NodeList drugNodes = ((Element)drugBanks.item(0)).getChildNodes();
		for(int i = 0; i < drugNodes.getLength(); i++) {
			Node node = drugNodes.item(i); 
			if(node.getParentNode().getNodeName().equals("drugbank") && node.getNodeType()==Node.ELEMENT_NODE) {
				Element drugNode = (Element)node;
				NodeList nameNodes = drugNode.getElementsByTagName("name");
				String name = nameNodes.item(0).getFirstChild().getNodeValue();
				if(!name.isEmpty())
					osw.write(name.trim().toLowerCase()+"\n");
			}
		}
		
		
		/*if(thisLine.length()<=50 && thisLine.trim().split(" ++").length<=3) {
			if(entryBased)
				dict.add(thisLine.trim().toLowerCase());
			else { // token based
				String[] words = thisLine.trim().split(" ++");
				for(String word : words) {
					if(word.length()>3) // filter word like "a" "of" "the"
						dict.add(word.toLowerCase()); // filter overlapped word
				}
			}
				
		}*/

		osw.close();
	}
	
	public static void buildDictHumanDO(boolean entryBased) throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("F:\\biomedical resource\\HumanDO.obo"), "utf-8"));
		String thisLine = null;
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream("F:\\biomedical resource\\HumanDO.dict"), "utf-8"); 
		while ((thisLine = br.readLine()) != null) {
			if(thisLine.indexOf("[Term]") != -1) {
				br.readLine();
				String nameLine = br.readLine();
				int pos = nameLine.indexOf("name:");
				if(pos==-1)
					throw new Exception();
				int begin = pos+"name:".length()+1;
				String name = nameLine.substring(begin);
				if(!name.isEmpty())
					osw.write(name.trim().toLowerCase()+"\n");
			}
		}
		br.close();
		
		/*while ((thisLine = br.readLine()) != null) {
			if(thisLine.trim().isEmpty()) continue;
			if(thisLine.length()<=50 && thisLine.trim().split(" ++").length<=6) {
				if(entryBased)
					dict.add(thisLine.trim().toLowerCase());
				else { // token based
					String[] words = thisLine.trim().split(" ++");
					for(String word : words) {
						if(word.length()>3) // filter word like "a" "of" "the"
							dict.add(word.toLowerCase()); // filter overlapped word
					}
				}
			}
		}*/

		osw.close();
	}
	
	public static void distributeEntryLengthAndEntryWordNumber(String dictPath) throws Exception {

		int[] entryLength = new int[6];

		int[] entryWordNumber = new int[6];
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dictPath), "utf-8"));
		String thisLine = null;
		while ((thisLine = br.readLine()) != null) {
			int len = thisLine.length();
			if(len<=10) entryLength[0]++;
			else if(len<=20) entryLength[1]++;
			else if(len<=30) entryLength[2]++;
			else if(len<=40) entryLength[3]++;
			else if(len<=50) entryLength[4]++;
			else	entryLength[5]++;
			
			int wNum = thisLine.split(" ").length;
			if(wNum<=1) entryWordNumber[0]++;
			else if(wNum<=2) entryWordNumber[1]++;
			else if(wNum<=3) entryWordNumber[2]++;
			else if(wNum<=4) entryWordNumber[3]++;
			else if(wNum<=5) entryWordNumber[4]++;
			else	entryWordNumber[5]++;
		}
		br.close();
		
		int sum = 0;
		for(int count:entryLength)
			sum+=count;
		String s = "len dist: ";
		for(int count:entryLength)
			s+=count*1.0/sum+" ";
		System.out.println(s);
		
		sum = 0;
		for(int count:entryWordNumber)
			sum+=count;
		s = "wNum dist: ";
		for(int count:entryWordNumber)
			s+=count*1.0/sum+" ";
		System.out.println(s);
	}
	
	public static void stripHumanDO() throws Exception {
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream("D:\\dict\\HumanDO.obo"), "utf-8"));
		String thisLine = null;
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream("D:\\dict\\HumanDO.strip"), "utf-8"); 
		while ((thisLine = br.readLine()) != null) {
			if(thisLine.indexOf("[Term]") != -1) {
				br.readLine();
				String nameLine = br.readLine();
				int pos = nameLine.indexOf("name:");
				if(pos==-1)
					throw new Exception();
				int begin = pos+"name:".length()+1;
				String name = nameLine.substring(begin);
				osw.write(name+"\n");
			}
		}
		br.close();
		osw.close();
	}
	
	public static void stripDrugBank() throws Exception{
		
		DocumentBuilderFactory dbf ; 
		DocumentBuilder db ;
		Document d;
		dbf = DocumentBuilderFactory.newInstance();
		db  = dbf.newDocumentBuilder();
		db.setEntityResolver(new DTDEntityResolver("D:\\dict\\drugbank.xsd"));
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream("D:\\dict\\drugbank.strip"), "utf-8"); 
		
		
		d = db.parse("D:\\dict\\drugbank.xml");
		NodeList drugBanks = d.getElementsByTagName("drugbank"); 
		//NodeList drugNodes = ((Element)drugBanks.item(0)).getElementsByTagName("drug"); 
		//NodeList drugNodes = d.getElementsByTagName("drug"); 
		NodeList drugNodes = ((Element)drugBanks.item(0)).getChildNodes();
		for(int i = 0; i < drugNodes.getLength(); i++) {
			Node node = drugNodes.item(i); 
			if(node.getParentNode().getNodeName().equals("drugbank") && node.getNodeType()==Node.ELEMENT_NODE) {
				Element drugNode = (Element)node;
				NodeList nameNodes = drugNode.getElementsByTagName("name");
				String name = nameNodes.item(0).getFirstChild().getNodeValue();
				osw.write(name+"\n");
			}
		}
		osw.close();
		
	}

}

class SaxParseDesc extends DefaultHandler {
	
	public TransformerHandler handler;
	public int status = 0;
	
	public static void begin() throws Exception{
		// prepare the input xml
		SaxParseDesc service = new SaxParseDesc();  
		SAXParserFactory factory = SAXParserFactory.newInstance();  
        SAXParser parser = factory.newSAXParser();  
        XMLReader reader = parser.getXMLReader();
        reader.setEntityResolver(new DTDEntityResolver("F:\\biomedical corpus\\MESH\\desc2015.dtd"));  
        reader.setContentHandler(service);  
        
        // prepare the output xml
        SAXTransformerFactory fac = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        service.handler = fac.newTransformerHandler();
        Transformer transformer = service.handler.getTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");//
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");// 是否自动添加额外的空白
        OutputStream outStream = new FileOutputStream("F:\\biomedical corpus\\MESH\\desc2015.dict.xml");
        Result resultxml = new StreamResult(outStream);
        service.handler.setResult(resultxml);
        // begin parsing
        reader.parse(new InputSource("F:\\biomedical corpus\\MESH\\desc2015.xml")); 
        
        outStream.close();
	}
	
	@Override
	public void startDocument() throws SAXException {
		handler.startDocument();
        
	}
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		if(qName.equals("DescriptorRecordSet")) {
			handler.startElement("", "", qName, attributes);
		}	else if(qName.equals("DescriptorRecord")) {
			handler.startElement("", "", qName, attributes);
			status = 1;
		}	else if(qName.equals("DescriptorUI") && status == 1) {
			handler.startElement("", "", qName, attributes);
			status = 2;
		}	else if(qName.equals("DescriptorName") && status == 2) {
			handler.startElement("", "", qName, attributes);
		}	else if(qName.equals("String") && status == 2) {
			handler.startElement("", "", qName, attributes);
		}	
			
		
	}
	
	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		
        if(status==2){  
        	handler.characters(ch, start, length);
        }
		
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if(qName.equals("DescriptorRecordSet")) {
			handler.endElement("", "", qName);
		}	else if(qName.equals("DescriptorRecord")) {
			handler.endElement("", "", qName);
		}	else if(qName.equals("DescriptorUI") && status==2) {
			handler.endElement("", "", qName);
		}	else if(qName.equals("DescriptorName") && status == 2) {
			handler.endElement("", "", qName);
			status = 0;
		}	else if(qName.equals("String") && status==2) {
			handler.endElement("", "", qName);
		}	
		
	}
	
	@Override
	public void endDocument() throws SAXException {
		handler.endDocument();
	}
}

class SaxParseSupp extends DefaultHandler {
	
	public TransformerHandler handler;
	public int status = 0;
	
	public static void begin() throws Exception{
		// prepare the input xml
		SaxParseSupp service = new SaxParseSupp();  
		SAXParserFactory factory = SAXParserFactory.newInstance();  
        SAXParser parser = factory.newSAXParser();  
        XMLReader reader = parser.getXMLReader();
        reader.setEntityResolver(new DTDEntityResolver("F:\\biomedical corpus\\MESH\\supp2015.dtd"));  
        reader.setContentHandler(service);  
        
        // prepare the output xml
        SAXTransformerFactory fac = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        service.handler = fac.newTransformerHandler();
        Transformer transformer = service.handler.getTransformer();
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");//
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");// 是否自动添加额外的空白
        OutputStream outStream = new FileOutputStream("F:\\biomedical corpus\\MESH\\supp2015.dict.xml");
        Result resultxml = new StreamResult(outStream);
        service.handler.setResult(resultxml);
        // begin parsing
        reader.parse(new InputSource("F:\\biomedical corpus\\MESH\\supp2015.xml")); 
        
        outStream.close();
	}
	
	@Override
	public void startDocument() throws SAXException {
		handler.startDocument();
        
	}
	
	@Override
	public void startElement(String uri, String localName, String qName,
			Attributes attributes) throws SAXException {
		if(qName.equals("SupplementalRecordSet")) {
			handler.startElement("", "", qName, attributes);
		}	else if(qName.equals("SupplementalRecord")) {
			handler.startElement("", "", qName, attributes);
			status = 1;
		}	else if(qName.equals("SupplementalRecordUI") && status == 1) {
			handler.startElement("", "", qName, attributes);
			status = 2;
		}	else if(qName.equals("SupplementalRecordName") && status == 2) {
			handler.startElement("", "", qName, attributes);
		}	else if(qName.equals("String") && status == 2) {
			handler.startElement("", "", qName, attributes);
		}	
			
		
	}
	
	@Override
	public void characters(char[] ch, int start, int length)
			throws SAXException {
		
        if(status==2){  
        	handler.characters(ch, start, length);
        }
		
	}
	
	@Override
	public void endElement(String uri, String localName, String qName)
			throws SAXException {
		if(qName.equals("SupplementalRecordSet")) {
			handler.endElement("", "", qName);
		}	else if(qName.equals("SupplementalRecord")) {
			handler.endElement("", "", qName);
		}	else if(qName.equals("SupplementalRecordUI") && status==2) {
			handler.endElement("", "", qName);
		}	else if(qName.equals("SupplementalRecordName") && status == 2) {
			handler.endElement("", "", qName);
			status = 0;
		}	else if(qName.equals("String") && status==2) {
			handler.endElement("", "", qName);
		}	
		
	}
	
	@Override
	public void endDocument() throws SAXException {
		handler.endDocument();
	}
}

