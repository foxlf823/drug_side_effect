package cdr;

import gnu.trove.TIntArrayList;
import gnu.trove.TIntIntHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import cn.fox.machine_learning.Buckshot;
import cn.fox.machine_learning.KMeans;
import cn.fox.math.Matrix;
import cn.fox.math.Normalizer;
import cn.fox.nlp.Segment;
import cn.fox.nlp.Sentence;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.TokenizerWithSegment;
import cn.fox.nlp.Word2Vec;
import cn.fox.nlp.WordVector;
import cn.fox.utils.IoUtils;
import cn.fox.utils.ObjectShuffle;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.Relation;
import drug_side_effect_utils.Tool;
import drug_side_effect_utils.WordClusterReader;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;

public class Test {

	public static void main(String[] args) throws Exception {
		FileInputStream fis = new FileInputStream(args[0]);
		Properties properties = new Properties();
		properties.load(fis);    
		fis.close();

		String train_xml = properties.getProperty("train_xml");
		String dev_xml = properties.getProperty("dev_xml");
		String sample_xml = properties.getProperty("sample_xml");
		String bioc_dtd = properties.getProperty("bioc_dtd");
		String common_english_abbr = properties.getProperty("common_english_abbr");
		String group = properties.getProperty("group");
		String pos_tagger = properties.getProperty("pos_tagger");
		String vector = properties.getProperty("vector");
		String train_dev_xml = properties.getProperty("train_dev_xml");
		String group_10 = properties.getProperty("group_10");
		
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd,BiocXmlParser.ParseOption.BOTH);
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'}, false, common_english_abbr);
		MaxentTagger tagger = new MaxentTagger(pos_tagger);
		Morphology morphology = new Morphology();
		
		Tool tool = new Tool();
		tool.sentSplit = sentSplit;
		tool.tagger = tagger;
		tool.morphology = morphology;
		
		ArrayList<BiocDocument> trainDoc = xmlParser.parseBiocXmlFile(train_xml);
		
		ArrayList<BiocDocument> devDoc = xmlParser.parseBiocXmlFile(dev_xml);
		//count1(trainDoc, tool);
		count1(devDoc, tool);
		
		// we randomly divide development set into two group, and one for developing, one for testing
		//makeGroupFile(dev_xml, xmlParser, 2, group);
		//makeGroupFile(train_dev_xml, xmlParser, 10, group_10);
		//makeGroupFile(train_xml, xmlParser, 1, "D:/research/temp/biocreative2015/group_train.txt");
		
		//makeGoldAnswerByGroup(group, "D:/research/biocreative/2015/CDR_Dev 0715/CDR_DevelopmentSet.PubTator.txt", "D:/research/temp/biocreative2015/CDR_DevelopmentSet_former.txt",1);
		//makeGoldAnswerByGroup(group, "D:/research/biocreative/2015/CDR_Dev 0715/CDR_DevelopmentSet.PubTator.txt", "D:/research/temp/biocreative2015/CDR_DevelopmentSet_latter.txt",2);
		//makeGoldAnswerByGroup(group, "D:/research/temp/biocreative2015/CDR_Training_Development.txt", "D:/research/temp/biocreative2015/development_pubtator.txt");
		
		//makeBrownClusterData(devDoc, "E:/biocreative2015/dev_for_bc.txt", tool);
		
		//makeW2VCluster(vector, "E:/biocreative2015/w2v_cluster.txt");
		
		/*{
			HashSet<String> groupIds = new HashSet<>();
			BufferedReader groupReader = new BufferedReader(new InputStreamReader(new FileInputStream("D:/research/temp/biocreative2015/group.txt"), "utf-8"));
			String thisLine = null;
			int groupToUse = 1;
			int groupCount = 1;
			while ((thisLine = groupReader.readLine()) != null) {
				if(thisLine.isEmpty()) { // group delimiter
					groupCount++;
					continue;
				}
					
				if(groupCount == groupToUse)
					groupIds.add(thisLine.trim());
			}
			groupReader.close();
			
			File instanceDir = new File("D:/research/temp/biocreative2015/nvalid_instance 窗口3 句法 0715");
			String desDir = "D:/research/temp/biocreative2015/cdr_test_instance";
			for(File dir: instanceDir.listFiles()) {
				if(groupIds.contains(dir.getName()))
					IoUtils.copyDir(dir, new File(desDir+"/"+dir.getName()));
			}
		}*/
		
		
		
		System.out.println();
	}
	
	
	public static void makeW2VCluster(String vector, String output) throws Exception {
		Word2Vec w2v = new Word2Vec();
		w2v.loadWord2VecOutput(vector);
		ArrayList<String> words = new ArrayList<>();
		ArrayList<Matrix> vectors = new ArrayList<>();
		for(String key:w2v.wordSet.keySet()) {
			WordVector wv = w2v.wordSet.get(key);
			words.add(wv.word);
			vectors.add(wv.vector);
		}
		int k = 100;
		// normalize
		for(int i=0;i<vectors.size();i++)
			Normalizer.doVectorNormalizing(vectors.get(i));
		// do Buckshot
		Buckshot bs = new Buckshot(k, vectors);
		ArrayList<Matrix> centroids = bs.doBuckshot();
		
		// do KMeans
		KMeans mk = new KMeans(k, vectors, centroids, 1000);
		mk.getResults();
		
		OutputStreamWriter osw1 = new OutputStreamWriter(new FileOutputStream(output), "utf-8");
		// dump result, i denotes class, j denotes word
		for(int i=0;i<k;i++) {
			osw1.write("##the "+i+" class :\n");
			int line = 0;
			for(int j=0;j<mk.vectors2classes.length;j++) {
				if(i == mk.vectors2classes[j]) { 
					osw1.write(words.get(j)+" ");
					line++;
					if(line==10) {
						osw1.write("\n");
						line = 0;
					}
				}
			}
			osw1.write("\n\n");
		}
		osw1.close();
	}
	
	public static void makeBrownClusterData(ArrayList<BiocDocument> docs, String output, Tool tool) throws Exception {
		OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(output), "utf-8");
		for(BiocDocument doc:docs) {
			String content = doc.title+" "+doc.abstractt;
			List<String> sentences = tool.sentSplit.splitWithFilters(content);
			
			for(String sentence:sentences) {
				
				Sentence mySentence = new Sentence();
				mySentence.text = sentence;
								
				ArrayList<Segment> given = new ArrayList<Segment>();
				ArrayList<Segment> segments = TokenizerWithSegment.tokenize(0, mySentence.text, given);
				List<CoreLabel> tokens = new ArrayList<CoreLabel>();
				for(Segment segment:segments) {
					CoreLabel token = new CoreLabel();
					token.setWord(segment.word);
					token.setValue(segment.word);
					tokens.add(token);
				}
				
				// pos tagging
				tool.tagger.tagCoreLabels(tokens);
				// lemma
				for(int i=0;i<tokens.size();i++)
					tool.morphology.stem(tokens.get(i));
				
				for(CoreLabel token:tokens)
					writer.write(token.lemma().toLowerCase()+" ");
				
				writer.write("\n");
			}
		}
		writer.close();
	}
	
	// because we divide the development set into two group, for evaluation, 
	// a new Pubtator format file with gold answers are needed
	// input is the original Pubtator file
	// output is the new one
	// groupToUse start from 1
	public static void makeGoldAnswerByGroup(String group, String input, String output, int groupToUse) throws Exception {
		HashSet<String> groupIds = new HashSet<>();
		BufferedReader groupReader = new BufferedReader(new InputStreamReader(new FileInputStream(group), "utf-8"));
		String thisLine = null;
		int groupCount = 1;
		while ((thisLine = groupReader.readLine()) != null) {
			if(thisLine.isEmpty()) { // group delimiter
				groupCount++;
				continue;
			}
				
			if(groupCount == groupToUse)
				groupIds.add(thisLine.trim());
		}
		groupReader.close();
		
		HashSet<String> evalFileNum = new HashSet<>();
		BufferedReader origReader = new BufferedReader(new InputStreamReader(new FileInputStream(input), "utf-8"));
		OutputStreamWriter newWriter = new OutputStreamWriter(new FileOutputStream(output), "utf-8");
		thisLine = null;
		while ((thisLine = origReader.readLine()) != null) {
			if(thisLine.isEmpty()) {
				newWriter.write("\n");
				continue;
			}
			
			int pos = thisLine.indexOf("\t");
			if(pos==-1)
				pos = thisLine.indexOf("|t|");
			if(pos==-1)
				pos = thisLine.indexOf("|a|");
			if(pos==-1)
				throw new Exception();
			
			String id = thisLine.substring(0, pos);
			if(groupIds.contains(id)) {
				evalFileNum.add(id);
				newWriter.write(thisLine+"\n");
			}
		}
		origReader.close();
		newWriter.close();
		System.out.println(evalFileNum.size());
	}
	// divide the documents in the given xml into n groups
	public static void makeGroupFile(String xmlPath, BiocXmlParser xmlParser, int n, String output) throws Exception {
		ArrayList<BiocDocument> docs = xmlParser.parseBiocXmlFile(xmlPath);
		ArrayList<String> ids = new ArrayList<>();
		
		for(int i=0;i<docs.size();i++) {
			ids.add(docs.get(i).id);
		}
		
		double[] proportions = new double[n];
		for(int i=0;i<n;i++) {
			proportions[i] = 1.0/n;
		}
		List[] splitted = ObjectShuffle.split(ids, proportions);
				
		OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(output), "utf-8");
		for(int i=0;i<splitted.length;i++) {
			
			for(int j=0;j<splitted[i].size();j++) {
				
				String id = (String)splitted[i].get(j);
				osw.write(id+"\n");
				
			}
				
			osw.write("\n");
		}
		osw.close();
		
		
	}
	
	// count inner-sentence and inter-sentence CID
	public static void count1(ArrayList<BiocDocument> docs, Tool tool) {
		ArrayList<Relation> listInner = new ArrayList<>();
		ArrayList<Relation> listInter = new ArrayList<>();
		ArrayList<Relation> listMiss = new ArrayList<>();
		TIntIntHashMap distribution = new TIntIntHashMap();
		int total = 0;
		
		for(int i=0;i<docs.size();i++) {
			BiocDocument doc = docs.get(i);
			// prerprocess
			String content = doc.title+" "+doc.abstractt;
			List<Sentence> mySentences = new ArrayList<Sentence>();
			List<String> sentences = tool.sentSplit.splitWithFilters(content);
			int offset = 0;
			for(String sentence:sentences) {
				int sentenceLength = sentence.length();
				Sentence mySentence = new Sentence();
				mySentence.text = sentence;
				mySentence.offset = offset;
				mySentence.length = sentenceLength;
				mySentences.add(mySentence);
				
				offset += sentenceLength;
			}
			
			total += doc.relations.size();
			
			for(Relation r:doc.relations) {
				// find all the entities with r.mesh1
				// find the sentence index corresponding the entities
				TIntArrayList sentIdxMesh1 = new TIntArrayList();
				List<Entity> entityMesh1 = new ArrayList<>();
				for(Entity e:doc.entities) {
					if(e.mesh.equals(r.mesh1)) {
						entityMesh1.add(e);
						sentIdxMesh1.add(getSentenceIndexWithEntity(mySentences, e));
					}
				}
				
				// find all the entities with r.mesh2
				// find the sentence index corresponding the entities
				TIntArrayList sentIdxMesh2 = new TIntArrayList();
				List<Entity> entityMesh2 = new ArrayList<>();
				for(Entity f:doc.entities) {
					if(f.mesh.equals(r.mesh2)) {
						entityMesh2.add(f);
						sentIdxMesh2.add(getSentenceIndexWithEntity(mySentences, f));
					}
				}
				if(doc.id.equals("3412544") && r.mesh1.equals("D010615"))
					System.out.println();
				int min = Integer.MAX_VALUE;
				int count = 0;
				// for es1....esn
				for(int g=0;g<sentIdxMesh1.size();g++) {
					// for fs1...fsm
					for(int m=0;m<sentIdxMesh2.size();m++) {
						int dist = Math.abs(sentIdxMesh1.get(g)-sentIdxMesh2.get(m));
						
						if(dist < min) {
							min = dist;
							count++;
						} 
						
					}
				}
				
				
				if(min==0)
					listInner.add(r);
				else if(min==Integer.MAX_VALUE)
					listMiss.add(r);
				else {
					listInter.add(r);
					//System.out.println(doc.id);
					if(distribution.contains(min)) {
						int oldValue = distribution.get(min);
						distribution.put(min, oldValue+1);
					} else {
						distribution.put(min, 1);
					}
				}
				
				
			}
		}
		
		System.out.println("inner relation: "+listInner.size());
		System.out.println("inter relation: "+listInter.size());
		System.out.println("miss relation: "+listMiss.size());
		System.out.println("total relation: "+total);
		System.out.println("distribution: distances, times");
		for(int key:distribution.keys()) {
			System.out.println(key+", "+distribution.get(key));
		}
	}
	
	// Get the sentence which two entities are both in, if not, return null.
	public static Sentence getTwoEntitesSentence(List<Sentence> mySentences, Entity entity1, Entity entity2) {
		for(int i=0;i<mySentences.size();i++) {
			Sentence sent = mySentences.get(i);
			if((entity1.offset>=sent.offset && entity1.offset+entity1.text.length()<=sent.offset+sent.length) &&
				(entity2.offset>=sent.offset && entity2.offset+entity2.text.length()<=sent.offset+sent.length)) {
				return sent;
			}
		}
		return null;
	}
	// Get the id of sentence which contains the entity
	public static int getSentenceIndexWithEntity(List<Sentence> mySentences, Entity entity1) {
		try {
			for(int i=0;i<mySentences.size();i++) {
				Sentence sent = mySentences.get(i);
				if((entity1.offset>=sent.offset && entity1.offset+entity1.text.length()<=sent.offset+sent.length)
					) {
					return i;
				}
			}
			throw new Exception();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}

}
