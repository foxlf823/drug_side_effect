package deprecated;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.fox.biomedical.Dictionary;
import cn.fox.utils.StopWord;
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
import cn.fox.machine_learning.Perceptron;
import cn.fox.mallet.FeatureVectorMaker;
import cn.fox.mallet.MalletSequenceTaggerInstance;
import cn.fox.mallet.MyCRFPipe;
import cn.fox.nlp.EnglishPos;
import cn.fox.nlp.Segment;
import cn.fox.nlp.Sentence;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.utils.CharCode;
import cn.fox.utils.Evaluater;
import cn.fox.utils.ObjectSerializer;
import cn.fox.utils.ObjectShuffle;
import cn.fox.utils.WordNetUtil;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.LexicalPattern;
import drug_side_effect_utils.Tool;
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

public class DrugDiseaseDetect {
	

	public static enum Label {
		B_D,I_D,B_C,I_C,O
	}

	public static void main(String[] args) throws Exception{
				
		FileInputStream fis = new FileInputStream(args[1]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String pos_tagger = properties.getProperty("pos_tagger");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String drug_dict = properties.getProperty("drug_dict");
		String disease_dict = properties.getProperty("disease_dict");
		String wordnet_dict = properties.getProperty("wordnet_dict");
		String entity_instance_dir = properties.getProperty("entity_instance_dir");
		String bioc_documents = properties.getProperty("bioc_documents");
		String entity_recognizer_ser = properties.getProperty("entity_recognizer_ser");
		String parser = properties.getProperty("parser");
		String jochem_dict = properties.getProperty("jochem_dict");
		String ctdchem_dict = properties.getProperty("ctdchem_dict");
		String ctdmedic_dict = properties.getProperty("ctdmedic_dict");
		String chemical_element_abbr = properties.getProperty("chemical_element_abbr");
		String max_train_times = properties.getProperty("max_train_times");
		String stop_word = properties.getProperty("stop_word");
		
		LexicalizedParser lp = LexicalizedParser.loadModel(parser);
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
	    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'},false, common_english_abbr);
		TokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "ptb3Escaping=false");
		MaxentTagger tagger = new MaxentTagger(pos_tagger);
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.BOTH);
		Dictionary drugbank = new Dictionary(drug_dict, 1);
		Dictionary humando = new Dictionary(disease_dict, 1);
		IDictionary dict = new edu.mit.jwi.Dictionary(new URL("file", null, wordnet_dict));
		dict.open();
		Morphology morphology = new Morphology();
		Dictionary jochem = new Dictionary(jochem_dict, 1);
		Dictionary ctdchem = new Dictionary(ctdchem_dict, 1);
		Dictionary ctdmedic = new Dictionary(ctdmedic_dict, 1);
		Dictionary chemElem = new Dictionary(chemical_element_abbr, 1);
		Pattern complexNounPattern = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9',\\(\\)\\[\\]\\{\\}\\.~\\+]*(-[a-zA-Z0-9',\\(\\)\\[\\]\\{\\}\\.~\\+]+)+[a-zA-Z0-9]");
		StopWord stopWord = new StopWord(stop_word);
		
		// use all entries
		/*DescSaxHandler desc = new DescSaxHandler(100, "F:\\biomedical resource\\MESH\\desc2015.dtd", "F:\\biomedical resource\\MESH\\desc2015.dict.xml");
		SuppSaxHandler supp = new SuppSaxHandler(100, "F:\\biomedical resource\\MESH\\supp2015.dtd", "F:\\biomedical resource\\MESH\\supp2015.dict.xml");
		desc.read();
		supp.read();*/
		
		Tool tool = new Tool();
		tool.sentSplit = sentSplit;
		tool.tokenizerFactory = tokenizerFactory;
		tool.tagger = tagger;
		//tool.xmlParser = xmlParser;
		tool.drugbank = drugbank;
		tool.humando = humando;
		tool.dict = dict;
		tool.morphology = morphology;
		tool.lp = lp;
		tool.gsf = gsf;
		tool.jochem = jochem;
		tool.ctdchem = ctdchem;
		tool.ctdmedic = ctdmedic;
		tool.chemElem = chemElem;
		tool.complexNounPattern = complexNounPattern;
		tool.stopWord = stopWord;
		/*tool.desc = desc;
		tool.supp = supp;*/
		
		//ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile(bioc_documents);
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D://biocreative//2015//cdr_sample//CDR_sample.xml");
		for(BiocDocument document:documents) {
			preprocess(tool, entity_instance_dir, document);
		}
		
		
		nValidate(1, entity_instance_dir, documents, entity_recognizer_ser, tool, Integer.parseInt(max_train_times));
		
	}
	
		
	public static void nValidate(int nFold, String instanceDir, ArrayList<BiocDocument> documents, String ser, 
			Tool tool, int max_train_times) throws Exception {
		double testPercent = 1.0/nFold;
		double trainPercent = 1-testPercent;
		File instanceFiles = new File(instanceDir);
		
		
		double sumPrecision = 0;
		double sumRecall = 0;
		double sumF1 = 0;
		CRF best = null;
		double bestf1 = 0;
		for(int i=0;i<nFold;i++) { // for each fold
			// split the data
			List[] splitted = null;
			if(nFold!=1)
				splitted = ObjectShuffle.split(Arrays.asList(instanceFiles.listFiles()), new double[] {trainPercent, testPercent});
			else {
				splitted = new ArrayList[2];
				splitted[0] = new ArrayList<File>(Arrays.asList(instanceFiles.listFiles()));
				splitted[1] = new ArrayList<File>(Arrays.asList(instanceFiles.listFiles()));
			}
			/*for(int k=0;k<instanceFiles.listFiles().length;k++) { //11773892
				if(instanceFiles.listFiles()[k].getName().indexOf("14659530") != -1)
					splitted[0].remove(instanceFiles.listFiles()[k]);
			}*/
			
			boolean notTrain = false;
			CRF crf = null;
			Pipe p = new MyCRFPipe();
			if(notTrain)  {
				crf = (CRF)ObjectSerializer.readObjectFromFile(ser);
			} else {
				// train
				InstanceList trainData = new InstanceList(p);
				for(int j=0;j<splitted[0].size();j++) {
					File instanceFile = (File)splitted[0].get(j);
					trainData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instanceFile), "UTF-8"),Pattern.compile("^\\s*$"), true));
				}
				crf = train(trainData, null, max_train_times);
			}
			int countPredict = 0;
			int countTrue = 0;
			int countCorrect = 0;
			
			ArrayList<Entity> wrongEntity = new ArrayList<Entity>(); // debug
			ArrayList<Entity> lostEntity = new ArrayList<Entity>();
			/*for(int k=0;k<instanceFiles.listFiles().length;k++) {
				if(instanceFiles.listFiles()[k].getName().indexOf("14659530") != -1 && !splitted[1].contains(instanceFiles.listFiles()[k]))
					splitted[1].add(instanceFiles.listFiles()[k]);
			}*/
			// test
			boolean test = true;
			if(test) {
				for(int j=0;j<splitted[1].size();j++) { // for each test file
					
					File instanceFile = (File)splitted[1].get(j);
					InstanceList testData = new InstanceList(p);
					testData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instanceFile), "UTF-8"),Pattern.compile("^\\s*$"), true));
					
					BiocDocument document = getBiocDocumentByID(instanceFile.getName().substring(0, instanceFile.getName().indexOf(".")), documents);
					
					ArrayList<Entity> preEntities = getEntities(document, testData, crf, tool);

					// after the entired document has been done, some obvious errors can be fixed here to improve the precision
					postprocessGlobal(tool, preEntities);
					
					countPredict+= preEntities.size();
					wrongEntity.addAll(preEntities);
					ArrayList<Entity> goldEntities = document.entities;
					lostEntity.addAll(goldEntities);
					countTrue += goldEntities.size();
					for(Entity preEntity:preEntities) {
						for(Entity goldEntity:goldEntities) {
							
							if(preEntity.equals(goldEntity)) {
								countCorrect++;
								wrongEntity.remove(preEntity);
								lostEntity.remove(goldEntity);
								break;
							}
						}
					}
				}
				
				double precision = Evaluater.getPrecisionV2(countCorrect, countPredict);
				double recall  = Evaluater.getRecallV2(countCorrect, countTrue);
				double f1 = Evaluater.getFMeasure(precision, recall, 1);
				sumPrecision += precision;
				sumRecall += recall;
				sumF1 += f1;
				System.out.println("The loop "+i+" of p,r,f1 are "+precision+" "+recall+" "+f1); 
				
				if(f1>bestf1) {
					bestf1 = f1;
					best = crf;
				}
				
				/*System.out.println("wrong Entity");
				for(Entity wrong:wrongEntity)
					System.out.println(wrong);
				System.out.println("lost Entity");
				for(Entity lost:lostEntity)
					System.out.println(lost);
			
				break; // lifei
	*/	
			}
				
		}
		// save the best
		ObjectSerializer.writeObjectToFile(best, ser);
		System.out.println("The macro average of p,r,f1 are "+sumPrecision/nFold+" "+sumRecall/nFold+" "+sumF1/nFold); 
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
			// for each word, adjust it offset
			int sentenceLength = sentence.length();
			
			// pos tagging
			tool.tagger.tagCoreLabels(tokens);
			// lemma
			for(int k=0;k<tokens.size();k++) {
				tool.morphology.stem(tokens.get(k));
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
			decode(preOutput, preEntities, tokens, tool, document.id, true);
			// after
		}
		
		return preEntities;
	}
	
	public static void findEntityInSentenceWithDict(ArrayList<Entity> oldEntities, String text, Tool tool, int maxTokenNumOfEntity) {
		
		ArrayList<Segment> given = new ArrayList<Segment>();
		ArrayList<Segment> segments = TokenizerWithSegment.tokenize(0, text, given);
		
		for(int begin=0;begin<segments.size();) {
			
			int end=begin;
			for(;end-begin<maxTokenNumOfEntity && end<segments.size();end++) {
				// make a entry
				String entry = "";
				for(int i=begin;i<=end;i++) {
					if(i==begin)
						entry += segments.get(i).word;
					else {
						int whitespaceToAdd = segments.get(i).begin-segments.get(i-1).end;
						for(int q=1;q<=whitespaceToAdd;q++)
							entry += " ";
						entry += segments.get(i).word;
					}
				}
				// search the entry in the dictionaries
				if((tool.humando.contains(entry) || tool.ctdmedic.contains(entry)) 
						&& !tool.stopWord.contains(entry)) {
					boolean overlap = false;
					int offsetBegin = segments.get(begin).begin;
					int offsetEnd = segments.get(end).end;
					for(Entity old:oldEntities) {
						if((offsetEnd>=old.offset && offsetBegin<=old.offsetEnd)) {
							overlap = true;
							break;
						}
					}
					if(!overlap) {
						Entity ae = new Entity(null, "Disease", offsetBegin, entry, null);
						ae.offsetEnd = offsetEnd;
						oldEntities.add(ae);
						break;
					}
				}
				else if((tool.chemElem.containsCaseSensitive(entry) || tool.drugbank.contains(entry) ||
						tool.jochem.contains(entry) || tool.ctdchem.contains(entry))
						&& !tool.stopWord.contains(entry)) {
					boolean overlap = false;
					int offsetBegin = segments.get(begin).begin;
					int offsetEnd = segments.get(end).end;
					for(Entity old:oldEntities) {
						if((offsetEnd>=old.offset && offsetBegin<=old.offsetEnd)) {
							overlap = true;
							break;
						}
					}
					if(!overlap) {
						Entity ae = new Entity(null, "Chemical", offsetBegin, entry, null);
						ae.offsetEnd = offsetEnd;
						oldEntities.add(ae);
						break;
					}
				}
			}
			
			
			if(end-begin==maxTokenNumOfEntity) {
				begin++;
			}
			else {
				begin = end+1;
			}
		}
		
		
	}
	
	public static void decode(Sequence preOutput, ArrayList<Entity> preEntities, List<CoreLabel> tokens, Tool tool, 
			String documentID, boolean isPostProcess) throws Exception {
		Entity temp = null;
		String lastLabel = "";
		for(int k=0;k<preOutput.size();k++) {
			if(preOutput.get(k).equals(Label.B_D.toString()) || preOutput.get(k).equals(Label.B_C.toString())) {
				if(temp!=null) {
					preEntities.add(temp);
					temp=null;
				}
				String type = preOutput.get(k).equals(Label.B_C.toString()) ? "Chemical":"Disease";
				temp = new Entity(null, type, tokens.get(k).beginPosition(), tokens.get(k).word(),null);
			} else if(preOutput.get(k).equals(Label.I_D.toString())) {
				if(temp == null) {
					// error predication(I_D come first)
					System.out.println("error predication(I_D come first): "+documentID+" "+tokens.get(k).beginPosition()+" "+tokens.get(k).word());
				} else if(lastLabel.equals(Label.B_C.toString()) || lastLabel.equals(Label.I_C.toString())) {
					// error predication(unconsistent)
					System.out.println("error predication(unconsistent I_D): "+documentID+" "+tokens.get(k).beginPosition()+" "+tokens.get(k).word());
					if(temp!=null) { // but we still keep the previous result
						preEntities.add(temp);
						temp=null;
					}
				} else {							
					int whitespaceToAdd = 0;
					if(k>=1) 
						whitespaceToAdd = tokens.get(k).beginPosition()-tokens.get(k-1).endPosition();
					for(int q=1;q<=whitespaceToAdd;q++)
						temp.text += " ";
					temp.text += tokens.get(k).word();
				} 
			} else if(preOutput.get(k).equals(Label.I_C.toString())) {
				if(temp == null) {
					// error predication(I_C come first)
					System.out.println("error predication(I_C come first): "+documentID+" "+tokens.get(k).beginPosition()+" "+tokens.get(k).word());
				} else if(lastLabel.equals(Label.B_D.toString()) || lastLabel.equals(Label.I_D.toString())) {
					// error predication(unconsistent)
					System.out.println("error predication(unconsistent I_C): "+documentID+" "+tokens.get(k).beginPosition()+" "+tokens.get(k).word());
					if(temp!=null) { // but we still keep the previous result
						preEntities.add(temp);
						temp=null;
					}
				} else {							
					int whitespaceToAdd = 0;
					if(k>=1) 
						whitespaceToAdd = tokens.get(k).beginPosition()-tokens.get(k-1).endPosition();
					for(int q=1;q<=whitespaceToAdd;q++)
						temp.text += " ";
					temp.text += tokens.get(k).word();
				} 
				
			} else if(preOutput.get(k).equals(Label.O.toString())){
				if(temp!=null) {
					preEntities.add(temp);
					temp=null;
				}
				// if the label is O, we use some rules to improve the recall
				if(isPostProcess)
					postprocessLocal(tokens, preOutput, k, tool, preEntities);
			} else {
				throw new Exception("wrong state: "+preOutput.get(k));
			}
		
			lastLabel = (String)preOutput.get(k);
		}
		
		if(temp!=null) {
			preEntities.add(temp);
		}
	}
	
	public static void postprocessGlobal(Tool tool, ArrayList<Entity> preEntities) {
		ArrayList<Entity> toBeDeleted = new ArrayList<>();
		
		
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
	public static void postprocessLocal(List<CoreLabel> tokens, Sequence preOutput, int k, Tool tool, ArrayList<Entity> preEntities) {
		CoreLabel token = tokens.get(k);
		/*if(token.word().equals("LA") && token.beginPosition()==221)
			System.out.print("");*/
		// search chem element
		if(tool.chemElem.containsCaseSensitive(token.word())) {
			preEntities.add(new Entity(null, "Chemical", token.beginPosition(), token.word(),null));
			return;
		}
		// search chem dict
		if((tool.drugbank.contains(token.word()) || tool.drugbank.contains(token.lemma()) ||
				tool.jochem.contains(token.word()) || tool.jochem.contains(token.lemma()) ||
				tool.ctdchem.contains(token.word()) || tool.ctdchem.contains(token.lemma())) &&
				!tool.stopWord.contains(token.word())) {
			preEntities.add(new Entity(null, "Chemical", token.beginPosition(), token.word(),null));
			return;
		} 
		// search disease dict
		if((tool.humando.contains(token.word()) || tool.humando.contains(token.lemma()) ||
				tool.ctdmedic.contains(token.word()) || tool.ctdmedic.contains(token.lemma())) &&
				!tool.stopWord.contains(token.word())) {
			preEntities.add(new Entity(null, "Disease", token.beginPosition(), token.word(),null));
			return;
		} 
		// trigger word: if a token contains "-"+trigger, it may be a chemical entity
		String word = token.word().toLowerCase();
		String lemma = token.lemma().toLowerCase();
		String[] triggers = new String[]{"-induced","-associated","-related"};
		for(String trigger:triggers) {
			int pos = -1;
			if((pos = word.indexOf(trigger)) != -1 || (pos = lemma.indexOf(trigger)) != -1) {
				if(token.word().charAt(pos-1) == ')')
					pos --;
				String s = token.word().substring(0,pos);
				if(!tool.stopWord.contains(s)) {
					preEntities.add(new Entity(null, "Chemical", token.beginPosition(), s, null));
					return;
				}
			}
		}
		// coreference: if a token has been regonized as a entity before, it should be now
		for(Entity pre:preEntities) {
			if(pre.text.equalsIgnoreCase(token.word()) || pre.text.equalsIgnoreCase(token.lemma())) {
				preEntities.add(new Entity(null, pre.type, token.beginPosition(), token.word(),null));
				return;
			}
		}
		//  length > 1
		if(token.word().length()>1 && /*it's a all upper token */
				(LexicalPattern.getUpCaseNum(token.word()) == token.word().length() || // has a "(" and ")" around it
				((k>0 && tokens.get(k-1).word().equals("(") && k<tokens.size()-1 && tokens.get(k+1).word().equals(")"))))
				) {
			if(!preEntities.isEmpty()) {
				// the type is the same with the pre-closest entity
				Entity pre = preEntities.get(preEntities.size()-1);
				// if each letter of the token is in the previous entity
				char[] letters = token.word().toLowerCase().toCharArray();
				String preText = pre.text.toLowerCase();
				int i=0;
				int from = 0;  // record the matched postion
				for(;i<letters.length;i++) {
					if((from=preText.indexOf(letters[i], from)) == -1) {
						break;
					} else
						from++;
				}
				if(i==letters.length) {
					preEntities.add(new Entity(null, pre.type, token.beginPosition(), token.word(),null));
					return;
				}
				
			}
			
		}
		
		
	}
	
	public static String preprocess(Tool tool, String instanceOutDir, BiocDocument document) throws Exception {
		SentenceSplitter sentSplit = tool.sentSplit;
		TokenizerFactory<CoreLabel> tokenizerFactory = tool.tokenizerFactory;
		MaxentTagger tagger = tool.tagger;
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
				Tree root = tool.lp.apply(tokens);
				if(sentence.indexOf("Pharmacokinetic analyses")!=-1)
					System.out.print("");
				root.indexLeaves();
				root.setSpans();
				List<Tree> leaves = root.getLeaves();
				
				// depend
				GrammaticalStructure gs = tool.gsf.newGrammaticalStructure(root);
				List<TypedDependency> tdl = gs.typedDependenciesCCprocessed();
			    SemanticGraph depGraph = new SemanticGraph(tdl);
				
				sent.tokens = tokens;
				sent.root = root;
				sent.leaves = leaves;
				sent.depGraph = depGraph;
				
				for(int i=0;i<tokens.size();i++) {
					CoreLabel token = tokens.get(i);
					// Because the offsets are computed within the current sentence, we need to modify them.
					/*token.setBeginPosition(offset+token.beginPosition());
					token.setEndPosition(offset+token.endPosition());*/
					
					
					// add features to the instance
					TObjectDoubleHashMap<String> featureVector = new TObjectDoubleHashMap<String>();
					prepareFeatures(tool, featureVector, sent, i, instance);
					
					instance.data.add(featureVector);
					
					// add labels to the instance
					Label label = null;
					Entity goldEntity = null;
					if((goldEntity=document.isInsideAGoldEntityAndReturnIt(token.beginPosition(), token.endPosition()-1))==null) {
						label = Label.O;
					} else {
						if(i>0) {
							if(document.isTokenAndPreviousInsideAGoldEntity(token.beginPosition(), token.endPosition()-1, tokens.get(i-1).beginPosition(),tokens.get(i-1).endPosition()-1)) {
								label = goldEntity.type.equals("Chemical") ? Label.I_C : Label.I_D;
							} else {
								label = goldEntity.type.equals("Chemical") ? Label.B_C : Label.B_D;
							}
							
						} else {
							label = goldEntity.type.equals("Chemical") ? Label.B_C : Label.B_D;
						}
					}

					instance.target.add(label.toString());
				}
				offset += sentenceLength;
				instanceList.add(instance);
			}
			
			String outPath = instanceOutDir+"//"+document.id+".instance";
			MalletSequenceTaggerInstance.writeAllInstances2File(instanceList, outPath);
			return outPath;
		
		//MalletSequenceTaggerInstance.writeAllInstances2File(instanceList, instanceOutPath);
		
	}
	
	
	public static void prepareFeatures(Tool tool, TObjectDoubleHashMap<String> map, Sentence sent, int i, 
			MalletSequenceTaggerInstance myInstance) throws Exception{
			
		CoreLabel token = sent.tokens.get(i);
		
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
		
		/*if(lpattern.ctHyphen >0)
			map.put("#HYPHEN_", 1.0);
		if(lpattern.ctApostrophe >0)
			map.put("#Apost_", 1.0);
		if(lpattern.ctBracket > 0)
			map.put("#Brac_", 1.0);
		if(lpattern.ctComma > 0)
			map.put("#Comma_", 1.0);*/
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
		if((tool.chemElem.containsCaseSensitive(token.word()) || tool.drugbank.contains(token.word()) ||
				tool.jochem.contains(token.word()) || tool.ctdchem.contains(token.word()))
				&& !tool.stopWord.contains(token.word()))
			map.put("#DICTC_", 1.0);
		
		if(token.word().length()>1 && /*it's a all upper token */
				(LexicalPattern.getUpCaseNum(token.word()) == token.word().length() || // has a "(" and ")" around it
				((i>0 && sent.tokens.get(i-1).word().equals("(") && i<sent.tokens.size()-1 && sent.tokens.get(i+1).word().equals(")"))))
				) {
			map.put("#ABBR_"+token.lemma().toLowerCase(), 1.0);
		}
		
		if(sent.root != null) {
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
		}
		
		/*
		 * Feature: disease, window size, the tree phrasal path from triggers to the current token
		 * Feature: drug, ...
		 */
		/*{
			EnglishPos.Type pos = EnglishPos.getType(token.tag());
			if(pos == EnglishPos.Type.NOUN || pos == EnglishPos.Type.ADJ) {
				// find the trigger word
				String[] diseaseTriggerWords = new String[]{"induce","-induced","-associated","-related"};
				IntPair diseaseTriggerSpan = null;
	OUT1:		for(int j=i-1;j>=0;j--) {
					for(String diseaseTriggerWord:diseaseTriggerWords) {
						if(sent.tokens.get(j).lemma().toLowerCase().indexOf(diseaseTriggerWord) != -1) {
							diseaseTriggerSpan = new IntPair(j, j);
							break OUT1;
						}
					}
					if(j==i-10) break;
				}
				if(diseaseTriggerSpan != null) { // get the path as feature
					Tree node = sent.leaves.get(token.index()-1);
					List<Tree> pPath = StanfordTree.getPhrasePathFromTriggerToNode(sent.root, diseaseTriggerSpan, node);
					if(StanfordTree.phrase.contains(pPath.get(0).value())) {
						String strPath = "";
						for(Tree lPath:pPath) {
							strPath += "_"+lPath.value();
						}
						map.put("#DPPATH"+strPath,1.0);
					} 
				}
				
				// find the trigger word
				String[][] drugTriggerWords = new String[][]{new String[]{"induced", "by"}, new String[]{"caused","by"}, 
						new String[]{"produced", "by"}};
				IntPair drugTriggerSpan = null;
	OUT2:		for(int j=i-1;j>0;j--) {
					for(String[] drugTriggerWord:drugTriggerWords) {
						if(sent.tokens.get(j).word().toLowerCase().equals(drugTriggerWord[1]) &&
								sent.tokens.get(j-1).word().toLowerCase().equals(drugTriggerWord[0])) {	
							drugTriggerSpan = new IntPair(j-1, j);
							break OUT2;
						}
					}
					if(j==i-10) break;
				}
				if(drugTriggerSpan != null) { // get the path as feature
					Tree node = sent.leaves.get(token.index()-1);
					List<Tree> pPath = StanfordTree.getPhrasePathFromTriggerToNode(sent.root, drugTriggerSpan, node);
					if(StanfordTree.phrase.contains(pPath.get(0).value())) {
						String strPath = "";
						for(Tree lPath:pPath) {
							strPath += "_"+lPath.value();
						}
						map.put("#CPPATH"+strPath,1.0);
					} 
				}
			}
		}
		*/
		
		
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
}
