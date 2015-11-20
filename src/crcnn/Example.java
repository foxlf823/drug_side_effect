package crcnn;

import java.util.List;

import gnu.trove.TIntArrayList;


public class Example {
	// List denotes one or more sentences.
	// TIntArrayList denotes all the word IDs in a sentence.
	List<TIntArrayList> featureIDs;
	int label; // 0-not CID, 1-CID 
	String drugMesh;
	String diseaseMesh;
	
	public Example(String drugMesh, String diseaseMesh) {
		this.drugMesh = drugMesh;
		this.diseaseMesh = diseaseMesh;
	}
}
