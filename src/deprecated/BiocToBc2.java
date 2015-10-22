package deprecated;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;

import cn.fox.nlp.SentenceSplitter;
import drug_side_effect_utils.BiocDocument;
import drug_side_effect_utils.BiocXmlParser;
import drug_side_effect_utils.Entity;

public class BiocToBc2 {

	public static void main(String[] args) throws Exception {
		BiocXmlParser xmlParser = new BiocXmlParser("D:\\biocreative\\2015\\CDR_Training\\bioc\\BioC.dtd", BiocXmlParser.ParseOption.BOTH);
		//ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D:\\biocreative\\2015\\CDR_Training\\CDR_TrainingSet.xml");
		ArrayList<BiocDocument> documents = xmlParser.parseBiocXmlFile("D://biocreative//2015//cdr_sample//CDR_sample.xml");
		SentenceSplitter sentSplit = new SentenceSplitter(new Character[]{';'},false, "D:/dict/common_english_abbr.txt");
		String sentenceFile = "D:/biocreative/2015/bc5_banner/train.in";
		String evalFile = "D:/biocreative/2015/bc5_banner/chem.eval";
		
		File sfile = new File(sentenceFile);
		if(sfile.exists())
			sfile.delete();
		File efile = new File(evalFile);
		if(efile.exists())
			efile.delete();
		
		int id = 0;
		for(BiocDocument document: documents) {
			OutputStreamWriter osw = new OutputStreamWriter(new FileOutputStream(sentenceFile, true), "utf-8");
			OutputStreamWriter oswEval = new OutputStreamWriter(new FileOutputStream(efile, true), "utf-8");
			String content = document.title+" "+document.abstractt;
			List<String> sentences = sentSplit.splitWithFilters(content);
			int sentOffset = 0;
			for(String sentence:sentences) {
				String sentBc2 = sentence.trim();
				osw.write(String.format("%08d", id)+" "+sentBc2+"\n");
				
				for(Entity entity:document.entities) {
					if(entity.type.equals("Chemical") && entity.offset>=sentOffset 
							&& entity.offset+entity.text.length()<=sentOffset+sentence.length()) {
						String strEntityBc2 = entity.text.trim();
						int startPos = sentBc2.indexOf(strEntityBc2);
						if(startPos == -1)
							throw new Exception();
						int numOfWhiteSpaceBefore = 0;
						char[] chs = sentBc2.toCharArray();
						for(int j=0;j<startPos;j++) {
							if(chs[j] == ' ')
								numOfWhiteSpaceBefore++;
						}
						int begin = startPos-numOfWhiteSpaceBefore;
						int numOfWhiteSpace = 0;
						for(int j=startPos;j<startPos+strEntityBc2.length();j++) {
							if(chs[j] == ' ')
								numOfWhiteSpace++;
						}
						int end = begin+strEntityBc2.length()-1-numOfWhiteSpace;
						
						oswEval.write(String.format("%08d", id)+"|"+begin+" "+end+"|"+strEntityBc2+"\n");
					}
				}
				
				id++;
				sentOffset += sentence.length();
			}
			osw.close();
			oswEval.close();
		}

	}

}
