package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.ReadableIndex;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.util.Utils;

import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertException;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.PageNode;
import freenet.clients.http.SessionManager;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.keys.KeyDecodeException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class SessionManagement extends Toadlet implements LinkEnabledCallback {

	private final String path;
	private final GraphDatabaseService db;
	private final ReadableIndex<Node> nodeIndex;
	private final WebOfTrust main;
	private final SessionManager sm;

	
	public SessionManagement(WebOfTrust main, HighLevelSimpleClient client, String URLPath, GraphDatabaseService db) {
		super(client);
		this.main = main;
		this.db = db;
		this.path = URLPath;
		nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		this.sm = main.getPR().getSessionManager(WebOfTrust.namespace);
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeReply(ctx, 403, "text/plain", "forbidden", "Your host is not allowed to access this page.");
			return;
		}


		//page basics
		PageNode mPageNode = ctx.getPageMaker().getPageNode("LCWoT - Session management", true, true, ctx);
		mPageNode.addCustomStyleSheet(WebOfTrust.basePath + "/WebOfTrust.css");
		HTMLNode contentDiv = new HTMLNode("div");
		contentDiv.addAttribute("id", "WebOfTrust_sessionManagement");
		
		try
		{
			URI raw = new URI(request.getParam("redirect-target"));
			
			// Use only the path, query, and fragment. Stay on the node's scheme, host, and port.
			URI target = new URI(null, null, raw.getPath(), raw.getQuery(), raw.getFragment());
			
			//do something for the login url (see uri)
			if (uri.getPath().toLowerCase().contains("login"))
			{
				HTMLNode form = new HTMLNode("form");
				form.addAttribute("action", "LogIn");
				form.addAttribute("method", "post");
				form.addChild(Utils.getInput("hidden", "formPassword", ctx.getFormPassword()));
				form.addChild(Utils.getInput("hidden", "redirect-target", target.toString()));
				
				form.addChild("span", "Identity: ");
				HTMLNode select = new HTMLNode("select");
				select.addAttribute("name", "identity");
				
				
				//Add local identities
				for(Node own_vertex : nodeIndex.get(IVertex.OWN_IDENTITY, true)) {
					// TODO: create a table to show the delete button behind the identity link

					String nick = (String) own_vertex.getProperty(IVertex.NAME);
					String id = (String) own_vertex.getProperty(IVertex.ID);
					
					HTMLNode option = new HTMLNode("option");
					option.addAttribute("value", id);
					option.setContent(nick + "(" + id + ")");
					select.addChild(option);
				}

				form.addChild(select);
				form.addChild(Utils.getInput("submit", "", "login"));
				contentDiv.addChild(form);
			}
			//otherwise check that we should destroy the current session
			else if (uri.getPath().toLowerCase().contains("logout"))
			{
				sm.deleteSession(ctx);
				writeTemporaryRedirect(ctx, "Logged out. Redirecting back to application", target.toString());
				return;
			}
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}
		finally
		{
			
		}
		

		mPageNode.content.addChild(contentDiv);
	    writeReply(ctx, 200, "text/html", "OK", mPageNode.outer.generate());
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, FetchException, KeyDecodeException, InsertException, URISyntaxException {
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeReply(ctx, 403, "text/plain", "forbidden", "Your host is not allowed to access this page.");
			return;
		}
	    
		if(!request.getPartAsString("formPassword", Integer.MAX_VALUE).equals(ctx.getFormPassword())) {
			writeReply(ctx, 403, "text/plain", "forbidden", "The form password is incorrect");
			return;
		}
		
		String identity = request.getPartAsStringFailsafe("identity", 200); //NOTE: 200 is an unreasonably large random value
		URI target = new URI(request.getPartAsStringFailsafe("redirect-target", 200)); //NOTE: 200 is an unreasonably large random value
		
		sm.createSession(identity, ctx);
		writeTemporaryRedirect(ctx, "Logged in. Redirecting back to application", target.toString());
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
