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

import com.sun.swing.internal.plaf.synth.resources.synth;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.EdgeWithProperty;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetCallback;
import freenet.client.async.ClientGetter;
import freenet.keys.FreenetURI;

public class RequestScheduler implements Runnable {

	private static final int MAX_REQUESTS = 8; 
	private static final int MAX_MAINTENANCE_REQUESTS = 1; 
	private static final double PROBABILITY_OF_FETCHING_DIRECTLY_TRUSTED_IDENTITY = 0.8;

	private static final long MAX_TIME_SINCE_LAST_INSERT = (10 * 1000);
	private static final long MINIMAL_SLEEP_TIME = (1 * 1000);

	private WebOfTrust main;
	private final H2Graph graph;
	private HighLevelSimpleClient hl;

	private List<ClientGetter> inFlight = new ArrayList<ClientGetter>();
	private Set<String> backlog = new HashSet<String>();
	private final Random ran = new Random();

	private IdentityUpdaterRequestClient rc;
	private ClientGetCallback cc;
	private FetchContext fc;

	public RequestScheduler(WebOfTrust main, H2Graph graph, HighLevelSimpleClient hl)
	{
		this.main = main;
		this.graph = graph;
		this.hl = hl;

		this.rc = new IdentityUpdaterRequestClient();
		this.cc = new IdentityUpdater(this, graph, hl, false);
		this.fc = hl.getFetchContext();
		this.fc.followRedirects = true;
	}

	@Override
	public void run() 
	{
		while(main.isRunning)
		{
			//chill out a bit
			try {
				Thread.sleep(MINIMAL_SLEEP_TIME);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			//clear requests from the backlog
			clearBacklog();

			//cleanup finished requests... (which did not call success / failure :S, probably a Freenet bug... )
			cleanup();

			//schedule random identity updates if there is no other activity at the time
			maintenance();

			//insertOwnIdentities();
		}
	}

	private void insertOwnIdentities() {

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
					String id = props.get(IVertex.ID).get(0);
					OwnIdentityInserter ii = new OwnIdentityInserter(graph, id, hl, main);
					ii.run();
				}
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
	}

	private void clearBacklog() {
		while(getNumInFlight() < MAX_REQUESTS && getNumBackLog() > 0)
		{
			FreenetURI next = getBacklogItem();

			//fetch the identity
			try {
				addInFlight(hl.fetch(next, 200000, rc, cc, fc));
			} catch (FetchException e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Cleanup ClientGetters which have finished or have been cancelled.
	 */

	private synchronized void cleanup() {
		Iterator<ClientGetter> iter = inFlight.iterator();
		while(iter.hasNext())
		{
			ClientGetter cg = iter.next();
			if (cg.isFinished() || cg.isCancelled()) iter.remove();
		}
	}

	private void maintenance() {
		if (getNumInFlight() <= MAX_MAINTENANCE_REQUESTS)
		{
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
							System.out.println("looking for a random edge from an ownidentity...");
							
							EdgeWithProperty edge = edges.get( ran.nextInt(edges.size()) );

							//get the node to which that edge is pointing
							Map<String, List<String>> props = graph.getVertexProperties(edge.vertex_to);

							//add the requestURI to the backlog
							if (props.containsKey(IVertex.REQUEST_URI))
							{
								System.out.println("Added to backlog..");
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

						//Some identity with a score of 0 or higher
						List<Long> vertices = graph.getVerticesWithPropertyValueLargerThan(IVertex.TRUST+"."+own_id, -1);

						if(vertices.size() > 0)
						{
							//get random vertex from that list
							long vertex = vertices.get( ran.nextInt(vertices.size()));

							//get properties of that vertex
							Map<String, List<String>> props = graph.getVertexProperties(vertex);

							//add URI to the backlog
							addBacklog(new FreenetURI(props.get(IVertex.REQUEST_URI).get(0)));
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
		}
	}

	public synchronized void addBacklog(FreenetURI uri)
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
							return; //skip, because we want to add the same uri with an older edition, which doesn't make sense.	
						}
					}
				} catch (MalformedURLException e) {
					e.printStackTrace();
				}
			}
			backlog.add(uri.toASCIIString());	
		}
	}

	public synchronized FreenetURI getBacklogItem()
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

	public List<ClientGetter> getInFlight()
	{
		return inFlight;
	}

	public int getNumBackLog()
	{
		return backlog.size();
	}

	public synchronized void addInFlight(ClientGetter cg)
	{
		synchronized (inFlight) {
			inFlight.add(cg);	
		}
		
	}

	public synchronized void removeInFlight(ClientGetter cg)
	{
		synchronized (inFlight) {
			inFlight.remove(cg);
		}
	}

	public synchronized int getNumInFlight()
	{
		return inFlight.size();
	}

}
