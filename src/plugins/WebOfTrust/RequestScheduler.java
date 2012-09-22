package plugins.WebOfTrust;

import java.net.MalformedURLException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.neo4j.cypher.ExecutionEngine;
import org.neo4j.cypher.ExecutionResult;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.ReadableIndex;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

import thomasmarkus.nl.freenet.graphdb.EdgeWithProperty;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;
import thomasmarkus.nl.freenet.graphdb.VertexIterator;
import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;

public class RequestScheduler extends Thread {

	public static final int MAX_REQUESTS = 5; 
	public String DB_TRANSACTION_LOCK = "yes";
	
	private static final int MAX_MAINTENANCE_REQUESTS = 1; 
	private static final double PROBABILITY_OF_FETCHING_DIRECTLY_TRUSTED_IDENTITY = 0.7;

	private static final long MAX_TIME_SINCE_LAST_INSERT = (60 * 1000) * 60; //don't insert faster than once per hour
	private static final long MINIMAL_SLEEP_TIME = (1*1000)*5;// * 120; // 2 minutes
	private static final long MINIMAL_SLEEP_TIME_WITH_BIG_BACKLOG = (1*1000); // 1 second
	private static final long MINIMAL_SLEEP_TIME_WOT_UPDATE = (60*1000) * 60 * 2; // update WoT once per 2 hour;
	
	private WebOfTrust main;
	private final GraphDatabaseService db;
	private HighLevelSimpleClient hl;

	private List<ClientGetter> inFlight = new ArrayList<ClientGetter>();
	private Set<String> backlog = new HashSet<String>();
	private final Random ran = new Random();

	private IdentityUpdaterRequestClient rc;
	private ClientGetCallback cc;
	private FetchContext fc;

	private long wot_last_updated = 0;
	
	public RequestScheduler(WebOfTrust main, GraphDatabaseService db, HighLevelSimpleClient hl)
	{
		this.main = main;
		this.db = db;
		this.hl = hl;

		this.rc = new IdentityUpdaterRequestClient();
		this.cc = new IdentityUpdater(this, db, hl, false);
		this.fc = hl.getFetchContext();
		this.fc.followRedirects = true;
	}

	@Override
	public void run() 
	{
		while(main.isRunning)
		{
			try
			{
				//clear requests from the backlog
				clearBacklog();

				//cleanup finished requests... (which did not call success / failure :S, probably a Freenet bug... )
				cleanup();

				//schedule random identity updates if there is no other activity at the time
				maintenance();

				//check if our own identities need to be inserted and do it if needed
				insertOwnIdentities();

				//update the Web of Trust
				updateWoT();
				
				//chill out a bit
				try {
					if (getBacklogSize() > 10)
					{
						Thread.sleep(MINIMAL_SLEEP_TIME_WITH_BIG_BACKLOG);						
					}
					else
					{
						Thread.sleep(MINIMAL_SLEEP_TIME);
					}
					
				} catch (InterruptedException e) {
				}
			}
			catch(Exception e)
			{
				System.out.println("An exception was thrown in the requestScheduler. Please report with sufficient details!");
				e.printStackTrace();
			}
		}
		
		//clear backlog
		synchronized(backlog)
		{
			backlog.clear();
		}
		
		//cancel all running requests
		System.err.println("Cancelling all running requests...");
		synchronized(inFlight) {
			Iterator<ClientGetter> iter = inFlight.iterator();
			while(iter.hasNext())
			{
				ClientGetter getter = iter.next();
				iter.remove();
				getter.cancel(null, main.getPR().getNode().clientCore.clientContext);
			}
		}
		System.err.println("All requests canceled.");
	}

	private void insertOwnIdentities() throws SQLException {

		ReadableIndex<Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		IndexHits<Node> own_identities = nodeIndex.get(IVertex.OWN_IDENTITY, true);
		
		Transaction tx = db.beginTx();
		try
		{
			
			for(Node own_identity : own_identities)
			{
				long timestamp = 0;
				if(own_identity.hasProperty(IVertex.LAST_INSERT))
				{
					timestamp =  (Long) own_identity.getProperty(IVertex.LAST_INSERT);	
				}

				if ((System.currentTimeMillis() - MAX_TIME_SINCE_LAST_INSERT) > timestamp)
				{
					final String id = (String) own_identity.getProperty(IVertex.ID);
					OwnIdentityInserter ii = new OwnIdentityInserter(db, id, hl, main);
					ii.run();
				}
			}
		
			tx.success();
		}
		finally
		{
			tx.finish();
			own_identities.close();
		}
	}

	private void clearBacklog() 
	{
		while(getInFlightSize() < MAX_REQUESTS && getBacklogSize() > 0)
		{
			FreenetURI next = getBacklogItem();

			//fetch the identity
			try {
				addInFlight(hl.fetch(next, WebOfTrust.FETCH_MAX_FILE_SIZE, rc, cc, fc));
			} catch (FetchException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Cleanup ClientGetters which have finished or have been cancelled.
	 */

	private void cleanup() {
		synchronized (inFlight) {
			Iterator<ClientGetter> iter = inFlight.iterator();
			while(iter.hasNext())
			{
				ClientGetter cg = iter.next();
				if (cg.isFinished() || cg.isCancelled()) iter.remove();
			}
		}
	}

	private void maintenance() throws SQLException {
		System.out.println("doing maintenance...");
		
		if (getInFlightSize() <= MAX_MAINTENANCE_REQUESTS)
		{
			ReadableIndex<Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
			
			try
			{
				double random = ran.nextDouble();
				if (random < PROBABILITY_OF_FETCHING_DIRECTLY_TRUSTED_IDENTITY) //fetch random directly connected identity
				{
					IndexHits<Node> vertices = nodeIndex.get(IVertex.OWN_IDENTITY, true);
					try
					{
						for(Node vertex : vertices)
						{
							Iterable<Relationship> edges = vertex.getRelationships(Direction.OUTGOING, Rel.TRUSTS);
							
							Iterator<Relationship> iter = edges.iterator();
							List<Relationship> edges_list = new ArrayList<Relationship>();
							while (iter.hasNext())
							{
								edges_list.add(iter.next());
							}
							
							//get a random edge
							if (edges_list.size() > 0)
							{
								Relationship edge = edges_list.get( ran.nextInt(edges_list.size()) );

								Node end_node = edge.getEndNode();
								
								//add the requestURI to the backlog
								if (end_node.hasProperty(IVertex.REQUEST_URI))
								{
									addBacklog(new FreenetURI((String) edge.getEndNode().getProperty(IVertex.REQUEST_URI)));	
								}
							}
						}
					}
					finally
					{
						vertices.close();	
					}
					
				}
				else
				{
					//find random identity
					IndexHits<Node> own_vertices = nodeIndex.get(IVertex.OWN_IDENTITY, true);
					
					try
					{
						Iterator<Node> iter = own_vertices.iterator();
						List<Node> own_vertices_list = new ArrayList<Node>();
						while (iter.hasNext())
						{
							own_vertices_list.add(iter.next());
						}

						
						if (own_vertices.hasNext())
						{
							Node own_vertex = own_vertices_list.get( ran.nextInt(own_vertices_list.size()));
							String own_id = (String) own_vertex.getProperty(IVertex.ID);

							//Some identity with a score of 0 or higher and sort by random, limit by 1
							//TODO: use the traverse NO cypher for this stuff!
							
							//true refers to a random element from the set
							ExecutionEngine engine = new ExecutionEngine( db );
							ExecutionResult result = engine.execute( "start r=relationship(:TRUSTS) n-[r]->n2 WHERE n2.TRUST >= 0 return n2" );
							
							Iterator<Node> vertices = (Iterator<Node>) result.columnAs("n2");

							if(vertices.hasNext())
							{
								//add URI to the backlog
								addBacklog(new FreenetURI((String) vertices.next().getProperty(IVertex.REQUEST_URI)));
							}
						}
					}
					finally
					{
						own_vertices.close();
					}
				}
			}
			catch (MalformedURLException e) {
				e.printStackTrace();
			}
		}
	}

	public void addBacklog(FreenetURI uri)
	{
		synchronized (backlog) {
			uri.setSuggestedEdition(-uri.getEdition()); //note: negative edition!

			Iterator<String> iter = backlog.iterator();
			Set<String> toRemove = new HashSet<String>();
			while(iter.hasNext())
			{
				String existingURIString = iter.next();
				try {
					FreenetURI existingURI = new FreenetURI(existingURIString);

					if (existingURI.equalsKeypair(uri))
					{
						if (existingURI.getEdition() > uri.getEdition())
						{
							return; //skip, because we want to add the same uri with an older edition, that doesn't make sense.	
						}
						else
						{
							toRemove.add(existingURIString);
						}
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
			backlog.removeAll(toRemove);
			backlog.add(uri.toASCIIString());	
		}
	}

	/**
	 * Update the web of trust values
	 * @throws SQLException
	 */
	
	private void updateWoT() throws SQLException
	{
		if (System.currentTimeMillis() - wot_last_updated > MINIMAL_SLEEP_TIME_WOT_UPDATE)
		{
			wot_last_updated = System.currentTimeMillis();

			ReadableIndex<Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
			ScoreComputer sc = new ScoreComputer(db);
			IndexHits<Node> vertices = nodeIndex.get(IVertex.OWN_IDENTITY, true);
			
			try
			{
				for(Node vertex : vertices)
				{
					sc.compute((String) vertex.getProperty(IVertex.ID));
				}
			}
			finally
			{
				vertices.close();
			}
		}
	}
	
	public FreenetURI getBacklogItem()
	{
		synchronized (backlog) {
			Iterator<String> iter = backlog.iterator();
			while(iter.hasNext())
			{
				String next = iter.next();
				iter.remove();
				try {
					return new FreenetURI(next);
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
		}
		return null;
	}

	public List<String> getInFlight()
	{
		List<String> result = new LinkedList<String>();

		synchronized (inFlight) {
			for(ClientGetter cg : inFlight)
			{
				result.add(cg.getURI().toASCIIString());
			}
		}
		
		return result;
	}

	public int getBacklogSize()
	{
		synchronized (backlog) {
			return backlog.size();	
		}
	}

	public void addInFlight(ClientGetter cg)
	{
		synchronized (inFlight) {
			inFlight.add(cg);	
		}
		
	}

	public void removeInFlight(ClientGetter cg)
	{
		synchronized (inFlight) {
			inFlight.remove(cg);
		}
	}

	public int getInFlightSize()
	{
		synchronized (inFlight) {
			return inFlight.size();	
		}
	}

}
