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
	public SimpleFieldSet handle(SimpleFieldSet input) throws Exception {

		final String identityID = input.get("Identity");
		final String propertyName = input.get("Property");

		reply.putSingle("Message", "PropertyValue");

		Node vertex = nodeIndex.get(IVertex.ID, identityID).getSingle();
		if (vertex == null) throw new Exception("The supplied identity to GetProperty is unknown.");
		if (vertex.hasProperty(propertyName)) reply.putSingle("Property", vertex.getProperty(propertyName).toString());

		return reply;
	}
}
