package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import plugins.WebOfTrust.datamodel.IVertex;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class GetOwnIdentities extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException {

		reply.putSingle("Message", "OwnIdentities");
		List<Long> ownIdentities = graph.getVertexByPropertyValue(IVertex.OWN_IDENTITY, "true");
		for(int i=0; i < ownIdentities.size(); i++)
		{
			Map<String, List<String>> identityProperties = graph.getVertexProperties(ownIdentities.get(i));

			reply.putOverwrite("Identity" + i, identityProperties.get(IVertex.ID).get(0));
			reply.putOverwrite("RequestURI" + i, identityProperties.get(IVertex.REQUEST_URI).get(0));
			reply.putOverwrite("InsertURI" + i, identityProperties.get(IVertex.INSERT_URI).get(0));
			reply.putOverwrite("Nickname" + i, identityProperties.get(IVertex.NAME).get(0));

			int contextCounter = 0;
			if (identityProperties.containsKey(IVertex.CONTEXT_NAME))
			{
				for (String context : identityProperties.get(IVertex.CONTEXT_NAME)) {
					reply.putOverwrite("Contexts" + i + ".Context" + contextCounter++, context);
				}
			}

			//TODO: only include properties that aren't one of the above
			int propertiesCounter = 0;
			for (Entry<String, List<String>> property : identityProperties.entrySet()) {
				reply.putOverwrite("Properties" + i + ".Property" + propertiesCounter + ".Name", property.getKey());
				reply.putOverwrite("Properties" + i + ".Property" + propertiesCounter++ + ".Value", property.getValue().get(0));
			}
		}
		return reply;
	}

}
