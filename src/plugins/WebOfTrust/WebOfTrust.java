package plugins.WebOfTrust;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import plugins.WebOfTrust.controller.OverviewController;
import plugins.WebOfTrust.controller.IdentityManagement;
import plugins.WebOfTrust.controller.ShowIdentityController;
import thomasmarkus.nl.freenet.graphdb.H2Graph;
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
import freenet.pluginmanager.PluginReplySender;
import freenet.pluginmanager.PluginRespirator;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;

public class WebOfTrust implements FredPlugin, FredPluginThreadless, FredPluginFCP, FredPluginL10n, FredPluginVersioned, FredPluginHTTP{

	private static final String db_path = "LCWoT"; 
	private static final String basePath = "/WebOfTrust";
	public static final int FETCH_MAX_FILE_SIZE = 2000000; 
	public static final String namespace = "WebOfTrust";
	
	private PluginRespirator pr;
	private WebInterface webInterface;
	private final List<FileReaderToadlet> toadlets = new ArrayList<FileReaderToadlet>();
	private HighLevelSimpleClient hl;
	private H2Graph graph;
	private RequestScheduler rs;

	public boolean isRunning = true;
	private FCPInterface fpi; 
	private final static Logger LOGGER = Logger.getLogger(WebOfTrust.class.getName());



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
		this.hl = pr.getNode().clientCore.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS, false, true);
		FetchContext fc = hl.getFetchContext();
		fc.followRedirects = true;

		try {
			//init graph
			this.graph = new H2Graph(db_path);	

			//setup fcp plugin handler
			this.fpi = new FCPInterface(graph);

			//setup requestscheduler
			this.rs = new RequestScheduler(this, graph, hl);
			new Thread(rs). start ( );
		} 
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}		
		//setup web interface
		setupWebinterface();

		LOGGER.info("Completed initialization.");
	}

	private void setupWebinterface()
	{
		LOGGER.info("Setting up webinterface");
		PluginContext pluginContext = new PluginContext(pr);
		this.webInterface = new WebInterface(pluginContext);

		//setup the manage page
		toadlets.add(new OverviewController(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/manage.html",
				basePath+"/manage", graph));

		toadlets.add(new IdentityManagement(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/restore.html",
				basePath+"/restore", graph));

		toadlets.add(new ShowIdentityController(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/showIdentity.html",
				basePath+"/ShowIdentity", graph));

		for(Toadlet toadlet : toadlets)
		{
			webInterface.registerInvisible(toadlet);	
		}
	}


	@Override
	public void terminate() {
		LOGGER.info("Terminating plugin");

		ToadletContainer toadletContainer = pr.getToadletContainer();
		for (FileReaderToadlet pageToadlet : toadlets) {
			toadletContainer.unregister(pageToadlet);
		}
		//toadletContainer.getPageMaker().removeNavigationCategory("SoneBridge");

		if (webInterface != null) webInterface.kill();

		//TODO: kill all requests which are still running

		//kill the database
		if( graph != null ) {
			System.out.println("Killing the graph database");
			try {
				graph.shutdown();
			} catch (SQLException e) {
				e.printStackTrace();
			}
			System.out.println("done");
		}

		//tell everybody else that we are no longer running
		isRunning = false;
	}


	public RequestScheduler getRequestScheduler()
	{
		return this.rs;
	}

	@Override
	public String getString(String key) {
		return "SoneBridge";
	}



	@Override
	public void setLanguage(LANGUAGE arg0) {

	}


	@Override
	public String getVersion() {
		return "2012-04-13";
	}


	@Override
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		return "<html><body><head><title>Forward page...</title></head>" +
				"<a href=\""+basePath+"/manage\">Click here to visit the overview page.</a>" +
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
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
