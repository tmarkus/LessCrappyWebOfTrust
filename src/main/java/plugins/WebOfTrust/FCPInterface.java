package plugins.WebOfTrust;

import java.lang.reflect.Constructor;

import org.neo4j.graphdb.GraphDatabaseService;

import plugins.WebOfTrust.fcp.FCPBase;

import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class FCPInterface {

	private GraphDatabaseService db;
	
	public FCPInterface(GraphDatabaseService db)
	{
		this.db = db;
	}

	public void handle(PluginReplySender prs, SimpleFieldSet sfs, Bucket bucket, int accessType) throws PluginNotFoundException {
		//System.out.println("Received the following message type: " + sfs.get("Message") + " with identifier: " + prs.getIdentifier());

		try {
			//find class with the name Message
			@SuppressWarnings("unchecked")
			Class<FCPBase> c = (Class<FCPBase>) Class.forName("plugins.WebOfTrust.fcp."+sfs.get("Message"));
			
			Constructor<? extends FCPBase> ctor = c.getConstructor(GraphDatabaseService.class);
			FCPBase handler = ctor.newInstance(db);
			
			long start = System.currentTimeMillis();
			
			//call its handle method with the input data
			final SimpleFieldSet reply = handler.handle(sfs);

			if (WebOfTrust.DEBUG) System.out.println(sfs.get("Message") + " took: " + (System.currentTimeMillis()-start)  + "ms");
			
			//send the reply
			prs.send(reply);
		}
		catch(Exception e)
		{
			SimpleFieldSet reply = new SimpleFieldSet(true);
			System.out.println("Failed to match message: " + sfs.get("Message") + " with reply");
			e.printStackTrace();
			reply.putSingle("Message", "Error");
	        reply.putSingle("OriginalMessage", sfs.get("Message"));
			reply.putSingle("Description", "Could not match message with reply because of an exception: " + e.getMessage());
			prs.send(reply);
		}
	}
}
