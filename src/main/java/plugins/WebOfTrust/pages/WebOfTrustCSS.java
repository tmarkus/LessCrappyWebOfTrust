package plugins.WebOfTrust.pages;

import java.io.IOException;
import java.net.URI;

import plugins.WebOfTrust.WebOfTrust;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.HTTPRequest;

public class WebOfTrustCSS extends Toadlet implements LinkEnabledCallback {
	protected String path;
	
	public WebOfTrustCSS(HighLevelSimpleClient client, String URLPath) {
		super(client);
		this.path = URLPath;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		if(WebOfTrust.allowFullAccessOnly && !ctx.isAllowedFullAccess()) {
			writeHTMLReply(ctx, 403, "forbidden", "Your host is not allowed to access this page.");
			return;
		}
		StringBuilder css = new StringBuilder();
		css.append("div#content {\n");
		css.append("  margin: auto;\n");
		css.append("  width: 70em;\n");
		css.append("}\n");
		css.append("#content ul {\n");
		css.append("  list-style-type: disc;\n");
		css.append("}");
		css.append("#content td {\n");
		css.append("  padding: 0;\n");
		css.append("}");
		try {
			writeReply(ctx, 200, "text/css", "OK", css.toString());
		} catch (IOException e) {
			// uh? javadocs ftw :)
		} catch (ToadletContextClosedException e) {
			// client aborted connection?
			// anyway, ignore.
		}
	}
	
	
	@Override
	public boolean isEnabled(ToadletContext ctx) {
		return true;
	}

	@Override
	public String path() {
		return path;
	}

}
