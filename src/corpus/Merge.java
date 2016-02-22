package corpus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import cn.fox.utils.IoUtils;
import cn.fox.utils.Pair;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;
import drug_side_effect_utils.Relation;

public class Merge {

	public static void main(String[] args) throws Exception {
		// merge the result of dnorm and tmchem
		String strRootDir = "E:/cdr/";
		String strDnormDir = strRootDir+"dnorm";
		String strTmchemDir = strRootDir+"tmchem";
		String strMergedDir = strRootDir+"merged";
		String strMapFile = "F:/biomedical resource/cdr_ext_corpus/map.txt";
		
		
		File fileDnormDir = new File(strDnormDir);
		File fileTmchemDir = new File(strTmchemDir);
		File fileMergedDir = new File(strMergedDir);
		IoUtils.clearDirectory(fileMergedDir);
		
		String bioc_dtd = "F:/biocreative/v/cdr/CDR_Training_072115/bioc/BioC.dtd";
		BiocXmlParser xmlParser = new BiocXmlParser(bioc_dtd, BiocXmlParser.ParseOption.BOTH);
		

		HashMap<String, Pair<String, String>> map = new HashMap<>();
		BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(strMapFile), "utf-8"));
		String line = null;
		while((line=br.readLine()) != null) {
			if(line.isEmpty())
				continue;
			
			String[] splitted = line.split("\t");
			map.put(splitted[0], new Pair<String, String>(splitted[1], splitted[2]));	
		}
		br.close();
		
		for(File file:fileDnormDir.listFiles()) {
			ArrayList<BiocDocument> dnormDoc = xmlParser.parseBiocXmlFile(file.getAbsolutePath());
			ArrayList<BiocDocument> tmchemDoc = xmlParser.parseBiocXmlFile(strTmchemDir+"/"+file.getName());
			
			// if a entity is overlapped with another, we get rid of both of them.
			HashSet<Entity> diseaseToDelete = new HashSet<>();
			HashSet<Entity> chemToDelete = new HashSet<>();
			for(int i=0;i<dnormDoc.get(0).entities.size();i++) {
				for(int j=0;j<tmchemDoc.get(0).entities.size();j++) {
					Entity disease = dnormDoc.get(0).entities.get(i);
					Entity chemical = tmchemDoc.get(0).entities.get(j);
					if(disease.offsetEnd>chemical.offset && disease.offset<chemical.offsetEnd) {
						diseaseToDelete.add(disease);
						chemToDelete.add(chemical);
					}
				}
			}
			
			for(Entity i:diseaseToDelete)
				dnormDoc.get(0).entities.remove(i);
			for(Entity j:chemToDelete)
				tmchemDoc.get(0).entities.remove(j);
			
			// merge
			BiocDocument mergeDoc = new BiocDocument(dnormDoc.get(0).id, dnormDoc.get(0).title, dnormDoc.get(0).abstractt);
			ArrayList<Entity> mergeEntities = new ArrayList<>();
			int i=0;
			int j=0;
			while(i<dnormDoc.get(0).entities.size() && j<tmchemDoc.get(0).entities.size()) {
				Entity disease = dnormDoc.get(0).entities.get(i);
				Entity chemical = tmchemDoc.get(0).entities.get(j);
				
				if(disease.offset<chemical.offset) {
					mergeEntities.add(disease);
					i++;
				} else {
					mergeEntities.add(chemical);
					j++;
				}
			}
			
			if(i==dnormDoc.get(0).entities.size()) {
				for(int k=j;k<tmchemDoc.get(0).entities.size();k++)
					mergeEntities.add(tmchemDoc.get(0).entities.get(k));
			} else if(j==tmchemDoc.get(0).entities.size()) {
				for(int k=i;k<dnormDoc.get(0).entities.size();k++)
					mergeEntities.add(dnormDoc.get(0).entities.get(k));
			}
			
			mergeDoc.entities = mergeEntities;
			
			// annotate relations in discourse level
			Pair<String, String> pairChemDis = map.get(mergeDoc.id);
			String pairChem = pairChemDis.a.toLowerCase();
			String pairDis = pairChemDis.b.toLowerCase();
			
			for(int m=0; m<mergeDoc.entities.size(); m++) {
				for(int n=0; n<m;n++) {
					Entity chem = null;
					Entity dis = null;
					if(mergeDoc.entities.get(m).type.equals("Chemical") &&
							mergeDoc.entities.get(n).type.equals("Disease"))
					{
						chem = mergeDoc.entities.get(m);
						dis = mergeDoc.entities.get(n);
					} else if(mergeDoc.entities.get(m).type.equals("Disease") &&
							mergeDoc.entities.get(n).type.equals("Chemical"))
					{
						chem = mergeDoc.entities.get(n);
						dis = mergeDoc.entities.get(m);
					} else
						continue;
					
					if(chem.mesh.equals("-1") || dis.mesh.equals("-1"))
						continue;
					
					String lowerChem = chem.text.toLowerCase();
					String lowerDis = dis.text.toLowerCase();
					if(lowerChem.equals(pairChem) && lowerDis.equals(pairDis)) {
						Relation cid = new Relation();
						cid.type = "CID";
						cid.mesh1 = chem.mesh; 
						cid.type1 = "Chemical";
						cid.mesh2 = dis.mesh;
						cid.type2 = "Disease";
						mergeDoc.relations.add(cid);
					}
					
				}
			}
			
			
			if(mergeDoc.relations.size()>0) {
				mergeDoc.dumpToPubtator(strMergedDir);
			}
		}
	}

}
