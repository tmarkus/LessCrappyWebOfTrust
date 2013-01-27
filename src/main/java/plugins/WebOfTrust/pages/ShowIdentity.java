package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.SortedSet;
import java.util.TreeSet;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.ReadableIndex;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;
import plugins.WebOfTrust.fcp.SetTrust;
import plugins.WebOfTrust.util.Utils;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.PageNode;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.HTMLNode;
import freenet.support.api.HTTPRequest;

public class ShowIdentity extends Toadlet implements LinkEnabledCallback {
	private final String path;
	private final GraphDatabaseService db;
	private final ReadableIndex<Node> nodeIndex;
	
	public ShowIdentity(HighLevelSimpleClient client, String URLPath, GraphDatabaseService db) {
		super(client);
		this.db = db;
		this.path = URLPath;
		nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeReply(ctx, 403, "text/plain", "forbidden", "Your host is not allowed to access this page.");
			return;
		}
		PageNode mPageNode = ctx.getPageMaker().getPageNode("LCWoT - Identity details", true, true, ctx);
		mPageNode.addCustomStyleSheet(WebOfTrust.basePath + "/WebOfTrust.css");
		HTMLNode contentDiv = new HTMLNode("div");
		contentDiv.addAttribute("id", "WebOfTrust_identityDetails");
		// FIXME: just for testing.
		// <br /> should be div margin/padding or something i guess
		// if ^ stylesheet is correctly set up the <b> tags can become h1 and h2 again.
		contentDiv.addChild("br");
		
		try {
			//get the query param
			// FIXME: maybe better to use
			// ^ request.getPartAsStringFailsafe(name, maxlength)
			final String id = request.getParam("id");
			
			// get the identity vertex & properties
			final Node identity = nodeIndex.get(IVertex.ID, id).getSingle();
			final boolean is_own_identity = identity.hasProperty(IVertex.OWN_IDENTITY);
			final SortedSet<String> sortedKeys = new TreeSet<String>();
			for(String key : identity.getPropertyKeys()) sortedKeys.add(key);
			
			contentDiv.addChild("b", "Here are the details of the identity");
			contentDiv.addChild("br");
			contentDiv.addChild("br");
					
			HTMLNode form = new HTMLNode("form");
			form.addAttribute("action", "#");
			form.addAttribute("method", "post");
			form.addChild(Utils.getInput("hidden", "action", "modify_properties"));
			form.addChild(Utils.getInput("hidden", "identity", id));

			HTMLNode table = new HTMLNode("table");
			HTMLNode tr = new HTMLNode("tr");
			tr.addChild("th", "Name");
			tr.addChild("th", "Value");
			table.addChild(tr);
			
			// generic properties associated with this identity in the graph store
			int property_index = 0;
			for(String key : sortedKeys) {
				Object value = identity.getProperty(key);
				if (is_own_identity) {
					table.addChild(getKeyPairRow(key, value, property_index));
				}
				else {
					tr = new HTMLNode("tr");
					tr.addChild("td", key);
					tr.addChild("td", value.toString());
					table.addChild(tr);	
				}
				property_index += 1;
			}

			if (is_own_identity) {
				// extra empty property value pair
				table.addChild(getKeyPairRow("", "", property_index));
				form.addChild(table);
				// submit button
				form.addChild(Utils.getInput("submit", "", "Modify properties"));
			} else {
				form.addChild(table);
			}
			contentDiv.addChild(form);
			
			// display available contexts
			HTMLNode contextDiv = new HTMLNode("div");
			contextDiv.addChild("br");
			contextDiv.addChild("b", "Contexts");
			contextDiv.addChild("br");
			contextDiv.addChild("br");
			HTMLNode context_list = new HTMLNode("table");
			for(Relationship rel : identity.getRelationships(Direction.OUTGOING, Rel.HAS_CONTEXT)) {
				tr = new HTMLNode("tr");
				
				final String context = (String) rel.getEndNode().getProperty(IContext.NAME);
				HTMLNode td = new HTMLNode("td", context);
				tr.addChild(td);
				
				if ((Boolean) identity.getProperty(IVertex.OWN_IDENTITY) == true)
				{
					// identity we are displaying is a local one, thus display additional options!
					HTMLNode td_form = new HTMLNode("td");
					form = new HTMLNode("form");
					form.addAttribute("action", WebOfTrust.basePath+"/ShowIdentity?id="+id);
					form.addAttribute("method", "post");
					form.addChild(Utils.getInput("submit", "", "Remove"));
					form.addChild(Utils.getInput("hidden", "action", "remove_context"));
					form.addChild(Utils.getInput("hidden", "context", context));
					form.addChild(Utils.getInput("hidden", "own_identity_id", id));
					td_form.addChild(form);
					tr.addChild(td_form);
				}
			}

			contextDiv.addChild(context_list);
			contentDiv.addChild(contextDiv);
			
			// allow specifying an updated trust value
			contentDiv.addChild("br");
			contentDiv.addChild("b", "Local trust assignments to this identity:");
			contentDiv.addChild("br");
			contentDiv.addChild("br");

			for(Node own_vertex : nodeIndex.get(IVertex.OWN_IDENTITY, true)) {
				byte current_trust_value = 0;
				String current_comment = "";
				for(Relationship edge : own_vertex.getRelationships(Direction.OUTGOING, Rel.TRUSTS)) {
					if (edge.getEndNode().equals(identity)) {
						current_trust_value = (Byte) edge.getProperty(IEdge.SCORE);
						current_comment = (String) edge.getProperty(IEdge.COMMENT);
						// TODO: add a break; here? not exactly sure what the goal of this is.
					}
				}
				form = new HTMLNode("form");
				form.addAttribute("method", "post");
				// TODO: why not use # here like above?
				form.addAttribute("action", WebOfTrust.basePath+"/ShowIdentity?id="+id);
				form.addAttribute("method", "post");
				HTMLNode fieldset = new HTMLNode("fieldset");
				fieldset.addChild("legend", (String) own_vertex.getProperty(IVertex.NAME));
				fieldset.addChild(Utils.getInput("hidden", "action", "set_trust"));
				fieldset.addChild(Utils.getInput("hidden", "identity_id", id));
				fieldset.addChild(Utils.getInput("hidden", "own_identity_id", (String) own_vertex.getProperty(IVertex.ID)));
				fieldset.addChild("span", "Trust: ");
				fieldset.addChild(Utils.getInput("number", "trust_value", Byte.toString(current_trust_value)));
				fieldset.addChild("span", "Comment: ");
				fieldset.addChild(Utils.getInput("text", "trust_comment", current_comment));
				fieldset.addChild(Utils.getInput("submit", "", "Update"));
				form.addChild(fieldset);
				contentDiv.addChild(form);
			}
			
			// explicit trust relations assigned by this identity
			contentDiv.addChild("br");
			contentDiv.addChild("b", "Explicit trust relations exposed by this identity:");
			contentDiv.addChild("br");
			contentDiv.addChild("br");
			
			table = new HTMLNode("table");
			tr = new HTMLNode("tr");
			tr.addChild("th", "nr.");
			tr.addChild("th", "identity");
			tr.addChild("th", "Trust");
			tr.addChild("th", "Comment");
			tr.addChild("th", "action");
			table.addChild(tr);
			
			int i = 1;
			HTMLNode link;
			HTMLNode td;
			for(Relationship edge : identity.getRelationships(Direction.OUTGOING, Rel.TRUSTS)) {
				if (edge.hasProperty(IEdge.SCORE) && edge.hasProperty(IEdge.COMMENT)) {
					byte trustValue = (Byte) edge.getProperty(IEdge.SCORE);
					String trustComment = (String) edge.getProperty(IEdge.COMMENT);
					
					String peerName;
					Node peer_identity = edge.getEndNode();
					if (peer_identity.hasProperty(IVertex.NAME)) {
						peerName = (String) peer_identity.getProperty(IVertex.NAME);
					} else {
						peerName = "(Not yet downloaded)";
					}
					String peerID = (String) peer_identity.getProperty(IVertex.ID);

					link = new HTMLNode("a", peerName+" ("+peerID+")");
					link.addAttribute("href", WebOfTrust.basePath+"/ShowIdentity?id="+peerID);
					link.addAttribute("name", Integer.toString(i));
					tr = new HTMLNode("tr");
					tr.addChild("td", Integer.toString(i));
					td = new HTMLNode("td");
					td.addChild(link);
					tr.addChild(td);
					tr.addChild("td", Integer.toString(trustValue));
					tr.addChild("td", trustComment);
					
					if (identity.hasProperty(IVertex.OWN_IDENTITY)) {
						// identity we are displaying is a local one, thus display additional options!
						form = new HTMLNode("form");
						form.addAttribute("action", WebOfTrust.basePath+"/ShowIdentity?id="+id + "#"+(i-1));
						form.addAttribute("method", "post");
						form.addChild(Utils.getInput("submit", "", "Remove"));
						form.addChild(Utils.getInput("hidden", "action", "remove_edge"));
						form.addChild(Utils.getInput("hidden", "edge_id", Long.toString(edge.getId())));
						td = new HTMLNode("td");
						td.addChild(form);
						tr.addChild(td);
					} else {
						tr.addChild("td");
					}
					table.addChild(tr);
					i += 1;
				}
			}
			contentDiv.addChild(table);
			
			// explicit trust relations given by others
			contentDiv.addChild("br");
			contentDiv.addChild("b", "Explicit trust relations given by others to this identity:");
			contentDiv.addChild("br");
			contentDiv.addChild("br");
			
			table = new HTMLNode("table");
			tr = new HTMLNode("tr");
			tr.addChild("th", "nr.");
			tr.addChild("th", "identity");
			tr.addChild("th", "Trust");
			tr.addChild("th", "Comment");
			table.addChild(tr);

			i = 1;
			for(Relationship edge : identity.getRelationships(Direction.INCOMING, Rel.TRUSTS)) {
				Node peer_identity = edge.getStartNode();
				byte trustValue = (Byte) edge.getProperty(IEdge.SCORE);
				String trustComment = (String) edge.getProperty(IEdge.COMMENT);
				
				String peerName;
				try	{
					peerName = (String) peer_identity.getProperty(IVertex.NAME);
				} catch(org.neo4j.graphdb.NotFoundException e) {
					peerName = "... not retrieved yet ...";
				}
				
				String peerID = (String) peer_identity.getProperty(IVertex.ID);
				tr = new HTMLNode("tr");
				link = new HTMLNode("a", peerName+" ("+peerID+")");
				link.addAttribute("href", WebOfTrust.basePath+"/ShowIdentity?id="+peerID);
				tr.addChild("td", Integer.toString(i));
				td = new HTMLNode("td");
				td.addChild(link);
				tr.addChild(td);
				tr.addChild("td", Byte.toString(trustValue));
				tr.addChild("td", trustComment);
				table.addChild(tr);

				i += 1;
			}
			contentDiv.addChild(table);
			mPageNode.content.addChild(contentDiv);
			// finally send the request
			writeReply(ctx, 200, "text/html", "OK", mPageNode.outer.generate());
		}
		catch(Exception ex) {
			// FIXME: catch only specific exceptions
			ex.printStackTrace();
			writeReply(ctx, 200, "text/plain", "error retrieve identity", "Cannot display identity, maybe it hasn't been retrieved from Freenet yet.");
		} finally {
			// uh? why do we need this?
		}
	}

	private HTMLNode getKeyPairRow(String key, Object value, int property_index) {
		HTMLNode tr = new HTMLNode("tr");
		HTMLNode td;
		// user modifiable key
		td = new HTMLNode("td");
		td.addChild(Utils.getInput("text", "propertyName"+property_index, key));
		tr.addChild(td);
		// user modifiable value
		HTMLNode input = Utils.getInput("text", "propertyValue"+property_index, value.toString());
		input.addAttribute("size", "100");
		td = new HTMLNode("td");
		td.addChild(input);
		tr.addChild(td);
		// invisible old key
		tr.addChild(Utils.getInput("hidden", "propertyType"+property_index, value.getClass().getName().toString()));
		tr.addChild(Utils.getInput("hidden", "oldPropertyName"+property_index, key));
		// invisible old value
		tr.addChild(Utils.getInput("hidden", "oldPropertyValue"+property_index, value.toString()));
		return tr;
	}
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException, InstantiationException, IllegalAccessException, ClassNotFoundException {
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeReply(ctx, 403, "text/plain", "forbidden", "Your host is not allowed to access this page.");
			return;
		}
		final String action = request.getPartAsStringFailsafe("action", 1000);
		
		Transaction tx = db.beginTx();
		try {
			if (action.equals("remove_edge")) {
				final long edge_id = Long.parseLong(request.getPartAsStringFailsafe("edge_id", 1000));
				db.getRelationshipById(edge_id).delete();
			} else if (action.equals("set_trust")) {
				setTrust(request);
			} else if (action.equals("modify_properties")) {
				modifyProperties(request);
			}
			else if (action.equals("remove_context"))
			{
				removeContext(request);
			}
			tx.success();
		} finally {
			tx.finish();
		}
		// finally treat the request like any GET request
		handleMethodGET(uri, request, ctx);
	}

	/**
	 * Remove a context from a local identity
	 * @param request
	 */
	
	private void removeContext(HTTPRequest request) {
		final String context_to_remove = request.getParam("context");
		final String id = request.getParam("own_identity_id");
	
		Transaction tx = db.beginTx();
		try
		{
			for(Node node : nodeIndex.get(IVertex.ID, id))
			{
				for(Relationship rel : node.getRelationships(Rel.HAS_CONTEXT, Direction.OUTGOING))
				{
					final String storedContext = (String) rel.getEndNode().getProperty(IContext.NAME);
					if (storedContext.equals(context_to_remove))
					{
						rel.delete();
					}
				}
			}
			tx.success();
		}
		finally
		{
			tx.finish();
		}
	}
	

	private void modifyProperties(HTTPRequest request) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
		final String id = request.getPartAsStringFailsafe("identity", 1000);
		final Node id_vertex = nodeIndex.get(IVertex.ID, id).getSingle();
		
		int i = 0;
		while(request.isPartSet("oldPropertyName"+i)) {
			String oldPropertyName = request.getPartAsStringFailsafe("oldPropertyName"+i, 10000);
			String oldPropertyValue = request.getPartAsStringFailsafe("oldPropertyValue"+i, 10000);
			String propertyName = request.getPartAsStringFailsafe("propertyName"+i, 10000);
			String propertyValue = request.getPartAsStringFailsafe("propertyValue"+i, 10000);
			String propertyType = request.getPartAsStringFailsafe("propertyType"+i, 10000);

			try {

			//build the right object type using the string representation
			Object realPropertyValue = Class.forName(propertyType).getDeclaredConstructor(String.class).newInstance(propertyValue);
			
			//update the property in the graph database
			id_vertex.removeProperty(oldPropertyName);
			if (!propertyName.trim().equals("")) { //only re-add when it is an actual value!
				id_vertex.setProperty(propertyName, realPropertyValue);
			}
			
			} catch (NoSuchMethodException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IllegalArgumentException e) {
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
			
			i += 1;
		}
	}

	protected void setTrust(HTTPRequest request) {
		String own_identity = request.getPartAsStringFailsafe("own_identity_id", 1000);
		String identity = request.getPartAsStringFailsafe("identity_id", 1000);
		String trustValue = request.getPartAsStringFailsafe("trust_value", 1000);
		String trustComment = request.getPartAsStringFailsafe("trust_comment", 1000);

		SetTrust.setTrust(db, nodeIndex, own_identity, identity, trustValue, trustComment);
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
