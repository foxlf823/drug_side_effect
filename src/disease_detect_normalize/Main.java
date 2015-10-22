package disease_detect_normalize;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.WordClusterReader;
import utils.Normalization;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.LexicalPattern;
import cn.fox.utils.ObjectShuffle;
import cn.fox.nlp.Sentence;
import cn.fox.utils.StopWord;
import drug_side_effect_utils.Tool;
import cn.fox.utils.WordNetUtil;
import cn.fox.nlp.TokenizerWithSegment;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.Trial;
import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFTrainerByLabelLikelihood;
import cc.mallet.fst.Transducer;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.iterator.LineGroupIterator;
import cc.mallet.types.Alphabet;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Sequence;
import cn.fox.biomedical.Dictionary;
import cn.fox.machine_learning.BrownCluster;
import cn.fox.machine_learning.Perceptron;
import cn.fox.mallet.FeatureVectorMaker;
import cn.fox.mallet.MalletSequenceTaggerInstance;
import cn.fox.mallet.MyCRFPipe;
import cn.fox.nlp.EnglishPos;
import cn.fox.nlp.Segment;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.Word2Vec;
import cn.fox.utils.CharCode;
import cn.fox.utils.Evaluater;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectSerializer;
import edu.mit.jwi.IDictionary;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.POS;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.IndexedWord;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.semgraph.SemanticGraph;
import edu.stanford.nlp.semgraph.SemanticGraphEdge;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructure;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.trees.TypedDependency;
import edu.stanford.nlp.util.IntPair;
import gnu.trove.TObjectDoubleHashMap;
import utils.Normalization;
import cn.fox.utils.ExeCaller;
import utils.CTDSaxParse;
import utils.MultiSieve;
import utils.CTDdisease;

public class Main {
	

	public static enum Label {
		B,I,L,U,O
	}

	public static void main(String[] args) throws Exception{
		//String path = "E:/biomedicine/Disease_detection/entity.properties";	
		FileInputStream fis = new FileInputStream(args[0]);//args[1] entity.properties
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String pos_tagger = properties.getProperty("pos_tagger");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String disease_dict = properties.getProperty("disease_dict");
		String wordnet_dict = properties.getProperty("wordnet_dict");
		String train_instance_dir = properties.getProperty("train_instance_dir");
		String bioc_documents = properties.getProperty("bioc_documents");
		String entity_recognizer_ser = properties.getProperty("entity_recognizer_ser");
		String parser = properties.getProperty("parser");		
		String ctdmedic_dict = properties.getProperty("ctdmedic_dict");		
		String max_train_times = properties.getProperty("max_train_times");
		String bioc_documents_dev = properties.getProperty("bioc_documents_dev");
		String dev_instance_dir = properties.getProperty("dev_instance_dir");
		String entity_out = properties.getProperty("entity_out");
		String ctd_xml = properties.getProperty("ctd_xml");
		String cdrDev_abbr = properties.getProperty("cdr_abbr");
		String cdrDev_sf_lf_pairs = properties.getProperty("cdrDev_sf_lf_pairs");	
		String stop_word = properties.getProperty("stop_word");
		String brown_cluster_path = properties.getProperty("brown_cluster_path");
		String vector = properties.getProperty("vector");
		String vector_cluster = properties.getProperty("vector_cluster");
		String train_dev_xml = properties.getProperty("train_dev_xml");
		
		LexicalizedParser lp = LexicalizedParser.loadModel(parser);
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
	    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'},false, common_english_abbr);
		TokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "ptb3Escaping=false");
		MaxentTagger tagger = new MaxentTagger(pos_tagger);
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.ONLY_DISEASE);
		StopWord stopWord = new StopWord(stop_word);
		
				
		Dictionary humando = new Dictionary(disease_dict, 1);
		edu.mit.jwi.Dictionary dict = new edu.mit.jwi.Dictionary(new URL("file", null, wordnet_dict));
		dict.open();
		Morphology morphology = new Morphology();
		//Jochem jochem = new Jochem(jochem_dict, 1);
		//CTDchem ctdchem = new CTDchem(ctdchem_dict, 1);
		Dictionary ctdmedic = new Dictionary(ctdmedic_dict, 1);
		//ChemicalElement chemElem = new ChemicalElement(chemical_element_abbr);
		Pattern complexNounPattern = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9',\\(\\)\\[\\]\\{\\}\\.~\\+]*(-[a-zA-Z0-9',\\(\\)\\[\\]\\{\\}\\.~\\+]+)+[a-zA-Z0-9]");
		
		BrownCluster entityBC = new BrownCluster(brown_cluster_path, 100);
		Word2Vec w2v = new Word2Vec();
		w2v.loadWord2VecOutput(vector);
		WordClusterReader wcr = new WordClusterReader(vector_cluster);
	
		Tool tool = new Tool();
		tool.sentSplit = sentSplit;
		tool.tokenizerFactory = tokenizerFactory;
		tool.tagger = tagger;
		//tool.xmlParser = xmlParser;
		//tool.drugbank = drugbank;
		tool.humando = humando;
		tool.dict = dict;
		tool.morphology = morphology; //形态学计算英语单词的基本形式,仅仅通过删除词形变化(不是派生形态学）只包括名词复数，代词和动词词尾
		tool.lp = lp;
		tool.gsf = gsf;
		//tool.jochem = jochem;
		//tool.ctdchem = ctdchem;
		tool.ctdmedic = ctdmedic;
		//tool.chemElem = chemElem;
		tool.complexNounPattern = complexNounPattern;
		tool.stopWord = stopWord;
		tool.entityBC = entityBC;
		tool.w2v = w2v;
		tool.wcr = wcr;
		
		// set parameters here!!!
		boolean preprocessTrain = false;
		boolean preprocessDev = true;
		boolean notTrain = true;
		
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile(bioc_documents);	
		ArrayList<BiocDocument> devDocuments = xmlParser.parseBiocXmlFile(bioc_documents_dev);	
		// proprecess brown clustering input files 
		/*processFileFormat(tool,cdr_brown_train,documents);
		processFileFormat(tool,cdr_brown_dev,devDocuments);*/
		
		//preprocess trainning data 
		
		if(preprocessTrain) {
			IoUtils.clearDirectory(new File(train_instance_dir));
			for(BiocDocument document:documents) {
				preprocess(tool, train_instance_dir, document);			
			}  
		}
		
		//BufferedWriter bwDev = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cdr_dev_plain),"UTF-8"));
	
		//preprocess testing data
		
		if(preprocessDev) {
			IoUtils.clearDirectory(new File(dev_instance_dir));
			for(BiocDocument document:devDocuments) {
				preprocess(tool, dev_instance_dir, document);			
			}   
		}
		
		
		//output cdr_dev_plain, to get abbreviation input file
		//BufferedWriter bwDev = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(cdr_dev_plain),"UTF-8"));
		/*for(BiocDocument document:devDocuments) {
			preprocess(tool, entity_instance_devdir, document);
			String content = document.id +"\t" + document.title +" " + document.abstractt;
			bwDev.write(content);
			bwDev.write('\n');
			bwDev.flush();
		}*/
//		bwDev.close();
    	//ExeCaller.call("/dawnfs/users/nlplab/user/lyx/Ab3P-v1.5/identify_abbr /dawnfs/users/nlplab/user/lyx/detect_norm_resource/cdr_dev_plain > /dawnfs/users/nlplab/user/lyx/Ab3P-v1.5/CDR_DevelopmentSetAbbr_out");	
		List[] splitted = null;
		splitted = new ArrayList[2];
		splitted[0] = new ArrayList<File>(Arrays.asList(new File(train_instance_dir).listFiles()));
		splitted[1] = new ArrayList<File>(Arrays.asList(new File(dev_instance_dir).listFiles()));
		
		
		CRF crf = null;
		
		
		if(notTrain)  {
			crf = (CRF)ObjectSerializer.readObjectFromFile(entity_recognizer_ser);
		} else {
			// train
			Pipe p = new MyCRFPipe();
			InstanceList trainData = new InstanceList(p);
			for(int j=0;j<splitted[0].size();j++) {
				File instanceFile = (File)splitted[0].get(j);
				trainData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instanceFile), "UTF-8"),Pattern.compile("^\\s*$"), true));
			}
			crf = train(trainData, null, Integer.parseInt(max_train_times));
			ObjectSerializer.writeObjectToFile(crf, entity_recognizer_ser);		
		}		
		/*ArrayList<Entity> wrongEntity = new ArrayList<Entity>(); // debug
		ArrayList<Entity> lostEntity = new ArrayList<Entity>();*/
	
		//Output prediction correct disease entities
		
				
		//load CDT lexicon and abrreviation
		Normalization cdrEntityNorm = new Normalization();		
		cdrEntityNorm.loadCdrAbbre(cdrDev_abbr,cdrDev_sf_lf_pairs);
		
		//load canonicalNameToId from CTDdisease.xml 
		CTDSaxParse ctdSaxParse = new CTDSaxParse("Disease");
		ctdSaxParse.startCTDSaxParse(ctd_xml);
		List<CTDdisease> ctdDiseasesList = ctdSaxParse.diseaseslist;
		Map<String,List<String>> idToNames = new HashMap<String,List<String>>();
		Map<String,String> idToName = new HashMap<String,String>();
		
		for(CTDdisease tempCtdDisease:ctdDiseasesList){
			List<String> names = new ArrayList<String>();
			names.add(tempCtdDisease.getDiseaseName());
			for(String synonymy:tempCtdDisease.getSynonyms()){
				names.add(synonymy);			
			}
			idToNames.put(tempCtdDisease.getDiseaseId(),names);			
		}
		for(CTDdisease tempCtdDisease: ctdDiseasesList){
			idToName.put(tempCtdDisease.getDiseaseId(),tempCtdDisease.getDiseaseName());
		}	
		MultiSieve multiSieve  = new MultiSieve(idToNames);
		// test
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(entity_out),"UTF-8"));
		int countPredict = 0;
		int countTrue = 0;
		int countCorrect = 0;
		for(int i=0;i<splitted[1].size();i++) { // for each test file
				File instanceFile = (File)splitted[1].get(i);
				InstanceList testData = new InstanceList(crf.getInputPipe());
				testData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instanceFile), "UTF-8"),Pattern.compile("^\\s*$"), true));
				BiocDocument document = getBiocDocumentByID(instanceFile.getName().substring(0, instanceFile.getName().indexOf(".")), devDocuments);
				ArrayList<Entity> preEntities = getEntities(document, testData, crf, tool);
				// postprocessGlobal(tool, preEntities);
				for(Entity preEntity:preEntities) {
				
					
					String preMeshId = null;
					String tempReturn = null;
					// whether abbreviation;sieve-2
					String entityText = cdrEntityNorm.replaceAbbre(preEntity, document);
					entityText = entityText.trim();
					List<String> entityTextList = new ArrayList<String>();				
					//sieve-1
					String returnMeshId = multiSieve.isExactMap(entityText);
					if(returnMeshId!= null){
						preMeshId = returnMeshId;					
					} else if((tempReturn = multiSieve.singularToPlural(entityText, entityTextList)) != null){
						preMeshId = tempReturn;					
					} else if((tempReturn = multiSieve.diseaseAffixationReplace(entityText, entityTextList))!= null){
						preMeshId = tempReturn;
					}else if((tempReturn = multiSieve.dropDiseaseOrAppendSynonyms(entityText,entityTextList))!= null) {
						preMeshId = tempReturn;
					} else if((tempReturn= multiSieve.dropPrepositionAndSwapping(entityText, entityTextList,tokenizerFactory))!=null){
						preMeshId = tempReturn;
					} else if((tempReturn = multiSieve.replaceDiseaseSynonyms(entityText, entityTextList)) != null){
						   preMeshId = tempReturn;
					} else if((tempReturn = multiSieve.replaceTumorSynonyms(entityText, entityTextList)) != null){
					   preMeshId = tempReturn;
					} else if((tempReturn = multiSieve.compareStemmer(entityTextList,idToName))!=null){
					   preMeshId = tempReturn;
					} 
					if(preMeshId==null)
						preMeshId = "-1";
					preEntity.preMeshId = preMeshId;					
					countPredict++;

					bw.write(preEntity.id +"\t"+preEntity.offset +"\t"+(preEntity.offset+preEntity.text.length())+"\t"+preEntity.text+"\t"+ preEntity.type + "\t"+/*"MESH:"+*/preEntity.preMeshId);
					bw.write("\n");
					bw.flush();								
				}
				}
		bw.close();	

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
						temp = new Entity(document.id, "Disease", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
					} else {
						String old = preOutput.get(k-1).toString();
						if(old.equals(Label.B.toString())|| old.equals(Label.I.toString())) {
							System.out.println("error "+old+preOutput.get(k));
						} else if(old.equals(Label.L.toString())|| old.equals(Label.U.toString())) {
							if(temp!=null) {
								preEntities.add(temp);
								temp=null;
							}
							temp = new Entity(document.id, "Disease", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
						} else {
							temp = new Entity(document.id, "Disease", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
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
							if(temp==null) {
								System.out.println("error no B before "+preOutput.get(k));
								continue;
							}
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
						temp = new Entity(document.id, "Disease", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
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
							temp = new Entity(document.id, "Disease", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
							if(temp!=null) {
								preEntities.add(temp);
								temp=null;
							}
						} else {//前一个是O，表示一个新实体的开始和结束。
							temp = new Entity(document.id, "Disease", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
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
	
	
	
	public static void postprocessGlobal(Tool tool, ArrayList<Entity> preEntities) {
		ArrayList<Entity> toBeDeleted = new ArrayList<Entity>();
		
		
		for(Entity pre:preEntities) {
			if(tool.stopWord.contains(pre.text))
				toBeDeleted.add(pre);
			else if(pre.text.length()==1 && CharCode.isLowerCase(pre.text.charAt(0)))
				toBeDeleted.add(pre);
			/*else if(LexicalPattern.getNumNum(pre.text) == pre.text.length()) // all number
				toBeDeleted.add(pre);*/
			else if(LexicalPattern.getAlphaNum(pre.text)==0)
				toBeDeleted.add(pre);
		}
		for(Entity delete:toBeDeleted) {
			preEntities.remove(delete);
		}
	}
	
	// don't need to use StopWord here, we will delete them in the  postprocessGlobal
	public static void postprocessLocal(List<CoreLabel> tokens, Sequence preOutput, int k, Tool tool, 
			ArrayList<Entity> preEntities, BiocDocument document) {
		CoreLabel token = tokens.get(k);
		
		// search disease dict
		if((tool.humando.contains(token.word()) /*|| tool.humando.contains(token.lemma())*/ ||
				tool.ctdmedic.contains(token.word()) /*|| tool.ctdmedic.contains(token.lemma())*/
				) &&
				!tool.stopWord.contains(token.word())) {
			//System.out.println(token.word());
			preEntities.add(new Entity(document.id, "Disease", token.beginPosition(), token.word(),null));
			return;
		} 
		
		
		
	}
	
	public static String preprocess(Tool tool, String instanceOutDir, BiocDocument document) throws Exception {
		SentenceSplitter sentSplit = tool.sentSplit;
		TokenizerFactory<CoreLabel> tokenizerFactory = tool.tokenizerFactory;
		MaxentTagger tagger = tool.tagger;
		//BiocXmlParser xmlParser = tool.xmlParser;
		//DrugBank drugbank = tool.drugbank;
		Dictionary humando = tool.humando;
		IDictionary dict = tool.dict;
		Morphology morphology = tool.morphology;
		
		// all the instances
		//ArrayList<MalletSequenceTaggerInstance> instanceList = new ArrayList<MalletSequenceTaggerInstance>();
		// each document corresponds to a instance file
		
			ArrayList<MalletSequenceTaggerInstance> instanceList = new ArrayList<MalletSequenceTaggerInstance>();
			
			String content = document.title+" "+document.abstractt;
			//content = content.replaceAll("-", " ");
			int offset = 0;
			// sentence segmentation
			List<String> sentences = sentSplit.splitWithFilters(content);
			for(String sentence:sentences) {
				Sentence sent = new Sentence();
				// Each sentence denotes a instance.
				MalletSequenceTaggerInstance instance = new MalletSequenceTaggerInstance();
				// tokenize
				/*Tokenizer<CoreLabel> tok = tokenizerFactory.getTokenizer(new StringReader(sentence));
				List<CoreLabel> tokens = tok.tokenize();*/
				ArrayList<Segment> given = new ArrayList<Segment>();
				// The code below may face problems, such as 26094, non-hypertensive, the isInsideAGoldEntityAndReturnIt will not work well
				/*Matcher m1 = tool.complexNounPattern.matcher(sentence);
				while(m1.find()) {
					String temp = m1.group();
					int offsetInSent = m1.start();
					given.add(new Segment(null, offset+offsetInSent, offset+offsetInSent+temp.length()));
				}*/
				
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
					// Because the offsets are computed within the current sentence, we need to modify them.
					/*token.setBeginPosition(offset+token.beginPosition());
					token.setEndPosition(offset+token.endPosition());*/
					
					
					// add features to the instance
					//HashMap<String,Double> featureVector = new HashMap<String, Double>();
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
			
		/*CoreLabel token = sent.tokens.get(i);
		
		
		map.put("#WD_"+token.lemma().toLowerCase(),1.0);
		map.put("#POS_"+token.tag(),1.0);
		if(i>=1) {
			map.put("#PRETK_"+sent.tokens.get(i-1).lemma().toLowerCase(),1.0);
			map.put("#PREPOS_"+sent.tokens.get(i-1).tag(), 1.0);
		}
		if(i>=2) {
			map.put("#PPRETK_"+sent.tokens.get(i-2).lemma().toLowerCase(), 1.0);
			map.put("#PPREPOS_"+sent.tokens.get(i-2).tag(), 1.0);
		}
		if(i<=sent.tokens.size()-2) {
			map.put("#NEXTTK_"+sent.tokens.get(i+1).lemma().toLowerCase(),1.0);
			map.put("#NEXTPOS_"+sent.tokens.get(i+1).tag(),1.0);
		}
		if(i<=sent.tokens.size()-3) {
			map.put("#NNEXTTK_"+sent.tokens.get(i+2).lemma().toLowerCase(),1.0);
			map.put("#NNEXTPOS_"+sent.tokens.get(i+2).tag(),1.0);
		}

		{
			String lem = token.lemma().toLowerCase();
			int len = lem.length()>4 ? 4:lem.length();
			map.put("#PREF_"+lem.substring(0, len),1.0);
			map.put("#SUF_"+lem.substring(lem.length()-len, lem.length()),1.0);
		}
		
		POS[] poses = {POS.NOUN, POS.ADJECTIVE};
		for(POS pos:poses) {
			ISynset synset = WordNetUtil.getMostSynset(tool.dict, token.lemma(), pos);
			if(synset!= null) {
				map.put("#HDSYNS_"+synset.getID(),1.0);
			} 

			ISynset hypernym = WordNetUtil.getMostHypernym(tool.dict, token.lemma(), pos);
			if(hypernym!= null) {
				map.put("#HDHYPER_"+hypernym.getID(),1.0);
			}
			
		}
		
		LexicalPattern lpattern = new LexicalPattern();
		lpattern.getAll(token.word());
		if(lpattern.ctUpCase == token.word().length())
			map.put("#UCASE_", 1.0);
		else if(lpattern.ctUpCase == 0)
			map.put("#LCASE_", 1.0);
		else
			map.put("#MCASE_", 1.0);
		
		if(lpattern.ctNum>0)
			map.put("#NUM_", 1.0);
		
		if(lpattern.ctAlpha == 0) // has no alphabet character
			map.put("#ALPHA_", 1.0);
		
		if(tool.stopWord.contains(token.word())) // match the stop word
			map.put("#STOP_", 1.0);

		if(token.word().length()==1 && CharCode.isLowerCase(token.word().charAt(0)))
			map.put("#SLCASE_", 1.0); // has only one lowercase character
		
		String[] bad = new String[]{"-induced","-associated","-related"};
		for(int j=0;j<bad.length;j++)
			if(token.word().indexOf(bad[j]) != -1) {
				map.put("#TRIG_", 1.0);
				break;
			}

		if((tool.humando.contains(token.word()) || tool.ctdmedic.contains(token.word())) 
				&& !tool.stopWord.contains(token.word())) {
			map.put("#DICTD_", 1.0);
		}
				
		if(token.word().length()>1 && it's a all upper token 
				(LexicalPattern.getUpCaseNum(token.word()) == token.word().length() || // has a "(" and ")" around it
				((i>0 && sent.tokens.get(i-1).word().equals("(") && i<sent.tokens.size()-1 && sent.tokens.get(i+1).word().equals(")"))))
				) {
			map.put("#ABBR_"+token.lemma().toLowerCase(), 1.0);
		}
		
		if(sent.root != null) {
			int tempIndex = token.index() -1;
			Tree node = sent.leaves.get(token.index()-1);
			Tree ancestor = node.ancestor(2, sent.root);
			map.put("#PARPHTP_"+ancestor.value(), 1.0);
			map.put("#PARDEPTH_", ancestor.depth()*1.0/10);
		}
		
		if(sent.depGraph != null) {
			IndexedWord node  = sent.depGraph.getNodeByIndexSafe(token.index());
			if(node != null) {
				List<SemanticGraphEdge> edges = sent.depGraph.incomingEdgeList(node);
				for(int j=0;j<edges.size();j++) {
					SemanticGraphEdge edge = edges.get(j);
					map.put("#HDW_"+edge.getGovernor().lemma().toLowerCase(),1.0);
					map.put("#HDWPOS_"+edge.getGovernor().tag(), 1.0);
					map.put("#RLTN_"+edge.getRelation(), 1.0);
					break;
				}
				List<SemanticGraphEdge> outEdges = sent.depGraph.outgoingEdgeList(node);
				if(!outEdges.isEmpty())
					map.put("#HDRO_", 1.0);
				
				int count = 1;
				for(CoreLabel depRoot:sent.depGraph.getRoots()) {
					if(count == 1)
						map.put("#DEPROOTTK_"+depRoot.lemma().toLowerCase(), 1.0);
					
					if(depRoot.word().equals(token.word()) && 
							depRoot.beginPosition()==token.beginPosition())
					{
						map.put("#DEPROOT_", 1.0);
						break;
					}
					
					count++;
				}

			}
		}*/
		
		
	}

	public static BiocDocument getBiocDocumentByID(String id, ArrayList<BiocDocument> documents) {
		for(BiocDocument document:documents) {
			if(document.id.equals(id))
				return document;
		}
		return null;
	}
	
	
	
		
		public static void testLabel(InstanceList testData, String ser) {
			CRF crf = (CRF)ObjectSerializer.readObjectFromFile(ser);
			long correctLabel = 0;
			long wrongLabel = 0;
		    
		    for (int i = 0; i < testData.size(); i++)
	        {
		    	Sequence trueOutput = (Sequence) testData.get(i).getTarget();
		    	Sequence input = (Sequence)testData.get(i).getData();
		    	Sequence preOutput = crf.transduce (input);
	            for (int j = 0; j < trueOutput.size(); j++)
	            {
	            	if(preOutput.get(j).equals(trueOutput.get(j)))
	            		correctLabel++;
	            	else {
	            		wrongLabel++;
	            		//System.out.println(((FeatureVectorSequence)testData.get(i).getData()).get(j)+" "+trueOutput.get(j)+" "+preOutput.get(j));
	            		
	            	}
	            }
	            
	        }
		    System.out.println("label accuracy is "+correctLabel*1.0/(correctLabel+wrongLabel));
		}
		
		public static CRF train(InstanceList trainingData, String ser, int max_train_times) throws Exception {
		    CRF crf = new CRF(trainingData.getPipe(), (Pipe)null);
		    crf.addOrderNStates(trainingData, new int[]{1}, null,Label.O.toString(), Pattern.compile("\\s"), Pattern.compile(".*"),true);
	        /*for (int i = 0; i < crf.numStates(); i++)
	        	crf.getState(i).setInitialWeight (0);*/
	        for (int i = 0; i < crf.numStates(); i++)
		        crf.getState(i).setInitialWeight (Transducer.IMPOSSIBLE_WEIGHT);
		    crf.getState(Label.O.toString()).setInitialWeight(0.0);
		    /*crf.getState(Label.B_C.toString()).setInitialWeight(0.0);
		    crf.getState(Label.B_D.toString()).setInitialWeight(0.0);*/
	        
		    CRFTrainerByLabelLikelihood crfTrainer = new CRFTrainerByLabelLikelihood (crf);
		    crfTrainer.setGaussianPriorVariance(1.0);
		    
		    /*crfTrainer.setUseSparseWeights(true);
		    crfTrainer.setUseSomeUnsupportedTrick(true);*/
		    
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
		
		public static String textSeparatedByWhitespace(String s){
			s = s.toLowerCase();
			StringBuilder bf = new StringBuilder();
			char[] chars = s.toCharArray();
			for(int i = 0; i < chars.length; i++){				
				if((chars[i]>=0x0061 && chars[i]<=0x007A)||chars[i]==0x0020||(chars[i]>=0x0030 && chars[i]<=0x0039)||chars[i]== 0x0025 || chars[i]==0x002D)
					bf.append(chars[i]);
				else {
					if( i<chars.length-1 ){
						if(chars[i] == 0x002E)//chars[i] ='.'and chars[i+1]is not space
							if(chars[i+1] != 0x0020)
								bf.append(chars[i]);
							else 
								bf.append(" " + chars[i]);							
						else
							if(chars[i+1] == 0x0020)
								bf.append(" " + chars[i]);
							else
					            bf.append(" " + chars[i] + " ");
					}
					else
						bf.append(" " + chars[i]);
				}
				
			}
			return bf.toString();
		}
		public  static void processFileFormat(Tool tool, String filePath,List<BiocDocument>documents){
			TokenizerFactory<CoreLabel> tokenizerFactory = tool.tokenizerFactory;	
			Morphology morphology = tool.morphology;
			try{
			BufferedWriter bwTrain = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(filePath),"UTF-8"));
			//preprocess  brown input file
			for(BiocDocument document:documents) {	 
				String content = document.title + " " + document.abstractt;
				StringBuilder contentLemma = new StringBuilder();
				//tokenize
				Tokenizer<CoreLabel> tok = tokenizerFactory.getTokenizer(new StringReader(content));
				List<CoreLabel> tokens = tok.tokenize();
				//lemma
				for(int i=0;i<tokens.size();i++) {
					morphology.stem(tokens.get(i));
				}			
				// output 
				for(CoreLabel token : tokens){
					contentLemma.append(" "+token.lemma());						
				}
//				content = textSeparatedByWhitespace(content); 
				bwTrain.write(contentLemma.toString());
				bwTrain.write('\n');
				bwTrain.flush();
			}
			bwTrain.close();
		
		}catch(Exception e){
			e.printStackTrace();
		}
	}
		public static void postProcessEntity(Entity preEntity, ArrayList<Entity>preIndividualEntities){
			String preEntityStr = preEntity.text;	
			preEntityStr = preEntityStr.replace(",", " ,");
			// whether it contains conjunction,such as "and","or"
			String[] splitEntity = preEntityStr.split(" "); //
			int[] entityListOffset =new int[splitEntity.length];
			for(int i = 0; i < entityListOffset.length; i++){
				if( i == 0)
					entityListOffset[i] = preEntity.offset;
				else 
					entityListOffset[i] = preEntity.offset + splitEntity[i-1].length();
			}
			List<String> entityList = Arrays.asList(splitEntity);
			int andIndex = entityList.indexOf("and");
			int orIndex = entityList.indexOf("or");
			int orAndIndex = entityList.indexOf("and/or");
		//	int solidusIndex = entityList.indexOf("/");
			//A and B  C  , A C ; B C;
		    if(andIndex!= -1){
		    	// 有逗号情况，至少有两个实体； 无逗号情况，含有两个实体。
		    	//（1）有逗号情况 A,B, C and D E; AE ,BE,CE,DE
		    	if( entityList.size() > 2 && (entityList.size()- andIndex)>2){
		    		int index = -1;
		    		String tempStr = "";
		    		List<Integer> splitIndex = new ArrayList<Integer>();
		    	    for(int i = 0; i < entityList.size();i++){
		    	    	if(i == andIndex)
		    	    		splitIndex.add(i);
		    	    	else if(entityList.get(i).equals(",") && i+1 != andIndex){ //,and
		    	    		     splitIndex.add(i);
		    	    	}
		    	    }
		    	    splitIndex.add(entityList.size()-1);
		    	    for(int i = 0; i < splitIndex.size(); i++){
		    	    	for(int j = index +1; j< splitIndex.get(i); j++){
		    	    		tempStr += entityList.get(j)+" ";
		    	    	}
		    	    	tempStr += entityList.get(entityList.size()-1);
		    	    	tempStr = tempStr.replace(",", "");
		    	    	Entity tempEntity = new Entity(preEntity.id,preEntity.type, entityListOffset[index+1],tempStr,null);
		    	    	preIndividualEntities.add(tempEntity);
		    	    	tempStr = "";
		    	    	index = splitIndex.get(i);
		    	    }
		    	}
		    }	    	   
		     //A or B  C  , A C ; B C;
		    else if(orIndex!= -1){		    	
		    	//有逗号情况 A, B, C or D E; AE ,BE,CE,DE
		    	if( entityList.size() > 2 && (entityList.size()- orIndex)>2){
		    		int index = -1;
		    		String tempStr = "";
		    		List<Integer> splitIndex = new ArrayList<Integer>();
		    	    for(int i = 0; i < entityList.size();i++){
		    	    	if(i == orIndex)
		    	    		splitIndex.add(i);
		    	    	else if(entityList.get(i).equals(",") && i+1 != orIndex){ //,and
		    	    		     splitIndex.add(i);
		    	    	}
		    	    }
		    	    splitIndex.add(entityList.size()-1);
		    	    for(int i = 0; i < splitIndex.size(); i++){
		    	    	for(int j = index +1; j< splitIndex.get(i); j++){
		    	    		tempStr += entityList.get(j)+" ";
		    	    	}
		    	    	tempStr += entityList.get(entityList.size()-1);
		    	    	tempStr = tempStr.replace(",", "");
		    	    	Entity tempEntity = new Entity(preEntity.id,preEntity.type, entityListOffset[index+1],tempStr,null);
		    	    	preIndividualEntities.add(tempEntity);
		    	    	tempStr = "";
		    	    	index = splitIndex.get(i);
		    	    }
		    	}
		    }  	 
		
		  //A and/or B  C  , A C ; B C;
		    else if(orAndIndex!= -1){		    	
		    	//有逗号情况 A, B, C or D E; AE ,BE,CE,DE
		    	if( entityList.size() > 2 && (entityList.size()- orAndIndex)>2){
		    		int index = -1;
		    		String tempStr = "";
		    		List<Integer> splitIndex = new ArrayList<Integer>();
		    	    for(int i = 0; i < entityList.size();i++){
		    	    	if(i == orAndIndex)
		    	    		splitIndex.add(i);
		    	    	else if(entityList.get(i).equals(",") && i+1 != orAndIndex){ //,and
		    	    		     splitIndex.add(i);
		    	    	}
		    	    }
		    	    splitIndex.add(entityList.size()-1);
		    	    for(int i = 0; i < splitIndex.size(); i++){
		    	    	for(int j = index +1; j< splitIndex.get(i); j++){
		    	    		tempStr += entityList.get(j)+" ";
		    	    	}
		    	    	tempStr += entityList.get(entityList.size()-1);
		    	    	tempStr = tempStr.replace(",", "");
		    	    	Entity tempEntity = new Entity(preEntity.id,preEntity.type, entityListOffset[index+1],tempStr,null);
		    	    	preIndividualEntities.add(tempEntity);
		    	    	tempStr = "";
		    	    	index = splitIndex.get(i);
		    	    }
		    	}
		    } 
		    //A B ; A, B
		    else if(isMatchCompositeEntity(preEntity)){
		    	for(int i=0; i< splitEntity.length; i++){
		    		Entity singularEntity = new Entity(preEntity.id,preEntity.type, entityListOffset[i],splitEntity[i],null);
		    		preIndividualEntities.add(singularEntity);
		      }
		    }
		    else{
		    	preIndividualEntities.add(preEntity);
		    }
			
		}
		public static boolean isMatchCompositeEntity(Entity preEntity){
			String[] compositeEntity = {"hemorrhagic cystitis","haemorrhagic myocarditis","necrotizing myopathy",
					"hemorrhagic infarct","hyperglycemic acidotic coma" ,"cholestatic hepatitis","necrotic blisters","eosinophilic myocarditis","myxedemic coma","intestinal angioedema","adenovirus pneumonia","hypersensitivity pneumonitis","hemorrhagic strokes","hemorrhage cystitis","choreatiform hyperkinesias","granulomatous hepatitis"};
			  for(int i =0; i < compositeEntity.length; i++){
			    	if(preEntity.text.equals(compositeEntity[i]))
			    		return true;  	
			    }
			  return false;
		}
		
			
}

