package drug_side_effect_utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;

public class MEDI extends RelationDict {

	@Override
	public void load(String path) {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(path), "utf-8"));
			String thisLine = null;
			int count = 0;
			while ((thisLine = br.readLine()) != null) {
				if(!thisLine.isEmpty()) {
					if(count==0) {
						count++;
						continue;
					}
					String[] chunks = thisLine.split(",");
					Pair pair = new Pair();
					
					// chemical
					pair.a = chunks[1].indexOf(";") != -1 ? 
							chunks[1].substring(0, chunks[1].indexOf(";")).toLowerCase() : chunks[1].toLowerCase(); 
					// disease
					pair.b = chunks[3].indexOf(";") != -1 ?
							chunks[3].substring(0, chunks[3].indexOf(";")).toLowerCase() : chunks[3].toLowerCase(); 
					set.add(pair);
					count++;
				}
					
			}
			br.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
		
	}
	
}
