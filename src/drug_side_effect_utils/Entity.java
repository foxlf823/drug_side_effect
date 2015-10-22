package drug_side_effect_utils;

import java.io.Serializable;


public class Entity implements Serializable{

	private static final long serialVersionUID = 3388812314116186691L;
	public String id; // id is unique in a document, sometimes it also denotes the document id
	public String type;
	public int offset; // the first character offset at "doc level"
	public String text;
	public int offsetEnd; // the last character offset+1 at "doc level"
	public String mesh; // the id in the biomedical dictionary
	public int start; // the token index that this segment starts
	public int end; // the token index that this segment ends.
	
	public int sentIdx; // the sentence index which this entity belongs to 
	
	public String goldMeshId; // the gold id in the biomedical dictionary
	public String preMeshId; //forecast Id in MEDIC lexicon
	
	public Entity(String id, String type, int offset, String text, String mesh) {
		super();
		this.id = id;
		this.type = type;
		this.offset = offset;
		this.text = text;
		this.mesh = mesh;
	}
	
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof Entity))
			return false;
		Entity o = (Entity)obj;
		if(o.type.equals(this.type) && o.offset==this.offset && o.text.equals(this.text)/* && o.sentIdx==this.sentIdx*/)
			return true;
		else 
			return false;
	}
	
	@Override
	public int hashCode() {
		int seed = 131; 
		int hash=0;
		hash = (hash * seed) + type.hashCode();  
		hash = (hash * seed) + offset;
		hash = (hash * seed) + text.hashCode();
		/*hash = (hash * seed) + sentIdx;*/
	    return hash;  
	}
	
	@Override
	public String toString() {
		return text+" "+sentIdx+" "+offset+" "+type;
	}
}
