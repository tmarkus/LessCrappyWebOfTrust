package freenet.plugin.web;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;


import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public abstract class HTMLFileReaderToadlet extends FileReaderToadlet {

	private Map<String, String> context = new HashMap<String, String>();
	
	public HTMLFileReaderToadlet(HighLevelSimpleClient client, String filepath, String URLPath) {
		super(client, filepath, URLPath);
	}

	public void setContext(Map<String, String> context)
	{
		this.context = context;
	}
	
	public void clearContext()
	{
		this.context.clear();
	}
	
	@Override
	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		
		
		String output = readFile();
		for(String key : context.keySet())
		{
			output = output.replace(key, context.get(key));
		}
		
		writeReply(ctx, 200, "text/html", "html file", output);
	}

	@Override
	public boolean isEnabled(ToadletContext arg0) {
		return true;
	}

}
