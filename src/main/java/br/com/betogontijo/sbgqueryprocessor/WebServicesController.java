package br.com.betogontijo.sbgqueryprocessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import br.com.betogontijo.sbgbeans.crawler.documents.SbgDocument;
import br.com.betogontijo.sbgbeans.crawler.repositories.SbgDocumentRepository;
import br.com.betogontijo.sbgbeans.indexer.documents.Node;
import br.com.betogontijo.sbgbeans.indexer.repositories.NodeRepository;
import br.com.betogontijo.sbgbeans.utils.WordUtils;

@RestController
@EnableMongoRepositories("br.com.betogontijo.sbgbeans")
public class WebServicesController {

	@Autowired
	NodeRepository nodeRepository;

	@Autowired
	SbgDocumentRepository documentRepository;

	String index = null;

	int snippetRange = 10;

	@RequestMapping(value = "/*", method = RequestMethod.GET)
	public String all() throws IOException {
		return getIndex();
	}

	@RequestMapping(value = "/search-result", method = RequestMethod.GET)
	public String searchResult(@RequestParam(name = "query", required = false) String query) throws IOException {
		return getIndex();
	}

	@RequestMapping(value = "/", method = RequestMethod.GET)
	public String search() throws IOException {
		return getIndex();
	}

	public String getIndex() throws IOException {
		if (index != null) {
			return index;
		}
		InputStream is = getClass().getClassLoader().getResourceAsStream("static/index.html");
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		String line = null;
		index = "";
		while ((line = br.readLine()) != null) {
			index += line;
		}
		return index;
	}

	Long responseTime;

	@RequestMapping(value = "/getData/{query}", method = RequestMethod.GET)
	public String getData(@PathVariable String query) throws JSONException {
		List<Object> data = new ArrayList<Object>();
		responseTime = System.currentTimeMillis();
		if (query != null && !query.equals("null")) {
			Map<String, SbgTerm> result = new HashMap<String, SbgTerm>();
			List<String> terms = splitQuery(query);
			List<Double> termsRank = new ArrayList<Double>();
			int docSize = (int) documentRepository.count();
			for (String term : terms) {
				term = WordUtils.normalize(term);
				int freq = 0;
				for (String string : terms) {
					if (terms.contains(string)) {
						freq++;
					}
				}
				List<SbgTerm> fetchWord = fetchWord(term, result);
				double idf = 0;
				if (fetchWord.size() > 0) {
					idf = log(docSize / fetchWord.size());
				}
				double tf = 1 + log(freq);
				double w = tf * idf;
				for (SbgTerm sbgTerm : fetchWord) {
					sbgTerm.setRank(sbgTerm.getRank() * idf * w);
				}
				termsRank.add(idf * tf);
			}
			for (SbgTerm sbgTerm2 : result.values()) {
				JSONObject jsonObject = new JSONObject();
				jsonObject.put("uri", sbgTerm2.getUri());
				jsonObject.put("title", sbgTerm2.getTitle());
				jsonObject.put("snippet", sbgTerm2.getSnippet());
				jsonObject.put("rank", sbgTerm2.getRank());
				data.add(jsonObject);
			}

			responseTime = System.currentTimeMillis() - responseTime;
			StringBuilder fetchStatistics = new StringBuilder();
			fetchStatistics.append("\"").append(data.size()).append(" results (").append(responseTime).append(" ms)\"");
			Collections.sort(data, new Comparator<Object>() {
				@Override
				public int compare(Object o1, Object o2) {
					JSONObject term1 = (JSONObject) o1;
					JSONObject term2 = (JSONObject) o2;
					try {
						return Double.valueOf(term2.get("rank").toString())
								.compareTo(Double.valueOf(term1.get("rank").toString()));
					} catch (NumberFormatException | JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
						return 0;
					}
				}
			});
			data.add(0, fetchStatistics.toString());
		}

		return data.toString();
	}

	private List<String> splitQuery(String query) {
		List<String> list = new ArrayList<String>();
		Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(query);
		while (m.find()) {
			list.add(m.group(1).replace("\"", ""));
		}
		return list;
	}

	@Cacheable(value = "word")
	private List<SbgTerm> fetchWord(String query, Map<String, SbgTerm> result) {
		String[] items = query.split(" ");
		Node node = nodeRepository.findByWord(items[0]);
		List<SbgTerm> wordResult = new ArrayList<SbgTerm>();
		if (node != null) {
			Map<String, Node> words = new HashMap<String, Node>();
			words.put(node.getWord(), node);
			List<Integer> docRefList = new ArrayList<Integer>(node.getInvertedList().keySet());
			for (int i = 1; i < items.length; i++) {
				String string = items[i];
				Node findByWord = nodeRepository.findByWord(string);
				docRefList.retainAll(findByWord.getInvertedList().keySet());
				words.put(string, findByWord);
			}
			if (docRefList.size() > 0) {
				for (Integer docRef : docRefList) {
					int snippetPos = 0;
					if ((snippetPos = isDocumentValid(1, docRef, items, words,
							node.getInvertedList().get(docRef)[0])) != -1) {
						SbgDocument sbgDocument = documentRepository.findById(docRef);
						SbgTerm sbgTerm = result.get(sbgDocument.getUri());
						int freq = words.get(items[0]).getInvertedList().get(docRef).length;
						double tf = 1 + log(freq);
						if (sbgTerm != null) {
							sbgTerm.setRank(sbgTerm.getRank() + tf);
							wordResult.add(sbgTerm);
						} else {
							SbgTerm term = new SbgTerm();
							term.setTitle(getDocumentTitle(sbgDocument));
							term.setUri(sbgDocument.getUri());
							if (snippetPos - snippetRange < 0) {
								snippetPos += (snippetPos - snippetRange) * (-1);
							} else if (snippetPos + snippetRange > sbgDocument.getWordsList().size()) {
								snippetPos += (sbgDocument.getWordsList().size() - snippetPos - snippetRange);
							}
							term.setSnippet(String.join(" ", sbgDocument.getWordsList()
									.subList(snippetPos - snippetRange, snippetPos + snippetRange)));
							term.setRank(tf);
							// TODO Key should use document and term
							result.put(sbgDocument.getUri(), term);
							wordResult.add(term);
						}
					}
				}
			}
		}
		return wordResult;
	}

	private double log(long freq) {
		return Math.log(freq);
	}

	private String getDocumentTitle(SbgDocument sbgDocument) {
		String title = sbgDocument.getTitle();
		if (title == null || title.isEmpty()) {
			try {
				String[] split = new URI(sbgDocument.getUri()).getPath().split("/");
				title = (split[split.length - 1].isEmpty() ? split[split.length - 2] : split[split.length - 1]);
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
			sbgDocument.setTitle(title);
		}
		return title;
	}

	private int isDocumentValid(int i, Integer docRef, String[] items, Map<String, Node> words, int snippetPos) {
		if (items.length == i) {
			return snippetPos;
		}
		int[] lastPos = words.get(items[i - 1]).getInvertedList().get(docRef);
		int[] currentPos = words.get(items[i]).getInvertedList().get(docRef);
		if (currentPos != null) {
			int k;
			int m = 0;
			int l = 0;
			for (int j = 0; j < currentPos.length; j++) {
				k = currentPos[j];
				while (m < k - 1 && l < lastPos.length) {
					m = lastPos[l++];
				}
				if (m == k - 1) {
					return isDocumentValid(++i, docRef, items, words, k);
				}
			}
		}
		return -1;
	}
}