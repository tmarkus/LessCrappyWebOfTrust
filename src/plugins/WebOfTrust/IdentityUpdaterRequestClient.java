package plugins.WebOfTrust;

import com.db4o.ObjectContainer;

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

	
	
	@Override
	public void removeFrom(ObjectContainer arg0) {
	}

}
