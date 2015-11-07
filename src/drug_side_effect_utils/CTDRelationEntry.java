package drug_side_effect_utils;


public class CTDRelationEntry {
	public String chemicalName;
	public String diseaseName;
	
	@Override
	public boolean equals(Object obj) {
		if(obj == null || !(obj instanceof CTDRelationEntry))
			return false;
		CTDRelationEntry o = (CTDRelationEntry)obj;
		if(this.chemicalName.equals(o.chemicalName) && this.diseaseName.equals(o.diseaseName))
			return true;
		else 
			return false;
	}
	
	@Override
	public int hashCode() {
	    return chemicalName.hashCode()+diseaseName.hashCode();
		
	}
	
	@Override
	public String toString() {
		return chemicalName+" , "+diseaseName;
	}
}
