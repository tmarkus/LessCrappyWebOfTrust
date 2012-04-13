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

import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetter;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.support.api.HTTPRequest;

public class ManagePageController extends freenet.plugin.web.HTMLFileReaderToadlet {

	private H2Graph graph;
	private WebOfTrust main;
	
	public ManagePageController(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, H2Graph graph) {
		super(client, filepath, URLPath);
		this.main = main;
		this.graph = graph;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		try
		{
			//existing patterns
		    Document doc = Jsoup.parse(readFile());
			
			Element existing = doc.select("#existing_patterns").first();

			/*
			for (String pattern : )
			{
				Element form = doc.createElement("form");
				form.attr("method", "post");
				form.attr("action", "/SoneBridge/manage");
				Element fieldset = doc.createElement("fieldset");
				fieldset.appendChild(doc.createElement("legend").text("Existing pattern"));
				fieldset.appendChild(doc.createElement("input").attr("type", "submit").attr("value", "Remove"));
				
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "action").attr("value", "delete"));
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "pattern").attr("value", pattern));
				
				form.appendChild(fieldset);
				existing.appendChild(form);
			}
			
			*/
			
			//new patterns
			Element stats_div = doc.select("#stats").first();
			
			long count_vertices = graph.getVertexCount();
			long count_edges = graph.getEdgeCount();
			
			stats_div.text("Number of identities: " + count_vertices).append("<br />");
			stats_div.append("Number of trust relations: " + count_edges + "<br />");
			stats_div.append("Number of requests in flight currently: " + main.getRequestScheduler().getNumInFlight() + "<br />");
			stats_div.append("Backlog: " + main.getRequestScheduler().getNumBackLog() + "<br />");
			

			stats_div.append("Own identities in local storage: "  + "<br />");
			stats_div.append("<ul>");
			for(long identity : graph.getVertexByPropertyValue("ownIdentity", "true"))
			{
				Map<String, List<String>> props = graph.getVertexProperties(identity);
				if (props.containsKey(IVertex.NAME))
				{
					stats_div.append("<li>" + props.get(IVertex.NAME).get(0) + "  (" + props.get("id").get(0) + ")</li>");	
				}
			}
			stats_div.append("</ul><br />");
			
			stats_div.append("URIs currently in flight: "  + "<br />");
			stats_div.append("<ul>");
			for(ClientGetter cg : main.getRequestScheduler().getInFlight())
			{
				stats_div.append("<li>" + cg.getURI() + " finished: " + cg.isFinished() + "</li>");	
			}
			stats_div.append("</ul>");
			
			addTrustInformation(stats_div);
			
			writeReply(ctx, 200, "text/html", "content", doc.html());
			
			//ScoreComputer sc = new ScoreComputer(graph);
			//sc.compute("zALLY9pbzMNicVn280HYqS2UkK0ZfX5LiTcln-cLrMU,GoLpCcShPzp3lbQSVClSzY7CH9c9HTw0qRLifBYqywY,AQACAAE");
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	public void addTrustInformation(Element stats_div)
	{
        
		//parse it and determine WHO you trust
        
        // TrustList/Trust
        // Identity, value
        
        //traverse the orientdb and get the number of identities.

		
	}
	
	
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, SQLException
	{
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
}
