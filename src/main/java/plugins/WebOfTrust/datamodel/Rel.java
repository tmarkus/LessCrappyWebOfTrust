package plugins.WebOfTrust.datamodel;

import org.neo4j.graphdb.RelationshipType;

public enum Rel implements RelationshipType
	{
	    TRUSTS,
	    HAS_CONTEXT
	}
	

