package plugins.WebOfTrust.fcp;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;

import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

import freenet.support.SimpleFieldSet;

public class AddContext extends FCPBase {

	public AddContext(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) {
		final String identityID = input.get("Identity");
		final String context = input.get("Context");

		IndexHits<Node> identity_vertices = nodeIndex.get(IVertex.ID, identityID);
		Transaction tx = db.beginTx();

		try
		{
			for(Node identity_vertex : identity_vertices)
			{
				Node contextNode = nodeIndex.get(IContext.NAME, context).getSingle();

				//check whether the identity already has the context or not
				boolean hasContext = false;
				
				//add the context if it doesn't exist yet
				if (contextNode == null) {
					contextNode = db.createNode();
					contextNode.setProperty(IContext.NAME, context);
				}
				else
				{
					for(Relationship rel : identity_vertex.getRelationships(Direction.OUTGOING, Rel.HAS_CONTEXT))
					{
						if (rel.getEndNode().equals(contextNode)) hasContext = true;
					}
				}
				
				//if not, add a relationship to the context node
				if (!hasContext) identity_vertex.createRelationshipTo(contextNode, Rel.HAS_CONTEXT);
			}

			reply.putOverwrite("Message", "ContextAdded");
			tx.success();
		}
		finally
		{
			tx.finish();
			identity_vertices.close();
		}
		
		return reply;
	}
}
