package plugins.WebOfTrust.fcp;

import java.sql.SQLException;

import org.apache.lucene.index.IndexNotFoundException;
import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.ReadableIndex;
import org.neo4j.server.rest.repr.NodeIndexRepresentation;
import org.neo4j.server.rrd.sampler.NodeIdsInUseSampleable;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class SetTrust extends FCPBase {

	public SetTrust(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) {

		final String trusterID = getMandatoryParameter(input, "Truster");
		final String trusteeID = getMandatoryParameter(input, "Trustee");
		final String trustValue = getMandatoryParameter(input, "Value");
		final String trustComment = getMandatoryParameter(input, "Comment");

		setTrust(trusterID, trusteeID, trustValue, trustComment);

		reply.putOverwrite("Message", "TrustSet");
		reply.putOverwrite("Truster", trusterID);
		reply.putOverwrite("Trustee", trusteeID);
		reply.putOverwrite("Value", trustValue);
		reply.putOverwrite("Comment", trustComment);
	
		return reply;
	}

	public void setTrust(final String trusterID, final String trusteeID, final String trustValue, final String trustComment) 
	{
		setTrust(db, nodeIndex, trusterID, trusteeID, trustValue, trustComment);
	}
	
	public static void setTrust(GraphDatabaseService db, ReadableIndex<Node> nodeIndex, final String trusterID, final String trusteeID, final String trustValue, final String trustComment) 
	{
		final Node truster = nodeIndex.get(IVertex.ID, trusterID).getSingle();
		final Node trustee = nodeIndex.get(IVertex.ID, trusteeID).getSingle();
		
		Relationship relation = null;
		for(Relationship rel : truster.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
		{
			if (rel.getEndNode().equals(trustee)) relation = rel;
		}
		
		Transaction tx = db.beginTx();
		try
		{
			if (relation == null) relation = truster.createRelationshipTo(trustee, Rel.TRUSTS);
			
			relation.setProperty(IEdge.SCORE, Integer.parseInt(trustValue));
			relation.setProperty(IEdge.COMMENT, trustComment);
			
			tx.success();
		}
		finally
		{
			tx.finish();
		}
	}
}
