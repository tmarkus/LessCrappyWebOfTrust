package plugins.WebOfTrust;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import plugins.WebOfTrust.pages.IdenticonController;
import plugins.WebOfTrust.pages.IdentityManagement;
import plugins.WebOfTrust.pages.OverviewController;
import plugins.WebOfTrust.pages.ShowIdentityController;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;
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
	private static final String basePath = "/WebOfTrust";
	public static final int FETCH_MAX_FILE_SIZE = 2000000; 
	public static final String namespace = "WebOfTrust";
	public static final int COMPATIBLE_VERSION = 11;
	
	private PluginRespirator pr;
	private WebInterface webInterface;
	private final List<FileReaderToadlet> toadlets = new ArrayList<FileReaderToadlet>();
	private HighLevelSimpleClient hl;
	private H2GraphFactory gf;
	private RequestScheduler rs;

	public volatile boolean isRunning = true;
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
		this.hl = pr.getNode().clientCore.makeClient(RequestStarter.IMMEDIATE_SPLITFILE_PRIORITY_CLASS, false, true);
		
		FetchContext fc = hl.getFetchContext();
		fc.followRedirects = true;

		try {
			//init graph
			this.gf = new H2GraphFactory(db_path, RequestScheduler.MAX_REQUESTS*5);	

			//setup fcp plugin handler
			this.fpi = new FCPInterface(gf);

			//setup requestscheduler
			this.rs = new RequestScheduler(this, gf, hl);
			new Thread(rs). start ( );
		} 
		catch (ClassNotFoundException e) {
			e.printStackTrace();
		} catch (SQLException e) {
			e.printStackTrace();
		}		
		//setup web interface
		try {
			setupWebinterface();
		} catch (SQLException e) {
			e.printStackTrace();
		}

		LOGGER.info("Completed initialization.");
	}

	private void setupWebinterface() throws SQLException
	{
		LOGGER.info("Setting up webinterface");
		PluginContext pluginContext = new PluginContext(pr);
		this.webInterface = new WebInterface(pluginContext);

		//setup the manage page
		toadlets.add(new OverviewController(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/manage.html",
				basePath+"/", gf));

		toadlets.add(new OverviewController(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/manage.html",
				basePath, gf));

		//Identicons
		toadlets.add(new IdenticonController(this,
				pr.getHLSimpleClient(),
				"",
				basePath+"/GetIdenticon"));
		
		toadlets.add(new IdentityManagement(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/restore.html",
				basePath+"/restore", gf));

		toadlets.add(new ShowIdentityController(this,
				pr.getHLSimpleClient(),
				"/staticfiles/html/showIdentity.html",
				basePath+"/ShowIdentity", gf));

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
		
		ToadletContainer toadletContainer = pr.getToadletContainer();
		for (FileReaderToadlet pageToadlet : toadlets) {
			try {
				toadletContainer.unregister(pageToadlet);
				pageToadlet.terminate();
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}

		if (webInterface != null) webInterface.kill();

		//kill the database
		try
		{
			if( gf != null ) {
				System.out.println("Killing the graph database");
				gf.stop();
				System.out.println("done");
			}
		}
		catch(SQLException e)
		{
			e.printStackTrace();
		}
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
		} catch (SQLException e) {
			e.printStackTrace();
		} catch (PluginNotFoundException e) {
			e.printStackTrace();
		}
	}
}
