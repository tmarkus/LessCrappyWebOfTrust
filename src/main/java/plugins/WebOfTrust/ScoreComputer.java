package plugins.WebOfTrust;


import java.util.ArrayList;
import java.util.List;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.ReadableIndex;
import org.neo4j.graphdb.traversal.Evaluators;
import org.neo4j.graphdb.traversal.TraversalDescription;
import org.neo4j.kernel.Traversal;
import org.neo4j.tooling.GlobalGraphOperations;

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
			System.out.println("Resetting graph by removing many properties...");
			resetGraph();
			System.out.println("Finished resetting graph");

			final IndexHits<Node> vertices = nodeIndex.get(IVertex.OWN_IDENTITY, true);
			for(Node vertex : vertices)
			{
				System.out.println("Calculating for: " + vertex.getProperty(IVertex.ID));

				final String trustProperty = IVertex.TRUST+"."+vertex.getProperty(IVertex.ID);
				final String distanceProperty = IVertex.DISTANCE + "." + vertex.getProperty(IVertex.ID);

				//assign 100 trust to local identity
				initOwnIdentity(vertex, trustProperty, distanceProperty, nodeIndex);

				//traverse the graph
				TraversalDescription td = Traversal.description()
						.breadthFirst()
						.relationships( Rel.TRUSTS, Direction.OUTGOING )
						.evaluator( Evaluators.excludeStartPosition() )
						.evaluator( Evaluators.toDepth(6) );


				System.out.println("Setting the distance for all identities");
				Transaction tx = db.beginTx();
				try
				{
					for(Path path : td.traverse(vertex))
					{
						final Node current_node = path.endNode();
						current_node.setProperty(distanceProperty, path.length());
					}
					tx.success();
				}
				finally
				{
					tx.finish();
				}
				System.out.println("Distance setting completed for all identities");


				for(byte i=1; i < 7; i++)
				{
					System.out.println("Current distance = " + i);

					tx = db.beginTx();
					try
					{
						IndexHits<Node> layer = nodeIndex.get(distanceProperty, i);
						for(Node current_node : layer)	
						{
							//calculate summed trust for all identities that assign trust to this identity with a lower distance
							int score = 0;
							for(Relationship rel : current_node.getRelationships(Direction.INCOMING, Rel.TRUSTS))
							{
								if ((Byte) rel.getStartNode().getProperty(distanceProperty) < i)
								{
									int normalized_parent_trust = Math.min((Integer) rel.getStartNode().getProperty(trustProperty), 100);
									normalized_parent_trust = Math.max(normalized_parent_trust, -100);

									score += (int) (score + Math.round(
											(100.0 / normalized_parent_trust) *
											((score/100.0)*Math.pow(decay, i-1)
													)*100));
								}
							}

							current_node.setProperty(trustProperty, (int) score);
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

	protected void resetGraph() {
		GlobalGraphOperations util = GlobalGraphOperations.at(db);

		final IndexHits<Node> vertices = nodeIndex.get(IVertex.OWN_IDENTITY, true);
		final List<Node> vertices_list = new ArrayList<Node>();
		for(Node node : vertices)
		{
			vertices_list.add(node);
		}

		Transaction tx = db.beginTx();
		try 
		{
			for(Node node : util.getAllNodes())
			{
				if (node.hasProperty(IVertex.ID))
				{
					//remove all (old trust properties from removed local identities)
					for(String prop : node.getPropertyKeys())
					{
						if (prop.startsWith(IVertex.TRUST) || prop.startsWith(IVertex.DISTANCE)) 
						{
							node.removeProperty(prop);
						}
					}

					for(Node localIdentity : vertices_list)
					{
						node.setProperty(IVertex.TRUST+"."+localIdentity.getProperty(IVertex.ID), 0);
					}
				}
			}

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
