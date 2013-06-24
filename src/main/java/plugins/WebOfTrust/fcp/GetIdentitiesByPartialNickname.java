package plugins.WebOfTrust.fcp;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.index.lucene.QueryContext;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;
import plugins.WebOfTrust.exceptions.UnknownIdentityException;

import freenet.node.FSParseException;
import freenet.support.SimpleFieldSet;

public class GetIdentitiesByPartialNickname extends GetIdentity {

	public GetIdentitiesByPartialNickname(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) throws UnknownIdentityException {

		final String trusterID = input.get("Truster");
		final String partialNickname = input.get("PartialNickname").trim();
		final String partialID = input.get("PartialID").trim();
		final String context = input.get("Context");
		int maxIdentities = 0; 
		try {
			maxIdentities = input.getInt("MaxIdentities");
		} catch (FSParseException e) {
			throw new IllegalArgumentException("MaxIdentities has an incorrect value");
		}
		
		//find all identities for given context
		reply.putSingle("Message", "Identities");
		int numIdentities = 0;

		//find trusterID
		final Node treeOwnerNode = nodeIndex.get(IVertex.ID, trusterID).getSingle();
		
		if (treeOwnerNode == null) throw new UnknownIdentityException("No such local identity '" + trusterID + "'.");
		
		final String treeOwnerTrustProperty = IVertex.TRUST+"_"+treeOwnerNode.getProperty(IVertex.ID);
		final Node contextNode = nodeIndex.get(IContext.NAME, context).getSingle();
		 
		
		//get all identities with a specific context
		if (nodeIndex.get(IContext.NAME, context).hasNext()) //the context exists in the graph
		{
			for(Node identity : nodeIndex.query(new QueryContext( IVertex.NAME + ":" + partialNickname)))
			{
				//check if identity has the context
				boolean has_context = false;
				for(Relationship contextRel : identity.getRelationships(Direction.OUTGOING, Rel.HAS_CONTEXT))
				{
					if (contextRel.getEndNode().equals(contextNode))
					{
						has_context = true;
						break;
					}
				}
				
				if (has_context)
				{
					//check whether keypart matches as well
					if (((String) identity.getProperty(IVertex.ID)).startsWith(partialID))
					{
						//find the score relation if present
						Relationship directScore = null;
						for(Relationship rel : identity.getRelationships(Direction.INCOMING, Rel.TRUSTS))
						{
							if (rel.getStartNode().getProperty(IVertex.ID).equals(trusterID))
							{
								directScore = rel;
								break;
							}
						}
						
						//check whether the trust is >= 0 before including
						if (identity.hasProperty(treeOwnerTrustProperty))
						{
							if (((Integer) identity.getProperty(treeOwnerTrustProperty)) >= 0)
							{
								addIdentityReplyFields(directScore, identity, Integer.toString(numIdentities), true, trusterID);
								numIdentities += 1;
								if (numIdentities >= maxIdentities) break;
							}
						}
					}
				}
			}
		}

		assert numIdentities <= maxIdentities;

		reply.put("IdentitiesMatched", numIdentities);
		
		
		if (WebOfTrust.DEBUG) System.out.println("GetIdentitiesByPartialNickname returned " + numIdentities + " identities for the context: " + context);
		
		return reply;
	}
}
