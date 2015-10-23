package drug_side_effect_utils;

import java.util.regex.Pattern;


import cn.fox.biomedical.Dictionary;
import cn.fox.biomedical.Sider;
import cn.fox.machine_learning.BrownCluster;
import cn.fox.machine_learning.Perceptron;
import cn.fox.nlp.SentenceSplitter;
import cn.fox.nlp.Word2Vec;
import cn.fox.stanford.Tokenizer;
import cn.fox.utils.StopWord;
import edu.mit.jwi.IDictionary;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.parser.lexparser.LexicalizedParser;
import edu.stanford.nlp.process.Morphology;
import edu.stanford.nlp.process.TokenizerFactory;
import edu.stanford.nlp.tagger.maxent.MaxentTagger;
import edu.stanford.nlp.trees.GrammaticalStructureFactory;

public class Tool {
	public SentenceSplitter sentSplit;
	public TokenizerFactory<CoreLabel> tokenizerFactory;
	public Tokenizer tokenizer;
	public MaxentTagger tagger;
	//public BiocXmlParser xmlParser;
	public Morphology morphology;
	public LexicalizedParser lp;
	public GrammaticalStructureFactory gsf;
	public IDictionary dict;
	
	public Dictionary drugbank;
	public Dictionary jochem;
	public Dictionary ctdchem;
	
	public Dictionary humando;
	public Dictionary ctdmedic;
	
	
	
	public DescSaxHandler desc;
	public SuppSaxHandler supp;
	
	public Dictionary chemElem;
	
	public Pattern complexNounPattern;
	
	public Sider sider;
	public MeshDict meshDict;

	public BrownCluster brown;
	
	public StopWord stopWord;
	
	public Word2Vec w2v;
	public WordClusterReader wcr;
}
