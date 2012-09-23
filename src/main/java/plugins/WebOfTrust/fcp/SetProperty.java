package plugins.WebOfTrust.fcp;


import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import plugins.WebOfTrust.datamodel.IVertex;

import freenet.support.SimpleFieldSet;

public class SetProperty extends FCPBase {

	public SetProperty(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) 
	{
		final String identityID = input.get("Identity");
		final String propertyName = input.get("Property");
		final String propertyValue = input.get("Value");

		Node vertex = nodeIndex.get(IVertex.ID, identityID).getSingle();
		
		Transaction tx = db.beginTx();
		try
		{
			vertex.setProperty(propertyName, propertyValue);
			tx.success();
		}
		finally
		{
			tx.finish();
		}

		reply.putOverwrite("Message", "PropertyAdded");
		return reply;
	}
}
