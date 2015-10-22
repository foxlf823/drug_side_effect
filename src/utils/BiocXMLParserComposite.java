
package utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import cn.fox.utils.DTDEntityResolver;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.Relation;

public class BiocXMLParserComposite {
	public static HashMap<Character, Integer> xmlDefaultChar;
	static {
		xmlDefaultChar = new HashMap<Character, Integer>();
		xmlDefaultChar.put('&', 5); // &amp;
		xmlDefaultChar.put('<', 4); // &lt;
		xmlDefaultChar.put('>', 4); // &gt;
		xmlDefaultChar.put('"', 6); // &quot; 
		xmlDefaultChar.put('\'', 6); // &apos;//如xml文件中出现Crohn&apos;s表示Crohn's，&apos;长度为6，'长度为1
	}
	DocumentBuilderFactory dbf ; 
	DocumentBuilder db ;
	
	public BiocXMLParserComposite(String dtdPath) {
		super();
		try {
			dbf = DocumentBuilderFactory.newInstance();
			db  = dbf.newDocumentBuilder();
			db.setEntityResolver(new DTDEntityResolver(dtdPath));
			
		} catch(Exception e) {
			e.printStackTrace();
		}
	}
	
	/* 
	 * Get the entities and relations of a bioc xml file.
	 * The bioc xml file may have multi-documents in it. 
	 */
	public ArrayList<BiocDocument> parseBiocXmlFile(String filePath) {
		ArrayList<BiocDocument> biocDocuments = new ArrayList<BiocDocument>();
		try {
			Document d = db.parse(filePath);
			
			NodeList documents = d.getElementsByTagName("document"); 
			for(int i = 0; i < documents.getLength(); i++) {
				Element document = (Element)documents.item(i); // document
				// id
				NodeList ids = document.getElementsByTagName("id");
				Element id = (Element)ids.item(0);
				String strId = id.getFirstChild().getNodeValue(); 
				// passage
				NodeList passages = document.getElementsByTagName("passage");
				Element title = (Element)passages.item(0);
				NodeList titleTexts = title.getElementsByTagName("text");
				String strTitle = titleTexts.item(0).getFirstChild().getNodeValue();
				//strTitle = strTitle.replaceAll("-", " ");
				Element abstracts = (Element)passages.item(1);
				NodeList abstractTexts = abstracts.getElementsByTagName("text");
				String strAbstract = abstractTexts.item(0).getFirstChild().getNodeValue();
				//strAbstract = strAbstract.replaceAll("-", " ");
				
				BiocDocument biocDocument = new BiocDocument(strId, strTitle, strAbstract);
				String originalText = strTitle+" "+strAbstract;
				// annotation
				NodeList annotations = document.getElementsByTagName("annotation");
				int compositeCount = 0; 
		ANNO:	for(int j=0;j<annotations.getLength();j++) {
					Element annotation = (Element)annotations.item(j);
					String strAnnotationId = annotation.getAttribute("id");
					NodeList infons = annotation.getElementsByTagName("infon");
					int infonsLength = infons.getLength();
					for(int k=0;k < infons.getLength();k++) {
						Element infon = (Element)infons.item(k);
						if(infon.getAttribute("key").equals("type")) {
							// entity
							String strAnnotationType = infon.getFirstChild().getNodeValue();
							if(strAnnotationType.equals("Chemical"))
								continue ANNO;
//							 
							String strAnnotationMesh = infons.item(infons.getLength()-1).getFirstChild().getNodeValue();
							if(strAnnotationMesh.indexOf("|")!=-1) { // composite mention
								String[] meshes = strAnnotationMesh.split("\\|");
								//System.out.println("Get a composite mention at "+strId);
								
								compositeCount = meshes.length;
								// we ignore the composite mention
								Element location = (Element)annotation.getElementsByTagName("location").item(0);
								int offset = Integer.parseInt(location.getAttribute("offset"));
								int len = Integer.parseInt(location.getAttribute("length"));
								String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
								len = len-getPayback(strAnnotationText);
								strAnnotationText = originalText.substring(offset, offset+len);
								Entity entity = new Entity(strId, strAnnotationType, offset, strAnnotationText, strAnnotationMesh);
								biocDocument.entities.add(entity);
							}
							else if(compositeCount > 1) {
								// get the first location
								/*Element location = (Element)annotation.getElementsByTagName("location").item(0);
								if(location != null) {
									int offset = Integer.parseInt(location.getAttribute("offset"));
									int len = Integer.parseInt(location.getAttribute("length"));
									String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
									len = len-getPayback(strAnnotationText);
									strAnnotationText = originalText.substring(offset, offset+len);
									Entity entity = new Entity(strAnnotationId, strAnnotationType, offset, strAnnotationText, strAnnotationMesh);
									biocDocument.entities.add(entity);
								}*/
								
								compositeCount--;
							}
							else if(compositeCount == 1) {
								// get the last location
								/*NodeList locations = annotation.getElementsByTagName("location");
								if(locations!=null && locations.getLength()!=0) {
									Element location = (Element)locations.item(locations.getLength()-1);
									int offset = Integer.parseInt(location.getAttribute("offset"));
									int len = Integer.parseInt(location.getAttribute("length"));
									String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
									len = len-getPayback(strAnnotationText);
									strAnnotationText = originalText.substring(offset, offset+len);
									Entity entity = new Entity(strAnnotationId, strAnnotationType, offset, strAnnotationText, strAnnotationMesh);
									biocDocument.entities.add(entity);
								}*/
								
								compositeCount--;
							} else {
								Element location = (Element)annotation.getElementsByTagName("location").item(0);
								int offset = Integer.parseInt(location.getAttribute("offset"));
								int len = Integer.parseInt(location.getAttribute("length"));
								String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
								len = len-getPayback(strAnnotationText);
								strAnnotationText = originalText.substring(offset, offset+len);
								Entity entity = new Entity(strId, strAnnotationType, offset, strAnnotationText, strAnnotationMesh);
								biocDocument.entities.add(entity);
							}
								
							
						} else if(infon.getAttribute("key").equals("relation")) {
							// relation
							String strAnnotationRelation = infons.item(0).getFirstChild().getNodeValue();
							String strAnnotationMesh1 = infons.item(1).getFirstChild().getNodeValue();
							String strAnnotationMesh2 = infons.item(2).getFirstChild().getNodeValue();
							Relation relation = new Relation(strAnnotationId, strAnnotationRelation, strAnnotationMesh1, strAnnotationMesh2);
							//biocDocument.relations.put(relation.id, relation);
							biocDocument.relations.add(relation);
						}
					}
				}
				
				biocDocuments.add(biocDocument);
			}
		
		} catch(Exception e) {
			e.printStackTrace();
		}
		return biocDocuments;
	}
	
	// Judge whether has xml default char, and how many offset should be paid back.
	public int getPayback(String s) {
		char[] chs = s.toCharArray();
		int payback = 0;
		for(char ch:chs) {
			Iterator<Character> it = xmlDefaultChar.keySet().iterator();
			while(it.hasNext()) {
				Character def = it.next();
				if(def==ch) {
					payback += xmlDefaultChar.get(def)-1;
					break;
				}
			}
		}
		return payback;
		
	}
}
