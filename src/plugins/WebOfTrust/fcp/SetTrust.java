package plugins.WebOfTrust.fcp;

import java.sql.SQLException;

import plugins.WebOfTrust.datamodel.IEdge;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class SetTrust extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException {

		final String trusterID = getMandatoryParameter(input, "Truster");
		final String trusteeID = getMandatoryParameter(input, "Trustee");
		final String trustValue = getMandatoryParameter(input, "Value");
		final String trustComment = getMandatoryParameter(input, "Comment");

		setTrust(graph, trusterID, trusteeID, trustValue, trustComment);

		reply.putOverwrite("Message", "TrustSet");
		reply.putOverwrite("Truster", trusterID);
		reply.putOverwrite("Trustee", trusteeID);
		reply.putOverwrite("Value", trustValue);
		reply.putOverwrite("Comment", trustComment);
	
		return reply;
	}

	public static void setTrust(H2Graph graph, final String trusterID, final String trusteeID, final String trustValue, final String trustComment) throws SQLException 
	{
		final long truster = graph.getVertexByPropertyValue("id", trusterID).get(0);
		final long trustee = graph.getVertexByPropertyValue("id", trusteeID).get(0);

		long edge;
		try
		{
			edge = graph.getEdgeByVerticesAndProperty(truster, trustee, "score");	
		}
		catch(SQLException e) //edge doesn't exist yet, so create new one
		{
			edge = graph.addEdge(truster, trustee);
		}

		graph.updateEdgeProperty(edge, IEdge.SCORE, trustValue);
		graph.updateEdgeProperty(edge, IEdge.COMMENT, trustComment);
	}
}
