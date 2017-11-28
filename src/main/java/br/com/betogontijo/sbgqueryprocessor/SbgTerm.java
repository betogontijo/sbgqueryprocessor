package br.com.betogontijo.sbgqueryprocessor;

import org.json.JSONException;
import org.json.JSONObject;

public class SbgTerm {

	JSONObject jsonObject = new JSONObject();

//	private String title;
//
//	private String uri;
//
//	private String snippet;
//
//	private double rank;

	public String getTitle() throws JSONException {
		return jsonObject.getString("title");
	}

	public void setTitle(String title) throws JSONException {
		jsonObject.put("title", title);
	}

	public String getUri() throws JSONException {
		return jsonObject.getString("uri");
	}

	public void setUri(String uri) throws JSONException {
		jsonObject.put("uri", uri);
	}

	public Double getRank() throws JSONException {
		return jsonObject.getDouble("rank");
	}

	public void setRank(double rank) throws JSONException {
		jsonObject.put("rank", rank);
	}

	public String getSnippet() throws JSONException {
		return jsonObject.getString("snippet");
	}

	public void setSnippet(String snippet) throws JSONException {
		jsonObject.put("snippet", snippet);
	}

	@Override
	public String toString() {
		return jsonObject.toString();
	}
	
}
