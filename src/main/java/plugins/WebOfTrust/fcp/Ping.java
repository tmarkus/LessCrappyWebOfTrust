package plugins.WebOfTrust.fcp;

import org.neo4j.graphdb.GraphDatabaseService;

import freenet.support.SimpleFieldSet;

public class Ping extends FCPBase {

	public Ping(GraphDatabaseService db) {
		super(db);
	}

	@Override
	public SimpleFieldSet handle(SimpleFieldSet input) {
		reply.putOverwrite("Message", "Pong");
		return reply;
	}
}
