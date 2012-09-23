package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;
import plugins.WebOfTrust.fcp.SetTrust;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public class ShowIdentityController extends freenet.plugin.web.HTMLFileReaderToadlet {

	private GraphDatabaseService db;
	
	public ShowIdentityController(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, GraphDatabaseService db) {
		super(client, main.getDB(), filepath, URLPath);
		this.db = db;
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
			final String id = request.getParam("id");
			
			//get the identity vertex & properties
			final Node identity = nodeIndex.get(IVertex.ID, id).getSingle();
			final boolean is_own_identity = identity.hasProperty(IVertex.OWN_IDENTITY);
			
			info_div.append("<h1>Identity properties</h1>");
			SortedSet<String> sortedKeys = new TreeSet<String>();
			for(String key : identity.getPropertyKeys()) sortedKeys.add(key);
			
			Element propertiesForm = doc.createElement("form").attr("action", "#").attr("method", "post");
			propertiesForm.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "action").val("modify_properties"));
			propertiesForm.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "identity").val(id));

			Element table = doc.createElement("table");
			propertiesForm.appendChild(table);
			table.appendChild(
								doc.createElement("tr").appendChild(
										doc.createElement("th").text("Name")).appendChild(
										doc.createElement("th").text("Value")));
			
			//generic properties associated with this identity in the graph store
			int property_index = 0;
			for(String key : sortedKeys)
			{
				Object value = identity.getProperty(key);
				if (is_own_identity)
				{
					Element propertyName = doc.createElement("input").attr("type", "text").attr("name", "propertyName"+property_index).val(key);
					Element propertyValue = doc.createElement("input").attr("type", "text") .attr("name", "propertyValue"+property_index).val(value.toString()).attr("size", "100");
					Element oldPropertyName = doc.createElement("input").attr("type", "hidden") .attr("name", "oldPropertyName"+property_index).val(key);
					Element oldPropertyValue = doc.createElement("input").attr("type", "hidden") .attr("name", "oldPropertyValue"+property_index).val(value.toString());
					
					Element tr = doc.createElement("tr");
					table.appendChild(tr);
					tr.appendChild(doc.createElement("td").appendChild(propertyName));
					tr.appendChild(doc.createElement("td").appendChild(propertyValue));
					
					tr.appendChild(oldPropertyName);
					tr.appendChild(oldPropertyValue);
				}
				else
				{
					table.appendChild(doc.createElement("tr").appendChild(doc.createElement("td").text(key)).appendChild(doc.createElement("td").text(value.toString())));	
				}
			
				property_index += 1;
			}

			//extra empty property value pair
			if (is_own_identity)
			{
				Element tr = doc.createElement("tr");
				tr.appendChild(doc.createElement("td").appendChild(doc.createElement("input").attr("type", "text").attr("name", "propertyName"+property_index))).appendChild(
					doc.createElement("td").appendChild(doc.createElement("input").attr("type", "text") .attr("name", "propertyValue"+property_index).attr("size", "100"))).appendChild(
					doc.createElement("input").attr("type", "hidden") .attr("name", "oldPropertyName"+property_index)).appendChild(
					doc.createElement("input").attr("type", "hidden") .attr("name", "oldPropertyValue"+property_index));
				
				table.appendChild(tr);
			}

			//submit button
			if (is_own_identity)
			{
				Element modifySubmit = doc.createElement("input").attr("type", "submit").val("Modify properties");
				propertiesForm.appendChild(modifySubmit);
			}
			info_div.appendChild(propertiesForm);


			//display available contexts
			Element contexts = doc.createElement("div");
			contexts.append("<h2>Contexts</h2>");
			Element contexts_list = doc.createElement("ul");
			for(Relationship rel : identity.getRelationships(Direction.OUTGOING, Rel.HAS_CONTEXT))
			{
				Element li = doc.createElement("li");
				li.text((String) rel.getEndNode().getProperty(IContext.NAME));
				contexts_list.appendChild(li);
			}
			info_div.appendChild(contexts.appendChild(contexts_list));

			
			//allow specifying an updated trust value
			info_div.append("<h1>Local trust assignments to this identity:</h1>");

			for(Node own_vertex : nodeIndex.get(IVertex.OWN_IDENTITY, true))
			{
				byte current_trust_value = 0;
				String current_comment = "";
				
				for(Relationship edge : own_vertex.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
				{
						current_trust_value = (Byte) edge.getProperty(IEdge.SCORE);
						current_comment = (String) edge.getProperty(IEdge.COMMENT);
				}
			
				Element form = doc.createElement("form").attr("method", "post").attr("action", WebOfTrust.basePath+"/ShowIdentity?id="+id);
				Element fieldset = doc.createElement("fieldset");
				fieldset.appendChild(doc.createElement("legend").text( (String) own_vertex.getProperty(IVertex.NAME)));
				form.appendChild(fieldset);
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "action").attr("value", "set_trust"));
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "identity_id").attr("value", id));
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "own_identity_id").attr("value", (String) own_vertex.getProperty(IVertex.ID)));
				fieldset.appendText("Trust: ");
				fieldset.appendChild(doc.createElement("input").attr("type", "number").attr("name", "trust_value").attr("value", Byte.toString(current_trust_value)));
				fieldset.appendText("Comment: ");
				fieldset.appendChild(doc.createElement("input").attr("type", "text").attr("name", "trust_comment").attr("value", current_comment));
				fieldset.appendChild(doc.createElement("input").attr("type", "submit").attr("value", "Update"));
				
				info_div.appendChild(form);
			}
			
			
			//explicit trust relations assigned by this identity
			info_div.append("<h1>Explicit trust relations exposed by this identity:</h1>");
			
			
			Element tableTrust = doc.createElement("table");
			tableTrust.appendChild(doc.createElement("tr")
									.appendChild(doc.createElement("th").text("nr."))
									.appendChild(doc.createElement("th").text("identity"))
									.appendChild(doc.createElement("th").text("Trust"))
									.appendChild(doc.createElement("th").text("Comment"))
									.appendChild(doc.createElement("th").text("action"))
									);
			
			int i = 1;
			for(Relationship edge : identity.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
			{
				
				if (edge.hasProperty(IEdge.SCORE) && edge.hasProperty(IEdge.COMMENT))
				{
					byte trustValue = (Byte) edge.getProperty(IEdge.SCORE);
					String trustComment = (String) edge.getProperty(IEdge.COMMENT);
					
					String peerName;
					Node peer_identity = edge.getEndNode();
					if (peer_identity.hasProperty(IVertex.NAME))	peerName = (String) peer_identity.getProperty(IVertex.NAME);
					else												peerName = "(Not yet downloaded)";
					
					String peerID = (String) peer_identity.getProperty(IVertex.ID);

					Element a = doc.createElement("a").attr("href", WebOfTrust.basePath+"/ShowIdentity?id="+peerID).text(peerName+" ("+peerID+")").attr("name", Integer.toString(i));
					Element tr = doc.createElement("tr")
							.appendChild(doc.createElement("td").text(Integer.toString(i)))
							.appendChild(doc.createElement("td").appendChild(a))
							.appendChild(doc.createElement("td").text(Integer.toString(trustValue)))
							.appendChild(doc.createElement("td").text(trustComment));
					tableTrust.appendChild(tr);
					
					//identity we are displaying is a local one, thus display additional options!
					if (identity.hasProperty(IVertex.OWN_IDENTITY))
					{
						Element delete_edge_form = doc.createElement("form");
						delete_edge_form.attr("action", WebOfTrust.basePath+"/ShowIdentity?id="+id + "#"+(i-1));
						delete_edge_form.attr("method", "post");
						delete_edge_form.appendChild(doc.createElement("input").attr("type", "submit").attr("value", "Remove"));
						delete_edge_form.appendChild(doc.createElement("input").attr("type", "hidden").attr("value", "remove_edge").attr("name", "action"));
						delete_edge_form.appendChild(doc.createElement("input").attr("type", "hidden").attr("value", Long.toString(edge.getId())).attr("name", "edge_id"));
						tr.appendChild(doc.createElement("td").appendChild(delete_edge_form));
					}
					
					i += 1;
				}
			}
			info_div.appendChild(tableTrust);
			
			//explicit trust relations given by others
			info_div.append("<h1>Explicit trust relations given by others to this identity:</h1>");

			
			Element tableTrusters = doc.createElement("table");
			tableTrusters.appendChild(doc.createElement("tr")
									.appendChild(doc.createElement("th").text("nr."))
									.appendChild(doc.createElement("th").text("identity"))
									.appendChild(doc.createElement("th").text("Trust"))
									.appendChild(doc.createElement("th").text("Comment"))
									);
			i = 1;
			for(Relationship edge : identity.getRelationships(Direction.INCOMING, Rel.TRUSTS))
			{
				Node peer_identity = edge.getStartNode();
				byte trustValue = (Byte) edge.getProperty(IEdge.SCORE);
				String trustComment = (String) edge.getProperty(IEdge.COMMENT);
				
				String peerName;
				try	{ peerName = (String) peer_identity.getProperty(IVertex.NAME);	}
				catch(org.neo4j.graphdb.NotFoundException e)	{ peerName = "... not retrieved yet ..."; }
				
				String peerID = (String) peer_identity.getProperty(IVertex.ID);
				
				Element a = doc.createElement("a").attr("href", WebOfTrust.basePath+"/ShowIdentity?id="+peerID).text(peerName+" ("+peerID+")");
				tableTrusters.appendChild(doc.createElement("tr")
						.appendChild(doc.createElement("td").text(Integer.toString(i)))
						.appendChild(doc.createElement("td").appendChild(a))
						.appendChild(doc.createElement("td").text(Byte.toString(trustValue)))
						.appendChild(doc.createElement("td").text(trustComment))
						);

				i += 1;
			}
			info_div.appendChild(tableTrusters);
			
			
			writeReply(ctx, 200, "text/html", "content", doc.html());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
			writeReply(ctx, 200, "text/plain", "error retrieve identity", "Cannot display identity, maybe it hasn't been retrieved from Freenet yet.");
		}
		finally
		{
		}
	}

	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		final String action = request.getPartAsStringFailsafe("action", 1000);

		Transaction tx = db.beginTx();
		try
		{
			if (action.equals("remove_edge"))
			{
				final long edge_id = Long.parseLong(request.getPartAsStringFailsafe("edge_id", 1000));
				db.getRelationshipById(edge_id).delete();
			}
			else if (action.equals("set_trust"))	setTrust(request);
			else if (action.equals("modify_properties")) modifyProperties(request);
		
			tx.success();
		}
		finally
		{
			tx.finish();
		}

		handleMethodGET(uri, request, ctx);
	}

	private void modifyProperties(HTTPRequest request) 
	{
		final String id = request.getPartAsStringFailsafe("identity", 1000);
		final Node id_vertex = nodeIndex.get(IVertex.ID, id).getSingle();
		
		int i = 0;
		while(request.isPartSet("oldPropertyName"+i))
		{
			String oldPropertyName = request.getPartAsStringFailsafe("oldPropertyName"+i, 10000);
			String oldPropertyValue = request.getPartAsStringFailsafe("oldPropertyValue"+i, 10000);
			String propertyName = request.getPartAsStringFailsafe("propertyName"+i, 10000);
			String propertyValue = request.getPartAsStringFailsafe("propertyValue"+i, 10000);

			id_vertex.removeProperty(oldPropertyName);
			if (!propertyName.trim().equals(""))
			{
				id_vertex.setProperty(propertyName, propertyValue);
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
	public void terminate() throws SQLException {
	}
}
