package plugins.WebOfTrust.fcp;


import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import plugins.WebOfTrust.datamodel.IVertex;

import freenet.support.SimpleFieldSet;

public class RemoveProperty extends FCPBase {

	public RemoveProperty(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) 
	{
		final String identityID = input.get("Identity");
		final String propertyName = input.get("Property");

		Node vertex = nodeIndex.get(IVertex.ID, identityID).getSingle();
		Transaction tx = db.beginTx();
		try
		{
			vertex.removeProperty(propertyName);
			tx.success();
		}
		finally
		{
			tx.finish();
		}
		

		reply.putOverwrite("Message", "PropertyRemoved");
		return reply;
	}
}
