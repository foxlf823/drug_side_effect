package svm;

import java.io.FileInputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import cn.fox.biomedical.Sider;
import cn.fox.nlp.Sentence;
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
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.util.PropertiesUtils;
import gnu.trove.TIntDoubleHashMap;
import gnu.trove.TIntDoubleIterator;
import gnu.trove.TObjectIntHashMap;
import jnisvmlight.LabeledFeatureVector;
import jnisvmlight.SVMLightInterface;
import jnisvmlight.SVMLightModel;
import jnisvmlight.TrainingParameters;

public class DocumentSVM implements Serializable {

	private static final long serialVersionUID = -884172327489435715L;
	private TObjectIntHashMap<String> alphabet;
	public boolean isTrain;
	public SVMLightModel model;
	
	public static HashSet<String> triggerOne = new HashSet<String>(Arrays.asList("induce","associate","after","during", "follow", "relate", 
			"cause", "develop", "due", "produce"));
	
	public DocumentSVM() {
		alphabet = new TObjectIntHashMap<>();
	}
	
	public int getFeatureDimension(String featureName) {
		if(isTrain) {
			if(alphabet.contains(featureName)) {
				return alphabet.get(featureName);
			} else {
				alphabet.put(featureName, alphabet.size()+1);
				return alphabet.get(featureName);
			}
		} else {
			if(alphabet.contains(featureName)) {
				return alphabet.get(featureName);
			} else {
				return 0;
			}
		}
	}
	
	public static void main(String[] args) throws Exception {
		FileInputStream fis = new FileInputStream(args[0]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();

		BiocXmlParser xmlParser = new BiocXmlParser(PropertiesUtils.getString(properties, "bioc_dtd", ""), BiocXmlParser.ParseOption.BOTH);
		ArrayList<BiocDocument> trainDocs = xmlParser.parseBiocXmlFile(PropertiesUtils.getString(properties, "bioc_documents", ""));	
		
		
		Tool tool = new Tool();
		tool.sentSplit = new SentenceSplitter(new Character[]{';'},false, PropertiesUtils.getString(properties, "common_english_abbr", ""));
		tool.tokenizer = new Tokenizer(true, ' ');	
		tool.tagger = new MaxentTagger(PropertiesUtils.getString(properties, "pos_tagger", ""));
		tool.morphology = new Morphology();
		tool.sider = new Sider(PropertiesUtils.getString(properties, "sider_dict", ""));
		tool.ctdParse = new CTDSaxParse(PropertiesUtils.getString(properties, "ctd_chemical_disease", ""));
		tool.medi = new MEDI();
		tool.medi.load(PropertiesUtils.getString(properties, "medi_dict", ""));
		
		
		DocumentSVM documentSVM = new DocumentSVM();
		documentSVM.isTrain = true;
		
		List<LabeledFeatureVector> listTrain = new ArrayList<>();
		for(BiocDocument doc:trainDocs) {
			doc.fillCoreChemical();
			List<LabeledFeatureVector> temp = preprocess(doc, tool, documentSVM);
			listTrain.addAll(temp);
		}
		LabeledFeatureVector[] traindata = new LabeledFeatureVector[listTrain.size()];
		traindata = listTrain.toArray(traindata);
		int positive = 0;
		for(int i=0;i<traindata.length;i++) {
			if(traindata[i].getLabel()==1.0)
				positive++;
		}
		System.out.println("positive percentage: "+positive*1.0/traindata.length);

		SVMLightInterface trainer = new SVMLightInterface();
		TrainingParameters tp = new TrainingParameters();
	    tp.getLearningParameters().verbosity = 1;

	    documentSVM.model = trainer.trainModel(traindata, tp);
	    documentSVM.isTrain = false;
	    ObjectSerializer.writeObjectToFile(documentSVM, PropertiesUtils.getString(properties, "document_svm_save_path", ""));
	    // evaluation is in the SVMeval
	    
	    
	}
	
	public void predict(BiocDocument document, Tool tool, HashSet<Relation> results, OutputStreamWriter osw) throws Exception {
		String content = document.title+" "+document.abstractt;
		int offset = 0;
		List<String> strSentences = tool.sentSplit.splitWithFilters(content);
		List<Sentence> listSentences = new ArrayList<>();
		for(String temp:strSentences) {
			ArrayList<CoreLabel> tokens = tool.tokenizer.tokenize(offset, temp);
			tool.tagger.tagCoreLabels(tokens);
			for(int k=0;k<tokens.size();k++) {
				tool.morphology.stem(tokens.get(k));
			}
			
			Sentence sent = new Sentence();
			sent.offset = offset;
			sent.length = temp.length();
			sent.tokens = tokens;
			listSentences.add(sent);
			
			List<Entity> entities = document.getEntitiesInRange(sent.offset, sent.offset+sent.length);
			for(Entity entity:entities) {
				entity.sentIdx = listSentences.size()-1;
				for(int i=0;i<sent.tokens.size();i++) {
					if(sent.tokens.get(i).beginPosition()==entity.offset)
						entity.start = i;
					if(sent.tokens.get(i).endPosition()==entity.offset+entity.text.length())
						entity.end = i;
				}
			}

			offset += temp.length();
		}
		
		for(int i=0;i<document.entities.size();i++) {
			Entity entity1 = document.entities.get(i);
			for(int j=0;j<i;j++) {
				Entity entity2 = document.entities.get(j);
				
				if(entity1.type.equals("Chemical") && entity2.type.equals("Disease")) {
					
					LabeledFeatureVector example = featureTemplate(document, entity1, entity2, this, tool, listSentences);
					double d = model.classify(example);
				      if (d > 0) {
				    	  if(results!=null)
				    		  results.add(new Relation(null, "CID", entity1.mesh, entity2.mesh));
				    	  else
				    		  osw.write(document.id+"\tCID\t"+entity1.mesh+"\t"+entity2.mesh+"\n");
				      }
					
				} else if(entity1.type.equals("Disease") && entity2.type.equals("Chemical")) {
					
					LabeledFeatureVector example = featureTemplate(document, entity2, entity1, this, tool, listSentences);
					double d = model.classify(example);
				      if (d > 0) {
				    	  if(results!=null)
				    		  results.add(new Relation(null, "CID", entity2.mesh, entity1.mesh));
				    	  else 
				    		  osw.write(document.id+"\tCID\t"+entity2.mesh+"\t"+entity1.mesh+"\n");
				      }
					
				}
			}
			
			
		}
		
		
	}
	
	public static List<LabeledFeatureVector> preprocess(BiocDocument document, Tool tool, DocumentSVM documentSVM) {
		List<LabeledFeatureVector> examples = new ArrayList<>();
		
		String content = document.title+" "+document.abstractt;
		int offset = 0;
		List<String> strSentences = tool.sentSplit.splitWithFilters(content);
		List<Sentence> listSentences = new ArrayList<>();
		for(String temp:strSentences) {
			ArrayList<CoreLabel> tokens = tool.tokenizer.tokenize(offset, temp);
			tool.tagger.tagCoreLabels(tokens);
			for(int k=0;k<tokens.size();k++) {
				tool.morphology.stem(tokens.get(k));
			}
			
			Sentence sent = new Sentence();
			sent.offset = offset;
			sent.length = temp.length();
			sent.tokens = tokens;
			listSentences.add(sent);
			
			List<Entity> entities = document.getEntitiesInRange(sent.offset, sent.offset+sent.length);
			for(Entity entity:entities) {
				entity.sentIdx = listSentences.size()-1;
				for(int i=0;i<sent.tokens.size();i++) {
					if(sent.tokens.get(i).beginPosition()==entity.offset)
						entity.start = i;
					if(sent.tokens.get(i).endPosition()==entity.offset+entity.text.length())
						entity.end = i;
				}
			}

			offset += temp.length();
		}
		
		for(int i=0;i<document.entities.size();i++) {
			Entity entity1 = document.entities.get(i);
			for(int j=0;j<i;j++) {
				Entity entity2 = document.entities.get(j);
				
				if(entity1.type.equals("Chemical") && entity2.type.equals("Disease")) {
					
					LabeledFeatureVector example = featureTemplate(document, entity1, entity2, documentSVM, tool, listSentences);
					examples.add(example);
					
				} else if(entity1.type.equals("Disease") && entity2.type.equals("Chemical")) {
					
					LabeledFeatureVector example = featureTemplate(document, entity2, entity1, documentSVM, tool, listSentences);
					examples.add(example);
					
				}
			}
			
			
		}
		
		return examples;
	}
	
	public static LabeledFeatureVector featureTemplate(BiocDocument document, Entity chemical, Entity disease, DocumentSVM svm, Tool tool, List<Sentence> listSentences) {
		
		TIntDoubleHashMap mapDim2Value = new TIntDoubleHashMap();
		
				
		Entity former = chemical.offset < disease.offset ? chemical:disease;
		Entity latter = chemical.offset < disease.offset ? disease:chemical;
		
		
		if(tool.sider.contains(chemical.text, disease.text)) {
			addFeature(mapDim2Value, svm, "SD1_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1);
		} else {
			addFeature(mapDim2Value, svm, "SD2_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1);
		}
		
		if(tool.ctdParse.containRelation(chemical.text, disease.text)) {
			addFeature(mapDim2Value, svm, "CTD1_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1);
		} else {
			addFeature(mapDim2Value, svm, "CTD2_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1);
		}
		
		if(tool.medi.contains(chemical.text, disease.text)) {
			addFeature(mapDim2Value, svm, "MEDI1_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1);
		} else {
			addFeature(mapDim2Value, svm, "MEDI2_"+chemical.text.toLowerCase()+disease.text.toLowerCase(), 1);
		}
		
		// words in entity
		addFeature(mapDim2Value, svm, "CM_"+chemical.text.toLowerCase(), 1);
		addFeature(mapDim2Value, svm, "DS_"+disease.text.toLowerCase(), 1);
		addFeature(mapDim2Value, svm, "MS1_"+chemical.mesh, 1);
		addFeature(mapDim2Value, svm, "MS2_"+disease.mesh, 1);
		
		if(document.meshOfCoreChemical.contains(chemical.mesh)) {
			addFeature(mapDim2Value, svm, "CC1_"+chemical.mesh, 1);
			addFeature(mapDim2Value, svm, "CC1_"+chemical.text.toLowerCase(), 1);
		} else {
			addFeature(mapDim2Value, svm, "CC2_"+chemical.mesh, 1);
			addFeature(mapDim2Value, svm, "CC2_"+chemical.text.toLowerCase(), 1);
		}
		
		// the number of sentences between the two entities
		//addFeature(mapDim2Value, svm, "SBE_"+(latter.sentIdx-former.sentIdx), 1);
		
		// trigger word
		/*if(former.sentIdx<latter.sentIdx) { // former and latter in different sentences
			for(int i=former.sentIdx;i<=latter.sentIdx;i++) {
				Sentence sent = listSentences.get(i);
				if(i==former.sentIdx) {
					for(int j=former.end+1;j<sent.tokens.size();j++) {
						if(DocumentSVM.triggerOne.contains(sent.tokens.get(j).lemma())) {
							addFeature(mapDim2Value, svm, "TW_"+sent.tokens.get(j).lemma(), 1);
						} 
					}
				} else if(i==latter.sentIdx) {
					for(int j=0;j<latter.start;j++) {
						if(DocumentSVM.triggerOne.contains(sent.tokens.get(j).lemma())) {
							addFeature(mapDim2Value, svm, "TW_"+sent.tokens.get(j).lemma(), 1);
						} 
					}
				} else {
					for(int j=0;j<sent.tokens.size();j++) {
						if(DocumentSVM.triggerOne.contains(sent.tokens.get(j).lemma())) {
							addFeature(mapDim2Value, svm, "TW_"+sent.tokens.get(j).lemma(), 1);
						} 
					}
				}
			}

		} else { // former and latter in the same sentences

			Sentence sent = listSentences.get(former.sentIdx);
			for(int j=former.end+1;j<latter.start;j++) {
				if(DocumentSVM.triggerOne.contains(sent.tokens.get(j).lemma())) {
					addFeature(mapDim2Value, svm, "TW_"+sent.tokens.get(j).lemma(), 1);
				} 
			}
		}*/
		
		int label = -1;
		if(document.twoEntitiesHaveRelation(chemical, disease))
			label = +1;
		
		int[] dims = new int[mapDim2Value.size()];
		double[] values = new double[mapDim2Value.size()];
		TIntDoubleIterator it = mapDim2Value.iterator();
		int count = 0;
		while(it.hasNext()) {
			it.advance();
			dims[count] = it.key();
			values[count] = it.value();
			count++;
		}
		
		
		LabeledFeatureVector featureVector = new LabeledFeatureVector(label, dims, values);
		featureVector.normalizeL2();
		return featureVector;
	}
	
	public static void addFeature(TIntDoubleHashMap mapDim2Value, DocumentSVM svm, String featureName, double featureValue) {
		int dim = svm.getFeatureDimension(featureName);
		if(dim>0)
			mapDim2Value.put(dim, featureValue);
	}
}
