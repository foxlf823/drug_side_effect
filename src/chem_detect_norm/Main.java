package chem_detect_norm;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import utils.CTDSaxParse;
import utils.CTDdisease;
import utils.CtdEntry;
import utils.MultiSieve;
import utils.Normalization;
import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFTrainerByLabelLikelihood;
import cc.mallet.fst.Transducer;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cn.fox.biomedical.Dictionary;
import cn.fox.machine_learning.BrownCluster;
import cn.fox.mallet.MalletSequenceTaggerInstance;
import cn.fox.mallet.MyCRFPipe;
import cn.fox.nlp.EnglishPos;
import cn.fox.nlp.Segment;
import cn.fox.nlp.Sentence;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.TokenizerWithSegment;
import cn.fox.nlp.Word2Vec;
import cn.fox.nlp.WordVector;
import cn.fox.stanford.Tokenizer;
import cn.fox.utils.CharCode;
import cn.fox.utils.Evaluater;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectSerializer;
import cn.fox.utils.StopWord;
import cn.fox.utils.WordNetUtil;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.LexicalPattern;
import drug_side_effect_utils.Tool;
import drug_side_effect_utils.WordClusterReader;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.POS;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TypedDependency;
import gnu.trove.TObjectDoubleHashMap;

public class Main {
	public static enum Label {
		B,I,L,U,O
	}

	public static void main(String[] args) throws Exception{
		FileInputStream fis = new FileInputStream(args[0]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String pos_tagger = properties.getProperty("pos_tagger");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String drug_dict = properties.getProperty("drug_dict");
		String jochem_dict = properties.getProperty("jochem_dict");
		String ctdchem_dict = properties.getProperty("ctdchem_dict");
		String chemical_element_abbr = properties.getProperty("chemical_element_abbr");
		String wordnet_dict = properties.getProperty("wordnet_dict");
		String train_instance_dir = properties.getProperty("train_instance_dir");
		String bioc_documents = properties.getProperty("bioc_documents");
		String entity_recognizer_ser = properties.getProperty("entity_recognizer_ser");
		String parser = properties.getProperty("parser");	
		String max_train_times = properties.getProperty("max_train_times");
		String bioc_documents_dev = properties.getProperty("bioc_documents_dev");
		String dev_instance_dir = properties.getProperty("dev_instance_dir");
		String entity_out = properties.getProperty("entity_out");
		String ctd_xml = properties.getProperty("ctd_xml");
		String cdrDev_abbr = properties.getProperty("cdrDev_abbr");
		String cdrDev_sf_lf_pairs = properties.getProperty("cdrDev_sf_lf_pairs");
		String stop_word = properties.getProperty("stop_word");
		String brown_cluster_path = properties.getProperty("brown_cluster_path");
		String vector = properties.getProperty("vector");
		String vector_cluster = properties.getProperty("vector_cluster");
		String train_dev_xml = properties.getProperty("train_dev_xml");
		
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'},false, common_english_abbr);
		MaxentTagger tagger = new MaxentTagger(pos_tagger);
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.ONLY_CHEMICAL);
		StopWord stopWord = new StopWord(stop_word);
		Tokenizer tokenizer = new Tokenizer(true, ' ');
		edu.mit.jwi.Dictionary dict = new edu.mit.jwi.Dictionary(new URL("file", null, wordnet_dict));
		dict.open();
		Morphology morphology = new Morphology();
		TokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "ptb3Escaping=false");
		
		
		Dictionary jochem = new Dictionary(jochem_dict, 1);
		Dictionary ctdchem = new Dictionary(ctdchem_dict, 1);
		Dictionary drugbank = new Dictionary(drug_dict, 1);
		Dictionary chemElem = new Dictionary(chemical_element_abbr, 1);
		
		BrownCluster entityBC = new BrownCluster(brown_cluster_path, 100);
		Word2Vec w2v = new Word2Vec();
		w2v.loadWord2VecOutput(vector);
		WordClusterReader wcr = new WordClusterReader(vector_cluster);
		LexicalizedParser lp = LexicalizedParser.loadModel(parser);
		
		Tool tool = new Tool();
		tool.sentSplit = sentSplit;
		tool.tokenizer = tokenizer;
		tool.tagger = tagger;
		tool.drugbank = drugbank;
		tool.jochem = jochem;
		tool.ctdchem = ctdchem;
		tool.chemElem = chemElem;
		tool.dict = dict;
		tool.morphology = morphology; 
		tool.stopWord = stopWord;
		tool.entityBC = entityBC;
		tool.w2v = w2v;
		tool.wcr = wcr;
		tool.lp = lp;
		tool.tokenizerFactory = tokenizerFactory;
		
		// set parameters here !!!!!!
		boolean preprocessTrain = true;
		boolean preprocessDev = true;
		boolean bTrain = true;
		
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile(train_dev_xml);	
		ArrayList<BiocDocument> devDocuments = xmlParser.parseBiocXmlFile(bioc_documents_dev);	
		
		
		if(preprocessTrain) {
			IoUtils.clearDirectory(new File(train_instance_dir));
			for(BiocDocument document:documents) {
				preprocess(tool, train_instance_dir, document);			
			}  
		}
		
		
		if(preprocessDev) {
			IoUtils.clearDirectory(new File(dev_instance_dir));
			for(BiocDocument document:devDocuments) {
				preprocess(tool, dev_instance_dir, document);			
			}   
		}
		
		List[] splitted = null;
		splitted = new ArrayList[2];
		splitted[0] = new ArrayList<File>(Arrays.asList(new File(train_instance_dir).listFiles()));
		splitted[1] = new ArrayList<File>(Arrays.asList(new File(dev_instance_dir).listFiles()));
		
		
		CRF crf = null;
		
		
		if(bTrain)  {
			// train
			Pipe p = new MyCRFPipe();
			InstanceList trainData = new InstanceList(p);
			for(int j=0;j<splitted[0].size();j++) {
				File instanceFile = (File)splitted[0].get(j);
				trainData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instanceFile), "UTF-8"),Pattern.compile("^\\s*$"), true));
			}
			crf = train(trainData, null, Integer.parseInt(max_train_times));
			ObjectSerializer.writeObjectToFile(crf, entity_recognizer_ser);	
			
		} else {
			crf = (CRF)ObjectSerializer.readObjectFromFile(entity_recognizer_ser);	
		}	
		
		
		
		
		// prepare for normalization
		Normalization cdrEntityNorm = new Normalization();		
		cdrEntityNorm.loadCdrAbbre(cdrDev_abbr,cdrDev_sf_lf_pairs);
		
		CTDSaxParse ctdSaxParse = new CTDSaxParse("Chemical");
		ctdSaxParse.startCTDSaxParse(ctd_xml);
		
		List<CtdEntry> ctdChemialList = ctdSaxParse.chemList;
		// group names with the same id
		Map<String,List<String>> idToNames = new HashMap<String,List<String>>();
		for(CtdEntry tempChem:ctdChemialList){
			List<String> names = new ArrayList<String>();
			names.add(tempChem.name);
			for(String synonymy:tempChem.synonyms){
				names.add(synonymy);			
			}
			idToNames.put(tempChem.id,names);			
		}
		
		// map: one id one name
		Map<String,String> idToName = new HashMap<String,String>();
		for(CtdEntry tempChem: ctdChemialList){
			idToName.put(tempChem.id,tempChem.name);
		}	
		
		MultiSieve multiSieve  = new MultiSieve(idToNames);
		
		// test
		int countPredict = 0;
		int countTrue = 0;
		int countCorrect = 0;
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(entity_out),"UTF-8"));
		
		for(int j=0;j<splitted[1].size();j++) { // for each test file
			
			File instanceFile = (File)splitted[1].get(j);
			InstanceList testData = new InstanceList(crf.getInputPipe());
			testData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instanceFile), "UTF-8"),Pattern.compile("^\\s*$"), true));
			
			BiocDocument document = getBiocDocumentByID(instanceFile.getName().substring(0, instanceFile.getName().indexOf(".")), devDocuments);
			
			ArrayList<Entity> preEntities = getEntities(document, testData, crf, tool);

						
			/*countPredict+= preEntities.size();
			ArrayList<Entity> goldEntities = document.entities;
			countTrue += goldEntities.size();
			for(Entity preEntity:preEntities) {
				for(Entity goldEntity:goldEntities) {
					if(preEntity.equals(goldEntity)) {
						countCorrect++;
						break;
					}
				}
			}*/
			
			// normalization
			for(Entity preEntity:preEntities) {
				
				String preMeshId = null;
				
				// multi sieve
				String tempReturn = null;
				String entityText = cdrEntityNorm.replaceAbbre(preEntity, document);
				entityText = entityText.trim();
				List<String> entityTextList = new ArrayList<String>();				
				String returnMeshId = multiSieve.isExactMap(entityText);
				if(returnMeshId!= null){
					preMeshId = returnMeshId;					
				} else if((tempReturn = multiSieve.singularToPlural(entityText, entityTextList)) != null){
					preMeshId = tempReturn;					
				} else if((tempReturn= multiSieve.dropPrepositionAndSwapping(entityText, entityTextList,tokenizerFactory))!=null){
					preMeshId = tempReturn;
				} else if((tempReturn = multiSieve.compareStemmer(entityTextList,idToName))!=null){
				   preMeshId = tempReturn;
				} 
				
				if(preMeshId==null)
					preMeshId = "-1";
				preEntity.preMeshId = preMeshId;
				
				bw.write(preEntity.id +"\t"+preEntity.offset +"\t"+(preEntity.offset+preEntity.text.length())+"\t"+preEntity.text+"\t"+ preEntity.type + "\t"+/*"MESH:"+*/preEntity.preMeshId);
				bw.write("\n");
			}
			
			
			
		}
		
		bw.close();	
		
		double precision = Evaluater.getPrecisionV2(countCorrect, countPredict);
		double recall  = Evaluater.getRecallV2(countCorrect, countTrue);
		double f1 = Evaluater.getFMeasure(precision, recall, 1);
		
		System.out.println(precision+"\t"+recall+"\t"+f1); 

	}

	
	public static String preprocess(Tool tool, String instanceOutDir, BiocDocument document) throws Exception {
		SentenceSplitter sentSplit = tool.sentSplit;
		MaxentTagger tagger = tool.tagger;
		IDictionary dict = tool.dict;
		Morphology morphology = tool.morphology;
		
		ArrayList<MalletSequenceTaggerInstance> instanceList = new ArrayList<MalletSequenceTaggerInstance>();
		
		String content = document.title+" "+document.abstractt;
		int offset = 0;
		// sentence segmentation
		List<String> sentences = sentSplit.splitWithFilters(content);
		for(String sentence:sentences) {
			Sentence sent = new Sentence();
			// Each sentence denotes a instance.
			MalletSequenceTaggerInstance instance = new MalletSequenceTaggerInstance();
			// tokenize
			ArrayList<CoreLabel> tokens = tool.tokenizer.tokenize(offset, sentence);
						
			// for each word
			int sentenceLength = sentence.length();
			// pos tagging
			tagger.tagCoreLabels(tokens);
			// lemma
			for(int i=0;i<tokens.size();i++) {
				morphology.stem(tokens.get(i));
			}
			// parsing
			/*Tree root = tool.lp.apply(tokens);
			root.indexLeaves();
			root.setSpans();
			List<Tree> leaves = root.getLeaves();*/
			
			// depend
			/*GrammaticalStructure gs = tool.gsf.newGrammaticalStructure(root);
			List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
		    SemanticGraph depGraph = new SemanticGraph(tdl);*/
			
			sent.tokens = tokens;
			/*sent.root = root;
			sent.leaves = leaves;
			sent.depGraph = depGraph;*/
			
			for(int i=0;i<tokens.size();i++) {
				CoreLabel token = tokens.get(i);
				
				// add features to the instance
				TObjectDoubleHashMap<String> featureVector = new TObjectDoubleHashMap<String>();
				prepareFeatures(tool, featureVector, sent, i, instance);
				
				instance.data.add(featureVector);
				
				// add labels to the instance
				Label label = null;
				Entity goldEntity = null;
				if((goldEntity=document.isInsideAGoldEntityAndReturnIt(token.beginPosition(), token.endPosition()-1))==null) {
					label = Label.O;
					if(i>0) {
						if(instance.target.get(i-1).equals(Label.B.toString()))
							instance.target.set(i-1, Label.U.toString());
						else if(instance.target.get(i-1).equals(Label.I.toString()))
							instance.target.set(i-1, Label.L.toString());
					}
				} else {
					if(i>0) {
						if(document.isTokenAndPreviousInsideAGoldEntity(token.beginPosition(), token.endPosition()-1, tokens.get(i-1).beginPosition(),tokens.get(i-1).endPosition()-1)) {
							label = Label.I;
						} else {
							label = Label.B;
						}
						
					} else {
						label = Label.B;
					}
				}

				instance.target.add(label.toString());
			}
			offset += sentenceLength;
			instanceList.add(instance);
		}
		
		String outPath = instanceOutDir+"\\"+document.id+".instance";
		MalletSequenceTaggerInstance.writeAllInstances2File(instanceList, outPath);
		return outPath;
		
	}
	
	public static void prepareFeatures(Tool tool, TObjectDoubleHashMap<String> map, Sentence sent, int i, 
			MalletSequenceTaggerInstance myInstance) throws Exception{
			
		CoreLabel token = sent.tokens.get(i);
		String lemmaLow = token.lemma().toLowerCase();
		
		map.put(lemmaLow,1.0);
		for(int j=i-1, count=1;count>0 && j>=0; count--,j--) {
			map.put(sent.tokens.get(j).lemma().toLowerCase(),1.0); 
		}
		for(int j=i+1, count=1;count>0 && j<sent.tokens.size(); count--,j++) {
			map.put(sent.tokens.get(j).lemma().toLowerCase(),1.0); 
		}
		
		map.put("#POS_"+token.tag(),1.0);
		for(int j=i-1, count=1;count>0 && j>=0; count--,j--) {
			map.put("#POS_"+sent.tokens.get(j).tag(),1.0); 
		}
		
		int len = lemmaLow.length()>4 ? 4:lemmaLow.length();
		map.put("#PREF_"+lemmaLow.substring(0, len),1.0);
		map.put("#SUF_"+lemmaLow.substring(lemmaLow.length()-len, lemmaLow.length()),1.0);
		
		
		map.put("#PTN_"+LexicalPattern.pipe(token.word()),1.0);
		
		String bc = tool.entityBC.getPrefix(lemmaLow);
		map.put("#BNC_"+bc, 1.0);
		
		
		String wc = tool.wcr.getCluster(lemmaLow);
		map.put("#WC_"+wc, 1.0);
		
		POS[] poses = {POS.NOUN, POS.ADJECTIVE};
		for(POS pos:poses) {
			ISynset synset = WordNetUtil.getMostSynset(tool.dict, lemmaLow, pos);
			if(synset!= null) {
				map.put("#WN_"+synset.getID(),1.0);
			} 

			ISynset hypernym = WordNetUtil.getMostHypernym(tool.dict, lemmaLow, pos);
			if(hypernym!= null) {
				map.put("#WN_"+hypernym.getID(),1.0);
			}
		}
		
		
		
		
	}
	
	public static CRF train(InstanceList trainingData, String ser, int max_train_times) throws Exception {
	    CRF crf = new CRF(trainingData.getPipe(), (Pipe)null);
	    crf.addOrderNStates(trainingData, new int[]{1}, null,Label.O.toString(), Pattern.compile("\\s"), Pattern.compile(".*"),true);

        for (int i = 0; i < crf.numStates(); i++)
	        crf.getState(i).setInitialWeight (Transducer.IMPOSSIBLE_WEIGHT);
	    crf.getState(Label.O.toString()).setInitialWeight(0.0);

        
	    CRFTrainerByLabelLikelihood crfTrainer = new CRFTrainerByLabelLikelihood (crf);
	    crfTrainer.setGaussianPriorVariance(1.0);

	    
	    int iterations = max_train_times; // max iteration times
	    boolean converged = false;
      	for (int i = 1; i <= iterations; i++) {
      		converged = crfTrainer.train (trainingData, 1);
      		if (converged)
      			break;
      	}
      	if(ser!=null)
      		ObjectSerializer.writeObjectToFile(crf, ser);
      	return crf;
	}
	
	public static BiocDocument getBiocDocumentByID(String id, ArrayList<BiocDocument> documents) {
		for(BiocDocument document:documents) {
			if(document.id.equals(id))
				return document;
		}
		return null;
	}
	
	// Given a bioc xml file and its instance data, get its entities. 
	public static ArrayList<Entity> getEntities(BiocDocument document, InstanceList instanceData, CRF crf, Tool tool) throws Exception{
		ArrayList<Entity> preEntities = new ArrayList<Entity>();
		List<Sentence> sentences = preparePredictInfo(tool, document);
		for(int m=0;m<sentences.size();m++) {
			Instance instance = instanceData.get(m);
			Sentence sentence = sentences.get(m); // we assume a instance corresponds to a sentence
			List<CoreLabel> tokens = sentence.tokens;
			// predicate			
			Sequence preOutput = crf.transduce((Sequence)instance.getData());
			Entity temp = null;
			for(int k=0;k<preOutput.size();k++) {
				if(preOutput.get(k).equals(Label.B.toString()) ) {//B与U相似，B不加入实体集，是一个新实体开始。
					if(k==0) {
						temp = new Entity(document.id, "Chemical", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
					} else {
						String old = preOutput.get(k-1).toString();
						if(old.equals(Label.B.toString())|| old.equals(Label.I.toString())) {
							System.out.println("error "+old+preOutput.get(k));
						} else if(old.equals(Label.L.toString())|| old.equals(Label.U.toString())) {
							if(temp!=null) {
								preEntities.add(temp);
								temp=null;
							}
							temp = new Entity(document.id, "Chemical", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
						} else {
							temp = new Entity(document.id, "Chemical", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
						}
					}
				} else if(preOutput.get(k).equals(Label.I.toString())) {
					if(k==0) {
						System.out.println("error first"+preOutput.get(k));
					} else {//前一个是B,I时追加
						String old = preOutput.get(k-1).toString();
						if(old.equals(Label.B.toString())|| old.equals(Label.I.toString())) {
							int whitespaceToAdd = tokens.get(k).beginPosition()-tokens.get(k-1).endPosition();
							for(int q=1;q<=whitespaceToAdd;q++)
								temp.text += " ";
							temp.text += tokens.get(k).word();
						} else {
							System.out.println("error "+old+preOutput.get(k));
						}
						
					} 
				} else if(preOutput.get(k).equals(Label.L.toString())) {
					if(k==0) {
						System.out.println("error first"+preOutput.get(k));
					} else {//前一个是B,I时，追加，且L表示新实体的结束。
						String old = preOutput.get(k-1).toString();
						if(old.equals(Label.B.toString())|| old.equals(Label.I.toString())) {
							int whitespaceToAdd = tokens.get(k).beginPosition()-tokens.get(k-1).endPosition();
							
							for(int q=1;q<=whitespaceToAdd;q++)
								temp.text += " ";
							temp.text += tokens.get(k).word();
							if(temp!=null) {//加入新实体
								preEntities.add(temp);
								temp=null;
							}
						} else {
							System.out.println("error "+old+preOutput.get(k));
						}
						
					} 
				} else if(preOutput.get(k).equals(Label.U.toString()) ) {
					if(k==0) {
						temp = new Entity(document.id, "Chemical", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
						if(temp!=null) {
							preEntities.add(temp);
							temp=null;
						}
					} else {
						String old = preOutput.get(k-1).toString();
						if(old.equals(Label.B.toString())|| old.equals(Label.I.toString())) {
							System.out.println("error "+old+preOutput.get(k));
						} else if(old.equals(Label.L.toString())|| old.equals(Label.U.toString())) {//前一个结束，新一个开始
							if(temp!=null) {
								preEntities.add(temp);
								temp=null;
							}
							temp = new Entity(document.id, "Chemical", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
							if(temp!=null) {
								preEntities.add(temp);
								temp=null;
							}
						} else {//前一个是O，表示一个新实体的开始和结束。
							temp = new Entity(document.id, "Chemical", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
							if(temp!=null) {
								preEntities.add(temp);
								temp=null;
							}
						}
					}
				} else if(preOutput.get(k).equals(Label.O.toString())){
					if(k==0) {
						;
					} else {
						String old = preOutput.get(k-1).toString();
						if(old.equals(Label.B.toString())|| old.equals(Label.I.toString())) {
							System.out.println("error "+old+preOutput.get(k));
						} else if(old.equals(Label.L.toString())|| old.equals(Label.U.toString())) {
							if(temp!=null) {
								preEntities.add(temp);
								temp=null;
							}
						} 
					}
					// if the label is O, we use some rules to improve the recall
					postprocessLocal(tokens, preOutput, k, tool, preEntities, document);
				} 
			
			}
			
			//System.out.println();
		}
		
		return preEntities;
	}
	
	// Given a document, prepare all its information for predication.
	public static List<Sentence> preparePredictInfo(Tool tool, BiocDocument document) {
		String content = document.title+" "+document.abstractt;
		List<Sentence> mySentences = new ArrayList<Sentence>();
		List<String> sentences = tool.sentSplit.splitWithFilters(content);
		int offset = 0;
		for(int m=0;m<sentences.size();m++) { // for each sentence
			Sentence mySentence = new Sentence();
			String sentence = sentences.get(m); 
			// tokenize
			ArrayList<Segment> given = new ArrayList<Segment>();
		
			
			ArrayList<Segment> segments = TokenizerWithSegment.tokenize(offset, sentence, given);
			List<CoreLabel> tokens = new ArrayList<CoreLabel>();
			for(Segment segment:segments) {
				CoreLabel token = new CoreLabel();
				token.setWord(segment.word);
				token.setValue(segment.word);
				token.setBeginPosition(segment.begin);
				token.setEndPosition(segment.end);
				tokens.add(token);
			}
			// for each word, adjust it offset  
			int sentenceLength = sentence.length();
			
			// pos tagging
			tool.tagger.tagCoreLabels(tokens);
			// lemma
			for(int k=0;k<tokens.size();k++) {
				tool.morphology.stem(tokens.get(k));//对word分类
			}
			offset += sentenceLength;
			
			mySentence.tokens = tokens;
			mySentences.add(mySentence);
		}
		
		return mySentences;
	}
	
	public static void postprocessLocal(List<CoreLabel> tokens, Sequence preOutput, int k, Tool tool, 
			ArrayList<Entity> preEntities, BiocDocument document) {
		CoreLabel token = tokens.get(k);
				
		// search chem dict
		if((tool.drugbank.contains(token.word()) /*|| tool.drugbank.contains(token.lemma())*/ ||
				tool.jochem.contains(token.word()) /*|| tool.jochem.contains(token.lemma())*/ ||
				tool.ctdchem.contains(token.word()) /*|| tool.ctdchem.contains(token.lemma())*/ ||
				tool.chemElem.contains(token.word()) /*|| tool.chemElem.contains(token.lemma())*/
				) &&
				!tool.stopWord.contains(token.word())) {
			System.out.println(token.word());
			preEntities.add(new Entity(document.id, "Chemical", token.beginPosition(), token.word(),null));
			return;
		} 
		
		
		
		
		
	}
	
}
