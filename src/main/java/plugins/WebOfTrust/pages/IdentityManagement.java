package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.ReadableIndex;

import plugins.WebOfTrust.IdentityUpdater;
import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.RequestScheduler;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.fcp.SetTrust;
import plugins.WebOfTrust.util.Utils;

import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.client.async.ClientGetCallback;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.PageNode;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.FreenetURI;
import freenet.keys.InsertableClientSSK;
import freenet.keys.KeyDecodeException;
import freenet.node.RequestStarter;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class IdentityManagement extends Toadlet implements LinkEnabledCallback {

	private final String path;
	private final GraphDatabaseService db;
	private final ReadableIndex<Node> nodeIndex;
	private final WebOfTrust main;
	
	
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

	
	public IdentityManagement(WebOfTrust main, HighLevelSimpleClient client, String URLPath, GraphDatabaseService db) {
		super(client);
		this.main = main;
		this.db = db;
		this.path = URLPath;
		nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeReply(ctx, 403, "text/plain", "forbidden", "Your host is not allowed to access this page.");
			return;
		}
		PageNode mPageNode = ctx.getPageMaker().getPageNode("LCWoT - Local identity management", true, true, ctx);
		mPageNode.addCustomStyleSheet(WebOfTrust.basePath + "/WebOfTrust.css");
		HTMLNode contentDiv = new HTMLNode("div");
		contentDiv.addAttribute("id", "WebOfTrust_identityManagement");
		// FIXME: just for testing.
		// <br /> should be div margin/padding or something i guess
		// if ^ stylesheet is correctly set up the <b> tags can become h1 and h2 again.
		contentDiv.addChild("br");
		contentDiv.addChild("b", "Manage your identities");
		contentDiv.addChild("br");
		contentDiv.addChild("br");
		HTMLNode form;

		HTMLNode p;
		HTMLNode link;
		for(Node own_vertex : nodeIndex.get(IVertex.OWN_IDENTITY, true)) {
			// TODO: create a table to show the delete button behind the identity link
			p = new HTMLNode("p");
			link = new HTMLNode("a", (String) own_vertex.getProperty(IVertex.NAME));
			link.addAttribute("href", "ShowIdentity?id="+(String) own_vertex.getProperty(IVertex.ID));
			p.addChild(link);
			form = new HTMLNode("form");
			form.addAttribute("action", "restore.html");
			form.addAttribute("method", "post");
			form.addChild(Utils.getInput("hidden", "formPassword", ctx.getFormPassword()));
			form.addChild(Utils.getInput("hidden", "action", "delete"));
			form.addChild(Utils.getInput("hidden", "id", (String) own_vertex.getProperty(IVertex.ID)));
			form.addChild(Utils.getInput("submit", "", "delete"));
			p.addChild(form);
			contentDiv.addChild(p);
		}
		
		// restore form
		HTMLNode div = new HTMLNode("div");
		div.addAttribute("id", "restore_form");
		form = new HTMLNode("form");
		form.addAttribute("action", "restore.html");
		form.addAttribute("method", "post");
		form.addChild(Utils.getInput("hidden", "formPassword", ctx.getFormPassword()));
		HTMLNode fieldset = new HTMLNode("fieldSet");
		fieldset.addChild("legend", "Restore an identity");
		fieldset.addChild(Utils.getInput("hidden", "action", "restore"));
		fieldset.addChild("span", "Insert USK key: ");
		fieldset.addChild(Utils.getInput("text", "insertURI", ""));
		fieldset.addChild("br");
		fieldset.addChild(Utils.getInput("submit", "", "restore"));
		form.addChild(fieldset);
		div.addChild(form);
		contentDiv.addChild(div);

		// create form
		div = new HTMLNode("div");
		div.addAttribute("id", "create_form");
		form = new HTMLNode("form");
		form.addAttribute("action", "restore.html");
		form.addAttribute("method", "post");
		form.addChild(Utils.getInput("hidden", "formPassword", ctx.getFormPassword()));
		fieldset = new HTMLNode("fieldSet");
		fieldset.addChild("legend", "Create an identity");
		fieldset.addChild(Utils.getInput("hidden", "action", "create"));
		fieldset.addChild("span", "Name: ");
		fieldset.addChild(Utils.getInput("text", "name", ""));
		fieldset.addChild("br");
		fieldset.addChild(Utils.getInput("submit", "", "create"));
		form.addChild(fieldset);
		div.addChild(form);
		contentDiv.addChild(div);

		mPageNode.content.addChild(contentDiv);
	    writeReply(ctx, 200, "text/html", "OK", mPageNode.outer.generate());
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, FetchException, KeyDecodeException, InsertException {
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeReply(ctx, 403, "text/plain", "forbidden", "Your host is not allowed to access this page.");
			return;
		}
	    
		String action = request.getPartAsStringFailsafe("action", 200);

	    Transaction tx = db.beginTx();
		try {
		    if(action.equals("restore")) {
			    FreenetURI insertURI = new FreenetURI(request.getPartAsStringFailsafe("insertURI", 20000));
			    restoreIdentity(insertURI);
		    } else if (action.equals("create")) {
				String name = request.getPartAsStringFailsafe("name", 2000);
				createIdentity(name);
			} else if (action.equals("delete")) {
				String id = request.getPartAsStringFailsafe("id", 2000);
				removeIdentity(id);
			}
		    tx.success();
		}
	    catch(Exception e)
	    {
	    	tx.failure();
	    	e.printStackTrace();
	    	writeReply(ctx, 500, "text/plain", "error", e.getMessage());
	    }

		finally {
			tx.finish();
		}

		//actions were processed, so display the page in the end
	    handleMethodGET(uri, request, ctx);
	}

	/**
	 * Remove a local identity
	 * @param id
	 */
	
	private void removeIdentity(String id) {
		final IndexHits<Node> nodes = nodeIndex.get(IVertex.ID, id);
		if (nodes.size() > 1) System.err.println("Multiple identities stored in the database with the same ID, this should not happen!");
		
		//delete all nodes with a specific identity (should be just one!)
		for(Node node : nodeIndex.get(IVertex.ID, id))
		{
			for(Relationship rel : node.getRelationships()) {
				rel.delete();
			}
			node.delete();
			System.err.println("Deleted local identity.");
		}
	}

	/**
	 * Restore an identity using the insert USK
	 * @param insertURI
	 * @throws FetchException
	 * @throws MalformedURLException
	 * @throws KeyDecodeException 
	 * @throws InsertException 
	 */
	
	private void restoreIdentity(FreenetURI insertURI) throws FetchException, MalformedURLException, KeyDecodeException, InsertException {
		HighLevelSimpleClient hl = main.getHL();
		RequestScheduler rs = main.getRequestScheduler();
		ClientGetCallback cc = new IdentityUpdater(rs, db, hl, true);  
		
			//check whether the insert URI is indeed OK or not
			insertURI.checkInsertURI();
		
			//create a request/insert keypair
			InsertableClientSSK key = null;
			if (insertURI.isUSK())
			{
				key = InsertableClientSSK.create(insertURI.sskForUSK());	
			}
			else
			{
				throw new KeyDecodeException("Specified restore key not allowed here.");
			}
			
			FreenetURI requestURI = key.getURI().setKeyType("USK")
					.setDocName(WebOfTrust.namespace)
					.setSuggestedEdition(insertURI.getEdition())
					.setMetaString(null);
			
			
			 if ( nodeIndex.get(IVertex.ID, Utils.getIDFromKey(requestURI)).size() == 0 ) 
			 {
				 System.out.println("Identity not yet in the database, adding...");
				 
				 Transaction tx = db.beginTx();
				 try
				 {
					 Node own_vertex = addOwnIdentity(requestURI, insertURI);
					 own_vertex.setProperty(IVertex.DONT_INSERT, true);
					 tx.success();
				 }
				 finally
				 {
					 tx.finish();
				 }
			 }
			 else
			 {
				 System.out.println("Upgrading an existing identity to a locally owned identity");
				 Node vertex = 	nodeIndex.get(IVertex.ID, Utils.getIDFromKey(requestURI)).getSingle();
				 Transaction tx = db.beginTx();
				 try
				 {
						vertex.setProperty(IVertex.OWN_IDENTITY, true);
						vertex.setProperty(IVertex.PUBLISHES_TRUSTLIST, true);
						vertex.setProperty(IVertex.INSERT_URI, insertURI.toASCIIString());
						tx.success();
				 }
				 finally
				 {
					tx.finish();
				 }
			 }
			
			 
			// Fetch the identity from freenet
			System.out.println("Starting to fetch your own identity");
			
			HighLevelSimpleClient hl_high_prio = main.getPR().getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS, false, true);
			rs.addInFlight(hl_high_prio.fetch(requestURI, 200000, cc, hl_high_prio.getFetchContext()));
	
			//wake up the request scheduler
			rs.interrupt();
	}

	private void createIdentity(String name) throws MalformedURLException {
		// create ssk keypair
		FreenetURI[] keypair = main.getHL().generateKeyPair(WebOfTrust.namespace);
		FreenetURI newRequestURI = keypair[1].setKeyType("USK")
											.setDocName(WebOfTrust.namespace).
											setSuggestedEdition(0).
											setMetaString(null);
		FreenetURI newInsertURI = keypair[0].setKeyType("USK")
											.setDocName(WebOfTrust.namespace)
											.setSuggestedEdition(0)
											.setMetaString(null);
		
		// create minimal identity in store
		Node own_identity_vertex = addOwnIdentity(newRequestURI, newInsertURI);
		
		// set the name of this identity
		own_identity_vertex.setProperty(IVertex.NAME, name);

		// add trust relations to seed identities
		for(String key : SEED_IDENTITIES) {
			FreenetURI seedKey = new FreenetURI(key);
			String seedID = Utils.getIDFromKey(seedKey);
			String id = Utils.getIDFromKey(newRequestURI);
			
			IdentityUpdater.getPeerIdentity(db, seedKey); //try to get from the db it and add it otherwise	
			SetTrust.setTrust(db, nodeIndex, id, seedID, "100", "Initial seed identity");
		
			//start fetching it
			main.getRequestScheduler().addBacklog(new FreenetURI(key));
		}
	
		//wake up the request scheduler
	  	main.getRequestScheduler().interrupt();
	}

	/**
	 * add minimal identity features to graph store
	 * @param requestURI
	 * @param insertURI
	 */
	
	private Node addOwnIdentity(FreenetURI requestURI, FreenetURI insertURI) {
			
		Transaction tx = db.beginTx();
		
		try
		{
			Node vertex = db.createNode();
			vertex.setProperty(IVertex.ID, Utils.getIDFromKey(requestURI));
			vertex.setProperty(IVertex.NAME, "... still fetching ...");
			vertex.setProperty(IVertex.OWN_IDENTITY, true);
			vertex.setProperty(IVertex.PUBLISHES_TRUSTLIST, true);
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
	public boolean isEnabled(ToadletContext ctx) {
		// TODO: wait for database initialization?
		// return WebOfTrust.ReadyToRock;
		return true;
	}

	@Override
	public String path() {
		return path;
	}

	
}
