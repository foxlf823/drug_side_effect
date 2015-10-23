package deprecated;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.process.CoreLabelTokenFactory;
import edu.stanford.nlp.process.PTBTokenizer;
import edu.stanford.nlp.process.Tokenizer;
import edu.stanford.nlp.process.TokenizerFactory;

/**
 * CDT语料特点：#开头部分是说明部分，下面每一行是由这几部分组成，部分之间用\t间隔。
 * Fields (non-OBO):按\t分隔，对应的split数组为：
 * DiseaseName (split[0]) \t DiseaseID (MeSH or OMIM identifier)(split[1]) \t Definition (split[2])\t AltDiseaseIDs (alternative identifiers; '|'-delimited list)(split[3]) \t ParentIDs (identifiers of the parent terms; '|'-delimited list)(split[4]) \t
 * TreeNumbers (identifiers of the disease's nodes; '|'-delimited list)(split[5]) \t ParentTreeNumbers (identifiers of the parent nodes; '|'-delimited list)(split[6]) \t Synonyms ('|'-delimited list)(split[7]) \t
 * SlimMappings (MEDIC-Slim mappings; '|'-delimited list)(split[8])
 * @author Administrator
 *
 */

public class CTDmedicLexicon {
		public  Map<String, String> alternateIDMap; // Maps from alternate IDs to primary IDs
		public  Map<String, String> primaryNameMap;
		public  Map<String,Set<String>> lexiconConcenptId;
		public  boolean checkParents;
		public CTDmedicLexicon() {
			this.checkParents = true;
		}

		public CTDmedicLexicon(boolean checkParents) {
			this.checkParents = checkParents;
		}

		public Map<String, String> getPrimaryNameMap() {
			return primaryNameMap;
		}
	//处理CDT_disease.tsv, 把第一个字符串为分析疾病名，第二个是MeshID，形成一个MeshID到analyzedName（token的集合）映射集
		private void preload(String filename, TokenizerFactory<CoreLabel> tokenizerFactory,Map<String, Set<String>> canonicalNames) throws IOException {
			BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
			
			String line = reader.readLine();
			while (line != null) {
				line = line.trim();
				if (!line.startsWith("#")) {
					String[] split = line.split("\t");
					//tokenizer split[0]
					Tokenizer<CoreLabel> tok = tokenizerFactory.getTokenizer(new StringReader(split[0]));
			        List<CoreLabel> tokens = tok.tokenize();
					Set<String> analyzedName = new HashSet<String>();
					//得到split[0]的每个token的toString，保存到analyzedName。
					for(CoreLabel token:tokens){
						analyzedName.add(tokens.toString());
					}					
					String id = split[1]; // ID
					if (canonicalNames.containsKey(id))
						throw new IllegalArgumentException("Duplicate ID " + id);
					canonicalNames.put(id, analyzedName);//每一行的split[0]得到的Set<String> analyzedName和split[1]得到的Id加到规范的Map<String, Set<String>> canonicalNames映射中。
				}
				line = reader.readLine();
			}
			reader.close();
		}

		public void loadLexicon(String filename) {
			int numConcepts = 0;
			int numNames = 0;
			int numConceptIds = 0;
			int numMeSHIDs = 0;
			int numOMIMIDs = 0;

			//Map<String, Set<String>> namesToConceptIDs = new HashMap<String, Set<String>>();
			alternateIDMap = new HashMap<String, String>();
			primaryNameMap = new HashMap<String, String>();
			lexiconConcenptId = new HashMap<String,Set<String>>();
			Map<String, Set<String>> canonicalNames = new HashMap<String, Set<String>>();//规范名字
			try {				
				TokenizerFactory<CoreLabel> tokenizerFactory = PTBTokenizer.factory(new CoreLabelTokenFactory(), "ptb3Escaping=false");
				preload(filename,tokenizerFactory, canonicalNames);//annonicalNames保存filename中meshID到set<String>analyzedName的map。即规范疾病名称和对应的meshId.
				BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF8"));
				String line = reader.readLine();
				while (line != null) {
					line = line.trim();
					if (!line.startsWith("#")) {
						String[] split = line.split("\t");
						if(split[0].equals("Zygomycosis"))
							System.out.println();
						
						Set<String> names = new HashSet<String>();//保存词典中所有的名字
						names.add(split[0]); // Canonical name规范的名称
						String conceptId = primaryNameMap.get(split[0].toLowerCase());//词典的主要词映射
						if (conceptId == null) {
							conceptId = split[1];
							primaryNameMap.put(split[0].toLowerCase(), conceptId);//每行第一个字段是primary name ,第二个是字段是conceptIds,都用大写表示
						}
						
						String id = split[1]; // ID
						numConcepts++; //统计CDT中concept的个数
						numConceptIds++;
						if (id.startsWith("OMIM:"))
							numOMIMIDs++;
						else
							numMeSHIDs++;
						if (split.length > 2) {
							String[] altIDs = split[2].split("\\|");//split[2]是definition
							for (String altID : altIDs) {
								if (altID.length() > 0) {
									if (altID.startsWith("OMIM:"))
										numOMIMIDs++;
									else
										numMeSHIDs++;
									String previous = alternateIDMap.put(altID, id);
									if (previous != null && !previous.equals(id))
										throw new IllegalArgumentException(altID + "was already associated with " + previous);
								}
							}
						}
						// System.out.println(id + "\t" + split[0]);
						// Alternate names
						if (split.length > 7) {
							Set<Set<String>> parentCanonicalNames = new HashSet<Set<String>>();//规范名称的父节点
							for (String parentId : split[4].split("\\|")) {
								parentCanonicalNames.add(canonicalNames.get(parentId));//每行有个concept，它对应的父节点的concept名称
							}
							for (String name : split[7].split("\\|")) {
								//split[7]是Synonyms同义词
								Tokenizer<CoreLabel> tok = tokenizerFactory.getTokenizer(new StringReader(name));
						        List<CoreLabel> tokens = tok.tokenize();
								Set<String> analyzedName = new HashSet<String>();//split[7]按|分开，然后分词，加入set集合，不重复
								for(CoreLabel token:tokens){
									analyzedName.add(token.toString());
									
								}
								if (checkParents && parentCanonicalNames.contains(analyzedName)) {//如果如果规范的父节点包括后面的同义词，直接输出
									System.out.println("Not adding alternate name " + name + " to concept " + id + " because it is the primary name of a parent");
								} else {
									// System.out.println("\t" + name);
									names.add(name);//如果规范的父节点没有包括后面的同义词，就加入name集合中
								}
							}
						}
						//找出每一行所有的concept名称，然后把名称和ID号对应起来即加入到namesToConceptIDs
						names.remove("");		

						lexiconConcenptId.put(id, names);//词典增加一个ID，和一个conceptName的set，每一行增加lexicon的一个元素，元素类型为conceptId,和conceptId对应的name的set集合。
					}
					line = reader.readLine();
				}
				reader.close();
			
			} catch (IOException e) {
				// TODO Improve exception handling
				throw new RuntimeException(e);
			}
		}
		public Map<String,Set<String>> getIdToConceptNames(){
			return lexiconConcenptId;
		}

		public Map<String, String> getAlternateIDMap() {
			return alternateIDMap;
		}
		/*public static void main(String[] arg) throws Exception{
			String cdtCorpusFile = "E:/biomedicine/BioTrack-3/NCBI Disease Corpus/DNorm-0.0/DNorm-0.0.6/data/CTD_diseases.tsv";
			CTDmedicLexicon cdtLexicon = new CTDmedicLexicon();
			cdtLexicon.loadLexicon(cdtCorpusFile);
			Map<String,Set<String>> idToConceptNames = cdtLexicon.lexiconConcenptId;
 			
		}*/

	}


