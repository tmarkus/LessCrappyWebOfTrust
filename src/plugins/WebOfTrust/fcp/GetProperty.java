package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class GetProperty extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException {

		final String identityID = input.get("Identity");
		final String propertyName = input.get("Property");

		List<Long> vertices = graph.getVertexByPropertyValue("id", identityID);
		final Map<String, List<String>> props = graph.getVertexProperties(vertices.get(0));

		reply.putSingle("Message", "PropertyValue");
		if (props.containsKey(propertyName)) reply.putSingle("Property", props.get(propertyName).get(0));

		return reply;
	}
}
