package plugins.WebOfTrust;

import freenet.node.RequestClient;

public class IdentityUpdaterRequestClient implements RequestClient  {

	@Override
	public boolean persistent() {
		return false;
	}

	@Override
	public boolean realTimeFlag() {
		return true;
	}
}
