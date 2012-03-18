package freenet.plugin.SoneBridge.controller;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.Map.Entry;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.plugin.freenetSoneBridge.Configuration;
import freenet.support.api.HTTPRequest;

public class ManagePageController extends freenet.plugin.web.HTMLFileReaderToadlet {

	private Configuration conf;
	private Map<String, String> localSones; 
	
	public ManagePageController(HighLevelSimpleClient client, String filepath, String URLPath, Configuration conf, Map<String, String> localSones) {
		super(client, filepath, URLPath);
		this.conf = conf;
		this.localSones = localSones;
	}

	public void setConf(Configuration conf)
	{
		this.conf = conf;
	}
	
	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		try
		{
			//existing patterns
		    Document doc = Jsoup.parse(readFile());
			
			Element existing = doc.select("#existing_patterns").first();
			for (String pattern : conf.getPatterns().keySet())
			{
				Element form = doc.createElement("form");
				form.attr("method", "post");
				form.attr("action", "/SoneBridge/manage");
				Element fieldset = doc.createElement("fieldset");
				fieldset.appendChild(doc.createElement("legend").text("Existing pattern"));
				fieldset.appendChild(doc.createElement("p").text(pattern + " -> " + lookupSoneNiceName(conf.getPatterns().get(pattern)) + "(" + conf.getPatterns().get(pattern) +")"));
				fieldset.appendChild(doc.createElement("input").attr("type", "submit").attr("value", "Remove"));
				
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "action").attr("value", "delete"));
				fieldset.appendChild(doc.createElement("input").attr("type", "hidden").attr("name", "pattern").attr("value", pattern));
				
				form.appendChild(fieldset);
				existing.appendChild(form);
			}
			
			//new patterns
			Element new_pattern_identity = doc.select("#new_pattern_identity").first();
			for(String key : localSones.keySet())
			{
				Element option = doc.createElement("option");
				option.attr("value", localSones.get(key));
				option.text(key);
				new_pattern_identity.appendChild(option);
			}

			writeReply(ctx, 200, "text/html", "content", doc.html());
		}
		catch(Exception ex)
		{
			ex.printStackTrace();
		}
	}
	
	public String lookupSoneNiceName(String id)
	{
		for(Entry<String, String> pair : localSones.entrySet())
		{
			if (pair.getValue().equals(id)) return pair.getKey();
		}
		return "UNKNOWN";
	}
	
	
	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
		
		String action = request.getPartAsStringFailsafe("action", 20000);
		
		if (action.equals("delete"))
		{
			String pattern = request.getPartAsStringFailsafe("pattern", 20000);
			conf.clearPattern(pattern);
			conf.save();
		}
		else
		{
			String identity = request.getPartAsStringFailsafe("identity", 20000);
			String pattern = request.getPartAsStringFailsafe("pattern", 20000);

			conf.addPattern(pattern, identity);
			conf.save();
		}
		
		handleMethodGET(uri, request, ctx);
	}

	public void setLocalSones(Map<String, String> localSones) {
		this.localSones = localSones;
	}

	
	
}
