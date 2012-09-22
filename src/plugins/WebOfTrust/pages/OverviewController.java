package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IVertex;


import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public class OverviewController extends freenet.plugin.web.HTMLFileReaderToadlet {

	private WebOfTrust main;
	
	public OverviewController(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, GraphDatabaseService db) {
		super(client, main.getDB(), filepath, URLPath);
		this.main = main;
		this.db = db;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		try
		{
			Document doc = Jsoup.parse(readFile());
			Element stats_div = doc.select("#stats").first();
			
			long count_vertices = 666;
			long count_edges = 666;
			
			Element list = doc.createElement("ul");
			
			list.appendChild(doc.createElement("li").text("Number of identities: " + count_vertices));
			list.appendChild(doc.createElement("li").text("Number of trust relations: " + count_edges));
			list.appendChild(doc.createElement("li").text("Number of requests in flight currently: " + main.getRequestScheduler().getInFlightSize()));
			list.appendChild(doc.createElement("li").text("Backlog: " + main.getRequestScheduler().getBacklogSize()));
			list.appendChild(doc.createElement("li").text("Number of active db connections: " + 666));
			
			stats_div.appendChild(list);

			stats_div.append("<h2> Own identities in local storage </h2>");
			Element own_identities = doc.createElement("ul");
			
			for(Node identity : nodeIndex.get(IVertex.OWN_IDENTITY, true))
			{
				if (identity.hasProperty(IVertex.NAME) )
				{
					own_identities.appendChild(doc.createElement("li").text((String) identity.getProperty(IVertex.NAME) + "  (" + (String) identity.getProperty(IVertex.ID) + ")"));
				}
			}

			stats_div.appendChild(own_identities);

			
			stats_div.append("<h2>URIs currently in flight</h2>");
			Element inflight = doc.createElement("ol");
			
			synchronized (main.getRequestScheduler().getInFlight()) {
				for(String in : main.getRequestScheduler().getInFlight())
				{
					inflight.appendChild(doc.createElement("li").text(in));
				}
			}
			
			stats_div.appendChild(inflight);
			
			writeReply(ctx, 200, "text/html", "content", doc.html());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
		}
	}
	
	@Override
	public void terminate() throws SQLException {
		
	}
}
