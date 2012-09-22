package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.LinkedList;
import java.util.List;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.IndexHits;
import org.neo4j.graphdb.index.ReadableIndex;

import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
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
				List<String> contexts = null;
				if (!identity_vertex.hasProperty(IVertex.CONTEXT_NAME))
				{
					contexts = new LinkedList<String>();
				}
				else
				{
					contexts = (List<String>) identity_vertex.getProperty(IVertex.CONTEXT_NAME);
				}
				
				if (!contexts.contains(context)) contexts.add(context);
				identity_vertex.setProperty(IVertex.CONTEXT_NAME, contexts);
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
