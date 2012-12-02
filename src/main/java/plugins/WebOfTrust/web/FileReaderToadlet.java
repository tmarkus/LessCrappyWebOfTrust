package plugins.WebOfTrust.web;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.index.ReadableIndex;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public abstract class FileReaderToadlet extends Toadlet implements LinkEnabledCallback {

	protected String path;
	protected String filePath;
	protected GraphDatabaseService db;
	protected ReadableIndex<Node> nodeIndex;
	
	public FileReaderToadlet(GraphDatabaseService db, HighLevelSimpleClient client, String filepath, String URLPath) {
		super(client);
		this.path = URLPath;
		this.filePath = filepath;
		this.db = db;
	
		nodeIndex = db.index().getNodeAutoIndexer().getAutoIndex();
	}

	protected String readFile() throws IOException
	{
		InputStream is = this.getClass().getResourceAsStream(filePath);
	    BufferedReader br = new BufferedReader(new InputStreamReader(is));
	
		  String output = "";
		  String line = "";
		  while(line != null)
		  {
			  line = br.readLine();
			  if (line != null) output += line + "\n";
		  }
		  return output;
	} 

	
	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
			writeHTMLReply(ctx, 200, "pageContent", readFile());
	}
	
	@Override
	protected void writeReply(ToadletContext ctx, int code, String mime, String title, String output)
	{
		if (ctx.isAllowedFullAccess())
		{

			try {
				super.writeReply(ctx, code, mime, title, output);
			} catch (ToadletContextClosedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else
		{
			try {
				super.writeReply(ctx, 403, "", "", "");
			} catch (ToadletContextClosedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	protected void writeHTMLReply(ToadletContext ctx, int code, String desc, String output)
	{
		if (ctx.isAllowedFullAccess())
		{
			try {
				super.writeHTMLReply(ctx, code, desc, output);
			} catch (ToadletContextClosedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		else
		{
			try {
				super.writeHTMLReply(ctx, 403, "", "");
			} catch (ToadletContextClosedException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	
	
	@Override
	public String path() {
		return path;
	}

	public abstract void terminate();
}
