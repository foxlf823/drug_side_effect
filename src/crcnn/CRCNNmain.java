package crcnn;

import java.io.FileInputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;

import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.Word2Vec;
import cn.fox.stanford.Tokenizer;
import cn.fox.utils.Evaluater;
import cn.fox.utils.ObjectSerializer;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.Relation;
import drug_side_effect_utils.Tool;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;
import edu.stanford.nlp.util.PropertiesUtils;
import gnu.trove.TIntArrayList;
import gnu.trove.TObjectIntHashMap;

public class CRCNNmain implements Serializable{
	
	public List<String> knownWords;
	public TObjectIntHashMap<String> wordIDs;
	public CRCNN crcnn;
	
	public static void main(String[] args) throws Exception {
		FileInputStream fis = new FileInputStream(args[0]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		Parameters parameters = new Parameters(properties);
		parameters.printParameters();
		
		String embedFile = PropertiesUtils.getString(properties, "embedFile", "");
		String modelFile = PropertiesUtils.getString(properties, "model_save_path", "");
		
		Tool tool = new Tool();
		tool.tokenizer = new Tokenizer(true, ' ');	
		tool.sentSplit = new SentenceSplitter(new Character[]{';'},false, PropertiesUtils.getString(properties, "common_english_abbr", ""));
		
		BiocXmlParser xmlParser = new BiocXmlParser(PropertiesUtils.getString(properties, "bioc_dtd", ""), BiocXmlParser.ParseOption.BOTH);
		ArrayList<BiocDocument> trainDocs = xmlParser.parseBiocXmlFile(PropertiesUtils.getString(properties, "bioc_documents", ""));
		ArrayList<BiocDocument> testDocs = xmlParser.parseBiocXmlFile(PropertiesUtils.getString(properties, "bioc_documents_test", ""));
		
		List<String> word = new ArrayList<>();
		// prepare alphabet
		for(BiocDocument doc:trainDocs) {
	    	String content = doc.title+" "+doc.abstractt;
			
			List<String> strSentences = tool.sentSplit.splitWithFilters(content);
			int offset = 0;
			for(String temp:strSentences) {
				ArrayList<CoreLabel> tokens = tool.tokenizer.tokenize(offset, temp);
				for(CoreLabel token: tokens) {
					word.add(token.word().toLowerCase());
				}
				offset += temp.length();
			}
		}
		
		CRCNNmain crcnnMain = new CRCNNmain();
		crcnnMain.knownWords = generateDict(word, 1);
		crcnnMain.knownWords.add(0, Parameters.UNKNOWN);
		crcnnMain.knownWords.add(1, Parameters.PADDING);
		crcnnMain.knownWords.add(2, Parameters.CHEMICAL);
		crcnnMain.knownWords.add(3, Parameters.DISEASE);
		crcnnMain.wordIDs = new TObjectIntHashMap<String>();
	    int m = 0;
	    for (String temp : crcnnMain.knownWords)
	    	crcnnMain.wordIDs.put(temp, (m++));
	    
	    double[][] E = new double[crcnnMain.knownWords.size()][parameters.embSize];
	    Word2Vec.loadEmbedding(embedFile, E, parameters.initRange, crcnnMain.knownWords);
	    
	    CRCNN crcnn = new CRCNN(parameters, E, crcnnMain);
	    crcnnMain.crcnn = crcnn;
	    
	    List<Example> trainExamples = new ArrayList<>();
	    for(int k=0;k<trainDocs.size();k++) {
	    	BiocDocument doc = trainDocs.get(k);
	    	List<Example> examples = prepareExample(tool, doc, crcnnMain);
	    	trainExamples.addAll(examples);
	    }
	    System.out.println("train examples number: "+trainExamples.size());
	    
	    BestPerformance best = new BestPerformance();
	    
	    for(int epoch=1;epoch<=parameters.maxIter;epoch++) {
	    	//long startTime = System.currentTimeMillis();
	    	double loss = 0;
	    	for(int i=0;i<trainExamples.size();i++) {
	    		loss += crcnn.forwardbackward(trainExamples.get(i)/*, epoch*/);
	    		
	    	}
	    	//System.out.println("Elapsed Time: " + (System.currentTimeMillis() - startTime) / 1000.0 + " (s)");
	    	if (epoch>1 && epoch % parameters.evalPerIter == 0) {
	    		System.out.println("loss: "+loss);
	    		evalute(testDocs, best, crcnnMain, tool, modelFile);
	    	}
	    	
	    	
	    }
	    
	    	
	    evalute(testDocs, best, crcnnMain, tool, modelFile);
		
	}
	
	public static void evalute(List<BiocDocument> docs, BestPerformance best, CRCNNmain crcnnMain,
			Tool tool, String modelFile) {
		int pre = 0;
		int gold = 0;
		int correct = 0;
		for(int k=0;k<docs.size();k++) {
			BiocDocument doc = docs.get(k);
			List<Example> testExamples = prepareExample(tool, doc, crcnnMain);
			HashSet<Relation> preRelations = new HashSet<>();
			for(int i=0;i<testExamples.size();i++) {
				Example e = testExamples.get(i);
				double[] scores = crcnnMain.crcnn.computeScore(e);
				if(scores[0] < scores[1]) {
					Relation tempRelation = new Relation(null, Parameters.RELATION, e.drugMesh, e.diseaseMesh);
					preRelations.add(tempRelation);
				}
				
			}
			
			pre += preRelations.size();
			gold += doc.relations.size();
			preRelations.retainAll(doc.relations);
			correct += preRelations.size();
		}
		
		double precision = Evaluater.getPrecisionV2(correct, pre);
		double recall = Evaluater.getRecallV2(correct, gold);
		double f1 = Evaluater.getFMeasure(precision, recall, 1);
		
		if ((f1 > best.f1Relation)) {
	        
	          System.out.println("Current Exceeds the best! p,r,f1: "+precision+" "+recall+" "+f1);
	          best.pRelation = precision;
	          best.rRelation = recall;
	          best.f1Relation = f1;
	          ObjectSerializer.writeObjectToFile(crcnnMain, modelFile);
	    }
		
	}
	
	/*
     *  prepare training examples.
     *  Each CID as an example, all the sentences in the document will be considered.
     *  Other entities replaced with their type.
     */
	public static List<Example> prepareExample(Tool tool, BiocDocument doc, CRCNNmain crcnnMain) {
		
	    List<Example> examples = new ArrayList<>();

	    	// the entities with the same mesh IDs will generate the same examples
	    	// so we record them and avoid generating overlapped examples
	    	HashSet<Relation> hasUsed = new HashSet<>();
	    	String content = doc.title+" "+doc.abstractt;
	    	for(int i=0;i<doc.entities.size();i++) {
	    		Entity latter = doc.entities.get(i);
	    		for(int j=0;j<i;j++) {
	    			Entity former = doc.entities.get(j);
	    			if(former.type.equals(latter.type))
	    				continue;
	    			
	    			Relation tempRelation = new Relation(null, Parameters.RELATION, former.mesh, latter.mesh);
	    			if(hasUsed.contains(tempRelation))
	    				continue;
	    			
	    			Entity drug = former.type.equals("Chemical") ? former:latter;
	    			Entity disease = former.type.equals("Chemical") ? latter:former;
	    			Example example = new Example(drug.mesh, disease.mesh);
	    			example.featureIDs = new ArrayList<>();
		    		List<String> strSentences = tool.sentSplit.splitWithFilters(content);
					int offset = 0;
					for(String temp:strSentences) {
						TIntArrayList sentFeatureIds = new TIntArrayList();
						ArrayList<CoreLabel> tokens = tool.tokenizer.tokenize(offset, temp);
						for(CoreLabel token: tokens) {
							Entity entity = doc.isInsideAGoldEntityAndReturnIt(token.beginPosition(), token.endPosition()-1);
							if(entity!=null && !entity.mesh.equals(former.mesh) && !entity.mesh.equals(latter.mesh)) {
								if(entity.type.equals("Chemical"))
									sentFeatureIds.add(crcnnMain.getWordID(Parameters.CHEMICAL));
								else
									sentFeatureIds.add(crcnnMain.getWordID(Parameters.DISEASE));
							} else
								sentFeatureIds.add(crcnnMain.getWordID(token.word().toLowerCase()));
						}
						example.featureIDs.add(sentFeatureIds);
						offset += temp.length();
					}
					
					if(doc.twoEntitiesHaveRelation(former, latter))
						example.label = 1;
					else
						example.label = 0;
					
					hasUsed.add(tempRelation);
					examples.add(example);
	    		}
	    	}
	    	
		
	    
	    return examples;
	}
	
	
	
	public int getWordID(String temp) {
		return wordIDs.containsKey(temp) ? wordIDs.get(temp) : wordIDs.get(Parameters.UNKNOWN);
	}
	
	public static List<String> generateDict(List<String> str, int cutOff)
	  {
	    Counter<String> freq = new IntCounter<>();
	    for (String aStr : str)
	      freq.incrementCount(aStr);

	    List<String> keys = Counters.toSortedList(freq, false);
	    List<String> dict = new ArrayList<>();
	    for (String word : keys) {
	      if (freq.getCount(word) >= cutOff)
	        dict.add(word);
	    }
	    return dict;
	  }
	
	public static double tanh(double x) {
		return (exp(x)-exp(-x))/(exp(x)+exp(-x));
	}
	
	public static double exp(double x) {
		if(x>50) x=50;
		else if(x<-50) x=-50;
		return Math.exp(x);
	}

}

class BestPerformance {
	public double pRelation= -1;
	public double rRelation= -1;
	public double f1Relation= -1;
	
	
}