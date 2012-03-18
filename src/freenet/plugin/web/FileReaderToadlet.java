package freenet.plugin.web;


import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public class FileReaderToadlet extends Toadlet {

	String path;
	String filePath;
	
	public FileReaderToadlet(HighLevelSimpleClient client, String filepath, String URLPath) {
		super(client);
		this.path = URLPath;
		this.filePath = filepath;
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
	public String path() {
		return path;
	}
	
	
}
