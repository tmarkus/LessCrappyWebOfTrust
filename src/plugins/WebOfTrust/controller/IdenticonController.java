package plugins.WebOfTrust.controller;

import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

import javax.imageio.ImageIO;

import plugins.WebOfTrust.WebOfTrust;
import plugins.WebOfTrust.identicon.Identicon;

import thomasmarkus.nl.freenet.graphdb.H2Graph;

import freenet.client.HighLevelSimpleClient;
import freenet.clients.http.ToadletContext;
import freenet.clients.http.ToadletContextClosedException;
import freenet.support.api.Bucket;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;

public class IdenticonController extends freenet.plugin.web.HTMLFileReaderToadlet {
	
	private WebOfTrust main;
	
	public IdenticonController(WebOfTrust main, HighLevelSimpleClient client, String filepath, String URLPath, H2Graph graph) {
		super(client, filepath, URLPath);
		this.main = main;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx) throws ToadletContextClosedException, IOException
	{
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
        ByteArrayOutputStream imageOutputStream = null;
        try {
                RenderedImage identiconImage = new Identicon(identityId.getBytes()).render(width, height);
                imageOutputStream = new ByteArrayOutputStream();
                ImageIO.write(identiconImage, "png", imageOutputStream);
                Bucket imageBucket = BucketTools.makeImmutableBucket(main.getPR().getNode().clientCore.tempBucketFactory, imageOutputStream.toByteArray());
                writeReply(ctx, 200, "image/png", "OK", imageBucket);
        } finally {
                Closer.close(imageOutputStream);
        }
	}
	

}
