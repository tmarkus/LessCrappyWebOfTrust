package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class GetIdentitiesByScore extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException {

		final String trusterID = input.get("Truster");
		final String selection = input.get("Selection").trim();
		final String context = input.get("Context");
		final boolean includeTrustValue = input.getBoolean("WantTrustValues", false);

		int select = 0;
		if (selection.equals("+")) select = -1;
		else if (selection.equals("0")) select = -1;

		Set<String> treeOwnerList = new HashSet<String>(); 
		Set<Long> treeOwnerVertexList = new HashSet<Long>();
		if (trusterID == null || trusterID.equals("null"))
		{
			List<Long> own_vertices = graph.getVertexByPropertyValue(IVertex.OWN_IDENTITY, "true");
			for(long vertex : own_vertices)
			{
				treeOwnerVertexList.add(vertex);
				Map<String, List<String>> props = graph.getVertexProperties(vertex);
				treeOwnerList.add(props.get("id").get(0));
			}
		}
		else
		{
			List<Long> own_vertex = graph.getVertexByPropertyValue(IVertex.ID, trusterID);
			treeOwnerList.add(trusterID);
			for(long vertex : own_vertex) treeOwnerVertexList.add(vertex);
		}
		
		//take the union of all trusted identities for all identities specified
		Set<Long> vertices = new HashSet<Long>();
		for(String treeOwner : treeOwnerList)
		{
			vertices.addAll(graph.getVerticesWithPropertyValueLargerThan(IVertex.TRUST+"."+treeOwner, select));	
		}
		
		reply.putSingle("Message", "Identities");
		int i = 0;
		for(long identity_vertex : vertices)
		{
			Map<String, List<String>> properties = graph.getVertexProperties(identity_vertex);

			//check whether the identity has a name (and we thus have retrieved it at least once)
			if (properties.containsKey(IVertex.NAME))
			{
				//check whether the identity has the context we need
				//TODO: This should be done as part of the query
				if (properties.containsKey(IVertex.CONTEXT_NAME) && properties.get(IVertex.CONTEXT_NAME).contains(context))
				{
					reply.putOverwrite("Identity" + i, properties.get(IVertex.ID).get(0));
					reply.putOverwrite("RequestURI" + i, properties.get(IVertex.REQUEST_URI).get(0));
					reply.putOverwrite("Nickname" + i, properties.get(IVertex.NAME).get(0));

					int contextCounter = 0;
					for (String identityContext: properties.get("contextName")) {
						reply.putOverwrite("Contexts" + i + ".Context" + contextCounter++, identityContext);
					}

					int propertiesCounter = 0;
					for (Entry<String, List<String>> property : properties.entrySet()) {
						reply.putOverwrite("Properties" + i + ".Property" + propertiesCounter + ".Name", property.getKey());
						reply.putOverwrite("Properties" + i + ".Property" + propertiesCounter++ + ".Value", property.getValue().get(0));
					}

					input.putOverwrite("ScoreOwner" + i, properties.get("id").get(0));

					try
					{
						int score = Integer.MIN_VALUE;
						for(long own_identity : treeOwnerVertexList)
						{
							int tmp_score = Integer.parseInt(graph.getEdgeValueByVerticesAndProperty(own_identity, identity_vertex, IEdge.SCORE));
							if (tmp_score > score) score = tmp_score;
						}
						
						reply.putOverwrite("Trust" + i, Integer.toString(score));
						reply.putOverwrite("Rank" + i, "666");
					}
					catch(SQLException e) //no score relation
					{
						reply.putOverwrite("Trust" + i, "null");
						reply.putOverwrite("Rank" + i, "null");
					}

					if (includeTrustValue)
					{
						reply.putOverwrite("Score" + i, properties.get(IVertex.TRUST+"."+trusterID).get(0));	
					}
					i += 1;
				}
			}
		}

		return reply;
	}

}
