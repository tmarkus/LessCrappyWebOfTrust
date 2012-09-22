package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.kernel.EmbeddedGraphDatabase;
import org.neo4j.tooling.GlobalGraphOperations;


import plugins.WebOfTrust.IdentityUpdater;
import plugins.WebOfTrust.IdentityUpdaterRequestClient;
import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.RequestScheduler;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.fcp.SetTrust;
import plugins.WebOfTrust.util.Utils;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;

import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;
import freenet.node.RequestStarter;
import freenet.support.api.HTTPRequest;

public class IdentityManagement extends freenet.plugin.web.HTMLFileReaderToadlet {
	
	private GraphDatabaseService db;
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

	
	public IdentityManagement(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, GraphDatabaseService db) {
		super(client, main.getDB(), filepath, URLPath);
		this.main = main;
		this.db = db;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		Document doc = Jsoup.parse(readFile());
		Element identities_div = doc.select("#identities").first();
		 
		try {
			for(Node own_vertex : nodeIndex.get(IVertex.OWN_IDENTITY, true))
			{
				Element p = doc.createElement("p");
				p.appendChild(doc.createElement("a").attr("href", "ShowIdentity?id="+(String) own_vertex.getProperty(IVertex.ID)).text((String) own_vertex.getProperty(IVertex.NAME)));
				
				Element form = doc.createElement("form").attr("action", "restore.html").attr("method", "post");
				Element hiddenValue = doc.createElement("input").attr("type", "hidden").attr("name", "action").attr("value", "delete");
				Element hiddenID = doc.createElement("input").attr("type", "hidden").attr("name", "id").attr("value", (String) own_vertex.getProperty(IVertex.ID));
				Element submit = doc.createElement("input").attr("type", "submit").attr("value", "delete");
				form.appendChild(hiddenValue);
				form.appendChild(hiddenID);
				form.appendChild(submit);
				
				p.appendChild(form);
				identities_div.appendChild(p);
			}
		}
		finally
		{
		}
		
	    writeReply(ctx, 200, "text/html", "content", doc.html());
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, SQLException, FetchException
	{
	    String action = request.getPartAsStringFailsafe("action", 200);

	    Transaction tx = db.beginTx();
		try
		{
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
		
		    tx.success();
		}
		finally
		{
			tx.finish();
		}
		
	    handleMethodGET(uri, request, ctx);
	}

	private void removeIdentity(String id) {
		Node node = nodeIndex.get(IVertex.ID, id).getSingle();
		for(Relationship rel : node.getRelationships())
		{
			rel.delete();
		}
		node.delete();
	}

	private void restoreIdentity(FreenetURI insertURI) throws FetchException, MalformedURLException 
	{
			IdentityUpdaterRequestClient rc = new IdentityUpdaterRequestClient();
			HighLevelSimpleClient hl = main.getHL();
			RequestScheduler rs = main.getRequestScheduler();
			ClientGetCallback cc = new IdentityUpdater(rs, db, hl, true);  

			try
			{
				InsertableClientSSK key = InsertableClientSSK.create(insertURI.sskForUSK());
				FreenetURI requestURI = key.getURI().setKeyType("USK")
						.setDocName(WebOfTrust.namespace)
						.setSuggestedEdition(insertURI.getEdition())
						.setMetaString(null);

				Node own_vertex = addOwnIdentity(requestURI, insertURI);

				Transaction tx = db.beginTx();
				try
				{
					own_vertex.setProperty(IVertex.DONT_INSERT, true);
					tx.success();
				}
				finally
				{
					tx.finish();
				}
				
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
		Node own_identity_vertex = addOwnIdentity(newRequestURI, newInsertURI);
		
		//set the name of this identity
		
			Transaction tx = db.beginTx();
			try
			{
				own_identity_vertex.setProperty(IVertex.NAME, name);
				tx.success();
			}
			finally
			{
				tx.finish();
			}

			//add trust relations to seed identities
			for(String key : SEED_IDENTITIES)
			{
				FreenetURI seedKey = new FreenetURI(key);
				String seedID = Utils.getIDFromKey(seedKey);
				String id = Utils.getIDFromKey(newRequestURI);
				
				try
				{
					IdentityUpdater.getPeerIdentity(db, seedKey); //try to get from the db it and add it otherwise	
					tx.success();
				}
				finally
				{
					tx.finish();
				}
				
				
				SetTrust.setTrust(db, nodeIndex, id, seedID, "100", "Initial seed identity");
			}
		
		
	}

	/**
	 * add minimal identity features to graph store
	 * @param requestURI
	 * @param insertURI
	 * @throws SQLException
	 */
	
	private Node addOwnIdentity(FreenetURI requestURI, FreenetURI insertURI) throws SQLException {
		
		Transaction tx = db.beginTx();
		try
		{
			Node vertex = db.createNode();
			vertex.setProperty(IVertex.ID, Utils.getIDFromKey(requestURI));
			vertex.setProperty(IVertex.NAME, "... still fetching ...");
			vertex.setProperty(IVertex.OWN_IDENTITY, true);
			vertex.setProperty(IVertex.INSERT_URI, insertURI.toASCIIString());
			vertex.setProperty(IVertex.REQUEST_URI, requestURI.toASCIIString());
			vertex.setProperty(IVertex.EDITION, -1l);
			
			tx.success();
			
			return vertex;
		}
		finally
		{
			tx.finish();
		}
	}

	@Override
	public void terminate() {
	}

	
}
