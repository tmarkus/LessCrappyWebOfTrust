package plugins.WebOfTrust;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class OneBytePerReadInputStream extends FilterInputStream {


    public OneBytePerReadInputStream(InputStream in) {
            super(in);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
            return super.read(b, off, len<=1 ? len : 1);
    }
	
}
