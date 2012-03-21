package freenet.plugin.SoneBridge;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map.Entry;
import java.util.logging.Logger;

import org.scribe.builder.ServiceBuilder;
import org.scribe.builder.api.TwitterApi;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Token;
import org.scribe.model.Verb;
import org.scribe.model.Verifier;
import org.scribe.oauth.OAuthService;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import freenet.plugin.SoneBridge.datamodel.twitter.Tweet;
import freenet.plugin.SoneBridge.datamodel.twitter.TwitterSearchResult;
import freenet.plugin.SoneBridge.datamodel.twitter.TwitterURLData;
import freenet.pluginmanager.PluginTalker;
import freenet.support.SimpleFieldSet;

public class TwitterTracker implements Runnable {

	private OAuthService service;
	private String API_KEY = "N48x4x3BWDLCuAgGaSleiA";
	private String API_SECRET = "4UB5bMECn0nLwDmtkBytYWd2OYeOrdSSrhaNexwI";

	private Token requestToken;
	private Token accessToken;

	private boolean keep_running = true;
	private Configuration conf;
	private PluginTalker talker;
	
	private final static Logger LOGGER = Logger.getLogger(Main.class.getName());
	
	public TwitterTracker(Configuration conf, PluginTalker talker) {

		this.conf = conf;
		this.talker = talker;
		
		// create service object
		service = new ServiceBuilder().provider(TwitterApi.class).apiKey(API_KEY).apiSecret(API_SECRET).build();
	}

	public String getAuthURL()
	{
		requestToken = service.getRequestToken();
		return service.getAuthorizationUrl(requestToken);
	}

	public void createAccesToken(String oob) {
		Verifier verifier = new Verifier(oob);
		accessToken = service.getAccessToken(requestToken, verifier);
		conf.storeAccessToken(accessToken);
	}
	
	public void setAccessToken(Token token)
	{
		accessToken = token;
	}
	
	public boolean ready()
	{
		if (accessToken == null) return false;
		else return true;
	}
	
	@Override
	public void run() {

		System.out.println("Starting twitterTracker");

		while (keep_running) {
			if(ready())
			{
				
				for (Entry<String, String> pair : conf.getPatterns().entrySet())
				{
					String pattern = pair.getKey();
					String sone_id = pair.getValue();
					
					String url = null;
					String type = null;
					boolean prefix_username = false;
					
					// request current tweets from user
					if (pattern.startsWith("@"))
					{
						type = "USER";
						url = "https://api.twitter.com/1/statuses/user_timeline.json?" +
								"include_rts=true&" +
								"exclude_replies=true&" +
								"include_entities=true&" +
								"screen_name="
								+ pattern.replace("@", "") + "";
					}
					else //a regular search query for either a hashtag or something similar
					{
						type = "SEARCH";
						prefix_username = true;
						
						URI uri;
						try {
							uri = new URI(
							        "http", 
							        "search.twitter.com", 
							        "/search.json",
							        "q="+pattern+"&rpp=100&include_entities=true&result_type=recent",
							        null);

							url = uri.toASCIIString();

						} catch (URISyntaxException e) {
							e.printStackTrace();
						}
					}

					if (conf.sinces.containsKey(pattern) && conf.sinces.get(pattern) > 0) //don't retrieve all tweets, just what's new
					{
						url += "&since_id=" + conf.sinces.get(pattern);
					}
					
					
					LOGGER.fine("Accessing URL: " + url);
					OAuthRequest request2 = new OAuthRequest(Verb.GET, url);
					service.signRequest(accessToken, request2);
					Response response2 = request2.send();
		
					// parse the json
					Gson gson = new Gson();

					try
					{
						List<Tweet> tweets = null;
						if (type.equals("USER"))
						{
							tweets = gson.fromJson(response2.getBody(),	new TypeToken<List<Tweet>>() {	}.getType());
						}
						else
						{
							TwitterSearchResult sr = gson.fromJson(response2.getBody(),	new TypeToken<TwitterSearchResult>() {	}.getType());
							tweets = sr.results;
						}
						
						long since_id = processTweets(tweets, sone_id, !conf.sinces.containsKey(pattern), prefix_username);
						if (since_id > 0) conf.sinces.put(pattern, since_id);
						conf.save();
					}
					catch(JsonSyntaxException ex)
					{
						LOGGER.fine("No new tweets since the latest query or some error ocurred :(");
					}
				}
			
				try {
					Thread.sleep(1000 * 60);
				} catch (InterruptedException e) {
				}
			}
			else
			{
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
		}
	}

	/**
	 * Process a list of tweets (repeats them in Sone)
	 * @param tweets
	 * 			The list of tweets
	 * @param sone_id
	 * 			The sone id that should repeat the tweet
	 * @param pretend
	 * 			Whether to pretend sending it to Sone or not
	 * @param prefix_username
	 * 			The username with which each tweet should be prefixed
	 * @return
	 * 			The id of the most recent tweet processed.	
	 */
	
	private long processTweets(List<Tweet> tweets, String sone_id, boolean pretend, boolean prefix_username) {

		for (int i = tweets.size() - 1; i >= 0; i--) {
				Tweet tweet = tweets.get(i);
		
					//send tweet to sone
					if (!pretend)
					{
						SimpleFieldSet sfs = new SimpleFieldSet(true);
						sfs.putOverwrite("Message", "CreatePost");
						sfs.putOverwrite("Identifier", "TwitterTracker");
						sfs.putOverwrite("Sone", sone_id);
						
						String text = "";
						
						//prefix tweets with username if this is a search query
						if (!prefix_username)	text = tweet.text.trim().replace("\n", "");
						else					text = "@" + tweet.from_user + ": " + tweet.text.trim().replace("\n", "");
						
						//expand the urls if they are in the tweet
						if (tweet.entities != null && tweet.entities.urls != null)
						{
							for(TwitterURLData urlData : tweet.entities.urls)
							{
								text = text.replace(urlData.url, urlData.expanded_url);
							}
						}
						
						sfs.putOverwrite("Text", text);
						talker.send(sfs, null);
					}
		}
		
		if (tweets.size() > 0) {
			LOGGER.fine("Latest id found in tweets: " + tweets.get(0).id);
			return tweets.get(0).id;
		}
		else return 0;
	}

	public void stop() {
		keep_running = false;
	}


}
