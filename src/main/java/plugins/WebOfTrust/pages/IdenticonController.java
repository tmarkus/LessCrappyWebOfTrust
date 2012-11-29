package plugins.WebOfTrust.pages;

import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

import javax.imageio.ImageIO;

import plugins.WebOfTrust.identicon.Identicon;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.LinkEnabledCallback;
import freenet.clients.http.Toadlet;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.Base64;
import freenet.support.IllegalBase64Exception;
import freenet.support.api.HTTPRequest;
import freenet.support.io.Closer;

public class IdenticonController extends Toadlet implements LinkEnabledCallback {
	private final String path;
	
	public IdenticonController(HighLevelSimpleClient client, String URLPath) {
		super(client);
		this.path = URLPath;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException {
		// FIXME: use failsafe get with max length of routig key
        String identityId = request.getParam("identity");
        int width = 128;
        int height = 128;
        try {
            width = Integer.parseInt(request.getParam("width"));
            height = Integer.parseInt(request.getParam("height"));
        } catch (NumberFormatException nfe1) {
            /* could not parse, ignore. defaults are fine. */
        }
        if (width < 1) {
            width = 128;
        }
        if (height < 1) {
            height = 128;
        }
        if (height > 800 || width > 800) {
        	// don't let some bad guy eat up our RAM
        	height = 800;
        	width = 800;
        } 
        ByteArrayOutputStream imageOutputStream = null;
		byte[] image;
        try {
            RenderedImage identiconImage = new Identicon(Base64.decode(identityId)).render(width, height);
            imageOutputStream = new ByteArrayOutputStream();
            ImageIO.write(identiconImage, "png", imageOutputStream);
            image = imageOutputStream.toByteArray();
            writeReply(ctx, 200, "image/png", "OK", image, 0, image.length);
        } catch (IllegalBase64Exception e) {
        	writeReply(ctx, 403, "text/plain", "invalid routing key", "invalid routing key: " + e.getMessage());
		} finally {
            Closer.close(imageOutputStream);
            image = null;
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
