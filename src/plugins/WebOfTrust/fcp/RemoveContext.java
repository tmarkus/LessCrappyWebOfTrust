package plugins.WebOfTrust.fcp;

import java.util.List;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

import freenet.support.SimpleFieldSet;

public class RemoveContext extends FCPBase {

	public RemoveContext(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) {
		final String identityID = input.get("Identity");
		final String context = input.get("Context");

		Node vertex = nodeIndex.get(IVertex.ID, identityID).getSingle();

		Transaction tx = db.beginTx();
		try
		{

			for(Relationship rel : vertex.getRelationships(Direction.OUTGOING, Rel.HAS_CONTEXT))
			{
				if (rel.getProperty(IContext.NAME).equals(context)) rel.delete();
			}
			tx.success();
		}
		finally
		{
			tx.finish();
		}
	
		reply.putOverwrite("Message", "ContextRemoved");
		return reply;
	}

}
