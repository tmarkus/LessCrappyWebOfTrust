package plugins.WebOfTrust;

import java.sql.SQLException;
import java.util.List;

import thomasmarkus.nl.freenet.graphdb.H2Graph;

public class Test {

	public static void main(String[] args) throws ClassNotFoundException, SQLException
	{
		H2Graph graph = new H2Graph("/home/tmarkus/Freenet/LCWoT");
		
		System.out.println(graph.getEdgeCount());
		
		ScoreComputer sc = new ScoreComputer(graph);
		long start = System.currentTimeMillis();
		sc.compute("zALLY9pbzMNicVn280HYqS2UkK0ZfX5LiTcln-cLrMU");
		System.out.println(System.currentTimeMillis() - start);
		
		
		start = System.currentTimeMillis();
		System.out.println();
		List<Long> trusted = graph.getVerticesWithPropertyValueLargerThan("score", -1);
		System.out.println("trusted: " + trusted.size() + "in: " + (System.currentTimeMillis() - start) + "ms");
	}
}
