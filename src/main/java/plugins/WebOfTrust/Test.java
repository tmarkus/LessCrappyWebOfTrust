package plugins.WebOfTrust;

import java.util.ArrayList;

import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.index.IndexProvider;
import org.neo4j.index.lucene.LuceneIndexProvider;
import org.neo4j.kernel.ListIndexIterable;
import org.neo4j.kernel.impl.cache.CacheProvider;
import org.neo4j.kernel.impl.cache.SoftCacheProvider;

public class Test {

	public static void main(String[] args) throws ClassNotFoundException
	{
		
		
		//the cache providers
		ArrayList<CacheProvider> cacheList = new ArrayList<CacheProvider>();
		cacheList.add( new SoftCacheProvider() );
 
		//the index providers
		IndexProvider lucene = new LuceneIndexProvider();
		ArrayList<IndexProvider> provs = new ArrayList<IndexProvider>();
		provs.add( lucene );
		ListIndexIterable providers = new ListIndexIterable();
		providers.setIndexProviders( provs );
 
		//the database setup
		GraphDatabaseFactory gdbf = new GraphDatabaseFactory();
		gdbf.setIndexProviders( providers );
		gdbf.setCacheProviders( cacheList );
		
		
		//GraphDatabaseService db = gdbf.newEmbeddedDatabase("db");
		
		/*
		db = gdbf.newEmbeddedDatabaseBuilder( db_path )
		.setConfig( GraphDatabaseSettings.node_keys_indexable, IVertex.ID+","+IVertex.OWN_IDENTITY )
	    .setConfig( GraphDatabaseSettings.relationship_keys_indexable, "relProp1,relProp2" )
	    .setConfig( GraphDatabaseSettings.node_auto_indexing, GraphDatabaseSetting.TRUE )
	    .setConfig( GraphDatabaseSettings.relationship_auto_indexing, GraphDatabaseSetting.TRUE ).newGraphDatabase();

		*/

		/*
		WrappingNeoServerBootstrapper srv = new WrappingNeoServerBootstrapper( (InternalAbstractGraphDatabase) db );
		srv.start();
		srv.stop();
		*/

		
		
		//List<Long> trusted = graph.getVerticesWithPropertyValueLargerThan(IVertex.TRUST+"."+id, -1);
		//System.out.println("trusted: " + trusted.size() + "in: " + (System.currentTimeMillis() - start) + "ms");
	}
}
