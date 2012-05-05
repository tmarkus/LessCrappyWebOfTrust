package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.List;

import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class RemoveContext extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException {
		final String identityID = input.get("Identity");
		final String context = input.get("Context");

		List<Long> identity_vertex_ids = graph.getVertexByPropertyValue(IVertex.ID, identityID);
		for(long identity_vertex_id : identity_vertex_ids)
		{
			graph.removeVertexPropertyValue(identity_vertex_id, "contextName", context);
		}

		reply.putOverwrite("Message", "ContextRemoved");
		return reply;
	}

}
