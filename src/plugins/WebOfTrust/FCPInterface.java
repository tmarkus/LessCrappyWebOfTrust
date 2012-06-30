package plugins.WebOfTrust;

import java.sql.SQLException;

import plugins.WebOfTrust.fcp.FCPBase;

import thomasmarkus.nl.freenet.graphdb.H2Graph;
import thomasmarkus.nl.freenet.graphdb.H2GraphFactory;
import freenet.pluginmanager.PluginNotFoundException;
import freenet.pluginmanager.PluginReplySender;
import freenet.support.SimpleFieldSet;
import freenet.support.api.Bucket;

public class FCPInterface {

	private H2GraphFactory gf;

	public FCPInterface(H2GraphFactory gf)
	{
		this.gf = gf;;
	}

	public void handle(PluginReplySender prs, SimpleFieldSet sfs, Bucket bucket, int accessType) throws SQLException, PluginNotFoundException {
		//System.out.println("Received the following message type: " + sfs.get("Message") + " with identifier: " + prs.getIdentifier());

		H2Graph graph = gf.getGraph();
		try {
			//find class with the name Message
			@SuppressWarnings("unchecked")
			Class<FCPBase> c = (Class<FCPBase>) Class.forName("plugins.WebOfTrust.fcp."+sfs.get("Message"));
			FCPBase handler = c.newInstance();
			
			long start = System.currentTimeMillis();
			
			//call its handle method with the input data
			final SimpleFieldSet reply = handler.handle(graph, sfs);

			System.out.println(sfs.get("Message") + " took: " + (System.currentTimeMillis()-start)  + "ms");

			
			//send the reply
			prs.send(reply);
		}
		catch(Exception e)
		{
			SimpleFieldSet reply = new SimpleFieldSet(true);
			System.out.println("Failed to match message: " + sfs.get("Message") + " with reply");
			e.printStackTrace();
			reply.putSingle("Message", "Error");
			reply.putSingle("Description", "Could not match message with reply");
			prs.send(reply);
		}
		finally
		{
			
			graph.close();
		}
	}
}
