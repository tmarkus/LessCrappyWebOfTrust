package plugins.WebOfTrust.fcp;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public class Ping extends FCPBase {

	@Override
	public SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) {
		reply.putOverwrite("Message", "Pong");
		return reply;
	}
}
