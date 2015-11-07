package svm;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Properties;

import cn.fox.biomedical.Sider;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.stanford.Tokenizer;
import cn.fox.utils.ObjectSerializer;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.CTDSaxParse;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.MEDI;
import drug_side_effect_utils.Relation;
import drug_side_effect_utils.Tool;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.PropertiesUtils;

public class SVMeval {

	public static void main(String[] args) throws Exception  {
		FileInputStream fis = new FileInputStream(args[0]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		BiocXmlParser xmlParser = new BiocXmlParser(PropertiesUtils.getString(properties, "bioc_dtd", ""), BiocXmlParser.ParseOption.BOTH);
		ArrayList<BiocDocument> testDocs = xmlParser.parseBiocXmlFile(PropertiesUtils.getString(properties, "bioc_documents_test", ""));
		
		Tool tool = new Tool();
		tool.sentSplit = new SentenceSplitter(new Character[]{';'},false, PropertiesUtils.getString(properties, "common_english_abbr", ""));
		tool.tokenizer = new Tokenizer(true, ' ');	
		tool.tagger = new MaxentTagger(PropertiesUtils.getString(properties, "pos_tagger", ""));
		tool.morphology = new Morphology();
		tool.sider = new Sider(PropertiesUtils.getString(properties, "sider_dict", ""));
		tool.ctdParse = new CTDSaxParse(PropertiesUtils.getString(properties, "ctd_chemical_disease", ""));
		tool.medi = new MEDI();
		tool.medi.load(PropertiesUtils.getString(properties, "medi_dict", ""));
		
		boolean postProcess = PropertiesUtils.getBool(properties, "post_process");
		
		DocumentSVM documentSVM = (DocumentSVM)ObjectSerializer.readObjectFromFile(PropertiesUtils.getString(properties, "document_svm_save_path", ""));
		SentenceSVM sentenceSVM = (SentenceSVM)ObjectSerializer.readObjectFromFile(PropertiesUtils.getString(properties, "sentence_svm_save_path", ""));
		
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(PropertiesUtils.getString(properties, "output_path", "")), "utf-8");
		for(BiocDocument doc:testDocs) {
			HashSet<Relation> results = new HashSet<>();
	    	doc.fillCoreChemical();
	    	documentSVM.predict(doc, tool, results, null);
	    	sentenceSVM.predict(doc, tool, results, null);
	    	
	    	if(postProcess && results.size()==0) {
	    		for(int i=0;i<doc.entities.size();i++) {
					Entity entity1 = doc.entities.get(i);
					for(int j=0;j<i;j++) {
						Entity entity2 = doc.entities.get(j);
						
						if(entity1.type.equals("Chemical") && entity2.type.equals("Disease")) {
							if(doc.meshOfCoreChemical.contains(entity1.mesh)) {
								results.add(new Relation(null, "CID", entity1.mesh, entity2.mesh));
			    			}
						} else if(entity1.type.equals("Disease") && entity2.type.equals("Chemical")) {
							if(doc.meshOfCoreChemical.contains(entity2.mesh)) {
								results.add(new Relation(null, "CID", entity2.mesh, entity1.mesh));
			    			}
						}
					}
				}
	    	}
	    	
	    	for(Relation temp:results)
		    	osw.write(doc.id+"\tCID\t"+temp.mesh1+"\t"+temp.mesh2+"\n");
	    }
	    osw.close();

	}

}
