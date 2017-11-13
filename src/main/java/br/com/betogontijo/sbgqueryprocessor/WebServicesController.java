package br.com.betogontijo.sbgqueryprocessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

@RestController
@EnableMongoRepositories("br.com.betogontijo.sbgbeans")
public class WebServicesController {

	@Autowired
	NodeRepository nodeRepository;

	@Autowired
	SbgDocumentRepository documentRepository;

	String index = null;

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
	public String getData(@PathVariable String query) {
		Set<Object> data = new HashSet<Object>();
		responseTime = System.currentTimeMillis();
		StringBuilder fetchStatistics = new StringBuilder();
		data.add(fetchStatistics);
		if (query != null && !query.equals("null")) {
			Map<String, SbgTerm> result = new HashMap<String, SbgTerm>();
			List<String> terms = splitQuery(query);
			List<Double> termsRank = new ArrayList<Double>();
			int docSize = (int) documentRepository.count();
			for (String term : terms) {
				int freq = 0;
				for (String string : terms) {
					if (terms.contains(string)) {
						freq++;
					}
				}
				double idf = queryWord(term, data, result, docSize);
				// need the document list here
				double tf = 1 + log(freq);
				termsRank.add(idf * tf);
			}
		}
		responseTime = System.currentTimeMillis() - responseTime;
		fetchStatistics.append("\"").append(data.size() - 1).append(" results (").append(responseTime).append(" ms)\"");
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
	private double queryWord(String query, Set<Object> data, Map<String, SbgTerm> result, int docSize) {
		String[] items = query.split(" ");
		Node node = nodeRepository.findByWord(items[0]);
		double idf = 0;
		if (node != null) {
			Set<Integer> docRefList = nodeRepository.findByWord(items[0]).getDocRefList();
			for (int i = 1; i < items.length; i++) {
				String string = items[i];
				docRefList.retainAll(nodeRepository.findByWord(string).getDocRefList());
			}
			if (docRefList.size() > 0) {
				Iterator<Integer> iterator = docRefList.iterator();
				idf = log(docSize / docRefList.size());
				while (iterator.hasNext()) {
					Integer docId = iterator.next();
					SbgDocument sbgDocument = documentRepository.findById(docId);
					Map<String, int[]> wordsMap = sbgDocument.getWordsMap();
					if (isDocumentValid(1, items, wordsMap)) {
						SbgTerm term = new SbgTerm();
						term.setTitle(getDocumentTitle(sbgDocument));
						term.setUri(sbgDocument.getUri());
						int freq = wordsMap.get(items[0]).length;
						double tf = 1 + log(freq);
						SbgTerm sbgTerm = result.get(sbgDocument.getUri());
						if (sbgTerm != null) {
							sbgTerm.setRank(sbgTerm.getRank() + (tf * idf));
						} else {
							term.setRank(tf * idf);
							result.put(sbgDocument.getUri(), term);
						}
					}
				}
			}
		}
		return idf;
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

	private boolean isDocumentValid(int i, String[] items, Map<String, int[]> wordsMap) {
		if (items.length == i) {
			return true;
		}
		int[] lastPos = wordsMap.get(items[i - 1]);
		int[] currentPos = wordsMap.get(items[i]);
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
					return isDocumentValid(++i, items, wordsMap);
				}
			}
		}
		return false;
	}
}