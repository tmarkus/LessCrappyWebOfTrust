package plugins.WebOfTrust.fcp;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.index.ReadableIndex;

import freenet.support.SimpleFieldSet;

public abstract class FCPBase {

	protected SimpleFieldSet reply = new SimpleFieldSet(true);
	public abstract SimpleFieldSet handle(SimpleFieldSet input);

	protected GraphDatabaseService db;
	protected ReadableIndex<Node> nodeIndex;
	protected ReadableIndex<Relationship> relIndex;
	
	public FCPBase(GraphDatabaseService db)
	{
		this.db = db;
		nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
		relIndex = db.index().getRelationshipAutoIndexer().getAutoIndex();
	}
	
	
	protected String getMandatoryParameter(final SimpleFieldSet sfs, final String name) throws IllegalArgumentException {
		final String result = sfs.get(name);
		if(result == null)
			throw new IllegalArgumentException("Missing mandatory parameter: " + name);

		return result;
	}
	
}
