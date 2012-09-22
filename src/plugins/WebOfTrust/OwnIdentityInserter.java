package plugins.WebOfTrust;

import java.io.IOException;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import thomasmarkus.nl.freenet.graphdb.Edge;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;

import javax.xml.parsers.DocumentBuilderFactory;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.ReadableIndex;
import org.w3c.dom.DOMImplementation;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.db4o.ObjectContainer;

import freenet.client.HighLevelSimpleClient;
import freenet.client.InsertBlock;
import freenet.client.InsertContext;
import freenet.client.InsertException;
import freenet.client.async.BaseClientPutter;
import freenet.client.async.ClientPutCallback;
import freenet.client.async.ClientPutter;
import freenet.keys.FreenetURI;
import freenet.node.RequestStarter;
import freenet.support.api.Bucket;
import freenet.support.io.Closer;



public class OwnIdentityInserter implements Runnable, ClientPutCallback  {

	private GraphDatabaseService db;
	private String ownID;
	private HighLevelSimpleClient hl;
	private WebOfTrust wot;
	
	public OwnIdentityInserter(GraphDatabaseService db, String ownID, HighLevelSimpleClient hl, WebOfTrust wot)
	{
		this.db = db;
		this.ownID = ownID;
		this.hl = hl;
		this.wot = wot;
	}
	
	@Override
	public void run() {

		ReadableIndex<Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		Node own_vertex = nodeIndex.get(IVertex.ID, ownID).getSingle();
		
		Transaction tx = db.beginTx();
		try {
		
			if (!own_vertex.hasProperty(IVertex.DONT_INSERT)  ) //identity should have some minimal amount of data...
			{
				String old_hash = "";
				if (own_vertex.hasProperty(IVertex.HASH))		old_hash = (String) own_vertex.getProperty(IVertex.HASH);
				final String new_hash = calculateIdentityHash(own_vertex);
				
				if (!new_hash.equals(old_hash))
				{
					//create the bucket
					final String xml = createXML(own_vertex, null);
					final Bucket bucket = wot.getPR().getNode().clientCore.persistentTempBucketFactory.makeBucket(xml.length());
					createXML(own_vertex, bucket);
					bucket.setReadOnly();

					//create XML document
					long next_edition = 0; //default
					if (own_vertex.hasProperty(IVertex.EDITION))	next_edition =  (Long) own_vertex.getProperty(IVertex.EDITION) + 1;
					
					FreenetURI nextInsertURI = new FreenetURI((String) own_vertex.getProperty(IVertex.INSERT_URI)).setSuggestedEdition(next_edition);
					
					final InsertBlock ib = new InsertBlock(bucket, null, nextInsertURI);
					final InsertContext ictx = hl.getInsertContext(true);
					
					
					//panic check for insertURI inclusion...
					if (xml.contains(IVertex.INSERT_URI))
					{
						System.out.println(xml);
						throw new IllegalStateException("The XML content may have included an insertURI!");
					}

					//insert the damn thing
					System.out.println("INSERTING OWN IDENTITY");
					ClientPutter pu = 	hl.insert(ib, false, null, false, ictx, this, RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS);
					
					//update the time when we stored it in the database (as to disallow inserting it every second)

					own_vertex.setProperty(IVertex.LAST_INSERT, System.currentTimeMillis());
					own_vertex.setProperty(IVertex.HASH, new_hash);
				}
			}
		
			tx.success();
		} catch (TransformerConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerFactoryConfigurationError e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParserConfigurationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (TransformerException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (InsertException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			tx.finish();
		}
	}
	
	@Override
	public void onSuccess(BaseClientPutter cp, ObjectContainer oc) {

		System.out.println("IDENTITY INSERT COMPLETE FOR URI: " + cp.getURI().toASCIIString());
		
		ReadableIndex<Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		Node own_vertex = nodeIndex.get(IVertex.ID, ownID).getSingle();
		
		//update the insert and request uris in the database
		Transaction tx = db.beginTx();
		try {
			FreenetURI newRequestURI = new FreenetURI( (String) own_vertex.getProperty(IVertex.REQUEST_URI));
			long new_edition = cp.getURI().getEdition();
			newRequestURI = newRequestURI.setSuggestedEdition(new_edition);
			
			own_vertex.setProperty(IVertex.EDITION, cp.getURI().getEdition());
			own_vertex.setProperty(IVertex.REQUEST_URI, newRequestURI.toASCIIString());
		
			//TODO: update the insert URI? 
			
			//update the hash value after these updates (otherwise infinite insert
			own_vertex.setProperty(IVertex.HASH, calculateIdentityHash(own_vertex));
		
			tx.success();
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		finally
		{
			Closer.close(((ClientPutter) cp).getData());
			tx.finish();
		}
	}
	
	
	private static String calculateIdentityHash(Node own_vertex) throws SQLException, NoSuchAlgorithmException
	{

		String string_to_hash = "";
		for(String property : own_vertex.getPropertyKeys())
		{
			if (! (property.equals(IVertex.HASH) || property.equals(IVertex.LAST_INSERT))) //don't hash the hash to prevent infinite regression...
			{
				string_to_hash += property;
				string_to_hash += own_vertex.getProperty(property).toString();
			}
		}

		for(Relationship edge : own_vertex.getRelationships(Direction.OUTGOING))
		{

			
			for(String prop : edge.getPropertyKeys())
			{
				string_to_hash += prop;
				string_to_hash += edge.getProperty(prop).toString();
			}
		}
		
		//get md5sum from resulting string
		MessageDigest md5 = MessageDigest.getInstance("MD5");
		md5.update(string_to_hash.getBytes());
		BigInteger hash = new BigInteger(1, md5.digest());
		String hashword = hash.toString(16);
		
		return hashword;
	}
	
	
	/**
	 * Create the XML export of all the interesting properties of an OWN identity
	 * @param own_identity - a long vertex id
	 * @return
	 * @throws SQLException
	 * @throws TransformerFactoryConfigurationError
	 * @throws ParserConfigurationException
	 * @throws TransformerException
	 * @throws IOException 
	 */
	
	private static String createXML(Node own_identity, Bucket bucket) throws SQLException, TransformerFactoryConfigurationError, ParserConfigurationException, TransformerException, IOException
	{

		DocumentBuilderFactory xmlFactory = DocumentBuilderFactory.newInstance();
		xmlFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
		// DOM parser uses .setAttribute() to pass to underlying Xerces
		xmlFactory.setAttribute("http://apache.org/xml/features/disallow-doctype-decl", true);
		DocumentBuilder mDocumentBuilder = xmlFactory.newDocumentBuilder(); 
		DOMImplementation mDOM = mDocumentBuilder.getDOMImplementation();

		Transformer mSerializer = TransformerFactory.newInstance().newTransformer();
		mSerializer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
		mSerializer.setOutputProperty(OutputKeys.INDENT, "yes"); //keep it human readable?
		mSerializer.setOutputProperty(OutputKeys.STANDALONE, "no");

		Document xmlDoc;
		xmlDoc = mDOM.createDocument(null, "WebOfTrust", null);
		
		// 1.0 does not support all Unicode characters which the String class supports. To prevent us from having to filter all Strings, we use 1.1
		xmlDoc.setXmlVersion("1.1");
		
		Element rootElement = xmlDoc.getDocumentElement();
		
		// We include the WoT version to have an easy way of handling bogus XML which might be created by bugged versions.
		rootElement.setAttribute("Version", Long.toString(WebOfTrust.COMPATIBLE_VERSION));
		
		/* Create the identity Element */
		
		Element identityElement = xmlDoc.createElement("Identity");
		identityElement.setAttribute("Version", Integer.toString(1)); /* Version of the XML format */
		
			identityElement.setAttribute("Name", (String) own_identity.getProperty(IVertex.NAME));
			if (own_identity.hasProperty(IVertex.PUBLISHES_TRUSTLIST) && (Boolean) own_identity.getProperty(IVertex.PUBLISHES_TRUSTLIST) )
			{
				identityElement.setAttribute("PublishesTrustList",   Boolean.toString(true));
			}
			else
			{
				identityElement.setAttribute("PublishesTrustList",   Boolean.toString(false));
			}
			
			/* Create the context Elements */
			if (own_identity.hasProperty(IVertex.CONTEXT_NAME) ) // do we even have contexts? :)
			{
				for(String context : (List<String>) own_identity.getProperty(IVertex.CONTEXT_NAME)) {
					Element contextElement = xmlDoc.createElement("Context");
					contextElement.setAttribute("Name", context);
					identityElement.appendChild(contextElement);
				}
			}

			/* Create a list of properties we SHOULD NOT insert */
			List<String> blackList = new LinkedList<String>();
			for(Field field : IVertex.class.getDeclaredFields())
			{
				try {
					blackList.add((String) field.get(new IVertex()));
				} catch (IllegalArgumentException e) {
					e.printStackTrace();
				} catch (IllegalAccessException e) {
					e.printStackTrace();
				}
			}
			
			/* Create the property Elements */
			for(String propertyName : own_identity.getPropertyKeys() ) {
				if (!blackList.contains(propertyName) && !propertyName.contains(IVertex.TRUST+"."))
				{
					Element propertyElement = xmlDoc.createElement("Property");
					propertyElement.setAttribute("Name", propertyName);
					propertyElement.setAttribute("Value",  own_identity.getProperty(propertyName).toString());
					identityElement.appendChild(propertyElement);
				}
			}
			
			/* Create the trust list Element and its trust Elements */
			if (own_identity.hasProperty(IVertex.PUBLISHES_TRUSTLIST) && (Boolean) own_identity.getProperty(IVertex.PUBLISHES_TRUSTLIST))
			{
				Element trustListElement = xmlDoc.createElement("TrustList");

				Iterable<Relationship> edges = own_identity.getRelationships(Direction.OUTGOING);
				for(Relationship edge : edges)
				{
					Node peer_identity = edge.getEndNode();
					
					if(peer_identity.hasProperty(IVertex.REQUEST_URI) )
					{
						Element trustElement = xmlDoc.createElement("Trust");
						trustElement.setAttribute("Identity", (String) peer_identity.getProperty(IVertex.REQUEST_URI));
						trustElement.setAttribute("Value",  edge.getProperty(IEdge.SCORE).toString());
						trustElement.setAttribute("Comment", (String) edge.getProperty(IEdge.COMMENT));
						trustListElement.appendChild(trustElement);
					}
				}
				identityElement.appendChild(trustListElement);
			}
		
		rootElement.appendChild(identityElement);

		//serialize the XML
		DOMSource domSource = new DOMSource(xmlDoc);
		StringWriter resultStringWriter = new StringWriter();
		StreamResult resultStreamString = new StreamResult(resultStringWriter);
		mSerializer.transform(domSource, resultStreamString);
		resultStringWriter.close();
		
		//store the XML in the bucket
		if (bucket != null)
		{
			StreamResult resultStreamBucket = new StreamResult(bucket.getOutputStream());
			mSerializer.transform(domSource, resultStreamBucket);
			resultStreamBucket.getOutputStream().close();
		}
		
		return resultStringWriter.toString();
	}

	
	@Override
	public void onMajorProgress(ObjectContainer arg0) {
	}

	@Override
	public void onFailure(InsertException ie, BaseClientPutter cp, ObjectContainer oc) {

		Closer.close(((ClientPutter) cp).getData());
		
		System.out.println("Failed to insert own identity, please investigate!");
		System.out.println("insert key: " + cp.getURI());
		System.out.println(ie.getMessage());
		System.out.println(ie.getLocalizedMessage());
		ie.printStackTrace();
	}

	@Override
	public void onFetchable(BaseClientPutter arg0, ObjectContainer arg1) {
	}

	@Override
	public void onGeneratedMetadata(Bucket arg0, BaseClientPutter arg1,ObjectContainer arg2) {
		
	}

	@Override
	public void onGeneratedURI(FreenetURI arg0, BaseClientPutter arg1, ObjectContainer arg2) {
	}

}
