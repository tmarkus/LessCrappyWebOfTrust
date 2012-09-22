package freenet.plugin.web;

import java.io.IOException;
import java.net.URI;

import org.neo4j.graphdb.GraphDatabaseService;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public class JavascriptFileReaderToadlet extends FileReaderToadlet {

	public JavascriptFileReaderToadlet(HighLevelSimpleClient client, GraphDatabaseService db, String filepath, String URLPath) {
		super(db, client, filepath, URLPath);
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		writeReply(ctx, 200, "text/javascript", "javascript file", readFile());
	}

	public void terminate()
	{
		
	}

	@Override
	public boolean isEnabled(ToadletContext arg0) {
		return false;
	}
}
