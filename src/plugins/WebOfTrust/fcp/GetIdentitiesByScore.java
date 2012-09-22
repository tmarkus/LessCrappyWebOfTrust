package plugins.WebOfTrust.fcp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.tooling.GlobalGraphOperations;

import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;

import freenet.support.SimpleFieldSet;

public class GetIdentitiesByScore extends GetIdentity {

	public GetIdentitiesByScore(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) {

		final String trusterID = input.get("Truster");
		final String selection = input.get("Selection").trim();
		final String context = input.get("Context");
		final boolean includeTrustValue = input.getBoolean("WantTrustValues", false);

		int select = 0;
		if (selection.equals("+")) select = -1;
		else if (selection.equals("0")) select = -1;

		Set<Node> treeOwnerList = new HashSet<Node>(); 
		if (trusterID == null || trusterID.equals("null")) //take the union of all identities seen by a local identity
		{
			for(Node vertex : nodeIndex.get(IVertex.OWN_IDENTITY, true))
			{
				treeOwnerList.add(vertex);
			}
		}
		else //only one own identity
		{
			treeOwnerList.add(nodeIndex.get(IVertex.ID, trusterID).getSingle());
		}
		
		//take the union of all trusted identities for all identities specified
		List<String> treeOwnerProperties = new LinkedList<String>();
		for(Node treeOwner : treeOwnerList)
		{
			treeOwnerProperties.add(IVertex.TRUST+"."+treeOwner.getProperty(IVertex.ID));
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
		
		System.out.println("Big query took: " + (System.currentTimeMillis() - start) + "ms");
		
		reply.putSingle("Message", "Identities");
		int i = 0;

		GlobalGraphOperations ggo = GlobalGraphOperations.at(db);
		Iterator<Node> vertices = ggo.getAllNodes().iterator();
		while(vertices.hasNext())
		{
			Node vertex = vertices.next();
			if (vertex.hasProperty(IVertex.CONTEXT_NAME) && ((List<String>) vertex.getProperty(IVertex.CONTEXT_NAME)).contains(context) )
			{
				//check whether the identity has a name (and we thus have retrieved it at least once)
				if (vertex.hasProperty(IVertex.NAME))
				{
					Node max_score_owner_id = null; //identity which has the maximum trust directly assigned (possibly none)
					Integer max_score = Integer.MIN_VALUE;
					
					for(Node own_identity : treeOwnerList)
					{
						for(Relationship rel : own_identity.getRelationships(Direction.OUTGOING))
						{
							if (rel.getEndNode().equals(vertex))
							{
								final int score = (Integer) rel.getProperty(IEdge.SCORE);
								if (score > max_score) 
								{
									max_score = score;
									max_score_owner_id = own_identity;
								}
							}
						}
					}

					addIdentityReplyFields(vertex, max_score_owner_id, Integer.toString(i));
					
					if (includeTrustValue)	reply.putOverwrite("Score" + i, Integer.toString(max_score));
					reply.putOverwrite("ScoreOwner" + i, (String) max_score_owner_id.getProperty(IVertex.ID));

					i += 1;
				}
			}
		}
		return reply;
	}
}
