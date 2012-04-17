package plugins.WebOfTrust.controller;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;


import plugins.WebOfTrust.FCPInterface;
import plugins.WebOfTrust.IdentityUpdater;
import plugins.WebOfTrust.IdentityUpdaterRequestClient;
import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.RequestScheduler;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.util.Utils;

import thomasmarkus.nl.freenet.graphdb.H2Graph;

import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.ClientGetCallback;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;
import freenet.node.RequestStarter;
import freenet.support.api.HTTPRequest;

public class IdentityManagement extends freenet.plugin.web.HTMLFileReaderToadlet {
	
	private H2Graph graph;
	private WebOfTrust main;
	
	/**
	 * The official seed identities of the WoT plugin: If a newbie wants to download the whole offficial web of trust, he needs at least one
	 * trust list from an identity which is well-connected to the web of trust. To prevent newbies from having to add this identity manually,
	 * the Freenet development team provides a list of seed identities - each of them is one of the developers.
	 */
	private static final String[] SEED_IDENTITIES = new String[] { 
		"USK@QeTBVWTwBldfI-lrF~xf0nqFVDdQoSUghT~PvhyJ1NE,OjEywGD063La2H-IihD7iYtZm3rC0BP6UTvvwyF5Zh4,AQACAAE/WebOfTrust/90", // xor
		"USK@z9dv7wqsxIBCiFLW7VijMGXD9Gl-EXAqBAwzQ4aq26s,4Uvc~Fjw3i9toGeQuBkDARUV5mF7OTKoAhqOA9LpNdo,AQACAAE/WebOfTrust/60", // Toad
		"USK@o2~q8EMoBkCNEgzLUL97hLPdddco9ix1oAnEa~VzZtg,X~vTpL2LSyKvwQoYBx~eleI2RF6QzYJpzuenfcKDKBM,AQACAAE/WebOfTrust/0", // Bombe
		"USK@cI~w2hrvvyUa1E6PhJ9j5cCoG1xmxSooi7Nez4V2Gd4,A3ArC3rrJBHgAJV~LlwY9kgxM8kUR2pVYXbhGFtid78,AQACAAE/WebOfTrust/19", // TheSeeker
		"USK@D3MrAR-AVMqKJRjXnpKW2guW9z1mw5GZ9BB15mYVkVc,xgddjFHx2S~5U6PeFkwqO5V~1gZngFLoM-xaoMKSBI8,AQACAAE/WebOfTrust/47", // zidel
	};

	
	public IdentityManagement(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, H2Graph graph) {
		super(client, filepath, URLPath);
		this.main = main;
		this.graph = graph;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		Document doc = Jsoup.parse(readFile());
		Element identities_div = doc.select("#identities").first();
		
		try {
			for(long own_vertex : graph.getVertexByPropertyValue(IVertex.OWN_IDENTITY, "true"))
			{
				Map<String, List<String>> props = graph.getVertexProperties(own_vertex);
				
				Element p = doc.createElement("p");
				p.appendChild(doc.createElement("a").attr("href", "ShowIdentity?id="+props.get(IVertex.ID).get(0)).text(props.get(IVertex.NAME).get(0)));
				
				Element form = doc.createElement("form").attr("action", "restore.html").attr("method", "post");
				Element hiddenValue = doc.createElement("input").attr("type", "hidden").attr("name", "action").attr("value", "delete");
				Element hiddenID = doc.createElement("input").attr("type", "hidden").attr("name", "id").attr("value", props.get(IVertex.ID).get(0));
				Element submit = doc.createElement("input").attr("type", "submit").attr("value", "delete");
				form.appendChild(hiddenValue);
				form.appendChild(hiddenID);
				form.appendChild(submit);
				
				p.appendChild(form);
				identities_div.appendChild(p);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
		
		
	    writeReply(ctx, 200, "text/html", "content", doc.html());
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, SQLException, FetchException
	{
	    String action = request.getPartAsStringFailsafe("action", 200);
		
	    if(action.equals("restore"))
	    {
		    FreenetURI insertURI = new FreenetURI(request.getPartAsStringFailsafe("insertURI", 20000));
		    restoreIdentity(insertURI);
	    }	
		else if (action.equals("create"))
		{
			String name = request.getPartAsStringFailsafe("name", 2000);
			createIdentity(name);
		}
		else if (action.equals("delete"))
		{
			String id = request.getPartAsStringFailsafe("id", 2000);
			removeIdentity(id);
		}
	    
	    handleMethodGET(uri, request, ctx);
	}

	private void removeIdentity(String id) throws SQLException {
		List<Long> vertices = graph.getVertexByPropertyValue(IVertex.ID, id);

		//remove the vertex itself
		for(long vertex : vertices)
		{
			graph.removeVertex(vertex);
			
			//remove the calculated trust values associated with this identity
			graph.removePropertyForAllVertices(IVertex.TRUST+"."+id);
		}
	
		
		
	}

	private void restoreIdentity(FreenetURI insertURI) throws SQLException, FetchException, MalformedURLException 
	{
			IdentityUpdaterRequestClient rc = new IdentityUpdaterRequestClient();
			HighLevelSimpleClient hl = main.getHL();
			RequestScheduler rs = main.getRequestScheduler();
			ClientGetCallback cc = new IdentityUpdater(rs, graph, hl, true);  

			try
			{
				InsertableClientSSK key = InsertableClientSSK.create(insertURI.sskForUSK());
				FreenetURI requestURI = key.getURI().setKeyType("USK")
						.setDocName(WebOfTrust.namespace)
						.setSuggestedEdition(insertURI.getEdition())
						.setMetaString(null);

				long own_vertex_id = addOwnIdentity(requestURI, insertURI);
				graph.updateVertexProperty(own_vertex_id, IVertex.DONT_INSERT, "true");
				
				//Fetch the identity from freenet
				System.out.println("Starting to fetch your own identity");
				
				HighLevelSimpleClient hl_high_prio = main.getPR().getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS, false, true);
				rs.addInFlight(hl_high_prio.fetch(requestURI, 200000, rc, cc, hl_high_prio.getFetchContext()));
			}
			catch(Exception e)
			{
				e.printStackTrace();
			}
	}

	private void createIdentity(String name) throws SQLException, MalformedURLException {
		//create ssk keypair
		FreenetURI[] keypair = main.getHL().generateKeyPair(WebOfTrust.namespace);
		FreenetURI newRequestURI = keypair[1].setKeyType("USK")
											.setDocName(WebOfTrust.namespace).
											setSuggestedEdition(0).
											setMetaString(null);
		FreenetURI newInsertURI = keypair[0].setKeyType("USK")
											.setDocName(WebOfTrust.namespace)
											.setSuggestedEdition(0)
											.setMetaString(null);
		
		//create minimal identity in store
		long own_identity_vertex = addOwnIdentity(newRequestURI, newInsertURI);
		
		//set the name of this identity
		graph.updateVertexProperty(own_identity_vertex, IVertex.NAME, name);
		
		//add trust relations to seed identities
		for(String key : SEED_IDENTITIES)
		{
			FreenetURI seedKey = new FreenetURI(key);
			String seedID = Utils.getIDFromKey(seedKey);
			String id = Utils.getIDFromKey(newRequestURI);
			
			IdentityUpdater.getPeerIdentity(graph, seedKey); //try to get from the db it and add it otherwise
			FCPInterface.setTrust(graph, id, seedID, "100", "Initial seed identity");
		}
	}

	/**
	 * add minimal identity features to graph store
	 * @param requestURI
	 * @param insertURI
	 * @throws SQLException
	 */
	
	private long addOwnIdentity(FreenetURI requestURI, FreenetURI insertURI) throws SQLException {
		long vertex_id = graph.createVertex();
		graph.updateVertexProperty(vertex_id, IVertex.ID, Utils.getIDFromKey(requestURI));
		graph.updateVertexProperty(vertex_id, IVertex.NAME, " ... still fetching ...");
		graph.updateVertexProperty(vertex_id, IVertex.OWN_IDENTITY, "true");
		graph.updateVertexProperty(vertex_id, IVertex.INSERT_URI, insertURI.toASCIIString());
		graph.updateVertexProperty(vertex_id, IVertex.REQUEST_URI, requestURI.toASCIIString());
		graph.updateVertexProperty(vertex_id, IVertex.EDITION, "-1");
		return vertex_id;
	}
}
