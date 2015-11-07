package drug_side_effect_utils;


import java.util.HashSet;



// This class can represent any dictionary which contains some relations between a pair.
public abstract class RelationDict {
	protected HashSet<Pair> set;
	
	public RelationDict() {
		set = new HashSet<Pair>();
	}
	
	public boolean contains(String a, String b) {
		Pair pair = new Pair();
		pair.a = a.toLowerCase();
		pair.b = b.toLowerCase();
		return set.contains(pair);
	}
	
	public abstract void load(String path);
	
	public class Pair {
		public String a;
		public String b;
		
		@Override
		public boolean equals(Object obj) {
			if(obj == null || !(obj instanceof Pair))
				return false;
			Pair o = (Pair)obj;
			if(this.a.equals(o.a) && this.b.equals(o.b))
				return true;
			else 
				return false;
		}
		
		@Override
		public int hashCode() {
		    return a.hashCode()+b.hashCode();
			
		}
		
		@Override
		public String toString() {
			return a+" , "+b;
		}
	}
}


