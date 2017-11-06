package br.com.betogontijo.sbgqueryprocessor;

import br.com.betogontijo.sbgbeans.crawler.documents.SbgDocument;

public class QueryItem {
	SbgDocument document;
	int presenceCount = 0;

	QueryItem(SbgDocument document) {
		this.document = document;
		presenceCount++;
	}

	public void increaseCount() {
		presenceCount++;
	}

	public SbgDocument getDocument() {
		return this.document;
	}

	public int getCount() {
		return this.presenceCount;
	}
}
