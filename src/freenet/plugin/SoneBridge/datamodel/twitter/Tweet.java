package freenet.plugin.SoneBridge.datamodel.twitter;

import java.util.Map;

public class Tweet {

	public String created_at;
	public String text;
	public Map<String, String> user;
	public long id;
	public String from_user;
	public TwitterEntities entities;
}
