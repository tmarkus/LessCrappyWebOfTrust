package plugins.WebOfTrust.fcp;

import java.sql.SQLException;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import freenet.support.SimpleFieldSet;

public abstract class FCPBase {

	protected SimpleFieldSet reply = new SimpleFieldSet(true);
	public abstract SimpleFieldSet handle(H2Graph graph, SimpleFieldSet input) throws SQLException;


	protected String getMandatoryParameter(final SimpleFieldSet sfs, final String name) throws IllegalArgumentException {
		final String result = sfs.get(name);
		if(result == null)
			throw new IllegalArgumentException("Missing mandatory parameter: " + name);

		return result;
	}
	
}
