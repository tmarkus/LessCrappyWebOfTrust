package plugins.WebOfTrust.controller;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import plugins.WebOfTrust.IdentityUpdater;
import plugins.WebOfTrust.IdentityUpdaterRequestClient;
import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.RequestScheduler;
import plugins.WebOfTrust.Utils;

import thomasmarkus.nl.freenet.graphdb.H2Graph;

import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.support.api.HTTPRequest;

public class RestoreIdentity extends freenet.plugin.web.HTMLFileReaderToadlet {

	private H2Graph graph;
	private WebOfTrust main;
	
	public RestoreIdentity(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, H2Graph graph) {
		super(client, filepath, URLPath);
		this.main = main;
		this.graph = graph;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
	    Document doc = Jsoup.parse(readFile());
	    writeReply(ctx, 200, "text/html", "content", doc.html());
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, SQLException, FetchException
	{
	    FreenetURI requestURI = new FreenetURI(request.getPartAsStringFailsafe("requestURI", 20000));
	    FreenetURI insertURI = new FreenetURI(request.getPartAsStringFailsafe("insertURI", 20000));
	    
	    List<Long> existing_identity = graph.getVertexByPropertyValue("id", Utils.getIDFromKey(requestURI));
		if (existing_identity.size() == 0) //does identity already exist?
		{
			IdentityUpdaterRequestClient rc = new IdentityUpdaterRequestClient();
			HighLevelSimpleClient hl = main.getHL();
			RequestScheduler rs = main.getRequestScheduler();
			ClientGetCallback cc = new IdentityUpdater(rs, graph, hl, true);  
			
			//add minimal identity features to graph store
			long vertex_id = graph.createVertex();
			graph.addVertexProperty(vertex_id, "id", Utils.getIDFromKey(requestURI));
			graph.addVertexProperty(vertex_id, "ownIdentity", "true");
			graph.addVertexProperty(vertex_id, "insertURI", insertURI.toASCIIString());
			graph.addVertexProperty(vertex_id, "requestURI", requestURI.toASCIIString());
			
			//Fetch the identity from freenet
			System.out.println("Starting to fetch your own identity");
			rs.addInFlight(hl.fetch(requestURI, 200000, rc, cc, hl.getFetchContext()));
		}
	    
		handleMethodGET(uri, request, ctx);
	}
}
