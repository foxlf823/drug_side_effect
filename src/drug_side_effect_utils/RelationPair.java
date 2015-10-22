package drug_side_effect_utils;

public class RelationPair {
	String p1;
	String p2;
	
	public RelationPair(String p1, String p2) {
		this.p1 = p1;
		this.p2 = p2;
	}
	
	@Override
	public boolean equals(Object obj) { // withou considering case or direction
		if(obj == null || !(obj instanceof RelationPair))
			return false;
		RelationPair o = (RelationPair)obj;
		if(this.p1.equalsIgnoreCase(o.p1) && this.p2.equalsIgnoreCase(o.p2))
			return true;
		else if(this.p1.equalsIgnoreCase(o.p2) && this.p2.equalsIgnoreCase(o.p1))
			return true;
		else 
			return false;
	}
	
	@Override
	public int hashCode() {
		// TODO Auto-generated method stub
		return p1.hashCode()+p2.hashCode();
	}
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return p1+"-"+p2;
	}
}
