package plugins.WebOfTrust.fcp;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

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

		final Set<Node> treeOwnerList = new HashSet<Node>(); 
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
		final List<String> treeOwnerProperties = new LinkedList<String>();
		for(Node treeOwner : treeOwnerList)
		{
			treeOwnerProperties.add(IVertex.TRUST+"."+treeOwner.getProperty(IVertex.ID));
		}
		
		//build cache of identities directly connected to own identity
		Map<Node, Map<Node, Relationship>> directTrustCache = new HashMap<Node, Map<Node, Relationship>>();
		for(Node ownIdentity : treeOwnerList)
		{
			Map<Node, Relationship> dt = new HashMap<Node, Relationship>();
			
			for(Relationship rel : ownIdentity.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
			{
				dt.put(rel.getEndNode(), rel);
			}
			
			directTrustCache.put(ownIdentity, dt);
		}
		
		//find all identities for given context
		reply.putSingle("Message", "Identities");
		int i = 0;

		//get all identities with a specific context
		for (final Relationship hasContextRel : nodeIndex.get(IContext.NAME, context).getSingle().getRelationships(Direction.INCOMING, Rel.HAS_CONTEXT))
		{
			final Node identity = hasContextRel.getStartNode();
			//check whether the identity has a name (and we thus have retrieved it at least once)
			if (identity.hasProperty(IVertex.NAME))
			{
				//determine whether we have a calculated trust value for this identity larger than 0
				boolean goodTrust = false;
				for(String prop : treeOwnerProperties)
				{
					if (identity.hasProperty(prop) && (Integer) identity.getProperty(prop) >= 0) goodTrust = true; 
				}
				
				if (goodTrust)
				{
					Integer max_score = Integer.MIN_VALUE;
					Relationship max_score_rel = null; //identity which has the maximum trust directly assigned (possibly none)
					
					for(final Node own_identity : treeOwnerList	)
					{
						Relationship rel = directTrustCache.get(own_identity).get(identity);
						if (rel != null)
						{
							final int score = (Byte) rel.getProperty(IEdge.SCORE);
							if (score > max_score) 
							{
								max_score = score;
								max_score_rel = rel;
							}
						}
					}

					addIdentityReplyFields(max_score_rel, identity, Integer.toString(i));
					
					if (includeTrustValue)	reply.putOverwrite("Score" + i, Integer.toString(max_score));
					if (max_score_rel != null) reply.putOverwrite("ScoreOwner" + i, (String) max_score_rel.getStartNode().getProperty(IVertex.ID));

					i += 1;
				}
			}
		}
		
		System.out.println("GetIdentitiesByScore returned " + i + " identities for the context: " + context);
		
		return reply;
	}
}
