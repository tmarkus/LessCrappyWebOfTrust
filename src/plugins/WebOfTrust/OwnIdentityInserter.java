package plugins.WebOfTrust;

import java.io.IOException;
import java.io.OutputStream;
import java.io.StringWriter;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Date;
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
import thomasmarkus.nl.freenet.graphdb.EdgeWithProperty;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
import javax.xml.parsers.DocumentBuilderFactory;

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
import freenet.support.api.BucketFactory;
import freenet.support.io.BucketTools;



public class OwnIdentityInserter implements Runnable, ClientPutCallback {

	private H2Graph graph;
	private String ownID;
	private HighLevelSimpleClient hl;
	private WebOfTrust wot;
	
	public OwnIdentityInserter(H2Graph graph, String ownID, HighLevelSimpleClient hl, WebOfTrust wot)
	{
		this.graph = graph;
		this.ownID = ownID;
		this.hl = hl;
		this.wot = wot;
	}
	
	@Override
	public void run() {
		try {
			//get all the properties of this identity from the graph
			long own_vertex = graph.getVertexByPropertyValue(IVertex.ID, ownID).get(0);
			Map<String, List<String>> props = graph.getVertexProperties(own_vertex);
		
			if (!props.containsKey(IVertex.NAME)) //identity should have some minimal amount of data...
			{
				//create XML document
				String xml = createXML(own_vertex);
				
				//insert the XML
				BucketTools bt = new BucketTools();
				Bucket bucket = wot.getPR().getNode().clientCore.persistentTempBucketFactory.makeBucket(xml.length());
				bucket.setReadOnly();
				
				//get metadata
				long next_edition = Long.parseLong(props.get(IVertex.EDITION).get(0))+1;
				FreenetURI nextInsertURI = new FreenetURI(props.get(IVertex.INSERT_URI).get(0)).setSuggestedEdition(next_edition);
				
				InsertBlock ib = new InsertBlock(bucket, null, nextInsertURI);
				InsertContext ictx = hl.getInsertContext(true);
				
				//insert the damn thing
				String old_hash = "";
				if (props.containsKey(IVertex.HASH))	old_hash = props.get(IVertex.HASH).get(0);
				String new_hash = calculateIdentityHash(own_vertex);
				
				System.out.println("Maybe inserting own identity... checking the hash");
				if (!new_hash.equals(old_hash))
				{
					//ClientPutter pu = hl.insert(ib, false, null, false, ictx, this, RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS);
					System.out.println("INSERTING OWN IDENTITY");

					//update the time when we stored it in the database (as to disallow inserting it every second)
					graph.updateVertexProperty(own_vertex, IVertex.LAST_INSERT, Long.toString(System.currentTimeMillis()));
					graph.updateVertexProperty(own_vertex, IVertex.HASH, new_hash);
				}
				else
				{
					System.out.println("nope... not inserting, because nothing seems to have been altered...");
				}
			}
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
		}
	}
	
	@Override
	public void onSuccess(BaseClientPutter cp, ObjectContainer oc) {

		//update the insert and request uris in the database
		try {
			List<Long> own_vertices = graph.getVertexByPropertyValue(IVertex.ID, ownID);

			for(long own_vertex : own_vertices)
			{
				Map<String, List<String>> props = graph.getVertexProperties(own_vertex);
				FreenetURI newRequestURI = new FreenetURI(props.get(IVertex.REQUEST_URI).get(0));
				long new_edition = cp.getURI().getEdition();
				newRequestURI.setSuggestedEdition(new_edition);
				
				graph.updateVertexProperty(own_vertex, IVertex.INSERT_URI, cp.getURI().toASCIIString());
				graph.updateVertexProperty(own_vertex, IVertex.REQUEST_URI, newRequestURI.toASCIIString());
			}
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (MalformedURLException e) {
			e.printStackTrace();
		}
	}
	
	
	private String calculateIdentityHash(long own_vertex_id) throws SQLException, NoSuchAlgorithmException
	{
		Map<String, List<String>> props = graph.getVertexProperties(own_vertex_id);
		List<Edge> edges = graph.getOutgoingEdges(own_vertex_id);

		String string_to_hash = "";
		for(Entry<String, List<String>> pair : props.entrySet())
		{
			if (! (pair.getKey().equals(IVertex.HASH) || pair.getKey().equals(IVertex.LAST_INSERT))) //don't hash the hash to prevent infinite regression...
			{
				string_to_hash += pair.getKey();
				for(String value : pair.getValue())
				{
					string_to_hash += value;
				}
			}
		}
	
		for(Edge edge : edges)
		{
			Map<String, List<String>> edge_props = edge.getProperties();

			for(Entry<String, List<String>> pair : edge_props.entrySet())
			{
				string_to_hash += pair.getKey();
				for(String value : pair.getValue())
				{
					string_to_hash += value;
				}
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
	 */
	
	private String createXML(long own_identity) throws SQLException, TransformerFactoryConfigurationError, ParserConfigurationException, TransformerException
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
		rootElement.setAttribute("Version", Long.toString(1));
		
		/* Create the identity Element */
		Map<String, List<String>> props = graph.getVertexProperties(own_identity);
		
		Element identityElement = xmlDoc.createElement("Identity");
		identityElement.setAttribute("Version", Integer.toString(1)); /* Version of the XML format */
		
			identityElement.setAttribute("Name",  props.get(IVertex.NAME).get(0));
			if (props.containsKey(IVertex.PUBLISHES_TRUSTLIST) && props.get(IVertex.PUBLISHES_TRUSTLIST).get(0).equals("true") )
			{
				identityElement.setAttribute("PublishesTrustList",   Boolean.toString(true));
			}
			else
			{
				identityElement.setAttribute("PublishesTrustList",   Boolean.toString(false));
			}
			
			/* Create the context Elements */
			
			for(String context : props.get(IVertex.CONTEXT_NAME)) {
				Element contextElement = xmlDoc.createElement("Context");
				contextElement.setAttribute("Name", context);
				identityElement.appendChild(contextElement);
			}
			
			/* Create the property Elements */
			String[] blacklistItems = {IVertex.CONTEXT_NAME, IVertex.EDITION, IVertex.INSERT_URI, IVertex.NAME, IVertex.OWN_IDENTITY, IVertex.PUBLISHES_TRUSTLIST, IVertex.REQUEST_URI, IVertex.TRUST};
			List<String> blackList = new LinkedList<String>();
			for(String item : blacklistItems) blackList.add(item);
			
			for(String propertyName : props.keySet()) {
				if (!blackList.contains(propertyName))
				{
					Element propertyElement = xmlDoc.createElement("Property");
					propertyElement.setAttribute("Name", propertyName);
					propertyElement.setAttribute("Value", props.get(propertyName).get(0));
					identityElement.appendChild(propertyElement);
				}
			}
			
			/* Create the trust list Element and its trust Elements */
			if (props.containsKey(IVertex.PUBLISHES_TRUSTLIST) && props.get(IVertex.PUBLISHES_TRUSTLIST).get(0).equals("true"))
			{
				Element trustListElement = xmlDoc.createElement("TrustList");

				List<Edge> edges = graph.getOutgoingEdges(own_identity);
				for(Edge edge : edges)
				{
					Map<String, List<String>> peer_identity_props = graph.getVertexProperties(edge.vertex_to);
					
					Element trustElement = xmlDoc.createElement("Trust");
					trustElement.setAttribute("Identity", peer_identity_props.get(IVertex.REQUEST_URI).get(0));
					trustElement.setAttribute("Value", edge.getProperty(IEdge.SCORE));
					trustElement.setAttribute("Comment", edge.getProperty(IEdge.COMMENT));
					trustListElement.appendChild(trustElement);
				}
				identityElement.appendChild(trustListElement);
			}
		
		rootElement.appendChild(identityElement);

		//serialize the XML
		DOMSource domSource = new DOMSource(xmlDoc);
		StringWriter result = new StringWriter();
		StreamResult resultStream = new StreamResult(result);
		mSerializer.transform(domSource, resultStream);

		return result.toString();
	}

	
	@Override
	public void onMajorProgress(ObjectContainer arg0) {
	}

	@Override
	public void onFailure(InsertException ie, BaseClientPutter arg1, ObjectContainer arg2) {

		System.out.println("Failed to insert own identity, please investigate!");
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
