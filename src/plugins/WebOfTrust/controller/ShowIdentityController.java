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
import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.Edge;
import thomasmarkus.nl.freenet.graphdb.H2Graph;

import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetter;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.support.api.HTTPRequest;

public class ShowIdentityController extends freenet.plugin.web.HTMLFileReaderToadlet {

	private H2Graph graph;
	private WebOfTrust main;
	
	public ShowIdentityController(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, H2Graph graph) {
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
			
			//new patterns
			Element info_div = doc.select("#info").first();

			//get the query param
			String id = request.getParam("id");
			
			//get the identity vertex & properties
			Long id_vertex = graph.getVertexByPropertyValue("id", id).get(0);
			Map<String, List<String>> props = graph.getVertexProperties(id_vertex);
			
			//generic properties associated with this identity in the graph store
			for(String key : props.keySet())
			{
				for(String value : props.get(key))
				{
					info_div.append(key + ": " + value + "<br />");	
				}
			}
			
			//explicit trust relations assigned by this identity
			info_div.append("<h1>Explicit trust relations set by this identity:</h1>");
			List<Edge> edges = graph.getOutgoingEdges(id_vertex);
			info_div.append("<ol>");
			for(Edge edge : edges)
			{
				Map<String, List<String>> peer_identity_props = graph.getVertexProperties(edge.vertex_to);
				Map<String, List<String>> edge_properties = edge.getProperties();
				
				String trustValue = edge_properties.get(IEdge.SCORE).get(0);
				String trustComment = edge_properties.get(IEdge.COMMENT).get(0);
				
				String peerName;
				if (peer_identity_props.containsKey(IVertex.NAME))	peerName = peer_identity_props.get(IVertex.NAME).get(0);
				else												peerName = "(Not yet downloaded)";
				String peerID = peer_identity_props.get(IVertex.ID).get(0);
				
				info_div.select("ol").append("<li>"+peerName+"("+peerID+"): Trust score: " + trustValue + "  comment: " + trustComment+"</li>");
			}

			//explicit trust relations given by others
			info_div.append("<h1>Explicit trust relations given by others to this identity:</h1>");
			edges = graph.getIncomingEdges(id_vertex);
			info_div.append("<ol>");
			for(Edge edge : edges)
			{
				Map<String, List<String>> peer_identity_props = graph.getVertexProperties(edge.vertex_from);
				Map<String, List<String>> edge_properties = edge.getProperties();
				
				String trustValue = edge_properties.get("score").get(0);
				String trustComment = edge_properties.get("comment").get(0);
				String peerName = peer_identity_props.get("name").get(0);
				String peerID = peer_identity_props.get("id").get(0);
				
				info_div.select("ol").last().append("<li>"+peerName+"("+peerID+"): Trust score: " + trustValue + "  comment: " + trustComment+"</li>");
			}

			
			writeReply(ctx, 200, "text/html", "content", doc.html());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, SQLException
	{
		/*
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
		*/
	}
}
