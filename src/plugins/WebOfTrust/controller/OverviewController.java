package plugins.WebOfTrust.controller;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.ScoreComputer;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;

import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetter;
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
			
			//ScoreComputer sc = new ScoreComputer(graph);
			//sc.compute("zALLY9pbzMNicVn280HYqS2UkK0ZfX5LiTcln-cLrMU,GoLpCcShPzp3lbQSVClSzY7CH9c9HTw0qRLifBYqywY,AQACAAE");
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
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, SQLException
	{
		H2Graph graph = null;
		try
		{
			graph = gf.getGraph();
			String action = request.getPartAsStringFailsafe("action", 20000);
			
			//generate the web of trust for each of the ownIdentities that we have
			if (action.equals("generate"))
			{
				ScoreComputer sc = new ScoreComputer(graph);
				for(long vertex_id : graph.getVertexByPropertyValue("ownIdentity", "true"))
				{
					Map<String, List<String>> props = graph.getVertexProperties(vertex_id);
					sc.compute(props.get("id").get(0));
				}
			}
			handleMethodGET(uri, request, ctx);
		}
		finally
		{
			graph.close();
		}
	}

	@Override
	public void terminate() throws SQLException {
		
	}
}
