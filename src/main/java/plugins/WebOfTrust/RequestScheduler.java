package plugins.WebOfTrust;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.ReadableIndex;

import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;

public class RequestScheduler extends Thread {

	public static final int MAX_REQUESTS = 10; 
	public static final int QUICKLY_CLEAR_BIG_BACKLOG_THRESHOLD = 5;
	
	private static final int MAX_MAINTENANCE_REQUESTS = 1; 
	private static final double PROBABILITY_OF_FETCHING_DIRECTLY_TRUSTED_IDENTITY = 0.7;

	private static final long MAX_TIME_SINCE_LAST_INSERT = (60 * 1000) * 60; //don't insert faster than once per hour
	private static final long MINIMAL_SLEEP_TIME = (1*1000) * 60 * 5; // 5 minutes
	private static final long MINIMAL_SLEEP_TIME_WITH_BIG_BACKLOG = (1*1000); // 1 second
	private static final long MINIMAL_SLEEP_TIME_WOT_UPDATE = (60*1000) * 60 * 1; // update WoT once every 1 hours;
	
	private final WebOfTrust main;
	private final GraphDatabaseService db;
	private final HighLevelSimpleClient hl;

	private final List<ClientGetter> inFlight = new ArrayList<ClientGetter>();
	private final Set<String> backlog = new HashSet<String>();
	private final Random ran = new Random();

	private final IdentityUpdaterRequestClient rc;
	private final ClientGetCallback cc;
	private final FetchContext fc;

	private long wot_last_updated = 0;
	private final ReadableIndex<Node> nodeIndex;

	
	public RequestScheduler(WebOfTrust main, GraphDatabaseService db, HighLevelSimpleClient hl)
	{
		this.main = main;
		this.db = db;
		this.hl = hl;
		this.nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		
		this.rc = new IdentityUpdaterRequestClient();
		this.cc = new IdentityUpdater(this, db, hl, false);
		this.fc = hl.getFetchContext();
		this.fc.followRedirects = true;
	
		this.wot_last_updated = System.currentTimeMillis();
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
					if (getBacklogSize() > QUICKLY_CLEAR_BIG_BACKLOG_THRESHOLD)
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

	private void insertOwnIdentities() {
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
			own_identities.close();
			tx.finish();
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

	private void maintenance() {
		if (getInFlightSize() <= MAX_MAINTENANCE_REQUESTS)
		{
			for(final Node own_identity : nodeIndex.get(IVertex.OWN_IDENTITY, true))
			{
				final Node node = getRandomNode(own_identity);

				if (node != null)
				{
					//add the requestURI to the backlog
					try {
						addBacklog(new FreenetURI((String) node.getProperty(IVertex.REQUEST_URI)));
					} catch (MalformedURLException e) {
						e.printStackTrace();
					}	
				}
			}
		}
	}

	/**
	 * Select a random node from the graph by walking through it
	 * @param identity
	 * @return
	 */
	
	protected Node getRandomNode(Node identity) {
		Node current_node = identity;
		final String trustProperty = IVertex.TRUST + "_" + identity.getProperty(IVertex.ID);
		
		for(byte distance=1; distance < 7; distance++)
		{
			//count relationships to choose from
			int nodes = 0;
			for(@SuppressWarnings("unused") final Relationship rel : current_node.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
			{
				nodes += 1;
			}
			
			//an identity with no outgoing trust relations
			if (nodes == 0) return current_node;

			//select a random identity
			final int index = ran.nextInt(nodes);
			int count = 0;
			for(final Relationship rel : current_node.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
			{
				if (count == index)
				{
					final Node node = rel.getEndNode();
					if (node.hasProperty(trustProperty) && (Integer) node.getProperty(trustProperty) >= 0)
					{
						if (ran.nextFloat() < PROBABILITY_OF_FETCHING_DIRECTLY_TRUSTED_IDENTITY)
						{
							return rel.getEndNode();	
						}
						else
						{
							current_node = rel.getEndNode();	
						}
					}
				}
				count += 1;
			}
		
			//no random node selected so, loop again expanding the newly selected node (possibly the same one)
		}
		return null; //no node was selected
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
	 */
	
	private void updateWoT()
	{
		if (System.currentTimeMillis() - wot_last_updated > MINIMAL_SLEEP_TIME_WOT_UPDATE)
		{
			wot_last_updated = System.currentTimeMillis();
			ScoreComputer sc = new ScoreComputer(db);
			sc.compute();
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
