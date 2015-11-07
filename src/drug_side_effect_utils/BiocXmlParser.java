package drug_side_effect_utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import cn.fox.utils.DTDEntityResolver;

public class BiocXmlParser {
	public enum ParseOption {
		BOTH, ONLY_DISEASE, ONLY_CHEMICAL
	}
	public static HashMap<Character, Integer> xmlDefaultChar;
	static {
		xmlDefaultChar = new HashMap<Character, Integer>();
		xmlDefaultChar.put('&', 5); // &amp;
		xmlDefaultChar.put('<', 4); // &lt;
		xmlDefaultChar.put('>', 4); // &gt;
		xmlDefaultChar.put('"', 6); // &quot;
		xmlDefaultChar.put('\'', 6); // &apos;
	}
	DocumentBuilderFactory dbf ; 
	DocumentBuilder db ;
	ParseOption option;
	
	public BiocXmlParser(String dtdPath, ParseOption option) {
		super();
		try {
			dbf = DocumentBuilderFactory.newInstance();
			db  = dbf.newDocumentBuilder();
			db.setEntityResolver(new DTDEntityResolver(dtdPath));
			this.option = option;
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
				ArrayList<Entity> compositeEntities = new ArrayList<>();
ANNO:			for(int j=0;j<annotations.getLength();j++) {
					Element annotation = (Element)annotations.item(j);
					String strAnnotationId = annotation.getAttribute("id");
					NodeList infons = annotation.getElementsByTagName("infon");
					for(int k=0;k<infons.getLength();k++) {
						Element infon = (Element)infons.item(k);
						if(infon.getAttribute("key").equals("type")) {
							// entity
							String strAnnotationType = infon.getFirstChild().getNodeValue();
							if(strAnnotationType.equals("Disease") && option == ParseOption.ONLY_CHEMICAL)
								continue ANNO;
							else if(strAnnotationType.equals("Chemical") && option == ParseOption.ONLY_DISEASE)
								continue ANNO;
							
							String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
							
							String strAnnotationMesh = infons.item(infons.getLength()-1).getFirstChild().getNodeValue();
							if(strAnnotationMesh.indexOf("|")!=-1) { // composite mention or multi-id mention
								if(infons.getLength()>=2 && infons.item(1).getFirstChild().getNodeValue().equals("CompositeMention"))
								{
									// composite mention
									// we ignore it but remember how many children it has
									String[] meshes = strAnnotationMesh.split("\\|");
									compositeCount = meshes.length;
									compositeEntities.clear();
								} else {
									// multi-id mention
									// we use the last id of it
									String[] meshes = strAnnotationMesh.split("\\|");
									Element location = (Element)annotation.getElementsByTagName("location").item(0);
									int offset = Integer.parseInt(location.getAttribute("offset"));
									int len = Integer.parseInt(location.getAttribute("length"));
									//String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
									
									//len = len-getPayback(strAnnotationText);
									String temp = originalText.substring(offset, offset+len);
									
									Entity entity = new Entity(strAnnotationId, strAnnotationType, offset, temp, meshes[meshes.length-1]);
									biocDocument.entities.add(entity);
								}
								
																
							}
							else if(compositeCount > 1) {  // composite child
								NodeList locations = annotation.getElementsByTagName("location");
								if(locations!=null && locations.getLength()==1) { // if there is only one location
									Element location = (Element)locations.item(0);
									int offset = Integer.parseInt(location.getAttribute("offset"));
									int len = Integer.parseInt(location.getAttribute("length"));
									//String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
									
									//len = len-getPayback(strAnnotationText);
									String temp = originalText.substring(offset, offset+len);
									
									Entity entity = new Entity(strAnnotationId, strAnnotationType, offset, temp, strAnnotationMesh);
									addCompotiteChild(compositeEntities, entity);
								}
								
																
								compositeCount--;
							}
							else if(compositeCount == 1) { // composite child
								
								NodeList locations = annotation.getElementsByTagName("location");
								if(locations!=null && locations.getLength()==1) { // if there is only one location
									Element location = (Element)locations.item(0);
									int offset = Integer.parseInt(location.getAttribute("offset"));
									int len = Integer.parseInt(location.getAttribute("length"));
									//String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
									//len = len-getPayback(strAnnotationText);
									String temp = originalText.substring(offset, offset+len);
									
									Entity entity = new Entity(strAnnotationId, strAnnotationType, offset, temp, strAnnotationMesh);
									addCompotiteChild(compositeEntities, entity);
									
								}
								biocDocument.entities.addAll(compositeEntities);
								compositeEntities.clear();
								compositeCount--;
							} else {
								Element location = (Element)annotation.getElementsByTagName("location").item(0);
								int offset = Integer.parseInt(location.getAttribute("offset"));
								int len = Integer.parseInt(location.getAttribute("length"));
								//String strAnnotationText = ((Element)annotation.getElementsByTagName("text").item(0)).getFirstChild().getNodeValue();
								
								//len = len-getPayback(strAnnotationText);
								String temp = originalText.substring(offset, offset+len);
								
								Entity entity = new Entity(strAnnotationId, strAnnotationType, offset, temp, strAnnotationMesh);
								biocDocument.entities.add(entity);
							}
								
							
						} else if(infon.getAttribute("key").equals("relation")) {
							// relation
							String strAnnotationRelation = infons.item(0).getFirstChild().getNodeValue();
							Element infon1 = (Element)infons.item(1);
							Element infon2 = (Element)infons.item(2);
							String strAnnotationMesh1 = infon1.getFirstChild().getNodeValue();
							String strAnnotationMesh2 = infon2.getFirstChild().getNodeValue();
							Relation relation = new Relation(strAnnotationId, strAnnotationRelation, strAnnotationMesh1, strAnnotationMesh2);
							relation.type1  = infon1.getAttribute("key");
							relation.type2 = infon2.getAttribute("key");
							//biocDocument.relations.put(relation.id, relation);
							
							biocDocument.relations.add(relation);
							
						}
					}
				}
				
				// for the corpus released on July 21st, 2015
				NodeList relations = document.getElementsByTagName("relation");
				for(int j=0;j<relations.getLength();j++) {
					Element relation = (Element)relations.item(j);
					String strAnnotationId = relation.getAttribute("id");
					NodeList infons = relation.getElementsByTagName("infon");
					
					String strAnnotationRelation = infons.item(0).getFirstChild().getNodeValue();
					Element infon1 = (Element)infons.item(1);
					Element infon2 = (Element)infons.item(2);
					String strAnnotationMesh1 = infon1.getFirstChild().getNodeValue();
					String strAnnotationMesh2 = infon2.getFirstChild().getNodeValue();
					Relation r = new Relation(strAnnotationId, strAnnotationRelation, strAnnotationMesh1, strAnnotationMesh2);
					r.type1  = infon1.getAttribute("key");
					r.type2 = infon2.getAttribute("key");
					
					biocDocument.relations.add(r);
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
	
	
	// add the child entity whose range is smaller than any other child 
	public void addCompotiteChild(ArrayList<Entity> compositeEntities, Entity entity) {
		Entity toBeDeleted = null;
		boolean overlapped = false;
		for(Entity child:compositeEntities) {
			if((child.offset+child.text.length()>=entity.offset && child.offset<=entity.offset+entity.text.length()) 
				|| (child.offset+child.text.length()==entity.offset+entity.text.length() && child.offset==entity.offset)) {
				if(child.offset<=entity.offset && child.offset+child.text.length()>=entity.offset+entity.text.length()) {
					toBeDeleted = child;
				} else if(child.offset>=entity.offset && child.offset+child.text.length()<=entity.offset+entity.text.length()) {
					
				} else {
					// partial overlapped
					//System.out.println("\""+entity+"\" partial overlapped with \""+child+"\"");
				}
				overlapped = true;
			} 

		}
		if(overlapped) {
			if(toBeDeleted!=null) {
				compositeEntities.remove(toBeDeleted);
				compositeEntities.add(entity);
			}
		}
		else {
			compositeEntities.add(entity);
		}
	}
}
