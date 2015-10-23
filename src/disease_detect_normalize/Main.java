package disease_detect_normalize;

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

import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.CTDSaxParse;
import drug_side_effect_utils.WordClusterReader;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.LexicalPattern;
import cn.fox.nlp.Sentence;
import drug_side_effect_utils.Tool;
import cn.fox.utils.WordNetUtil;
import deprecated.Normalization;
import cn.fox.nlp.TokenizerWithSegment;
import cn.fox.nlp.Word2Vec;
import cn.fox.nlp.WordVector;
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
import cn.fox.nlp.Segment;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectSerializer;
import cn.fox.utils.StopWord;
import edu.mit.jwi.item.ISynset;
import edu.mit.jwi.item.POS;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import gnu.trove.TObjectDoubleHashMap;

public class Main {
	

	public static enum Label {
		B,I,L,U,O
	}

	public static void main(String[] args) throws Exception{
		FileInputStream fis = new FileInputStream(args[0]); // the property file
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();
		
		boolean preprocessTrain = Boolean.parseBoolean(args[1]);
		boolean preprocessDev = Boolean.parseBoolean(args[2]);
		boolean Train = Boolean.parseBoolean(args[3]);
		
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String pos_tagger = properties.getProperty("pos_tagger");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String disease_dict = properties.getProperty("disease_dict");
		String wordnet_dict = properties.getProperty("wordnet_dict");
		String train_instance_dir = properties.getProperty("train_instance_dir");
		String bioc_documents = properties.getProperty("bioc_documents");
		String entity_recognizer_ser = properties.getProperty("entity_recognizer_ser");
		String ctdmedic_dict = properties.getProperty("ctdmedic_dict");		
		String max_train_times = properties.getProperty("max_train_times");
		String bioc_documents_test = properties.getProperty("bioc_documents_test");
		String test_instance_dir = properties.getProperty("test_instance_dir");
		String entity_out = properties.getProperty("entity_out");
		String ctd_xml = properties.getProperty("ctd_xml");
		String brown_cluster_path = properties.getProperty("brown_cluster_path");
		String vector_cluster = properties.getProperty("vector_cluster");
		// if you use the training and development set as the final training set, open this
		// String train_dev_xml = properties.getProperty("train_dev_xml");
		String stop_word = properties.getProperty("stop_word");
		boolean usePostProcess = Boolean.parseBoolean(properties.getProperty("usePostProcess"));
		int crfState = Integer.parseInt(properties.getProperty("crf_state"));
		
		
		TreebankLanguagePack tlp = new PennTreebankLanguagePack();
	    GrammaticalStructureFactory gsf = tlp.grammaticalStructureFactory();
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'},false, common_english_abbr);
		MaxentTagger tagger = new MaxentTagger(pos_tagger);
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.ONLY_DISEASE);
		
				
		Dictionary humando = new Dictionary(disease_dict, 1);
		edu.mit.jwi.Dictionary dict = new edu.mit.jwi.Dictionary(new URL("file", null, wordnet_dict));
		dict.open();
		Morphology morphology = new Morphology();
		Dictionary ctdmedic = new Dictionary(ctdmedic_dict, 1);
		//Pattern complexNounPattern = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9',\\(\\)\\[\\]\\{\\}\\.~\\+]*(-[a-zA-Z0-9',\\(\\)\\[\\]\\{\\}\\.~\\+]+)+[a-zA-Z0-9]");
		
		BrownCluster entityBC = new BrownCluster(brown_cluster_path, 100);
		WordClusterReader wcr = new WordClusterReader(vector_cluster);
		StopWord stopWord = new StopWord(stop_word);
	
		Tool tool = new Tool();
		tool.sentSplit = sentSplit;
		tool.tagger = tagger;
		tool.humando = humando;
		tool.dict = dict;
		tool.morphology = morphology;
		tool.gsf = gsf;
		tool.ctdmedic = ctdmedic;
		tool.brown = entityBC;
		tool.wcr = wcr;
		tool.stopWord = stopWord;

		
		
		// load the corpus
		ArrayList<BiocDocument> trainDocs = xmlParser.parseBiocXmlFile(bioc_documents);	
		ArrayList<BiocDocument> testDocs = xmlParser.parseBiocXmlFile(bioc_documents_test);	
		
		// pre-process
		if(preprocessTrain) {
			IoUtils.clearDirectory(new File(train_instance_dir));
			for(BiocDocument document:trainDocs) {
				preprocess(tool, train_instance_dir, document, crfState);			
			}  
		}
		if(preprocessDev) {
			IoUtils.clearDirectory(new File(test_instance_dir));
			for(BiocDocument document:testDocs) {
				preprocess(tool, test_instance_dir, document, crfState);			
			}   
		}
		
		List<File> trainFiles =  new ArrayList<File>(Arrays.asList(new File(train_instance_dir).listFiles()));
		List<File> testFiles = new ArrayList<File>(Arrays.asList(new File(test_instance_dir).listFiles()));
		
		
		CRF crf = null;
		
		
		if(Train){
			// train
			Pipe p = new MyCRFPipe();
			InstanceList trainData = new InstanceList(p);
			for(int j=0;j<trainFiles.size();j++) {
				File instanceFile = trainFiles.get(j);
				trainData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instanceFile), "UTF-8"),Pattern.compile("^\\s*$"), true));
			}
			crf = train(trainData, null, Integer.parseInt(max_train_times));
			ObjectSerializer.writeObjectToFile(crf, entity_recognizer_ser);		
		} else {
			crf = (CRF)ObjectSerializer.readObjectFromFile(entity_recognizer_ser);
		}
				
		// prepare for normalization		
		CTDSaxParse ctdSaxParse = new CTDSaxParse(ctd_xml);
		Map<String, String> mapName2Id = ctdSaxParse.buildIdNameMap();
		
		// test
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(entity_out),"UTF-8"));
		for(int i=0;i<testFiles.size();i++) { // for each test file
			File instanceFile = testFiles.get(i);
			InstanceList testData = new InstanceList(crf.getInputPipe());
			testData.addThruPipe(new LineGroupIterator(new InputStreamReader(new FileInputStream(instanceFile), "UTF-8"),Pattern.compile("^\\s*$"), true));
			BiocDocument document = getBiocDocumentByID(instanceFile.getName().substring(0, instanceFile.getName().indexOf(".")), testDocs);
			ArrayList<Entity> preEntities = getEntities(document, testData, crf, tool, usePostProcess, crfState);

			for(Entity preEntity:preEntities) {

				preEntity.preMeshId = normalizeDictLookup(preEntity, mapName2Id);			

				bw.write(preEntity.id +"\t"+preEntity.offset +"\t"+(preEntity.offset+preEntity.text.length())+"\t"+preEntity.text+"\t"+ preEntity.type + "\t"+preEntity.preMeshId);
				bw.write("\n");
											
			}
		}
		bw.close();	

	}
	
	public static String normalizeDictLookup(Entity entity, Map<String, String> mapName2Id) {
		String low = entity.text.toLowerCase();
		if(mapName2Id.keySet().contains(low)) {
			return mapName2Id.get(low);
		} else 
			return "-1";
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
	public static ArrayList<Entity> getEntities(BiocDocument document, InstanceList instanceData, CRF crf, 
			Tool tool, boolean usePostProcess, int crfState) throws Exception{
		ArrayList<Entity> preEntities = new ArrayList<Entity>();
		List<Sentence> sentences = preparePredictInfo(tool, document);
		for(int m=0;m<sentences.size();m++) {
			Instance instance = instanceData.get(m);
			Sentence sentence = sentences.get(m); // we assume a instance corresponds to a sentence
			List<CoreLabel> tokens = sentence.tokens;
			// predicate			
			Sequence preOutput = crf.transduce((Sequence)instance.getData());
			
			if(crfState==3) {
				Entity temp = null;
				for(int k=0;k<preOutput.size();k++) {
					if(preOutput.get(k).equals(Label.B.toString()) ) {
						if(k==0) {
							temp = new Entity(document.id, "Disease", tokens.get(k).beginPosition(), tokens.get(k).word(),null);
						} else {
							String old = preOutput.get(k-1).toString();
							if(old.equals(Label.B.toString())|| old.equals(Label.I.toString())) {
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
					} else if(preOutput.get(k).equals(Label.O.toString())){
						if(k==0) {
							;
						} else {
							String old = preOutput.get(k-1).toString();
							if(old.equals(Label.B.toString())|| old.equals(Label.I.toString())) {
								if(temp!=null) {
									preEntities.add(temp);
									temp=null;
								}
							} 
						}
						// if the label is O, we use some rules to improve the recall
						if(usePostProcess)
							postprocessLocal(tokens, preOutput, k, tool, preEntities, document);
					} else {
						throw new Exception();
					}
	
				}
			} else if(crfState == 5) {
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
						if(usePostProcess)
							postprocessLocal(tokens, preOutput, k, tool, preEntities, document);
					} else {
						throw new Exception();
					}
					
				}
			} else {
				throw new Exception();
			}
			
			
			
		}
		
		return preEntities;
	} 
	
	
	public static void postprocessLocal(List<CoreLabel> tokens, Sequence preOutput, int k, Tool tool, 
			ArrayList<Entity> preEntities, BiocDocument document) {
		CoreLabel token = tokens.get(k);
		
		// search disease dict
		if((tool.humando.contains(token.word()) || tool.humando.contains(token.lemma()) ||
				tool.ctdmedic.contains(token.word()) || tool.ctdmedic.contains(token.lemma())
				) &&
				!tool.stopWord.contains(token.word())) {
			preEntities.add(new Entity(document.id, "Disease", token.beginPosition(), token.word(),null));
			return;
		} 
		
		
		
	}
	
	public static String preprocess(Tool tool, String instanceOutDir, BiocDocument document, int crfState) throws Exception {
		SentenceSplitter sentSplit = tool.sentSplit;
		MaxentTagger tagger = tool.tagger;
		Morphology morphology = tool.morphology;
		
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
				
				// for each word
				int sentenceLength = sentence.length();
				// pos tagging
				tagger.tagCoreLabels(tokens);
				// lemma
				for(int i=0;i<tokens.size();i++) {
					morphology.stem(tokens.get(i));
				}

				sent.tokens = tokens;
				
				
				for(int i=0;i<tokens.size();i++) {
					CoreLabel token = tokens.get(i);
					
					// add features to the instance
					TObjectDoubleHashMap<String> featureVector = new TObjectDoubleHashMap<String>();
					prepareFeatures(tool, featureVector, sent, i, instance);
					
					instance.data.add(featureVector);
					
					// add labels to the instance
					if(crfState == 3) {
						Label label = null;
						Entity goldEntity = null;
						if((goldEntity=document.isInsideAGoldEntityAndReturnIt(token.beginPosition(), token.endPosition()-1))==null) {
							label = Label.O;
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
					} else if(crfState ==5) {
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
					} else {
						throw new Exception();
					}

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
		int window = 2;
		int fixLen = 4;
		// bag-of-word
		map.put("WD_"+token.word(), 1.0);
		map.put("LM_"+token.lemma(),1.0);
		map.put("PO_"+token.tag(),1.0);
		map.put("PA_"+LexicalPattern.pipe(token.word()), 1.0);
		
		int len = token.lemma().length()>fixLen ? fixLen:token.lemma().length();
		map.put("PR_"+token.lemma().substring(0, len),1.0);
		map.put("SF_"+token.lemma().substring(token.lemma().length()-len, token.lemma().length()),1.0);
		
		
		
		for(int j=i-1, count=window;count>0 && j>=0; count--,j--) {
			CoreLabel current = sent.tokens.get(j); 
			int len1 = current.lemma().length()>fixLen ? fixLen:current.lemma().length();
			
			map.put("WD#"+count+"_"+current.word(),1.0); 
			map.put("LM#"+count+"_"+current.lemma(),1.0); 
			map.put("PO#"+count+"_"+current.tag(),1.0); 
			map.put("PA#"+count+"_"+LexicalPattern.pipe(current.word()), 1.0);
			
			map.put("PR#"+count+"_"+current.lemma().substring(0, len1),1.0);
			map.put("SF#"+count+"_"+current.lemma().substring(current.lemma().length()-len1, current.lemma().length()),1.0);
			
			
		}
		for(int j=i+1, count=window;count>0 && j<sent.tokens.size(); count--,j++) {
			CoreLabel current = sent.tokens.get(j); 
			int len1 = current.lemma().length()>fixLen ? fixLen:current.lemma().length();
			
			map.put("WD*"+count+"_"+current.word(),1.0); 
			map.put("LM*"+count+"_"+current.lemma(),1.0); 
			map.put("PO*"+count+"_"+current.tag(),1.0); 
			map.put("PA*"+count+"_"+LexicalPattern.pipe(current.word()), 1.0);
			
			map.put("PR*"+count+"_"+current.lemma().substring(0, len1),1.0);
			map.put("SF*"+count+"_"+current.lemma().substring(current.lemma().length()-len1, current.lemma().length()),1.0);
			
			
		}
		
	
		if(tool.humando.contains(token.word())) {
			map.put("HM_"+token.word(),1.0);
		} else if(tool.humando.contains(token.lemma()))
			map.put("HM_"+token.lemma(),1.0);
		
		if(tool.ctdmedic.contains(token.word())) {
			map.put("CM_"+token.word(),1.0);
		} else if(tool.ctdmedic.contains(token.lemma())) {
			map.put("CM_"+token.lemma(),1.0);
		}
		POS[] poses = {POS.NOUN, POS.ADJECTIVE};
		for(POS pos:poses) {
			ISynset synset = WordNetUtil.getMostSynset(tool.dict, token.lemma(), pos);
			if(synset!= null) {
				map.put("WN_"+synset.getID().getOffset(),1.0);
			} 

			ISynset hypernym = WordNetUtil.getMostHypernym(tool.dict, token.lemma(), pos);
			if(hypernym!= null) {
				map.put("WN_"+hypernym.getID().getOffset(),1.0);
			}
		}
		
		String bc = tool.brown.getPrefix(token.lemma());
		map.put("BN_"+bc, 1.0);
		
		
		String wc = tool.wcr.getCluster(token.lemma());
		map.put("WC_"+wc, 1.0);
		
		
			
		
	}

	public static BiocDocument getBiocDocumentByID(String id, ArrayList<BiocDocument> documents) {
		for(BiocDocument document:documents) {
			if(document.id.equals(id))
				return document;
		}
		return null;
	}
	

	public static CRF train(InstanceList trainingData, String ser, int max_train_times) throws Exception {
	    CRF crf = new CRF(trainingData.getPipe(), (Pipe)null);
	    String startName = crf.addOrderNStates(trainingData, new int[]{1}, null,Label.O.toString(), Pattern.compile("\\s"), Pattern.compile(".*"),true);
        /*for (int i = 0; i < crf.numStates(); i++)
        	crf.getState(i).setInitialWeight (0);*/
        for (int i = 0; i < crf.numStates(); i++)
	        crf.getState(i).setInitialWeight (Transducer.IMPOSSIBLE_WEIGHT);
		crf.getState(startName).setInitialWeight(0.0);
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

