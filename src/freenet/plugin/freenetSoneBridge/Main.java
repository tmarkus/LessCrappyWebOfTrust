package freenet.plugin.freenetSoneBridge;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


import com.google.gson.Gson;

import freenet.clients.http.ToadletContainer;
import freenet.l10n.BaseL10n.LANGUAGE;
import freenet.node.FSParseException;
import freenet.plugin.SoneBridge.controller.ManagePageController;
import freenet.plugin.SoneBridge.controller.SetupPageController;
import freenet.plugin.web.FileReaderToadlet;
import freenet.pluginmanager.FredPlugin;
import freenet.pluginmanager.FredPluginL10n;
import freenet.pluginmanager.FredPluginTalker;
import freenet.pluginmanager.FredPluginThreadless;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginRespirator;
import freenet.pluginmanager.PluginTalker;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;
import freenet.support.plugins.helpers1.PluginContext;
import freenet.support.plugins.helpers1.WebInterface;

public class Main implements FredPlugin, FredPluginThreadless, FredPluginTalker, FredPluginL10n {

	private PluginRespirator pr;
	private PluginTalker talker;
	private TwitterTracker tracker;
	private String basePath = "/SoneBridge";
	private WebInterface webInterface;
	Configuration conf;
	private List<FileReaderToadlet> toadlets = new ArrayList<FileReaderToadlet>();
	
	private Map<String, String> localSones = new HashMap<String, String>();
	
	private final static Logger LOGGER = Logger.getLogger(Main.class.getName());
	
	@Override
	public void onReply(String longid, String shortid, SimpleFieldSet sfs, Bucket arg3) {
	
		try
		{
			LOGGER.fine("Received message: " + sfs.get("Message"));
		
			if (sfs.get("Message").equals("ListLocalSones"))
			{
				for(int i=0; i < sfs.getInt("LocalSones.Count"); i++)
				{
					LOGGER.fine(sfs.get("LocalSones."+i+".NiceName"));
					LOGGER.fine((sfs.get("LocalSones."+i+".ID")));
				
					localSones.put(sfs.get("LocalSones."+i+".NiceName"), sfs.get("LocalSones."+i+".ID"));
				}
			}
		}
		catch(FSParseException ex)
		{
			ex.printStackTrace();
		}
	}
	

	@Override
	public void runPlugin(PluginRespirator pr) {

		this.pr = pr;

		//setup plugintalker
		try {
			this.talker = pr.getPluginTalker(this, "net.pterodactylus.sone.main.SonePlugin", "sone");
		} catch (PluginNotFoundException e) {
			e.printStackTrace();
			return;
		}

		//load configuration
		try {
			BufferedReader in = new BufferedReader(new FileReader(Configuration.CONFIGURATION_FILENAME));
			Gson gson = new Gson();
			conf = gson.fromJson(in, Configuration.class);
		}
		catch(FileNotFoundException e) //new config
		{
			System.out.println("Configuration file " + Configuration.CONFIGURATION_FILENAME + " not found. Starting from scratch.");
			conf = new Configuration();
			conf.save();
		}
		
		//setup the basic tracker
		tracker = new TwitterTracker(conf, talker);

		
		//setup web interface
		LOGGER.info("Setting up webinterface");
		PluginContext pluginContext = new PluginContext(pr);
		this.webInterface = new WebInterface(pluginContext);

		//setup setupPage
		SetupPageController setupPage = new SetupPageController(pr.getHLSimpleClient(), "/staticfiles/html/setup.html", basePath+"/setup");

		if (!conf.hasAccessToken())
		{
			setupPage.setURL(tracker.getAuthURL());
			setupPage.configured(false);
		}
		else
		{
			tracker.setAccessToken(conf.getAccessToken());
			setupPage.configured(true);
		}
		
		//setup the manage page
		ManagePageController managePage = new ManagePageController(pr.getHLSimpleClient(), "/staticfiles/html/manage.html", basePath+"/manage", conf);
		managePage.setLocalSones(localSones);
		
		setupPage.setTracker(tracker);
		webInterface.registerInvisible(setupPage);
		webInterface.registerInvisible(managePage);
		
		//webInterface.addNavigationCategory("/SoneBridge/setup", "SoneBridge", "Bridging Sone to other social platforms", this);
		
		toadlets.add(setupPage);
		toadlets.add(managePage);
		
		//start the tracker;
		LOGGER.info("Starting tracker.");
		new Thread(tracker).start();    
		
		//get a list of sone ids
		SimpleFieldSet sfs = new SimpleFieldSet(true);
		sfs.putOverwrite("Message", "GetLocalSones");
		sfs.putOverwrite("Identifier", "startup");
		talker.send(sfs, null);

		LOGGER.info("Completed initialization.");
	}

	@Override
	public void terminate() {
		LOGGER.info("Terminating plugin");
		
		if (conf != null) conf.save(); 
		if (tracker != null) tracker.stop();
	
		ToadletContainer toadletContainer = pr.getToadletContainer();
		for (FileReaderToadlet pageToadlet : toadlets) {
			toadletContainer.unregister(pageToadlet);
		}
		//toadletContainer.getPageMaker().removeNavigationCategory("SoneBridge");
		
		if (webInterface != null) webInterface.kill();
	}


	@Override
	public String getString(String key) {
		return "SoneBridge";
	}


	@Override
	public void setLanguage(LANGUAGE arg0) {
		
	}

}
