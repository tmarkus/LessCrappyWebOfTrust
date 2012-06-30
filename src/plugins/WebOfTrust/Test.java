package plugins.WebOfTrust;

import java.sql.SQLException;
import java.util.List;

import plugins.WebOfTrust.datamodel.IVertex;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;

public class Test {

	public static void main(String[] args) throws ClassNotFoundException, SQLException
	{
		
		H2GraphFactory gf = new H2GraphFactory("/home/tmarkus/Freenet/LCWoT");
		H2Graph graph = gf.getGraph();
		
		System.out.println(graph.getEdgeCount());
		final String id = "zALLY9pbzMNicVn280HYqS2UkK0ZfX5LiTcln-cLrMU"; 
		
		ScoreComputer sc = new ScoreComputer(graph);
		long start = System.currentTimeMillis();
		sc.compute(id);
		System.out.println(System.currentTimeMillis() - start);
		
		
		start = System.currentTimeMillis();
		System.out.println();
		//List<Long> trusted = graph.getVerticesWithPropertyValueLargerThan(IVertex.TRUST+"."+id, -1);
		//System.out.println("trusted: " + trusted.size() + "in: " + (System.currentTimeMillis() - start) + "ms");
	}
}
