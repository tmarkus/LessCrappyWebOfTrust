package freenet.plugin.SoneBridge.controller;

import java.io.IOException;
import java.net.URI;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.plugin.SoneBridge.TwitterTracker;
import freenet.support.api.HTTPRequest;

public class SetupPageController extends freenet.plugin.web.HTMLFileReaderToadlet {

	private TwitterTracker tracker;
	private boolean configured = false; 
	private String url = "";
	
	public SetupPageController(HighLevelSimpleClient client, String filepath, String URLPath) {
		super(client, filepath, URLPath);
	}

	public void setTracker(TwitterTracker tracker)
	{
		this.tracker = tracker;
	}

	public void setURL(String url)
	{
		this.url = url;
	}
	
	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
	    Document doc = Jsoup.parse(readFile());
	    
	    if (configured)
	    {
	    	doc.select("#setup").attr("style", "visibility: hidden;");
	    }
	    else
	    {
	    	doc.select("#url").attr("href", url);
	    	doc.select("#done").attr("style", "visibility: hidden;");
	    }
	    
		writeReply(ctx, 200, "text/html", "document", doc.html());
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		String oob = request.getPartAsStringFailsafe("oob", 20000);
		tracker.createAccesToken(oob);
		configured(true);
		handleMethodGET(uri, request, ctx);
	}

	public void configured(boolean is_configured) {
		this.configured = is_configured;
	}

	
	
}
