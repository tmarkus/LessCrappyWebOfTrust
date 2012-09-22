package plugins.WebOfTrust.fcp;


import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;

import plugins.WebOfTrust.datamodel.IVertex;

import freenet.support.SimpleFieldSet;

public class GetProperty extends FCPBase {

	public GetProperty(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) {

		final String identityID = input.get("Identity");
		final String propertyName = input.get("Property");

		Node vertex = nodeIndex.get(IVertex.ID, identityID).getSingle();
		
		reply.putSingle("Message", "PropertyValue");
		if (vertex.hasProperty(propertyName)) reply.putSingle("Property", vertex.getProperty(propertyName).toString());

		return reply;
	}
}
