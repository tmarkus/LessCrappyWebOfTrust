package plugins.WebOfTrust.fcp;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.neo4j.graphdb.Direction;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;

import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.datamodel.Rel;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class GetOwnIdentities extends FCPBase {

	public GetOwnIdentities(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) {

		reply.putSingle("Message", "OwnIdentities");
		
		int i = 0;
		for(Node ownIdentity : nodeIndex.get(IVertex.OWN_IDENTITY, true))
		{
			reply.putOverwrite("Identity" + i, (String) ownIdentity.getProperty(IVertex.ID));
			reply.putOverwrite("RequestURI" + i, (String) ownIdentity.getProperty(IVertex.REQUEST_URI));
			reply.putOverwrite("InsertURI" + i, (String) ownIdentity.getProperty(IVertex.INSERT_URI));
			reply.putOverwrite("Nickname" + i, (String) ownIdentity.getProperty(IVertex.NAME));

			int contextCounter = 0;
			
			for(Relationship rel : ownIdentity.getRelationships(Direction.OUTGOING, Rel.HAS_CONTEXT))
			{
				reply.putOverwrite("Contexts" + i + ".Context" + contextCounter++, (String) rel.getEndNode().getProperty(IContext.NAME));
			}

			//TODO: only include properties that aren't one of the above
			int propertiesCounter = 0;
			for (String propertyName : ownIdentity.getPropertyKeys()) {
				reply.putOverwrite("Properties" + i + ".Property" + propertiesCounter + ".Name", propertyName);
				reply.putOverwrite("Properties" + i + ".Property" + propertiesCounter++ + ".Value", ownIdentity.getProperty(propertyName).toString());
			}
		
		i += 1;
		}
		return reply;
	}

}
