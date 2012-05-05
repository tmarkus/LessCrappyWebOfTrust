package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class GetIdentity extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException {
		
		final String trusterID = input.get("Truster"); 
		final String identityID = input.get("Identity");

		reply.putOverwrite("Message", "Identity");

		long own_id = graph.getVertexByPropertyValue(IVertex.ID, trusterID).get(0);

		List<Long> identities = graph.getVertexByPropertyValue(IVertex.ID, identityID);

		for(long identity : identities)
		{
			Map<String, List<String>> props = graph.getVertexProperties(identity);

			reply.putOverwrite("Nickname", props.get(IVertex.NAME).get(0));
			reply.putOverwrite("RequestURI", props.get(IVertex.REQUEST_URI).get(0));

			try	//directly trusted
			{
				long edge = graph.getEdgeByVerticesAndProperty(own_id, identity, IEdge.SCORE);
				Map<String, List<String>> edge_props = graph.getEdgeProperties(edge);

				reply.putOverwrite("Trust", edge_props.get(plugins.WebOfTrust.datamodel.IEdge.SCORE).get(0));
				reply.putOverwrite("Rank", "666");
			}
			catch(SQLException e) //not directly trusted, so set score accordingly
			{
				reply.putOverwrite("Trust", "null");
				reply.putOverwrite("Rank", "null");
			}

			try
			{
				reply.putOverwrite("Score", props.get(IVertex.TRUST+"."+trusterID).get(0));	
			}
			catch(NullPointerException e) //trust not stored in db
			{
				reply.putOverwrite("Score", "null");
			}

			if(props.containsKey(IVertex.CONTEXT_NAME))
			{
				int contextCounter=0;
				for(String context : props.get("contextName"))
				{
					reply.putOverwrite("Context" + contextCounter, context);
					contextCounter += 1;
				}
			}

			int propertiesCounter = 0;
			for (Entry<String, List<String>> property : props.entrySet()) {
				reply.putOverwrite("Property" + propertiesCounter + ".Name", property.getKey());
				reply.putOverwrite("Property" + propertiesCounter++ + ".Value", property.getValue().get(0));
			}
		}
		return reply;
	}
}
