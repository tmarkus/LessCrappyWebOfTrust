package plugins.WebOfTrust;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.ReadableIndex;
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
		final String trustProperty = IVertex.TRUST+"."+ownIdentityID;
		final String distanceProperty = IVertex.DISTANCE + "." + ownIdentityID;
		
		final List<String> properties = new LinkedList<String>();
		properties.add(IVertex.VERTEX_ID);
		
		ReadableIndex<Node> nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		
		Transaction tx = db.beginTx();
		try 
		{
			//delete the calculated trust values for all identities for this ownIdentity
			GlobalGraphOperations util = GlobalGraphOperations.at(db);
			for(Node node : util.getAllNodes())
			{
				node.removeProperty(trustProperty);
				node.removeProperty(distanceProperty);
			}
			
			
			//assign 100 trust to local identity
			Node own_vertex = nodeIndex.get(IVertex.ID, ownIdentityID).getSingle();

			own_vertex.setProperty(trustProperty, 100);
			own_vertex.setProperty(distanceProperty, 0);
			
			//manually set trust for directly trusted entities (override)
			final Iterable<Relationship> edges = own_vertex.getRelationships(Direction.OUTGOING, Rel.TRUSTS);
			for(Relationship edge : edges)
			{
				Node vertex_to = edge.getEndNode();
				vertex_to.setProperty(distanceProperty, 1);
				vertex_to.setProperty(trustProperty, edge.getProperty(IEdge.SCORE));
			}

			//get unique vertices from current union of all outgoing edges
			Set<Node> vertices = new HashSet<Node>();
			for(Relationship edge : own_vertex.getRelationships(Direction.OUTGOING, Rel.TRUSTS)) vertices.add(edge.getEndNode());
			
			for(int distance=1; distance < 6; distance++)
			{
				//conditionally update the distance value number (many vertices will probably be revisited)
				for(Node vertex : vertices)
				{
					if (vertex.hasProperty(distanceProperty) && (Integer) vertex.getProperty(distanceProperty) > distance)
					{
						vertex.setProperty(distanceProperty, distance);	
					}
				}
			
				//retrieve vertices for next iteration
				Set<Node> next_vertices = new HashSet<Node>();
				for(Node vertex : vertices) {
					for(Relationship edge : vertex.getRelationships(Direction.OUTGOING, Rel.TRUSTS) ) {
						if (!next_vertices.contains(edge.getEndNode() ) &&
							! edge.getEndNode().hasProperty(distanceProperty) ) 
						{
							next_vertices.add(edge.getEndNode());	
						}
					}
				}
				
				System.out.println("vertices: " + vertices.size());
				System.out.println("next vertices: " + next_vertices.size());
				vertices = next_vertices;
			}
			
			
			//calculate the trust for every other identity other than the directly trusted ones identity by their weighted average score
			  // extend EdgeProperty class with the outgoing vertex property using a boolean parameter, otherwise null
			
			tx.success();
		}
		finally
		{
			tx.finish();
		}
	}
}
