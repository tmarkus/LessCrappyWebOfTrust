package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.VertexIterator;
import freenet.support.SimpleFieldSet;

public class GetIdentitiesByScore extends GetIdentity {

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
		if (trusterID == null || trusterID.equals("null")) //take the union of all identities seen by a local identity
		{
			List<Long> own_vertices = graph.getVertexByPropertyValue(IVertex.OWN_IDENTITY, "true");
			for(long vertex : own_vertices)
			{
				treeOwnerVertexList.add(vertex);
				Map<String, List<String>> props = graph.getVertexProperties(vertex);
				treeOwnerList.add(props.get("id").get(0));
			}
		}
		else //only one own identity
		{
			treeOwnerVertexList.addAll( graph.getVertexByPropertyValue(IVertex.ID, trusterID) );
			treeOwnerList.add(trusterID);
		}
		
		//take the union of all trusted identities for all identities specified
		List<String> treeOwnerProperties = new LinkedList<String>();
		for(String treeOwner : treeOwnerList)
		{
			treeOwnerProperties.add(IVertex.TRUST+"."+treeOwner);
		}
		
		
		List<String> queryProperties = new LinkedList<String>();
		queryProperties.addAll(treeOwnerProperties);
		queryProperties.add(IVertex.NAME);
		queryProperties.add(IVertex.CONTEXT_NAME);
		queryProperties.add(IVertex.ID);
		queryProperties.add(IVertex.REQUEST_URI);
		
		Map<String, String> requiredProperties = new HashMap<String, String>(); 
		requiredProperties.put(IVertex.CONTEXT_NAME, context);
		
		long start = System.currentTimeMillis();
		VertexIterator vertices = graph.getVertices(treeOwnerProperties, select, queryProperties, requiredProperties, false, Integer.MAX_VALUE);
		System.out.println("Big query took: " + (System.currentTimeMillis() - start) + "ms");
		
		reply.putSingle("Message", "Identities");
		int i = 0;

		while(vertices.hasNext())
		{
			Map<String, List<String>> properties = vertices.next();

			//check whether the identity has a name (and we thus have retrieved it at least once)
			if (properties.containsKey(IVertex.NAME))
			{
				long max_score_owner_id = -1; //identity which has the maximum trust directly assigned (possibly none)
				
				try
				{
					int max_score = Integer.MIN_VALUE;
					for(long own_identity : treeOwnerVertexList)
					{
						int score = Integer.parseInt(graph.getEdgeValueByVerticesAndProperty(own_identity, vertices.getLastVertexId(), IEdge.SCORE));
						if (score > max_score) 
						{
							max_score = score;
							max_score_owner_id = own_identity;
						}
					}
				}
				catch(SQLException e) {} //no direct score relation no problem, just ignore

				addIdentityReplyFields(graph, properties, max_score_owner_id, vertices.getLastVertexId(), Integer.toString(i));
				
				if (includeTrustValue)	reply.putOverwrite("Score" + i, properties.get(IVertex.TRUST+"."+trusterID).get(0));
				reply.putOverwrite("ScoreOwner" + i, properties.get("id").get(0));

				i += 1;
			}
		}
		return reply;
	}
}
