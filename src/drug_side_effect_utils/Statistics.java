package drug_side_effect_utils;

import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Result;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import cn.fox.nlp.SentenceSplitter;
import cn.fox.utils.DTDEntityResolver;

public class Statistics {

	public static void main(String[] args) throws Exception{
		/*searchMaxLenOfEntity("Disease");
		searchMaxLenOfEntity("Chemical");*/
		/*searchLenDistributionOfEntity("Disease");
		searchLenDistributionOfEntity("Chemical");*/
		//countRelationEntityType();
		//checkMesh();
		
	}
	
	public static void checkMesh() throws Exception {
		/*HashMap<String, String> dictDesc = DescSaxHandler.read();
		HashMap<String, String> dictSupp = SuppSaxHandler.read();
		BiocXmlParser xmlParser = new BiocXmlParser("D:\\biocreative\\2015\\CDR_Training\\bioc\\BioC.dtd");
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D:\\biocreative\\2015\\CDR_Training\\CDR_TrainingSet.xml");
		for(BiocDocument document:documents) {
			for(Entity entity: document.entities) {
				String valueDesc = dictDesc.get(entity.mesh);
				String valueSupp = dictSupp.get(entity.mesh);
				if( valueDesc== null &&  valueSupp== null)
					System.out.println("mesh not in dict: "+entity.mesh+" "+entity.text);
				else if(valueDesc!=null && !valueDesc.equalsIgnoreCase(entity.text)) {
					System.out.println("name not equal: "+entity.mesh+" entity: "+entity.text+" dict: "+valueDesc);
				} else if(valueSupp!=null && !valueSupp.equalsIgnoreCase(entity.text)) {
					System.out.println("name not equal: "+entity.mesh+" entity: "+entity.text+" dict: "+valueSupp);
				}
			}
		}*/
	}
	
	public static void searchMaxLenOfEntity(String entityType) {
		BiocXmlParser xmlParser = new BiocXmlParser("D:\\biocreative\\2015\\cdr_sample\\BioC.dtd", BiocXmlParser.ParseOption.BOTH);
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D:\\biocreative\\2015\\cdr_sample\\CDR_sample.xml");
		int max = 0;
		for(BiocDocument document:documents) {
			for(Entity temp:document.entities) {
			
				String tempText = temp.text.replaceAll("-", " ");
				int tempLen = tempText.split(" ").length;
				if(temp.type.equals(entityType) && tempLen>max)
					max = tempLen;
			}
		}
		System.out.println("the max length of "+entityType+" is "+max);
	}
	
	public static void searchLenDistributionOfEntity(String entityType) {
		int[] len = new int[10];
		BiocXmlParser xmlParser = new BiocXmlParser("D:\\biocreative\\2015\\cdr_sample\\BioC.dtd", BiocXmlParser.ParseOption.BOTH);
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D:\\biocreative\\2015\\cdr_sample\\CDR_sample.xml");
		for(BiocDocument document:documents) {
			for(Entity temp:document.entities) {
				String tempText = temp.text.replaceAll("-", " ");
				int tempLen = tempText.split(" ").length;
				if(temp.type.equals(entityType))
					len[tempLen]++;
			}
		}
		System.out.println(entityType);
		for(int i:len)
			System.out.println(i);
	}
	
	/*public static void countRelationEntityType() {
		BiocXmlParser xmlParser = new BiocXmlParser("D:\\biocreative\\2015\\cdr_sample\\BioC.dtd");
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D:\\biocreative\\2015\\cdr_sample\\CDR_sample.xml");
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'}, false, "D:\\dict\\common_english_abbr.txt");
		Tool tool = new Tool();
		tool.sentSplit = sentSplit;
		for(BiocDocument document:documents) {
			List<RelationEntity> goldRelations = RelationPredict.generateGoldRelationEntity(document, tool);
			for(RelationEntity relation:goldRelations)
				if( (relation.entity1.type.equals("Disease") && relation.entity2.type.equals("Chemical")) ||
						(relation.entity1.type.equals("Chemical") && relation.entity2.type.equals("Disease")) )
					;
				else
					System.out.println(relation);
		}
	}*/

}



