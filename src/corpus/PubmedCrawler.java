package corpus;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.fox.biomedical.Sider;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import edu.stanford.nlp.util.PropertiesUtils;

public class PubmedCrawler {
	
	public static Pattern idPattern = Pattern.compile("<Id>(.+?)</Id>");
	public static Pattern titlePattern = Pattern.compile("<ArticleTitle>(.+?)</ArticleTitle>", Pattern.DOTALL);
	public static Pattern abstractPattern = Pattern.compile("<AbstractText.*?>(.+?)</AbstractText>", Pattern.DOTALL);

	public static void main(String[] args) throws Exception {
		HashSet<String> setCdrID = getDocIDOfCDRCorpus();
		String outputDir = "C:/Users/fox/Desktop/cdr ext corpus";
		HashSet<String> existID = new HashSet<>();
		OutputStreamWriter mapPairID = new OutputStreamWriter(new FileOutputStream("C:/Users/fox/Desktop/map.txt"), "utf-8");
		int count = 0;
		
		Sider sider = new Sider("F:/biomedical resource/sider/meddra_adverse_effects.tsv");
		for(Sider.Pair pair:sider.list) {
			String drug = pair.drug.replaceAll("\\s", "+");
			String disease = pair.sideEffect.replaceAll("\\s", "+");
			
			String urlFindID = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?"
					+ "db=pubmed&term="+drug+"[Title/Abstract]"+"+AND+"+disease+"[Title/Abstract]"+"&retmode=xml";
			String ret1 = getURLContent(urlFindID);
			
			
			Matcher matcher = idPattern.matcher(ret1);
			
			while(matcher.find()) {
				String id = matcher.group(1);
				if(setCdrID.contains(id))
					continue;
				if(existID.contains(id))
					continue;
				
				String urlGetByID = "http://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?"
						+ "db=pubmed&id="+id+"&retmode=text&rettype=xml";
				String ret2 = getURLContent(urlGetByID);
				
				String ret3 = id+"|t|";
				matcher = titlePattern.matcher(ret2);
				if(matcher.find()) {
					ret3 += matcher.group(1);
				}
				ret3 += "\n";
				
				ret3 += id+"|a|";
				matcher = abstractPattern.matcher(ret2);
				while(matcher.find()) {
					ret3 += matcher.group(1);
				}
				ret3 += "\n";

				
				OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(outputDir+"/"+id+".txt"), "utf-8");
				osw.write(ret3);
				osw.close();
				
				mapPairID.write(id+"\t"+pair.drug+"\t"+pair.sideEffect+"\n");
				existID.add(id);
				count++;
				break;
			}
			
			/*if(count>=10)
				break;*/
		}
		
		mapPairID.close();
		//System.out.println();
	}
	
	public static HashSet<String> getDocIDOfCDRCorpus() {
		BiocXmlParser xmlParser = new BiocXmlParser("F:/biocreative/v/cdr/CDR_Training_072115/bioc/BioC.dtd", BiocXmlParser.ParseOption.BOTH);
		ArrayList<BiocDocument> trainDocs = xmlParser.parseBiocXmlFile("F:/biocreative/v/cdr/CDR_Data/CDR.Corpus.v010516/CDR_TrainingSet.BioC.xml");
		ArrayList<BiocDocument> devDocs = xmlParser.parseBiocXmlFile("F:/biocreative/v/cdr/CDR_Data/CDR.Corpus.v010516/CDR_DevelopmentSet.BioC.xml");
		ArrayList<BiocDocument> testDocs = xmlParser.parseBiocXmlFile("F:/biocreative/v/cdr/CDR_Data/CDR.Corpus.v010516/CDR_TestSet.BioC.xml");

		HashSet<String> setCdrID = new HashSet();
		for(BiocDocument doc:trainDocs)
			setCdrID.add(doc.id);
		for(BiocDocument doc:devDocs)
			setCdrID.add(doc.id);
		for(BiocDocument doc:testDocs)
			setCdrID.add(doc.id);
		
		return setCdrID;
	}
	
	
	public static String getURLContent(String url) {
		URL u = null;
		URLConnection uc = null;
		BufferedReader reader = null;
		String s = "";
		int tryCount = 0;
		while (tryCount<5) {
			try {
				tryCount++;
				u = new URL(url);
				uc = u.openConnection();
				uc.setReadTimeout(5000);  
				uc.setConnectTimeout(5000);  
				uc.connect();  
				reader = new BufferedReader(new InputStreamReader(new BufferedInputStream(uc.getInputStream())));
				int c = 0;
				while ((c = reader.read()) != -1) {
					s += (char)c;
				}
				reader.close();
				break;
			} catch (Exception e) {
				System.out.println("error: "+url);
				System.out.println("try "+tryCount+" times");
				try {
					Thread.sleep(60000);
				} catch(Exception ee) {
					System.out.println("error: sleep");
				}
				continue;
			}			
		}

		return s;
	}

}
