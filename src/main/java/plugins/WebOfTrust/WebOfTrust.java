package plugins.WebOfTrust;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.config.Setting;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSetting;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.graphdb.factory.GraphDatabaseSetting.BaseOptionsSetting;
import org.neo4j.graphdb.index.IndexProvider;
import org.neo4j.index.lucene.LuceneIndexProvider;
import org.neo4j.kernel.ListIndexIterable;
import org.neo4j.kernel.impl.cache.CacheProvider;
import org.neo4j.kernel.impl.cache.SoftCacheProvider;

import plugins.WebOfTrust.datamodel.IContext;
import plugins.WebOfTrust.datamodel.IEdge;
import plugins.WebOfTrust.datamodel.IVertex;
import plugins.WebOfTrust.pages.IdenticonController;
import plugins.WebOfTrust.pages.IdentityManagement;
import plugins.WebOfTrust.pages.OverviewController;
import plugins.WebOfTrust.pages.ShowIdentityController;
import freenet.client.FetchContext;
import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContainer;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.node.RequestStarter;
import freenet.plugin.web.FileReaderToadlet;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginFCP;
import freenet.pluginmanager.FredPluginHTTP;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.FredPluginVersioned;
import freenet.pluginmanager.PluginHTTPException;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;

public class WebOfTrust implements FredPlugin, FredPluginThreadless, FredPluginFCP, FredPluginL10n, FredPluginVersioned, FredPluginHTTP{

	private static final String db_path = "LCWoT"; 
	public static final String basePath = "/WebOfTrust";
	public static final int FETCH_MAX_FILE_SIZE = 2000000; 
	public static final String namespace = "WebOfTrust";
	public static final int COMPATIBLE_VERSION = 11;
	public static final boolean allowFullAccessOnly = true; 
	
	private PluginRespirator pr;
	private WebInterface webInterface;
	private final List<FileReaderToadlet> toadlets = new ArrayList<FileReaderToadlet>();
	private HighLevelSimpleClient hl;
	
	private GraphDatabaseService db;
	
	private RequestScheduler rs;

	public volatile boolean isRunning = true;
	private FCPInterface fpi; 
	private final static Logger LOGGER = Logger.getLogger(WebOfTrust.class.getName());

	public GraphDatabaseService getDB()
	{
		return this.db;
	}
	
	public HighLevelSimpleClient getHL()
	{
		return this.hl;
	}

	public PluginRespirator getPR()
	{
		return this.pr;
	}

	@Override
	public void runPlugin(PluginRespirator pr) {

		this.pr = pr;
		this.hl = pr.getNode().clientCore.makeClient(RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS, false, true);
		
		FetchContext fc = hl.getFetchContext();
		fc.followRedirects = true;

		//init neo4j embedded db
		GraphDatabaseService db = setupNeo4j();
		
		//setup the web ui for neo4j
		//TODO: include the jars required (system/lib ?)
		
		//manually calculate all trust values on bootup
		ScoreComputer sc = new ScoreComputer(db);
		sc.compute();
		
		//setup fcp plugin handler
		this.fpi = new FCPInterface(db);

		//setup requestscheduler
		this.rs = new RequestScheduler(this, db, hl);
		new Thread(rs). start ( );		

		//setup web interface
		setupWebinterface();

		LOGGER.info("Completed initialization.");
	}

	protected GraphDatabaseService setupNeo4j() {
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
		
		
		//db = gdbf.newEmbeddedDatabase(db_path);
		
		
		db = gdbf.newEmbeddedDatabaseBuilder( db_path )
		.setConfig( GraphDatabaseSettings.node_keys_indexable, IVertex.ID+","+IVertex.OWN_IDENTITY+","+IContext.NAME )
//	    .setConfig( GraphDatabaseSettings.relationship_keys_indexable, IEdge.SCORE )
	    .setConfig( GraphDatabaseSettings.node_auto_indexing, GraphDatabaseSetting.TRUE )
	    .setConfig( GraphDatabaseSettings.relationship_auto_indexing, GraphDatabaseSetting.TRUE )
	    .newGraphDatabase();
		
		

		/*
		srv = new WrappingNeoServerBootstrapper( (InternalAbstractGraphDatabase) db );
		srv.start();
		*/
		
		// The server is now running
		
		return db;
	}

	private void setupWebinterface()
	{
		LOGGER.info("Setting up webinterface");
		PluginContext pluginContext = new PluginContext(pr);
		this.webInterface = new WebInterface(pluginContext);

		//setup the manage page
		FileReaderToadlet oc = new OverviewController(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/manage.html",
				basePath, db);
		toadlets.add(oc);

		pr.getPageMaker().addNavigationCategory(basePath + "/","WebOfTrust.menuName.name", "WebOfTrust.menuName.tooltip", this);
		ToadletContainer tc = pr.getToadletContainer();
		tc.register(oc, "WebOfTrust.menuName.name", basePath + "/", true, "WebOfTrust.mainPage", "WebOfTrust.mainPage.tooltip", WebOfTrust.allowFullAccessOnly, oc);
		tc.register(oc, null, basePath + "/", true, WebOfTrust.allowFullAccessOnly);
		
		//Identicons
		toadlets.add(new IdenticonController(this,
				pr.getHLSimpleClient(),
				"",
				basePath+"/GetIdenticon"));
		
		toadlets.add(new IdentityManagement(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/restore.html",
				basePath+"/restore", db));

		toadlets.add(new ShowIdentityController(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/showIdentity.html",
				basePath+"/ShowIdentity", db));

		for(Toadlet toadlet : toadlets)
		{
			webInterface.registerInvisible(toadlet);	
		}
	}


	@Override
	public void terminate() {
		LOGGER.info("Terminating plugin");

		//tell everybody else that we are no longer running
		isRunning = false;
		
		
		//remove Navigation category
		pr.getPageMaker().removeNavigationCategory("WebOfTrust.menuName.name");
		
		//deregister toadlets
		ToadletContainer toadletContainer = pr.getToadletContainer();
		for (FileReaderToadlet pageToadlet : toadlets) {
				toadletContainer.unregister(pageToadlet);
				pageToadlet.terminate();
		}

		if (webInterface != null) webInterface.kill();

		//interrupt the request scheduler
		rs.interrupt();
		
		if( db != null ) {
			System.out.println("Killing the graph database");
			
			//srv.stop();
			
			//shutdown the graph database
			db.shutdown();

			System.out.println("done");
		}
	}


	public RequestScheduler getRequestScheduler()
	{
		return this.rs;
	}

	@Override
	public String getString(String key) {
		return "WoT";
	}



	@Override
	public void setLanguage(LANGUAGE arg0) {

	}

	@Override
	public String getVersion() {
		return "Hopefully recent enough.";
	}


	@Override
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		return "<html><body><head><title>Forward page...</title></head>" +
				"<a href=\""+basePath+"/\">Click here to visit the overview page.</a>" +
				"</body></html>";
	}


	@Override
	public String handleHTTPPost(HTTPRequest arg0) throws PluginHTTPException {
		return null;
	}

	@Override
	public void handle(PluginReplySender prs, SimpleFieldSet sfs, Bucket bucket, int accessType) {
		try {
			fpi.handle(prs, sfs, bucket, accessType);
		}
		catch (PluginNotFoundException e) {
			e.printStackTrace();
		}
	}
}
