package drug_side_effect_utils;

import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.stats.Counter;
import edu.stanford.nlp.stats.Counters;
import edu.stanford.nlp.stats.IntCounter;

public class BiocDocument {
	public String id;
	/*
	 * Title has an offset of zero, while the abstract is assumed to begin after the title and one space. 
	 */
	public String title;
	public String abstractt;
	
	/*public HashMap<String,Entity> entities;
	public HashMap<String,Relation> relations;*/
	public ArrayList<Entity> entities; // gold
	public HashSet<Relation> relations; // gold
	public ArrayList<Entity> preEntities; // predicted entities
	public HashSet<String> meshOfCoreChemical;
	
	public BiocDocument(String id, String title, String abstractt) {
		super();
		this.id = id;
		this.title = title;
		this.abstractt = abstractt;
		
		entities = new ArrayList<Entity>(); // new HashMap<String,Entity>();
		relations = new HashSet<Relation>(); // new HashMap<String,Relation>();
	}
	
	@Override
	public String toString() {
		// TODO Auto-generated method stub
		return id;
	}
	
	
	public void fillCoreChemical() {
		Counter<String> tokPosCount = new IntCounter<>();
		meshOfCoreChemical = new HashSet<>();
		for(int j=0; j<entities.size(); j++) {
			if(entities.get(j).type.equals("Chemical") && !entities.get(j).mesh.equals("-1")) {
				tokPosCount.incrementCount(entities.get(j).mesh);
				if(this.title.indexOf(entities.get(j).text) != -1)
					meshOfCoreChemical.add(entities.get(j).mesh);
			}
			
		}
								
		List<String> sortedTokens = Counters.toSortedList(tokPosCount, false);
		meshOfCoreChemical.add(sortedTokens.get(0));
		
	}
	
	// Judge whether a token(begin at "start" and end at "end") is inside a gold entity or not.
	public boolean isInsideAGoldEntity(int start, int end) {
		for(Entity temp:entities) {
			if(start >= temp.offset && end <= (temp.offset+temp.text.length()-1))
			{
				return true;
			}
		}
		return false;
	}
	
	public Entity isInsideAGoldEntityAndReturnIt(int start, int end) {
		for(Entity temp:entities) {
			if(start >= temp.offset && end <= (temp.offset+temp.text.length()-1))
			{
				return temp;
			}
		}
		return null;
	}
	
	// Judge whether a token and its previous token are inside the same gold entity
	public boolean isTokenAndPreviousInsideAGoldEntity(int startToken, int endToken, int startPre, int endPre) {
		for(Entity temp:entities) {
			if(	startToken >= temp.offset && startPre >= temp.offset &&
					endToken <= (temp.offset+temp.text.length()-1) && endPre <= (temp.offset+temp.text.length()-1))
			{
				return true;
			}
		}
		return false;
	}
	
	// entity must have mesh
	public boolean twoEntitiesHaveRelation(Entity entity1, Entity entity2) {
		Relation r = new Relation(null, "CID", entity1.mesh, entity2.mesh);
		if(relations.contains(r))
			return true;
		else 
			return false;
		
		
		
	}
	
	// begin and end are the offset at document level
	// An entity should start at 'begin' and end at 'end-1' 
	public List<Entity> getEntitiesInRange(int begin, int end) {
		List<Entity> results = new ArrayList<>();
		for(Entity entity:entities) {
			if(entity.offset>=begin && entity.offset+entity.text.length()<=end)
				results.add(entity);
		}
		return results;
	}
	
	// currently not consider composite roles
	public void dumpToPubtator(String fileDir) {
		try {
			OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(fileDir+"/"+id+".txt"), "utf-8");
			osw.write(id+"|t|"+title+"\n");
			osw.write(id+"|a|"+abstractt+"\n");
			for(int i=0;i<entities.size();i++) {
				Entity entity = entities.get(i);
				osw.write(id+"\t"+entity.offset+"\t"+entity.offsetEnd+"\t"+entity.text+"\t"+entity.type+"\t"+entity.mesh+"\n");
			}
			for(Relation r:relations) {
				// 26094	CID	D008750	D003866
				osw.write(id+"\t"+r.type+"\t"+r.mesh1+"\t"+r.mesh2+"\n");
			}
			
			osw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
