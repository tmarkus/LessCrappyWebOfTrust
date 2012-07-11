package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.Edge;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public class ShowIdentityController extends freenet.plugin.web.HTMLFileReaderToadlet {

	private H2GraphFactory gf;
	
	public ShowIdentityController(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, H2GraphFactory gf) {
		super(client, filepath, URLPath);
		this.gf = gf;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		H2Graph graph = null;
		try
		{
			graph = gf.getGraph();

			//existing patterns
		    Document doc = Jsoup.parse(readFile());
			
			//new patterns
			Element info_div = doc.select("#info").first();

			//get the query param
			final String id = request.getParam("id");
			
			//get the identity vertex & properties
			final Long id_vertex = graph.getVertexByPropertyValue(IVertex.ID, id).get(0);
			final Map<String, List<String>> props = graph.getVertexProperties(id_vertex);
			final boolean is_own_identity = props.containsKey(IVertex.OWN_IDENTITY);
			
			info_div.append("<h1>Identity properties</h1>");
			SortedSet<String> sortedKeys = new TreeSet<String>(props.keySet());
			
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
				for(String value : props.get(key))
				{
					if (is_own_identity)
					{
						Element propertyName = doc.createElement("input").attr("type", "text").attr("name", "propertyName"+property_index).val(key);
						Element propertyValue = doc.createElement("input").attr("type", "text") .attr("name", "propertyValue"+property_index).val(value).attr("size", "100");
						Element oldPropertyName = doc.createElement("input").attr("type", "hidden") .attr("name", "oldPropertyName"+property_index).val(key);
						Element oldPropertyValue = doc.createElement("input").attr("type", "hidden") .attr("name", "oldPropertyValue"+property_index).val(value);
						
						Element tr = doc.createElement("tr");
						table.appendChild(tr);
						tr.appendChild(doc.createElement("td").appendChild(propertyName));
						tr.appendChild(doc.createElement("td").appendChild(propertyValue));
						
						tr.appendChild(oldPropertyName);
						tr.appendChild(oldPropertyValue);
					}
					else
					{
						table.appendChild(doc.createElement("tr").appendChild(doc.createElement("td").text(key)).appendChild(doc.createElement("td").text(value)));	
					}
				
					property_index += 1;
				}
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
			
			//allow specifying an updated trust value
			info_div.append("<h1>Local trust assignments to this identity:</h1>");
			for(long own_vertex_id : graph.getVertexByPropertyValue(IVertex.OWN_IDENTITY, "true"))
			{
				String current_trust_value = "";
				String current_comment = "";
				for(Edge edge: graph.getIncomingEdges(id_vertex))
				{
					if (edge.vertex_from == own_vertex_id) {
						current_trust_value = edge.getProperty(IEdge.SCORE);
						current_comment = edge.getProperty(IEdge.COMMENT);
					}
				}
			
				Map<String, List<String>> own_props = graph.getVertexProperties(own_vertex_id);
				
				Element form = doc.createElement("form").attr("method", "post").attr("action", WebOfTrust.basePath+"/ShowIdentity?id="+id);
				Element fieldset = doc.createElement("fieldset");
				fieldset.appendChild(doc.createElement("legend").text(own_props.get(IVertex.NAME).get(0)));
				form.appendChild(fieldset);
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "action").attr("value", "set_trust"));
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "identity_id").attr("value", id));
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "own_identity_id").attr("value", own_props.get(IVertex.ID).get(0)));
				fieldset.appendText("Trust: ");
				fieldset.appendChild(doc.createElement("input").attr("type", "number").attr("name", "trust_value").attr("value", current_trust_value));
				fieldset.appendText("Comment: ");
				fieldset.appendChild(doc.createElement("input").attr("type", "text").attr("name", "trust_comment").attr("value", current_comment));
				fieldset.appendChild(doc.createElement("input").attr("type", "submit").attr("value", "Update"));
				
				info_div.appendChild(form);
			}
			
			
			//explicit trust relations assigned by this identity
			info_div.append("<h1>Explicit trust relations exposed by this identity:</h1>");
			List<Edge> edges = graph.getOutgoingEdges(id_vertex);
			
			Element tableTrust = doc.createElement("table");
			tableTrust.appendChild(doc.createElement("tr")
									.appendChild(doc.createElement("th").text("nr."))
									.appendChild(doc.createElement("th").text("identity"))
									.appendChild(doc.createElement("th").text("Trust"))
									.appendChild(doc.createElement("th").text("Comment"))
									.appendChild(doc.createElement("th").text("action"))
									);
			
			int i = 1;
			for(Edge edge : edges)
			{
				Map<String, List<String>> peer_identity_props = graph.getVertexProperties(edge.vertex_to);
				Map<String, List<String>> edge_properties = edge.getProperties();
				
				if (edge_properties.containsKey(IEdge.SCORE) && edge_properties.containsKey(IEdge.COMMENT))
				{
					String trustValue = edge_properties.get(IEdge.SCORE).get(0);
					String trustComment = edge_properties.get(IEdge.COMMENT).get(0);
					
					String peerName;
					if (peer_identity_props.containsKey(IVertex.NAME))	peerName = peer_identity_props.get(IVertex.NAME).get(0);
					else												peerName = "(Not yet downloaded)";
					String peerID = peer_identity_props.get(IVertex.ID).get(0);

					Element a = doc.createElement("a").attr("href", WebOfTrust.basePath+"/ShowIdentity?id="+peerID).text(peerName+" ("+peerID+")").attr("name", Integer.toString(i));
					Element tr = doc.createElement("tr")
							.appendChild(doc.createElement("td").text(Integer.toString(i)))
							.appendChild(doc.createElement("td").appendChild(a))
							.appendChild(doc.createElement("td").text(trustValue))
							.appendChild(doc.createElement("td").text(trustComment));
					tableTrust.appendChild(tr);
					
					//identity we are displaying is a local one, thus display additional options!
					if (props.containsKey(IVertex.OWN_IDENTITY))
					{
						Element delete_edge_form = doc.createElement("form");
						delete_edge_form.attr("action", "/"+WebOfTrust.basePath+"/ShowIdentity?id="+id + "#"+(i-1));
						delete_edge_form.attr("method", "post");
						delete_edge_form.appendChild(doc.createElement("input").attr("type", "submit").attr("value", "Remove"));
						delete_edge_form.appendChild(doc.createElement("input").attr("type", "hidden").attr("value", "remove_edge").attr("name", "action"));
						delete_edge_form.appendChild(doc.createElement("input").attr("type", "hidden").attr("value", Long.toString(edge.id)).attr("name", "edge_id"));
						tr.appendChild(doc.createElement("td").appendChild(delete_edge_form));
					}
					
					i += 1;
				}
			}
			info_div.appendChild(tableTrust);
			
			//explicit trust relations given by others
			info_div.append("<h1>Explicit trust relations given by others to this identity:</h1>");
			edges = graph.getIncomingEdges(id_vertex);

			
			Element tableTrusters = doc.createElement("table");
			tableTrusters.appendChild(doc.createElement("tr")
									.appendChild(doc.createElement("th").text("nr."))
									.appendChild(doc.createElement("th").text("identity"))
									.appendChild(doc.createElement("th").text("Trust"))
									.appendChild(doc.createElement("th").text("Comment"))
									);
			i = 1;
			for(Edge edge : edges)
			{
				Map<String, List<String>> peer_identity_props = graph.getVertexProperties(edge.vertex_from);
				Map<String, List<String>> edge_properties = edge.getProperties();
				
				String trustValue = edge_properties.get("score").get(0);
				String trustComment = edge_properties.get("comment").get(0);
				String peerName = peer_identity_props.get("name").get(0);
				String peerID = peer_identity_props.get("id").get(0);
				
				Element a = doc.createElement("a").attr("href", "/"+WebOfTrust.basePath+"/ShowIdentity?id="+peerID).text(peerName+" ("+peerID+")");
				tableTrusters.appendChild(doc.createElement("tr")
						.appendChild(doc.createElement("td").text(Integer.toString(i)))
						.appendChild(doc.createElement("td").appendChild(a))
						.appendChild(doc.createElement("td").text(trustValue))
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

	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, SQLException, IOException
	{
		final String action = request.getPartAsStringFailsafe("action", 1000);

		H2Graph graph = gf.getGraph();
		try
		{
			if (action.equals("remove_edge"))
			{
				final long edge_id = Long.parseLong(request.getPartAsStringFailsafe("edge_id", 1000));
				graph.removeEdge(edge_id);
			}
			else if (action.equals("set_trust"))	setTrust(request, graph);
			else if (action.equals("modify_properties")) modifyProperties(request, graph);
		}
		finally
		{
			graph.close();
		}

		handleMethodGET(uri, request, ctx);
	}

	private void modifyProperties(HTTPRequest request, H2Graph graph) throws SQLException 
	{
		final String id = request.getPartAsStringFailsafe("identity", 1000);
		final long id_vertex = graph.getVertexByPropertyValue(IVertex.ID, id).get(0);
		
		int i = 0;
		while(request.isPartSet("oldPropertyName"+i))
		{
			String oldPropertyName = request.getPartAsStringFailsafe("oldPropertyName"+i, 10000);
			String oldPropertyValue = request.getPartAsStringFailsafe("oldPropertyValue"+i, 10000);
			String propertyName = request.getPartAsStringFailsafe("propertyName"+i, 10000);
			String propertyValue = request.getPartAsStringFailsafe("propertyValue"+i, 10000);
			
			graph.removeVertexPropertyValue(id_vertex, oldPropertyName, oldPropertyValue);
			if (!propertyName.trim().equals(""))
			{
				graph.addVertexProperty(id_vertex, propertyName, propertyValue);	
			}
		
			i += 1;
		}
	}

	protected void setTrust(HTTPRequest request, H2Graph graph)
			throws SQLException {
		String own_identity = request.getPartAsStringFailsafe("own_identity_id", 1000);
		String identity = request.getPartAsStringFailsafe("identity_id", 1000);
		String trustValue = request.getPartAsStringFailsafe("trust_value", 1000);
		String trustComment = request.getPartAsStringFailsafe("trust_comment", 1000);

		List<Long> own_vertices = graph.getVertexByPropertyValue(IVertex.ID, own_identity);
		List<Long> identity_vertices = graph.getVertexByPropertyValue(IVertex.ID, identity);
		
		for(long own_vertex : own_vertices)
		{
			for(long identity_vertex : identity_vertices)
			{
				long edge;
				try						{ edge = graph.getEdgeByVerticesAndProperty(own_vertex, identity_vertex, IEdge.SCORE); }
				catch(SQLException e) 	{ edge = graph.addEdge(own_vertex, identity_vertex); }

				graph.updateEdgeProperty(edge, IEdge.SCORE, trustValue);
				graph.updateEdgeProperty(edge, IEdge.COMMENT, trustComment);
			}
		}
	}
	
	@Override
	public void terminate() throws SQLException {
	}
}
