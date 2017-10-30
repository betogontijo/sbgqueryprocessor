package br.com.betogontijo.sbgqueryprocessor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.Set;

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

	private static final String ITEM_TEMPLATE = "{ \"title\": \"?\",	\"uri\": \"?\",	\"desc\": \"?\",	\"code\": \"?\" }";

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
		if (query == null || query.equals("null")) {
			return "[]";
		}
		Node node = nodeRepository.findByWord(query);
		if (node != null) {
			Iterator<Integer> iterator = node.getDocRefList().iterator();
			String data = "[";
			while (iterator.hasNext()) {
				Integer docId = iterator.next();
				SbgDocument findById = documentRepository.findById(docId);
				String title = findById.getTitle();
				if (title == null || title.isEmpty()) {
					try {
						String[] split = new URI(findById.getUri()).getPath().split("/");
						if (split[split.length - 1].isEmpty()) {
							title = split[split.length - 2];
						} else {
							title = split[split.length - 1];
						}
					} catch (URISyntaxException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
				data += ITEM_TEMPLATE.replaceFirst("\\?", title).replaceFirst("\\?", findById.getUri()) + ",";
				if (data.length() > 1200) {
					break;
				}
			}
			data = data.substring(0, data.length() - 1) + "]";
			return data;
		}
		return "[]";
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