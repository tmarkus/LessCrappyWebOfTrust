package plugins.WebOfTrust;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import freenet.client.FetchResult;
import freenet.keys.FreenetURI;

public class Utils {

	public static Document getXMLDoc(FetchResult result)
	{
		try
		{
			DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
	        DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
	        Document doc = docBuilder.parse(new OneBytePerReadInputStream(new ByteArrayInputStream(result.asByteArray())));
	        
	        return doc;
		}
		catch(NullPointerException ex)
		{
			
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return null;
	}
	/**
	 * Generate an identifier of of an identity given a full USK
	 * @param key - a full freenet key (only tested with USK@)
	 * @return return the ASCII representation of the routing key part
	 */
	
	public static String getIDFromKey(FreenetURI key)
	{
		return key.toASCIIString().split("@|/")[1].split(",")[0];
	}
	
}
