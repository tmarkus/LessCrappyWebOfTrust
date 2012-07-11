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

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

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

	public static final int MAX_REQUESTS = 10; 
	private static final int MAX_MAINTENANCE_REQUESTS = 1; 
	private static final double PROBABILITY_OF_FETCHING_DIRECTLY_TRUSTED_IDENTITY = 0.7;

	private static final long MAX_TIME_SINCE_LAST_INSERT = (60 * 1000) * 60; //don't insert faster than once per hour
	private static final long MINIMAL_SLEEP_TIME = (1*1000) * 120; // 2 minutes
	private static final long MINIMAL_SLEEP_TIME_WITH_BIG_BACKLOG = (1*1000); // 1 second
	private static final long MINIMAL_SLEEP_TIME_WOT_UPDATE = (60*1000) * 60 * 2; // update WoT once per 2 hour;
	private static final long MAX_DB_CONNECTIONS = 5;
	
	private WebOfTrust main;
	private final H2GraphFactory gf;
	private HighLevelSimpleClient hl;

	private List<ClientGetter> inFlight = new ArrayList<ClientGetter>();
	private Set<String> backlog = new HashSet<String>();
	private final Random ran = new Random();

	private IdentityUpdaterRequestClient rc;
	private ClientGetCallback cc;
	private FetchContext fc;

	private long wot_last_updated = 0;
	
	public RequestScheduler(WebOfTrust main, H2GraphFactory gf, HighLevelSimpleClient hl)
	{
		this.main = main;
		this.gf = gf;
		this.hl = hl;

		this.rc = new IdentityUpdaterRequestClient();
		this.cc = new IdentityUpdater(this, gf, hl, false);
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
		System.out.println("Cancelling all running requests...");
		synchronized(inFlight) {
			Iterator<ClientGetter> iter = inFlight.iterator();
			while(iter.hasNext())
			{
				ClientGetter getter = iter.next();
				iter.remove();
				getter.cancel(null, main.getPR().getNode().clientCore.clientContext);
			}
		}
	}

	private void insertOwnIdentities() throws SQLException {

		H2Graph graph = gf.getGraph();
		try
		{
			List<Long> own_identities = graph.getVertexByPropertyValue(IVertex.OWN_IDENTITY, "true");
			for(long own_identity : own_identities)
			{
				Map<String, List<String>> props = graph.getVertexProperties(own_identity);

				long timestamp = 0;
				if(props.containsKey(IVertex.LAST_INSERT))
				{
					timestamp = Long.parseLong(props.get(IVertex.LAST_INSERT).get(0));	
				}

				if ((System.currentTimeMillis() - MAX_TIME_SINCE_LAST_INSERT) > timestamp)
				{
					final String id = props.get(IVertex.ID).get(0);
					OwnIdentityInserter ii = new OwnIdentityInserter(gf, id, hl, main);
					ii.run();
				}
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
		finally
		{
			graph.close();
		}
	}

	private void clearBacklog() 
	{
		while(getInFlightSize() < MAX_REQUESTS && getBacklogSize() > 0 && gf.getActiveConnections() < MAX_DB_CONNECTIONS)
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
		if (getInFlightSize() <= MAX_MAINTENANCE_REQUESTS)
		{
			H2Graph graph = gf.getGraph();
			try
			{
				
				double random = ran.nextDouble();
				if (random < PROBABILITY_OF_FETCHING_DIRECTLY_TRUSTED_IDENTITY) //fetch random directly connected identity
				{
					List<Long> vertices = graph.getVertexByPropertyValue(IVertex.OWN_IDENTITY, "true");
					for(long vertex_id : vertices)
					{
						List<EdgeWithProperty> edges = graph.getOutgoingEdgesWithProperty(vertex_id, IEdge.SCORE);

						//get a random edge
						if (edges.size() > 0)
						{
							EdgeWithProperty edge = edges.get( ran.nextInt(edges.size()) );

							//get the node to which that edge is pointing
							Map<String, List<String>> props = graph.getVertexProperties(edge.vertex_to);

							//add the requestURI to the backlog
							if (props.containsKey(IVertex.REQUEST_URI))
							{
								addBacklog(new FreenetURI(props.get(IVertex.REQUEST_URI).get(0)));	
							}
						}
					}
				}
				else
				{
					//find random identity
					List<Long> own_vertices = graph.getVertexByPropertyValue(IVertex.OWN_IDENTITY, "true");

					if (own_vertices.size() > 0)
					{
						long own_vertex = own_vertices.get( ran.nextInt(own_vertices.size()));
						Map<String, List<String>> own_props = graph.getVertexProperties(own_vertex);
						String own_id = own_props.get("id").get(0);

						//Some identity with a score of 0 or higher and sort by random, limit by 1
						VertexIterator vertices = graph.getVertices(IVertex.TRUST+"."+own_id, -1, IVertex.REQUEST_URI, true, 1);

						if(vertices.hasNext())
						{
							//add URI to the backlog
							addBacklog(new FreenetURI(vertices.next().get(IVertex.REQUEST_URI).get(0)));
						}
					}
				}
			}
			catch(SQLException e)
			{
				e.printStackTrace();
			} catch (MalformedURLException e) {
				e.printStackTrace();
			}
			finally
			{
				graph.close();
			}
		}
	}

	public void addBacklog(FreenetURI uri)
	{
		synchronized (backlog) {
			uri.setSuggestedEdition(-uri.getEdition()); //note: negative edition!

			Iterator<String> iter = backlog.iterator();
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
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
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
			
			H2Graph graph = gf.getGraph();
			try
			{
				ScoreComputer sc = new ScoreComputer(graph);
				for(long vertex_id : graph.getVertexByPropertyValue("ownIdentity", "true"))
				{
					sc.compute( graph.getVertexProperties(vertex_id).get("id").get(0) );
				}
			}
			finally
			{
				graph.close();
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
