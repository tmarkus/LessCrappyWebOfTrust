package plugins.WebOfTrust.fcp;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.NotFoundException;
import org.neo4j.graphdb.Relationship;

import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;

import freenet.support.SimpleFieldSet;

public class GetIdentity extends FCPBase {

	public GetIdentity(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) {
		
		final String trusterID = input.get("Truster"); 
		final String identityID = input.get("Identity");

		reply.putOverwrite("Message", "Identity");

		Node own_id = nodeIndex.get(IVertex.ID, trusterID).getSingle();
		Node identity = nodeIndex.get(IVertex.ID, identityID).getSingle();

		Relationship direct_trust_rel = null;
		for(final Relationship rel : own_id.getRelationships(Direction.OUTGOING, Rel.TRUSTS))
		{
			if (rel.getEndNode().equals(identity)) direct_trust_rel = rel;
		}
		
		addIdentityReplyFields(direct_trust_rel, identity, "");
		
		return reply;
	}

	protected void addIdentityReplyFields(Relationship max_score_rel, Node identity, String index) 
	{
		reply.putOverwrite("Identity" + index, (String) identity.getProperty(IVertex.ID));
		reply.putOverwrite("Nickname"+index,  (String) identity.getProperty(IVertex.NAME));
		reply.putOverwrite("RequestURI"+index,  (String) identity.getProperty(IVertex.REQUEST_URI));

		//initial values
		reply.putOverwrite("Trust"+index, "null");
		reply.putOverwrite("Rank"+index, "null");

		Node ownIdentity = null;
		if (max_score_rel != null)
		{
				ownIdentity = max_score_rel.getStartNode();
				reply.putOverwrite("Trust"+index, max_score_rel.getProperty(IEdge.SCORE).toString());
				reply.putOverwrite("Rank"+index, "666");
		}

		try
		{
			reply.putOverwrite("Score"+index, Integer.toString((Integer) identity.getProperty(IVertex.TRUST+"."+ownIdentity.getProperty(IVertex.ID))));	
		}
		catch(NotFoundException e) //trust not stored in db
		{
			reply.putOverwrite("Score"+index, "null");
		}
		catch(NullPointerException e) //trust also not stored in db
		{
			reply.putOverwrite("Score"+index, "null");
		}
		
		
		if(identity.hasProperty(IVertex.CONTEXT_NAME))
		{
			int contextCounter=0;
			for(Relationship contextRel : identity.getRelationships(Direction.OUTGOING, Rel.HAS_CONTEXT))
			{
				String context = (String) contextRel.getEndNode().getProperty(IContext.NAME);
				if (index.equals(""))	reply.putOverwrite("Context" + contextCounter, context);
				else					reply.putOverwrite("Contexts" + index + ".Context" + contextCounter++, context);
				contextCounter += 1;
			}
		}

		int propertiesCounter = 0;
		for (String propertyName : identity.getPropertyKeys()) {
			if (index.equals(""))
			{
				reply.putOverwrite("Property"+index + propertiesCounter + ".Name", propertyName);
				reply.putOverwrite("Property"+index + propertiesCounter++ + ".Value", identity.getProperty(propertyName).toString());
			}
			else
			{
				reply.putOverwrite("Properties" + index + ".Property" + propertiesCounter + ".Name", propertyName);
				reply.putOverwrite("Properties" + index + ".Property" + propertiesCounter++ + ".Value", identity.getProperty(propertyName).toString());
			}
		}
	}
}
