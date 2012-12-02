package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;

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

public class Overview extends Toadlet implements LinkEnabledCallback {
	private final String path;
	private final GraphDatabaseService db;
	private final ReadableIndex<Node> nodeIndex;
	private final WebOfTrust main;
	
	public Overview(WebOfTrust main, HighLevelSimpleClient client, String URLPath, GraphDatabaseService db) {
		super(client);
		this.path = URLPath;
		this.db = db;
		this.main = main;
		nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeReply(ctx, 403, "text/plain", "forbidden", "Your host is not allowed to access this page.");
			return;
		}
		PageNode mPageNode = ctx.getPageMaker().getPageNode("LCWoT - overview", true, true, ctx);
		mPageNode.addCustomStyleSheet(WebOfTrust.basePath + "/WebOfTrust.css");
		HTMLNode contentDiv = new HTMLNode("div");
		contentDiv.addAttribute("id", "WebOfTrust_overview");
		// FIXME: just for testing.
		// <br /> should be div margin/padding or something i guess
		// if ^ stylesheet is correctly set up the <b> tags can become h1 and h2 again.
		contentDiv.addChild("br");
		
		try
		{
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

			HTMLNode link = new HTMLNode("a");
			link.addAttribute("href", WebOfTrust.basePath + "/restore.html");
			link.addChild(new HTMLNode("b", "Manage local identities"));
			contentDiv.addChild(link);
			contentDiv.addChild("br");
			contentDiv.addChild("br");
			
			contentDiv.addChild("b", "Here are some statistics to oogle");
			contentDiv.addChild("br");
			contentDiv.addChild("br");
			HTMLNode list = new HTMLNode("ul");
			list.addChild("li", "Number of identities: " + count_identities);
			list.addChild("li", "Number of trust relations: " + count_trust_relations);
			list.addChild("li", "Number of requests in flight currently: " + main.getRequestScheduler().getInFlightSize());
			list.addChild("li", "Backlog: " + main.getRequestScheduler().getBacklogSize());
			contentDiv.addChild(list);
			contentDiv.addChild("br");
			
			contentDiv.addChild("b", "Own identities in local storage");
			contentDiv.addChild("br");
			list = new HTMLNode("ul");
			for(Node identity : nodeIndex.get(IVertex.OWN_IDENTITY, true))
			{
				if (identity.hasProperty(IVertex.NAME) )
				{
					list.addChild("li", (String) identity.getProperty(IVertex.NAME) + "  (" + (String) identity.getProperty(IVertex.ID) + ")");
				}
			}
			contentDiv.addChild(list);
			contentDiv.addChild("br");
			
			contentDiv.addChild("b", "URIs currently in flight");
			list = new HTMLNode("ol");
			synchronized (main.getRequestScheduler().getInFlight()) {
				for(String in : main.getRequestScheduler().getInFlight())
				{
					list.addChild("li", in);
				}
			}
			contentDiv.addChild(list);
			mPageNode.content.addChild(contentDiv);
			
			writeReply(ctx, 200, "text/html", "OK", mPageNode.outer.generate());
		}
		// FIXME: catch only specific exceptions
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
		}
	}

	@Override
	public String path() {
		return path;
	}

	@Override
	public boolean isEnabled(ToadletContext ctx) {
		// TODO: wait for database initialization?
		// return WebOfTrust.ReadyToRock;
		return true;
	}
}
