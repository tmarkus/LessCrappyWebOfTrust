package freenet.plugin.web;

import java.io.IOException;
import java.net.URI;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public class CSSFileReaderToadlet extends FileReaderToadlet {

	public CSSFileReaderToadlet(HighLevelSimpleClient client, String filepath,String URLPath) { 
		super(client, filepath, URLPath);
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		writeReply(ctx, 200, "text/css", "css file", readFile());
	}

	
}
