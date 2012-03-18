package freenet.plugin.freenetSoneBridge;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.scribe.model.Token;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class Configuration {

	public static final String CONFIGURATION_FILENAME = "SoneBridge.json";
	public Map<String, String> accessToken = new HashMap<String, String>();
	public Map<String, String> patternMap = new HashMap<String, String>();
	public Map<String, Long> sinces = new HashMap<String, Long>();

	
	public void save()
	{
		Gson gson = new GsonBuilder().setPrettyPrinting().create();
		String json = gson.toJson(this);
		
		try {
			BufferedWriter out = new BufferedWriter(new FileWriter(CONFIGURATION_FILENAME,false));
			out.write(json);
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public boolean hasAccessToken()
	{
		if (accessToken != null && accessToken.size() > 0) return true;
		else return false;
	}
	
	public Token getAccessToken()
	{
		return new Token(accessToken.get("token"), accessToken.get("secret"));
	}

	public void storeAccessToken(Token token)
	{
		this.accessToken.put("token", token.getToken());
		this.accessToken.put("secret", token.getSecret());
	}
	
	public void addPattern(String pattern, String identity)
	{
		if (!"pattern".equals("") && !identity.equals(""))
		{
			patternMap.put(pattern, identity);	
		}
	}
	
	public void clearPattern(String pattern)
	{
		patternMap.remove(pattern);
	}

	public Map<String, String> getPatterns()
	{
		return patternMap;
	}
}
