package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.util.Utils;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.PageNode;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

public class CypherQuery extends Toadlet implements LinkEnabledCallback {

	private String path;
	private WebOfTrust wot;

	public CypherQuery(HighLevelSimpleClient client, String URLPath, WebOfTrust main) {
		super(client);
		this.path = URLPath;
		this.wot = main;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		String query = request.getParam("query");
		
		if (query.isEmpty())
		{
			PageNode mPageNode = ctx.getPageMaker().getPageNode("LCWoT - cypher queries", true, true, ctx);
			mPageNode.addCustomStyleSheet(WebOfTrust.basePath + "/WebOfTrust.css");
			HTMLNode contentDiv = new HTMLNode("div");
			contentDiv.addAttribute("id", "WebOfTrust_query");

			HTMLNode form = new HTMLNode("form");
			form.addAttribute("action", "");
			form.addAttribute("method", "post");
			
			HTMLNode textarea = new HTMLNode("textarea");
			textarea.addAttribute("name", "query");
			textarea.addAttribute("cols", "60");
			textarea.addAttribute("rows", "20");
			textarea.setContent("Please enter a valid neo4j cypher query.");
			
			form.addChild(textarea);
			form.addChild(Utils.getInput("submit", "", "submit"));
			contentDiv.addChild(form);
			mPageNode.content.addChild(contentDiv);

			writeReply(ctx, 200, "text/html", "OK", mPageNode.outer.generate());
		}
		else
		{
			generateCyptherResult(ctx, query);	
		}
		
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException 
	{
		String query = request.getPartAsStringFailsafe("query", 100000);
		generateCyptherResult(ctx, query);
	}

	private void generateCyptherResult(ToadletContext ctx, String query) throws ToadletContextClosedException, IOException
	{
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeReply(ctx, 403, "text/plain", "forbidden", "Your host is not allowed to access this page.");
			return;
		}
		
		// let's execute a query now
		ExecutionEngine engine = new ExecutionEngine( wot.getDB() );
		ExecutionResult result = engine.execute(query);
		
		String output = "";
		boolean firstRow = true;
		for ( Map<String, Object> row : result )
		{
		    if (firstRow)
		    {
				for(String key : row.keySet())	output += key + "\t";
		    	output += "\n";
		    	firstRow = false;
		    }	

			for ( Entry<String, Object> column : row.entrySet() )
		    {
		        output += column.getValue() + "\t ";
		    }
		    output += "\n";
		}
	
		writeReply(ctx, 200, "text/plain; charset=utf-8", "cypher results", output);
		return;
	}

	@Override
	public boolean isEnabled(ToadletContext arg0) {
		return true;
	}

	@Override
	public String path() {
		return this.path;
	}

}
