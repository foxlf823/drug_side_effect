package deprecated;

import gnu.trove.TObjectDoubleHashMap;

import java.io.Serializable;

import drug_side_effect_utils.Entity;

public class RelationInstance implements Serializable{

	private static final long serialVersionUID = 4074261137769418574L;
	public Entity former;
	public Entity latter;
	public TObjectDoubleHashMap<String> map;
	public String label;
	public RelationInstance(Entity former, Entity latter, TObjectDoubleHashMap<String> map, String label) {
		super();
		this.former = former;
		this.latter = latter;
		this.map = map;
		this.label = label;
	}
	
	
}
