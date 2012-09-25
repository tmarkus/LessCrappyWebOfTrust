package plugins.WebOfTrust;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.ReadableIndex;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

public class ScoreComputer {

	private final GraphDatabaseService db;
	ReadableIndex<Node> nodeIndex;

	protected static final float decay = 0.4f;

	public ScoreComputer(GraphDatabaseService db)
	{
		this.db = db;
		this.nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
	}

	public void compute()
	{
		System.out.println("Starting trust calculations...");

		synchronized (db) {

			//delete the calculated trust values for all identities for this ownIdentity
			final IndexHits<Node> own_identities = nodeIndex.get(IVertex.OWN_IDENTITY, true);
			for(Node own_identity : own_identities)
			{
				System.out.println("Calculating for: " + own_identity.getProperty(IVertex.ID));

				final String trustProperty = IVertex.TRUST+"."+own_identity.getProperty(IVertex.ID);
				final String distanceProperty = IVertex.DISTANCE + "." + own_identity.getProperty(IVertex.ID);

				//assign 100 trust to local identity
				initOwnIdentity(own_identity, trustProperty, distanceProperty, nodeIndex);

				//calcuate the distance of every identity for this own_identity
				Index<Node> distanceIndex = calculateDistances(own_identity, distanceProperty);

				//local overrides for trust values
				localOverrides(own_identity, trustProperty);
				
				for(byte distance=2; distance < 7; distance++)
				{
					System.out.println("Current distance = " + distance);

					Transaction tx = db.beginTx();
					try
					{
						IndexHits<Node> layer = distanceIndex.get(distanceProperty, distance);
						for(Node current_node : layer)	
						{
							//calculate summed trust for all identities that assign trust to this identity with a lower distance
							int avg_score = 0;
							boolean changed = false;
							for(Relationship rel : current_node.getRelationships(Direction.INCOMING, Rel.TRUSTS))
							{
								final Node startNode = rel.getStartNode(); 
								if (startNode.hasProperty(trustProperty) &&
										(Integer) startNode.getProperty(trustProperty) > 0 &&
										startNode.hasProperty(distanceProperty))
								{
									final byte score = (Byte) rel.getProperty(IEdge.SCORE);
									final byte distanceValue = (Byte) startNode.getProperty(distanceProperty);
									changed = true;

									avg_score += Math.round(score*Math.pow(decay, distanceValue-1));
								}
							}
							
							if (changed) current_node.setProperty(trustProperty, avg_score);
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
		
		System.out.println("Down with trust calculation.");
	}

	protected void localOverrides(Node own_identity, final String trustProperty) {
		Transaction tx = db.beginTx();
		try
		{
			for(final Relationship rel : own_identity.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
			{
				rel.getEndNode().setProperty(trustProperty, (int) ((Byte) rel.getProperty(IEdge.SCORE)));
			}
			tx.success();
		}
		finally
		{
			tx.finish();
		}
	}

	protected Index<Node> calculateDistances(Node own_identity, final String distanceProperty) {

		//traverse the graph
		TraversalDescription td = Traversal.description()
				.breadthFirst()
				.relationships( Rel.TRUSTS, Direction.OUTGOING )
				.evaluator( Evaluators.excludeStartPosition() )
				.evaluator( Evaluators.toDepth(6) );


		System.out.println("Setting the distance for all identities");
		Index<Node> distanceIndex;
		
		Transaction tx = db.beginTx();
		try
		{
			distanceIndex = db.index().forNodes( "distance" );
			
			for(Path path : td.traverse(own_identity))
			{
				final Node current_node = path.endNode();
				current_node.setProperty(distanceProperty, (byte) path.length());
				distanceIndex.add(current_node, distanceProperty, (byte) path.length());
			}
			tx.success();
		}
		finally
		{
			tx.finish();
		}
		System.out.println("Distance setting completed for all identities");
		return distanceIndex;
	}

	/**
	 * Initialize a local identity such that it trusts itself
	 * @param own_vertex
	 * @param trustProperty
	 * @param distanceProperty
	 * @param nodeIndex
	 */
	
	protected void initOwnIdentity(Node own_vertex, final String trustProperty, final String distanceProperty, ReadableIndex<Node> nodeIndex) {
		Transaction tx = db.beginTx();
		try
		{
			own_vertex.setProperty(trustProperty, 100);
			own_vertex.setProperty(distanceProperty, (byte) 0);
			tx.success();
		}
		finally
		{
			tx.finish();
		}
	}

	/**
	 * Compute the trust value for an identity on the fly
	 * @param identityID
	 */

	public int computerForIdentity(String identityID, String ownIdentityID)
	{

		//check whether there is direct trust for this identity and own_identity

		//if not, start the full blown shit stuff

		return 0;
	}
}
