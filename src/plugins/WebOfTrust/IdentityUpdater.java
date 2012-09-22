package plugins.WebOfTrust;

import java.net.MalformedURLException;
import java.sql.SQLException;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.ReadableIndex;
import org.w3c.dom.DOMException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;
import plugins.WebOfTrust.util.Utils;

import com.db4o.ObjectContainer;

import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;

public class IdentityUpdater implements ClientGetCallback{

	private final GraphDatabaseService db;
	private final boolean isOwnIdentity;
	private RequestScheduler rs;
	private ReadableIndex<org.neo4j.graphdb.Node> nodeIndex;

	public IdentityUpdater(RequestScheduler rs, GraphDatabaseService db, HighLevelSimpleClient hl, boolean isOwnIdentity)
	{
		this.db = db;
		this.isOwnIdentity = isOwnIdentity;
		this.rs = rs;
		this.nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
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

	private void addTrustRelations(Document doc, FreenetURI freenetURI) throws SQLException, MalformedURLException, DOMException
	{
		Node ownIdentity = doc.getElementsByTagName("Identity").item(0);
		final String identityName = ownIdentity.getAttributes().getNamedItem("Name").getNodeValue();
		final String publishesTrustList = ownIdentity.getAttributes().getNamedItem("PublishesTrustList").getNodeValue();
		long current_edition = freenetURI.getEdition();

		//setup identiy and possibly store it in the graphstore
		final org.neo4j.graphdb.Node identity = getIdentity(freenetURI, current_edition);

		if (current_edition > getCurrentStoredEdition(identity)) //what we are fetching should be newer, if not, don't even bother updating everything
		{
			updateKeyEditions(freenetURI, current_edition, identity); //always update the keys no matter what

			//always update:
			Transaction tx = db.beginTx();
			try
			{
				identity.setProperty(IVertex.NAME, identityName);
				identity.setProperty(IVertex.PUBLISHES_TRUSTLIST, publishesTrustList);
			
				SetContexts(identity, doc.getElementsByTagName("Context"));
				SetProperties(identity, doc.getElementsByTagName("Property"));

				identity.setProperty(IVertex.LAST_FETCHED, System.currentTimeMillis());

				for(Relationship rel : identity.getRelationships(Direction.OUTGOING)) rel.delete();
				
				tx.success();
			}
			finally
			{
				tx.finish();
			}

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

						tx = db.beginTx();
						try
						{
							org.neo4j.graphdb.Node peer = getPeerIdentity(db, peerIdentityKey);
							Relationship edge = peer.createRelationshipTo(identity, Rel.TRUSTS);

							edge.setProperty(IEdge.COMMENT, trustComment);
							edge.setProperty(IEdge.SCORE, trustValue);

							//fetch the new identity if the USK value we're referred to seeing is newer than the one we are already aware of
							final long current_ref_edition = peerIdentityKey.getEdition();

							long stored_edition = -1;
							if (peer.hasProperty(IVertex.EDITION))
							{
								stored_edition = (Long) peer.getProperty(IVertex.EDITION); 
							}

							if(stored_edition < current_ref_edition)
							{
								//update request uri to latest know edition
								peer.setProperty(IVertex.REQUEST_URI, peerIdentityKey.toASCIIString());

								//start fetching it
								rs.addBacklog(peerIdentityKey);
							}								
							
							tx.success();
						}
						finally
						{
							tx.finish();
						}

					}
				}
			}
		}
	}
	public static org.neo4j.graphdb.Node getPeerIdentity(GraphDatabaseService db, final FreenetURI peerIdentityKey)	throws SQLException 
	{
		ReadableIndex<org.neo4j.graphdb.Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		org.neo4j.graphdb.Node peer = nodeIndex.get(IVertex.ID, Utils.getIDFromKey(peerIdentityKey)).getSingle();

		if (peer == null)  {	//create a new identity
			peer = db.createNode();

			peer.setProperty(IVertex.REQUEST_URI, peerIdentityKey.toASCIIString());
			peer.setProperty(IVertex.ID, Utils.getIDFromKey(peerIdentityKey));

			//default to edition 0, because we want to ensure fetching the identity
			peer.setProperty(IVertex.EDITION, -1l);

			//store when we first saw the identity
			peer.setProperty(IVertex.FIRST_FETCHED, System.currentTimeMillis());
		}
	
		return peer;
	}

	private static void SetProperties(org.neo4j.graphdb.Node identity, NodeList propertiesXML) throws SQLException 
	{
		//add all the (new) properties
		for(int i=0; i < propertiesXML.getLength(); i++)
		{
			Node context = propertiesXML.item(i);
			final NamedNodeMap attr = context.getAttributes();
			final String name = attr.getNamedItem("Name").getNodeValue();
			final String value = attr.getNamedItem("Value").getNodeValue();

			identity.setProperty(name, value);
		}
	}

	private org.neo4j.graphdb.Node getIdentity(FreenetURI identityKey, long current_edition) throws SQLException {

		ReadableIndex<org.neo4j.graphdb.Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		final String identityID = Utils.getIDFromKey(identityKey);
		org.neo4j.graphdb.Node identity = nodeIndex.get(IVertex.ID, identityID).getSingle();

		if (identity == null)  { //or create a new one
			Transaction tx = db.beginTx();
			try
			{
				identity = db.createNode();
				identity.setProperty(IVertex.ID, identityID);		

				tx.success();
			}
			finally
			{
				tx.finish();
			}
		}

		Transaction tx = db.beginTx();
		try
		{
			//update the request and insert editions based on the edition
			if (isOwnIdentity) identity.setProperty(IVertex.OWN_IDENTITY, true); 

			//always try and remove the DONT INSERT property...
			identity.removeProperty(IVertex.DONT_INSERT);

			tx.success();
		}
		finally
		{
			tx.finish();
		}


		return identity;
	}

	private static long getCurrentStoredEdition(org.neo4j.graphdb.Node vertex) throws SQLException
	{
		if (vertex.hasProperty(IVertex.EDITION))
		{
			return (Long) vertex.getProperty(IVertex.EDITION);
		}
		else
		{
			return -1l;
		}
	}

	private void updateKeyEditions(FreenetURI identityKey, long current_edition, org.neo4j.graphdb.Node identity) throws SQLException 
	{
		if (current_edition > getCurrentStoredEdition(identity))
		{
			Transaction tx = db.beginTx();

			try
			{
				identity.setProperty(IVertex.EDITION, current_edition);

				//update the request and insert keys with the most recent known edition
				identity.setProperty(IVertex.REQUEST_URI, identityKey.setSuggestedEdition(current_edition).toASCIIString());

				tx.success();
			}
			finally
			{
				tx.finish();
			}

			//update the insert uri if we have one
			if (identity.hasProperty(IVertex.INSERT_URI))
			{
				try {
					FreenetURI insertURI = new FreenetURI((String) identity.getProperty(IVertex.INSERT_URI));
					insertURI = insertURI.setSuggestedEdition(current_edition);

					tx = db.beginTx();
					try
					{
						identity.setProperty(IVertex.INSERT_URI, insertURI.toASCIIString());

						tx.success();
					}
					finally {tx.finish();};

				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
		}
	}

	private void SetContexts(org.neo4j.graphdb.Node identity, NodeList contextsXML) throws SQLException 
	{
		//remove all old contexts
		for(Relationship rel :identity.getRelationships(Rel.HAS_CONTEXT))
		{
			rel.delete();
		}

		//add all the (new) contexts
		for(int i=0; i < contextsXML.getLength(); i++)
		{
			Node context = contextsXML.item(i); //XML node!
			final NamedNodeMap attr = context.getAttributes();
			final String name = attr.getNamedItem("Name").getNodeValue();

			org.neo4j.graphdb.Node contextNode = nodeIndex.get(IContext.NAME, name).getSingle();
			if (contextNode == null)
			{
					contextNode = db.createNode();
					contextNode.setProperty(IContext.NAME, name);
			}

			identity.createRelationshipTo(contextNode, Rel.HAS_CONTEXT);
		}
	}

}
