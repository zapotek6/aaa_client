package uk.co.labfour.cloud2.aaa_client;

import uk.co.labfour.cloud2.protocol.BaseRequest;
import uk.co.labfour.cloud2.protocol.BaseResponse;
import uk.co.labfour.error.BException;
import uk.co.labfour.net.transport.IGenericTransport;

public interface IServiceManager {
	
	public void doStartServingRequest(AAAClient aaa_client, IGenericTransport transport, BaseRequest request) throws BException;
}
