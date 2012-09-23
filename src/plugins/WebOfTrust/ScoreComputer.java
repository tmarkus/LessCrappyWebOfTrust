package plugins.WebOfTrust;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Path;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
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

	protected static final float decay = 0.4f;

	public ScoreComputer(GraphDatabaseService db)
	{
		this.db = db;
	}

	public void compute(String ownIdentityID) throws SQLException
	{
		
		System.out.println("Starting trust calculation for " + ownIdentityID);
		
		final String trustProperty = IVertex.TRUST+"."+ownIdentityID;
		final String distanceProperty = IVertex.DISTANCE + "." + ownIdentityID;

		ReadableIndex<Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();

		//delete the calculated trust values for all identities for this ownIdentity
		System.out.println("Resetting graph by removing many properties...");
		resetGraph(trustProperty, distanceProperty);
		System.out.println("Finished resetting graph");
		
		//assign 100 trust to local identity
		Node own_identity = initOwnIdentity(ownIdentityID, trustProperty, distanceProperty, nodeIndex);

		//traverse the graph
		
		
		TraversalDescription td = Traversal.description()
	            .breadthFirst()
	            .relationships( Rel.TRUSTS, Direction.OUTGOING )
	            .evaluator( Evaluators.excludeStartPosition() )
	            .evaluator( Evaluators.toDepth(6) );
		
		for(Path path : td.traverse(own_identity))
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
		}
	
		System.out.println("Down with trust calculation.");
	}

	protected Node initOwnIdentity(String ownIdentityID, final String trustProperty, final String distanceProperty, ReadableIndex<Node> nodeIndex) {
		Node own_vertex = nodeIndex.get(IVertex.ID, ownIdentityID).getSingle();
	
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
		
		return own_vertex;
	}

	protected void resetGraph(final String trustProperty, final String distanceProperty) {
		GlobalGraphOperations util = GlobalGraphOperations.at(db);

		Transaction tx = db.beginTx();
		try 
		{
			for(Node node : util.getAllNodes())
			{
				if (node.hasProperty(IVertex.ID))
				{
					node.setProperty(trustProperty, 0);
					node.removeProperty(distanceProperty);
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
