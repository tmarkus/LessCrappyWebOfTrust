package plugins.WebOfTrust;

import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.util.Utils;

import thomasmarkus.nl.freenet.graphdb.Edge;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;

import com.db4o.ObjectContainer;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;

public class IdentityUpdater implements ClientGetCallback{

	private final H2GraphFactory gf;
	private final boolean isOwnIdentity;
	private RequestScheduler rs;

	public IdentityUpdater(RequestScheduler rs, H2GraphFactory gf, HighLevelSimpleClient hl, boolean isOwnIdentity)
	{
		this.gf = gf;
		this.isOwnIdentity = isOwnIdentity;
		this.rs = rs;
	}

	@Override
	public void onMajorProgress(ObjectContainer oc) {
		System.out.println("Something happened!");
	}

	@Override
	public void onFailure(FetchException fe, ClientGetter cg, ObjectContainer oc) {

		//deregister our request
		rs.removeInFlight(cg);

		if (fe.mode == FetchException.PERMANENT_REDIRECT)
		{
			rs.addBacklog(fe.newURI);
		}
	}

	@Override
	public void onSuccess(FetchResult fr, ClientGetter cg, ObjectContainer oc) {

		try{
			//deregister our request
			rs.removeInFlight(cg);

			//parse the xml
			Document doc = Utils.getXMLDoc(fr);

			//try to add additional trust relations
			addTrustRelations(doc, cg.getURI());
		}
		catch(Exception e)
		{
			e.printStackTrace();	
		}
	}

	private void addTrustRelations(Document doc, FreenetURI freenetURI) throws SQLException
	{
		H2Graph graph = gf.getGraph();
		try
		{
			graph.setAutoCommit(false);
			
			Node ownIdentity = doc.getElementsByTagName("Identity").item(0);
			final String identityName = ownIdentity.getAttributes().getNamedItem("Name").getNodeValue();
			final String publishesTrustList = ownIdentity.getAttributes().getNamedItem("PublishesTrustList").getNodeValue();
			long current_edition = freenetURI.getEdition();

			//setup identiy and possibly store it in the graphstore
			final long identity = getIdentity(graph, freenetURI, current_edition);

			if (current_edition > getCurrentStoredEdition(graph, identity)) //what we are fetching should be newer, if not, don't even bother updating everything
			{
				updateKeyEditions(graph, freenetURI, current_edition, identity); //always update the keys no matter what

				//always update:
				graph.updateVertexProperty(identity, IVertex.NAME, identityName);
				graph.updateVertexProperty(identity, IVertex.PUBLISHES_TRUSTLIST, publishesTrustList);
				SetContexts(graph, identity, doc.getElementsByTagName("Context"));
				SetProperties(graph, identity, doc.getElementsByTagName("Property"));
				graph.updateVertexProperty(identity, IVertex.LAST_FETCHED, Long.toString(System.currentTimeMillis()));


				//remove all outgoing edges for this peer
				for(Edge edge : graph.getOutgoingEdges(identity))	graph.removeEdge(edge.id);
				
				//Add all the identities that this identity trusts in turn
				Node list = doc.getElementsByTagName("TrustList").item(0);

				if (list != null) //maybe there isn't a trustlist of the identity doesn't publish one
				{
					NodeList children = list.getChildNodes();

					for(int i=0; i < children.getLength(); i++)
					{
						Node element = children.item(i);

						if (element.getNodeType() != Node.TEXT_NODE)
						{
							final NamedNodeMap attr = element.getAttributes();
							final FreenetURI peerIdentityKey = new FreenetURI(attr.getNamedItem("Identity").getNodeValue());
							final String trustComment = attr.getNamedItem("Comment").getNodeValue();
							final Byte trustValue = Byte.parseByte(attr.getNamedItem("Value").getNodeValue());
							
							long peer = getPeerIdentity(graph, peerIdentityKey);
							long edge = graph.addEdge(identity, peer);
							try
							{
								graph.updateEdgeProperty(edge, IEdge.COMMENT, trustComment);
								graph.updateEdgeProperty(edge, IEdge.SCORE, Byte.toString(trustValue));
							}
							catch(SQLException e)
							{
								System.out.println("Failed to add comment or score relation to graph database!");
								System.out.println("Comment = "+trustComment+", Score = "+ Byte.toString(trustValue));
								System.out.println("Identity: " + peerIdentityKey);
								throw e;
							}
							//fetch the new identity if the USK value we're referred to seeing is newer than the one we are already aware of
							final long current_ref_edition = peerIdentityKey.getEdition();
							final Map<String, List<String>> peerProperties = graph.getVertexProperties(peer);
							long stored_edition = -1;
							if (peerProperties != null && peerProperties.containsKey(IVertex.EDITION))
							{
								stored_edition = Long.parseLong(graph.getVertexProperties(peer).get(IVertex.EDITION).get(0));
							}

							if(stored_edition < current_ref_edition)
							{
								//update request uri to latest know edition
								graph.updateVertexProperty(peer, IVertex.REQUEST_URI, peerIdentityKey.toASCIIString());

								//start fetching it
								rs.addBacklog(peerIdentityKey);
							}
						}
					}
				}
			}
			graph.commit();
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
		finally
		{
			graph.setAutoCommit(true);
			graph.close();
		}
	}

	public static long getPeerIdentity(H2Graph graph, final FreenetURI peerIdentityKey)	throws SQLException 
	{
		List<Long> identityMatches = graph.getVertexByPropertyValue(IVertex.ID, Utils.getIDFromKey(peerIdentityKey));
		long peer;

		//existing identity
		if (identityMatches.size() > 0) {
			peer = identityMatches.get(0);
		}
		else  {	//or create a new one
			peer = graph.createVertex();
			graph.updateVertexProperty(peer, IVertex.REQUEST_URI, peerIdentityKey.toASCIIString());
			graph.updateVertexProperty(peer, IVertex.ID, Utils.getIDFromKey(peerIdentityKey));

			//default to edition 0, because we want to ensure fetching the identity
			graph.updateVertexProperty(peer, IVertex.EDITION, "-1");

			//store when we first saw the identity
			graph.updateVertexProperty(peer, IVertex.FIRST_FETCHED, Long.toString(System.currentTimeMillis()));
		}

		return peer;
	}

	private static void SetProperties(H2Graph graph, long identity, NodeList propertiesXML) throws SQLException 
	{
		//add all the (new) properties
		for(int i=0; i < propertiesXML.getLength(); i++)
		{
			Node context = propertiesXML.item(i);
			final NamedNodeMap attr = context.getAttributes();
			final String name = attr.getNamedItem("Name").getNodeValue();
			final String value = attr.getNamedItem("Value").getNodeValue();

			graph.updateVertexProperty(identity, name, value);
		}
	}

	private long getIdentity(H2Graph graph, FreenetURI identityKey, long current_edition) throws SQLException {

		final String identityID = Utils.getIDFromKey(identityKey);
		final List<Long> matches = graph.getVertexByPropertyValue(IVertex.ID, identityID);
		long identity;

		if (matches.size() > 0) {	//existing identity
			identity = matches.get(0);
		}
		else  { //or create a new one
			identity = graph.createVertex();
			graph.updateVertexProperty(identity, IVertex.ID, identityID);

			//update the request and insert editions based on the edition
			if (isOwnIdentity) graph.updateVertexProperty(identity, IVertex.OWN_IDENTITY, Boolean.toString(true));
		}

		//always try and remove the DONT INSERT property...
		graph.removeVertexProperty(identity, IVertex.DONT_INSERT);
		
		return identity;
	}

	private static long getCurrentStoredEdition(H2Graph graph, long vertex_id) throws SQLException
	{
		Map<String, List<String>> props = graph.getVertexProperties(vertex_id);

		if (props.containsKey(IVertex.EDITION))
		{
			return Long.parseLong(props.get(IVertex.EDITION).get(0));
		}
		else
		{
			return -1;
		}
	}

	private static void updateKeyEditions(H2Graph graph, FreenetURI identityKey, long current_edition, long identity) throws SQLException 
	{
		if (current_edition > getCurrentStoredEdition(graph, identity))
		{
			Map<String, List<String>> props = graph.getVertexProperties(identity);
			graph.updateVertexProperty(identity, IVertex.EDITION, Long.toString(current_edition));	

			//update the request and insert keys with the most recent known edition
			graph.updateVertexProperty(identity, IVertex.REQUEST_URI, identityKey.setSuggestedEdition(current_edition).toASCIIString());

			//update the insert uri if we have one
			if (props.containsKey(IVertex.INSERT_URI))
			{
				try {
					FreenetURI insertURI = new FreenetURI(props.get(IVertex.INSERT_URI).get(0));
					insertURI = insertURI.setSuggestedEdition(current_edition);
					graph.updateVertexProperty(identity, IVertex.INSERT_URI, insertURI.toASCIIString());
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private static void SetContexts(H2Graph graph, long identity, NodeList contextsXML) throws SQLException 
	{
		//remove all old contexts
		graph.removeVertexProperty(identity, IVertex.CONTEXT_NAME);

		//add all the (new) contexts
		for(int i=0; i < contextsXML.getLength(); i++)
		{
			Node context = contextsXML.item(i);
			final NamedNodeMap attr = context.getAttributes();
			final String name = attr.getNamedItem("Name").getNodeValue();

			graph.addVertexProperty(identity, IVertex.CONTEXT_NAME, name);
		}
	}

}
