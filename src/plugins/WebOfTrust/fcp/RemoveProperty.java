package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.List;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class RemoveProperty extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException 
	{
		final String identityID = input.get("Identity");
		final String propertyName = input.get("Property");

		List<Long> vertices = graph.getVertexByPropertyValue("id", identityID);
		for(long vertex_id : vertices)
		{
			graph.removeVertexProperty(vertex_id, propertyName);
		}

		reply.putOverwrite("Message", "PropertyRemoved");
		return reply;
	}
}
