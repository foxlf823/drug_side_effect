package cdr;

import gnu.trove.TIntObjectHashMap;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;

import cc.mallet.types.SparseVector;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.RelationEntity;

/*
 * You have to implement this class depending on the specific question.
 * The two abstract functions are used from the inner methods.
 * You can decide how to implement them event just meaningless implement.
 */
public class PerceptronOutputData implements Serializable{
	
	private static final long serialVersionUID = 8669519647030994555L;
	/*
	 * This is only used by gold, key denotes the token index and value denotes the feature vector
	 */
	public TIntObjectHashMap<SparseVector> featureVectors1;
	public TIntObjectHashMap<SparseVector> featureVectors2;
	// public ArrayList<SparseVector> featureVectors1;
	// public ArrayList<SparseVector> featureVectors2;
	/*
	 * This is only used by predict, last token corresponds to a feature vector.
	 */
	//public TObjectDoubleHashMap<String> featureVector;
	public SparseVector featureVector1;
	public SparseVector featureVector2;
	
	// each entity denote a segment because a segment may not be a entity.
	// the last segment of ArrayList may not be the currentSegment, because this object can be gold
	// the currentSegment must end at the tokenIndex(in the PerceptronStatus) token.
	public ArrayList<Entity> segments; 
	public HashSet<RelationEntity> relations;
	/* 
	 * Whether the current output data is the same with "other"
	 */
	public boolean isGold; // Indicate whether this object is gold or not
	
	public String id; // the id which document contains this sentence
	public int sentIdx; // the sentence index
	
	public PerceptronOutputData(boolean isGold, String id, int sentIdx) {
		this.isGold = isGold;
		
		featureVectors1 = new TIntObjectHashMap<SparseVector>();
		featureVectors2 = new TIntObjectHashMap<SparseVector>();
		
		segments = new ArrayList<Entity>();
		relations = new HashSet<RelationEntity>();
		
		this.id = id;
		this.sentIdx = sentIdx;
	}
	
	
	
	public int getLastSegmentIndex(int tokenIndex) {
		
		int i=0;
		Entity thisSegment = null;
		do {
			thisSegment = segments.get(i);
			i++;
		}while(tokenIndex>thisSegment.end);
		return i-1;
		
	}
	
	public boolean isIdenticalWith(PerceptronInputData input, PerceptronOutputData other, PerceptronStatus status) {
		PerceptronOutputData other1 = (PerceptronOutputData)other;
		
		if(status.step==2 || status.step==3) {
						
			HashSet<RelationEntity> otherRelation = new HashSet<RelationEntity>(); // other is a gold output data
			// at least one entity belongs to the current sentence
			for(RelationEntity relation:other1.relations) {
				if(relation.entity1.sentIdx < input.sentIdx) {
					if(relation.entity2.end<=status.tokenIndex)
						otherRelation.add(relation);
				} else if(relation.entity2.sentIdx < input.sentIdx) {
					if(relation.entity1.end<=status.tokenIndex)
						otherRelation.add(relation);
				} else { // entity1 and entity2 are both in the current sentence
					if(relation.entity1.end<=status.tokenIndex && relation.entity2.end<=status.tokenIndex)
						otherRelation.add(relation);
				}
				
			}
			if(!relations.equals(otherRelation))
				return false;
			
		}
		
		return true;
	}
	
	public static PerceptronOutputData append(PerceptronOutputData yy, String t, PerceptronInputData xx, int k, int i
			, String id) {
		PerceptronInputData x = xx;
		PerceptronOutputData y = yy;
		PerceptronOutputData ret = new PerceptronOutputData(false, id, yy.sentIdx);
					
		// copy segment
		for(int m=0;m<y.segments.size();m++) {
			ret.segments.add(y.segments.get(m));
		}
		// append segment
		int segmentOffset = x.offset.get(k);
		String segmentText = "";
		for(int m=k;m<=i;m++) {
			int whitespaceToAdd = x.offset.get(m)-(segmentOffset+segmentText.length());
			if(whitespaceToAdd>0) {
				for(int j=0;j<whitespaceToAdd;j++)
					segmentText += " ";
			}	
			segmentText += x.tokens.get(m);
		}
		Entity segment = new Entity(null, t, segmentOffset, segmentText, null);
		segment.start = k;
		segment.end = i;
		ret.segments.add(segment);
		// copy relation
		for(RelationEntity relation:y.relations) {
			ret.relations.add(relation);
		}
		return ret;
	}
	
	
	
	@Override
	public String toString() {
		String s = "segments: "+segments.get(0);
		for(int i=1;i<segments.size();i++)
			s += ", "+segments.get(i);
		s+="\n";
		s+="relations: ";
		int i=0;
		for(RelationEntity relation:relations) {
			if(i!=0)
				s+=", "+relation;
			else
				s+=relation;
			i++;
		}
		s+="\n";
		return s;
	}
}
