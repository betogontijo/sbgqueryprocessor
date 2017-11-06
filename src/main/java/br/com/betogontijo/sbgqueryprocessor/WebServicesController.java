package br.com.betogontijo.sbgqueryprocessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
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

	@RequestMapping(value = "/getData/{query}", method = RequestMethod.GET)
	public String getData(@PathVariable String query) {
		List<Object> data = new ArrayList<Object>();
		Long responseTime = 0L;
		StringBuilder fetchStatistics = new StringBuilder("\"? results (? seconds)\"");
		data.add(fetchStatistics);
		Map<Integer, QueryItem> result = new HashMap<Integer, QueryItem>();
		if (query != null && !query.equals("null")) {
			responseTime = System.currentTimeMillis();
			List<String> list = new ArrayList<String>();
			Matcher m = Pattern.compile("([^\"]\\S*|\".+?\")\\s*").matcher(query);
			while (m.find()) {
				list.add(m.group(1)); // Add .replace("\"", "") to remove
										// surrounding quotes.
			}
			for (String string : list) {
				queryWord(string, result);
			}
			for (Entry<Integer, QueryItem> entry : result.entrySet()) {
				SbgDocument document = entry.getValue().getDocument();
				JSONObject item = new JSONObject();
				try {
					item.put("title", document.getTitle());
					item.put("uri", document.getUri());
					data.add(item);
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				data.add(item);
			}
		}
		responseTime = System.currentTimeMillis() - responseTime;
		Date date = new Date(responseTime);
		DateFormat formatter = new SimpleDateFormat("s.SSS");
		String dateFormatted = formatter.format(date);
		int resultIndex = fetchStatistics.indexOf("?");
		fetchStatistics.replace(resultIndex, resultIndex + 1, result.size() + "");
		int reponseIndex = fetchStatistics.indexOf("?");
		fetchStatistics.replace(reponseIndex, reponseIndex + 1, dateFormatted);
		return data.toString();
	}

	private void queryWord(String query, Map<Integer, QueryItem> result) {
		Node node = nodeRepository.findByWord(query);
		if (node != null) {
			List<SbgDocument> resultDocs = documentRepository.findByWord(query);
			for (SbgDocument sbgDocument : resultDocs) {
				Integer docId = sbgDocument.getId();
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
				QueryItem queryItem = result.get(docId);
				if (queryItem != null) {
					queryItem.increaseCount();
				} else {
					result.put(docId, new QueryItem(sbgDocument));
				}
			}
		}
	}

	@RequestMapping(value = "/word/{key}", method = RequestMethod.GET)
	public String findBookByTitle(@PathVariable String key) {
		long initialTime = System.currentTimeMillis();
		String output = findAndFormat(key);
		long finishTime = System.currentTimeMillis();

		Date date = new Date(finishTime - initialTime);
		DateFormat formatter = new SimpleDateFormat("ss:SSS");
		String dateFormatted = formatter.format(date);
		output += "Fetching Time: " + dateFormatted;
		return output;
	}

	@Cacheable(value = "word", key = "#key")
	private String findAndFormat(String key) {
		Node node = nodeRepository.findByWord(key);
		String output = "{";
		if (node != null) {
			// Format node to output
			Set<Integer> docRefList = node.getDocRefList();
			Set<int[]> occurrencesList = node.getOccurrencesList();
			Iterator<Integer> docIterator = docRefList.iterator();
			Iterator<int[]> occurrencesIterator = occurrencesList.iterator();
			while (docIterator.hasNext()) {
				output += "(" + docIterator.next() + ",[";
				int[] next = occurrencesIterator.next();
				for (int j = 0; j < next.length; j++) {
					output += next[j] + ",";
				}
				output = output.substring(0, output.length() - 1) + "]),";
			}
			output = output.substring(0, output.length() - 1);
		}
		output += "} ";
		return output;
	}
}