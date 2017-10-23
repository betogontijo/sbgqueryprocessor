package br.com.betogontijo.sbgqueryprocessor;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import br.com.betogontijo.sbgbeans.indexer.documents.Node;
import br.com.betogontijo.sbgbeans.indexer.repositories.NodeRepository;

@RestController
@EnableMongoRepositories("br.com.betogontijo.sbgbeans")
public class WebServicesController {
	@Autowired
	NodeRepository nodeRepository;

	@RequestMapping(value = "/word/{key}", method = RequestMethod.GET)
	@Cacheable(value = "word", key = "#key")
	public String findBookByTitle(@PathVariable String key) {
		long initialTime = System.currentTimeMillis();
		Node node = nodeRepository.findByWord(key);
		String output = "{";
		if (node != null) {
			// Format node to output
			List<Integer> docRefList = node.getDocRefList();
			List<int[]> occurrencesList = node.getOccurrencesList();
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
		long finishTime = System.currentTimeMillis();

		Date date = new Date(finishTime - initialTime);
		DateFormat formatter = new SimpleDateFormat("ss:SSS");
		String dateFormatted = formatter.format(date);
		output += "} Fetching Time: " + dateFormatted;
		return output;
	}
}