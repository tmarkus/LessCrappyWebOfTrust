package plugins.WebOfTrust.fcp;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;

import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

import freenet.support.SimpleFieldSet;

public class RemoveTrust extends FCPBase {

	public RemoveTrust(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) 
	{
		final String trusterID = getMandatoryParameter(input, "Truster");
		final String trusteeID = getMandatoryParameter(input, "Trustee");

		
		Node truster = nodeIndex.get(IVertex.ID, trusterID).getSingle();
		Node trustee = nodeIndex.get(IVertex.ID, trusteeID).getSingle();

		Transaction tx = db.beginTx();
		try
		{
			for(Relationship rel : truster.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
			{
				if (rel.getEndNode().equals(trustee))
				{
					rel.delete();
				}
			}
			tx.success();
		}
		finally
		{
			tx.finish();
		}
		
		reply.putOverwrite("Message", "TrustRemoved");
		reply.putOverwrite("Truster", trusterID);
		reply.putOverwrite("Trustee", trusteeID);
		
		return reply;
	}

}
