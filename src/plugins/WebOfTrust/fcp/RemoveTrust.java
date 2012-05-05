package plugins.WebOfTrust.fcp;

import java.sql.SQLException;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class RemoveTrust extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException 
	{
		final String trusterID = getMandatoryParameter(input, "Truster");
		final String trusteeID = getMandatoryParameter(input, "Trustee");

		long truster = graph.getVertexByPropertyValue(IVertex.ID, trusterID).get(0);
		long trustee = graph.getVertexByPropertyValue(IVertex.ID, trusteeID).get(0);

		try
		{
			long edge = graph.getEdgeByVerticesAndProperty(truster, trustee, IEdge.SCORE);	
			graph.removeEdge(edge);
		}
		catch(SQLException e) 
		{
			System.out.println("Failed to find edge with vertex_from: " + truster + " vertex_to: " + trustee + " and the 'score' property");
		}

		reply.putOverwrite("Message", "TrustRemoved");
		reply.putOverwrite("Truster", trusterID);
		reply.putOverwrite("Trustee", trusteeID);
		
		return reply;
	}

}
