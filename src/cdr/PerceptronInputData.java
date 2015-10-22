package cdr;

import gnu.trove.TIntArrayList;

import java.io.Serializable;
import java.util.ArrayList;

import cn.fox.nlp.Sentence;

public class PerceptronInputData implements Serializable{
	
	private static final long serialVersionUID = 4107472666142847362L;
	// Usually, "tokens" is like "Jobs" "founded" "Apple" "!" .
	public ArrayList<String> tokens;
	public TIntArrayList offset; // the offset that each token start at and the first offset may not be 0 
	public Sentence sentInfo; // the information(pos, syntactic, dep) about the sentence which denotes this PerceptronInputData
	public String id; // the id which document contains this sentence
	public int sentIdx; // the sentence index
	
	public PerceptronInputData(String id, int sentIdx) {
		tokens = new ArrayList<String>();
		offset = new TIntArrayList();
		this.id = id;
		this.sentIdx = sentIdx;
	}
	
	

	@Override
	public String toString() {
		String s = "words: "+tokens.get(0);
		for(int i=1;i<tokens.size();i++)
			s += ", "+tokens.get(i);
		s+="\n";
		return s;
	}
}
