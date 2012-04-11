package plugins.WebOfTrust;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import plugins.WebOfTrust.controller.ManagePageController;
import plugins.WebOfTrust.controller.RestoreIdentity;
import plugins.WebOfTrust.controller.ShowIdentityController;


import thomasmarkus.nl.freenet.graphdb.H2Graph;

import freenet.client.FetchContext;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientGetter;
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
import freenet.pluginmanager.PluginTalker;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;

public class WebOfTrust implements FredPlugin, FredPluginThreadless, FredPluginFCP, FredPluginL10n, FredPluginVersioned, FredPluginHTTP{

	private static final String db_path = "LCWoT"; 
	private static final String basePath = "/WebOfTrust";
	
	private PluginRespirator pr;
	private PluginTalker talker;
	private WebInterface webInterface;
	private final List<FileReaderToadlet> toadlets = new ArrayList<FileReaderToadlet>();
	private final Map<String, String> localSones = new HashMap<String, String>();
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
		LOGGER.info("Setting up webinterface");
		PluginContext pluginContext = new PluginContext(pr);
		this.webInterface = new WebInterface(pluginContext);

		//setup the manage page
		ManagePageController managePage = new ManagePageController(this,
																	pr.getHLSimpleClient(),
																	"/staticfiles/html/manage.html",
																	basePath+"/manage", graph);

		RestoreIdentity restoreIdentityPage = new RestoreIdentity(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/restore.html",
				basePath+"/restore", graph);

		ShowIdentityController showIdentityPage = new ShowIdentityController(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/showIdentity.html",
				basePath+"/ShowIdentity", graph);
		
		webInterface.registerInvisible(managePage);
		webInterface.registerInvisible(restoreIdentityPage);
		webInterface.registerInvisible(showIdentityPage);
		
		toadlets.add(managePage);
		toadlets.add(restoreIdentityPage);
		toadlets.add(showIdentityPage);

		LOGGER.info("Completed initialization.");
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

		//kill all requests which are still running
		try
		{
			for(ClientGetter cg : rs.getInFlight()) {
				//cg.cancel(null, null);
			}
		}
		catch(Exception e)
		{
			System.out.println("Something went wrong when trying to cancel a request... (probably ClientGetter.cancel() requires more information.");
		}
		
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
		return "2012-04-06";
	}


	@Override
	public String handleHTTPGet(HTTPRequest request) throws PluginHTTPException {
		return "<html><body><head><title>Forward page...</title></head>" +
				"<a href=\""+basePath+"/setup\">Click here to visit the setup page.</a>" +
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
