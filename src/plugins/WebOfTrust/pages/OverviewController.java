package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public class OverviewController extends freenet.plugin.web.HTMLFileReaderToadlet {

	private H2GraphFactory gf;
	private WebOfTrust main;
	
	public OverviewController(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, H2GraphFactory gf) {
		super(client, filepath, URLPath);
		this.main = main;
		this.gf = gf;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		H2Graph graph = null;
		try
		{
		    graph = gf.getGraph();
			Document doc = Jsoup.parse(readFile());
			Element stats_div = doc.select("#stats").first();
			
			long count_vertices = graph.getVertexCount();
			long count_edges = graph.getEdgeCount();
			
			Element list = doc.createElement("ul");
			
			list.appendChild(doc.createElement("li").text("Number of identities: " + count_vertices));
			list.appendChild(doc.createElement("li").text("Number of trust relations: " + count_edges));
			list.appendChild(doc.createElement("li").text("Number of requests in flight currently: " + main.getRequestScheduler().getInFlightSize()));
			list.appendChild(doc.createElement("li").text("Backlog: " + main.getRequestScheduler().getBacklogSize()));
			list.appendChild(doc.createElement("li").text("Number of active db connections: " + gf.getActiveConnections()));
			
			stats_div.appendChild(list);

			stats_div.append("<h2> Own identities in local storage </h2>");
			Element own_identities = doc.createElement("ul");
			
			for(long identity : graph.getVertexByPropertyValue("ownIdentity", "true"))
			{
				Map<String, List<String>> props = graph.getVertexProperties(identity);
				if (props.containsKey(IVertex.NAME))
				{
					own_identities.appendChild(doc.createElement("li").text(props.get(IVertex.NAME).get(0) + "  (" + props.get("id").get(0) + ")"));
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
			try {
				graph.close();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	@Override
	public void terminate() throws SQLException {
		
	}
}
