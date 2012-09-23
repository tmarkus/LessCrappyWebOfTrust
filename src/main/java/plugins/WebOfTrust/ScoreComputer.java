package plugins.WebOfTrust;


import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
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
			
			for(Path path : td.traverse(vertex))
			{
				final Node current_node = path.endNode();
				
				Transaction tx = db.beginTx();
				try
				{
					current_node.setProperty(distanceProperty, (byte) path.length());
					
					final int parent_trust = (Integer) path.lastRelationship().getStartNode().getProperty(trustProperty);

					if (parent_trust > 0)
					{
						final byte score = (Byte) path.lastRelationship().getProperty(IEdge.SCORE);
						final int current_trust = (Integer) current_node.getProperty(trustProperty);

						int normalized_parent_trust = Math.min(parent_trust, 100);
						normalized_parent_trust = Math.max(normalized_parent_trust, -100);
						
						if (path.length() == 1)
						{
							current_node.setProperty(trustProperty, (int) score);
						}
						else if (normalized_parent_trust > 0 && score != 0)
						{
								current_node.setProperty(trustProperty, (int) (current_trust + Math.round(
																					(100.0 / normalized_parent_trust) *
																					((score/100.0)*Math.pow(decay, path.length()-1)
																					)*100))
								);
								
						}
					}
						
					tx.success();
				}
				finally
				{
					tx.finish();
				}
			}	}
		}
		
		System.out.println("Down with trust calculation.");
	}

	protected void initOwnIdentity(Node own_vertex, final String trustProperty, final String distanceProperty, ReadableIndex<Node> nodeIndex) {
		Transaction tx = db.beginTx();
		try
		{
			own_vertex.setProperty(trustProperty, 100);
			own_vertex.setProperty(distanceProperty, 0);
			tx.success();
		}
		finally
		{
			tx.finish();
		}
	}

	protected void resetGraph() {
		GlobalGraphOperations util = GlobalGraphOperations.at(db);

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
						if (prop.startsWith(IVertex.TRUST) || prop.startsWith(IVertex.DISTANCE)) node.removeProperty(prop);  
					}

					final IndexHits<Node> vertices = nodeIndex.get(IVertex.OWN_IDENTITY, true);
					for(Node localIdentity : vertices)
					{
						node.setProperty(IVertex.TRUST+"."+localIdentity.getProperty(IVertex.ID), 0);
						node.removeProperty(IVertex.DISTANCE+"."+localIdentity.getProperty(IVertex.ID));	
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
