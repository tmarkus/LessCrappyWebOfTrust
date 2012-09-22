package plugins.WebOfTrust.fcp;

import java.util.List;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import plugins.WebOfTrust.datamodel.IVertex;

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
		
		List<String> contexts = (List<String>) vertex.getProperty(IVertex.CONTEXT_NAME);
		contexts.remove(context);
		
		Transaction tx = db.beginTx();
		try
		{
			vertex.setProperty(IVertex.CONTEXT_NAME, contexts);	
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
