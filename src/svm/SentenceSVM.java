package svm;


import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Serializable;
import java.util.ArrayList;
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

public class SentenceSVM implements Serializable {

	private static final long serialVersionUID = 6224347607742755452L;
	private TObjectIntHashMap<String> alphabet;
	public boolean isTrain;
	public SVMLightModel model;
	
	public SentenceSVM() {
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
		
		Tool tool = new Tool();
		tool.sentSplit = new SentenceSplitter(new Character[]{';'},false, PropertiesUtils.getString(properties, "common_english_abbr", ""));
		tool.tokenizer = new Tokenizer(true, ' ');	
		tool.tagger = new MaxentTagger(PropertiesUtils.getString(properties, "pos_tagger", ""));
		tool.morphology = new Morphology();
		tool.sider = new Sider(PropertiesUtils.getString(properties, "sider_dict", ""));
		tool.ctdParse = new CTDSaxParse(PropertiesUtils.getString(properties, "ctd_chemical_disease", ""));
		tool.medi = new MEDI();
		tool.medi.load(PropertiesUtils.getString(properties, "medi_dict", ""));
		
		BiocXmlParser xmlParser = new BiocXmlParser(PropertiesUtils.getString(properties, "bioc_dtd", ""), BiocXmlParser.ParseOption.BOTH);
		ArrayList<BiocDocument> trainDocs = xmlParser.parseBiocXmlFile(PropertiesUtils.getString(properties, "bioc_documents", ""));	
		
		SentenceSVM sentenceSVM = new SentenceSVM();
		sentenceSVM.isTrain = true;
		
		List<LabeledFeatureVector> listTrain = new ArrayList<>();
		for(BiocDocument doc:trainDocs) {
			doc.fillCoreChemical();
			List<LabeledFeatureVector> temp = preprocess(doc, tool, sentenceSVM);
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

	    sentenceSVM.model = trainer.trainModel(traindata, tp);
	    sentenceSVM.isTrain = false;
	    ObjectSerializer.writeObjectToFile(sentenceSVM, PropertiesUtils.getString(properties, "sentence_svm_save_path", ""));
	    // evaluation is in the SVMeval
	    
	    /*List<LabeledFeatureVector> listTest = new ArrayList<>();
		for(BiocDocument doc:testDocs) {
			List<LabeledFeatureVector> temp = preprocess(doc, tool, sentenceSVM);
			listTest.addAll(temp);
		}
		LabeledFeatureVector[] testdata = new LabeledFeatureVector[listTest.size()];
		testdata = listTest.toArray(testdata);
		int accuracy = 0;
	    for (int i = 0; i < testdata.length; i++) {
	      double d = sentenceSVM.model.classify(testdata[i]);
	      if ((testdata[i].getLabel() < 0 && d < 0)
	          || (testdata[i].getLabel() > 0 && d > 0)) {
	    	  accuracy++;
	      }
	    }
		
	    System.out.println("label accuracy: "+accuracy*1.0 / testdata.length);*/
	    
	    
	}
	
	public void predict(BiocDocument document, Tool tool, HashSet<Relation> results, OutputStreamWriter osw) throws Exception {
		String content = document.title+" "+document.abstractt;
		int offset = 0;
		List<String> strSentences = tool.sentSplit.splitWithFilters(content);
		
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
			
			List<Entity> entities = document.getEntitiesInRange(sent.offset, sent.offset+sent.length);
			for(int i=0;i<entities.size();i++) {
				Entity entity1 = entities.get(i);
				for(int j=0;j<i;j++) {
					Entity entity2 = entities.get(j);
					
					if(entity1.type.equals("Chemical") && entity2.type.equals("Disease")) {
						
						LabeledFeatureVector example = featureTemplate(document, entity1, entity2, sent, this, tool);
						double d = model.classify(example);
					      if (d > 0) { // chemical first, disease last
					    	  if(results!=null)
					    		  results.add(new Relation(null, "CID", entity1.mesh, entity2.mesh));
					    	  else
					    		  osw.write(document.id+"\tCID\t"+entity1.mesh+"\t"+entity2.mesh+"\n");
					      }
						
					} else if(entity1.type.equals("Disease") && entity2.type.equals("Chemical")) {
						
						LabeledFeatureVector example = featureTemplate(document, entity2, entity1, sent, this, tool);
						double d = model.classify(example);
					      if (d > 0) { // chemical first, disease last
					    	  if(results!=null)
					    		  results.add(new Relation(null, "CID", entity2.mesh, entity1.mesh));
					    	  else 
					    		  osw.write(document.id+"\tCID\t"+entity2.mesh+"\t"+entity1.mesh+"\n");
					      }
						
					}
				}
				
				
			}
			
			offset += temp.length();
		}
	}
	
	public static List<LabeledFeatureVector> preprocess(BiocDocument document, Tool tool, SentenceSVM sentenceSVM) {
		List<LabeledFeatureVector> examples = new ArrayList<>();
		
		String content = document.title+" "+document.abstractt;
		int offset = 0;
		List<String> strSentences = tool.sentSplit.splitWithFilters(content);
		
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
			
			List<Entity> entities = document.getEntitiesInRange(sent.offset, sent.offset+sent.length);
			for(int i=0;i<entities.size();i++) {
				Entity entity1 = entities.get(i);
				for(int j=0;j<i;j++) {
					Entity entity2 = entities.get(j);
					
					if(entity1.type.equals("Chemical") && entity2.type.equals("Disease")) {
						
						LabeledFeatureVector example = featureTemplate(document, entity1, entity2, sent, sentenceSVM, tool);
						examples.add(example);
						
					} else if(entity1.type.equals("Disease") && entity2.type.equals("Chemical")) {
						
						LabeledFeatureVector example = featureTemplate(document, entity2, entity1, sent, sentenceSVM, tool);
						examples.add(example);
						
					}
				}
				
				
			}
			
			offset += temp.length();
		}
		
		
		
		return examples;
	}
	
	public static void addFeature(TIntDoubleHashMap mapDim2Value, SentenceSVM svm, String featureName, double featureValue) {
		int dim = svm.getFeatureDimension(featureName);
		if(dim>0)
			mapDim2Value.put(dim, featureValue);
	}
	
	public static LabeledFeatureVector featureTemplate(BiocDocument document, Entity chemical, Entity disease, Sentence sentence, SentenceSVM svm, Tool tool) {
		
		TIntDoubleHashMap mapDim2Value = new TIntDoubleHashMap();
		
				
		Entity former = chemical.offset < disease.offset ? chemical:disease;
		Entity latter = chemical.offset < disease.offset ? disease:chemical;
		
		for(int i=0;i<sentence.tokens.size();i++) {
			CoreLabel token = sentence.tokens.get(i);
			if(isATokenInsideAnEntity(token, former) || isATokenInsideAnEntity(token, latter)) {
				// inside former or latter
			} else {
				Entity other = document.isInsideAGoldEntityAndReturnIt(token.beginPosition(), token.endPosition()-1);
				if(other != null) {
					// inside an entity except former or latter
					addFeature(mapDim2Value, svm, other.type, 1);
				} else {
					if(token.beginPosition()<former.offset) {
						// words before former
						addFeature(mapDim2Value, svm, "WB_"+token.lemma(), 1);
					} else if(token.beginPosition()>=former.offset+former.text.length() &&
							token.endPosition()<=latter.offset) {
						// words inbetween
						addFeature(mapDim2Value, svm, "WIB_"+token.lemma(), 1);
					} else {
						// words after latter
						addFeature(mapDim2Value, svm, "WA_"+token.lemma(), 1);
					}
				}
			}
			
		}
		
		
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
		
		/*if(document.meshOfCoreChemical.contains(chemical.mesh)) {
			addFeature(mapDim2Value, svm, "CC1_"+chemical.mesh, 1);
			addFeature(mapDim2Value, svm, "CC1_"+chemical.text.toLowerCase(), 1);
		} else {
			addFeature(mapDim2Value, svm, "CC2_"+chemical.mesh, 1);
			addFeature(mapDim2Value, svm, "CC2_"+chemical.text.toLowerCase(), 1);
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

	/*public static Sentence isTwoEntitiesInTheSameSentence(Entity entity1, Entity entity2, List<Sentence> sentences) {
		for(Sentence sentence:sentences) {
			if(entity1.offset >= sentence.offset && entity1.offset+entity1.text.length() <= sentence.offset+sentence.length
					&& entity2.offset >= sentence.offset && entity2.offset+entity2.text.length() <= sentence.offset+sentence.length
					) {
				return sentence;
			}
		}
		return null;
	}*/
	
	public static boolean isATokenInsideAnEntity(CoreLabel token, Entity entity) {
		if(token.beginPosition()>=entity.offset && token.endPosition()<=entity.offset+entity.text.length())
			return true;
		else
			return false;
	}
	
	
}
