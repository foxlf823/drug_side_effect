package drug_side_effect_utils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

public class MeshDict implements Serializable{
	HashMap<String,HashSet<String>> dict;
	
	public MeshDict(String biocDocumentPath, String biocDtd) {
		dict = new HashMap<String,HashSet<String>>();
		BiocXmlParser xmlParser = new BiocXmlParser(biocDtd, BiocXmlParser.ParseOption.BOTH);
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile(biocDocumentPath);
		for(BiocDocument doc:documents) {
			for(Entity entity:doc.entities) {
				if(entity.mesh.equals("-1"))
					continue;
				if(dict.keySet().contains(entity.mesh) ) { // this mesh has existed in the dictionary
					dict.get(entity.mesh).add(entity.text); // add the name to the existed mesh
				} else {
					HashSet<String> names = new HashSet<>(); // make a new mesh and its names
					names.add(entity.text);
					dict.put(entity.mesh, names);
				}
			}
		}
	}
	
	// Given a word and return its mesh, if this word doesn't exist, return -1;
	public String getMesh(String word) {
		for(String mesh:dict.keySet()) {
			if(dict.get(mesh).contains(word))
				return mesh;
		}
		return "-1";
	}
}
