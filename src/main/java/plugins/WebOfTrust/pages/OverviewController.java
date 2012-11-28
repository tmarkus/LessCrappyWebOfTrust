package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;

//import org.jsoup.Jsoup;
//import org.jsoup.nodes.Document;
//import org.jsoup.nodes.Element;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.ReadableIndex;
import org.neo4j.tooling.GlobalGraphOperations;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;


import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.PageNode;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class OverviewController extends Toadlet implements LinkEnabledCallback {
	protected String path;
	protected String filePath;
	protected GraphDatabaseService db;
	protected ReadableIndex<Node> nodeIndex;
	private WebOfTrust main;
	
	public OverviewController(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, GraphDatabaseService db) {
		super(client);
		this.path = URLPath;
		this.filePath = filepath;
		this.db = db;
		
		nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeHTMLReply(ctx, 403, "forbidden", "Your host is not allowed to access this page.");
			return;
		}
		PageNode mPageNode = ctx.getPageMaker().getPageNode("LCWoT - overview", true, true, ctx);
		try
		{
//			Document doc = Jsoup.parse(readFile());
//			Element stats_div = doc.select("#stats").first();

			long count_identities = 0;
			long count_trust_relations = 0;
			
			GlobalGraphOperations ggo = GlobalGraphOperations.at(db);
			for(Node node : ggo.getAllNodes())
			{
				if (node.hasProperty(IVertex.ID)) count_identities +=1;
			}
			for(Relationship rel : ggo.getAllRelationships())
			{
				if (rel.isType(Rel.TRUSTS)) count_trust_relations +=1;
			}
			HTMLNode list = new HTMLNode("ul");
			list.addChild("li", "Number of identities: " + count_identities);
			list.addChild("li", "Number of trust relations: " + count_trust_relations);
			list.addChild("li", "Number of requests in flight currently: " + main.getRequestScheduler().getInFlightSize());
			list.addChild("li", "Backlog: " + main.getRequestScheduler().getBacklogSize());
			list.addChild("li", "Number of active db connections: " + 666);
//			Element list = doc.createElement("ul");
//			
//			list.appendChild(doc.createElement("li").text("Number of identities: " + count_identities));
//			list.appendChild(doc.createElement("li").text("Number of trust relations: " + count_trust_relations));
//			list.appendChild(doc.createElement("li").text("Number of requests in flight currently: " + main.getRequestScheduler().getInFlightSize()));
//			list.appendChild(doc.createElement("li").text("Backlog: " + main.getRequestScheduler().getBacklogSize()));
//			list.appendChild(doc.createElement("li").text("Number of active db connections: " + 666));
			mPageNode.content.addChild(list);
//			stats_div.appendChild(list);

			mPageNode.content.addChild("h2", "Own identities in local storage");
//			stats_div.append("<h2> Own identities in local storage </h2>");
//			Element own_identities = doc.createElement("ul");
			list = new HTMLNode("ul");
			for(Node identity : nodeIndex.get(IVertex.OWN_IDENTITY, true))
			{
				if (identity.hasProperty(IVertex.NAME) )
				{
					list.addChild("li", (String) identity.getProperty(IVertex.NAME) + "  (" + (String) identity.getProperty(IVertex.ID) + ")");
//					own_identities.appendChild(doc.createElement("li").text((String) identity.getProperty(IVertex.NAME) + "  (" + (String) identity.getProperty(IVertex.ID) + ")"));
				}
			}

			mPageNode.content.addChild(list);
//			stats_div.appendChild(own_identities);

			mPageNode.content.addChild("h2", "URIs currently in flight");
//			stats_div.append("<h2>URIs currently in flight</h2>");
//			Element inflight = doc.createElement("ol");
			list = new HTMLNode("ol");
			
			synchronized (main.getRequestScheduler().getInFlight()) {
				for(String in : main.getRequestScheduler().getInFlight())
				{
//					inflight.appendChild(doc.createElement("li").text(in));
					list.addChild("li", in);
				}
			}
			
			mPageNode.content.addChild(list);
//			stats_div.appendChild(inflight);
			
//			writeReply(ctx, 200, "text/html", "content", doc.html());
			writeReply(ctx, 200, "text/html", "OK", mPageNode.outer.generate());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
		}
	}
	
//	@Override
//	public void terminate() {
//		
//	}

	@Override
	public String path() {
		return filePath;
	}

	@Override
	public boolean isEnabled(ToadletContext ctx) {
		// TODO Auto-generated method stub
		return true;
	}
}
