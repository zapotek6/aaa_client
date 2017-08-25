package uk.co.labfour.cloud2.aaa_client;

import java.util.concurrent.CountDownLatch;

import uk.co.labfour.cloud2.protocol.BaseRequest;
import uk.co.labfour.cloud2.protocol.BaseResponse;
import uk.co.labfour.net.transport.IGenericTransport;

public class RequestInfo {
	BaseRequest request;
	BaseResponse response;
	long reqId = -1L;
	IGenericTransport transport;
	IService service;
	String apiKey;
	String resourceUuid;
	String action;
	CountDownLatch syncResponse = null;
	
	
	public RequestInfo(BaseRequest request, String apiKey, String resourceUuid, String action, IGenericTransport transport, IService service) {
		this.request = request;
		this.transport = transport;
		this.service = service;
		this.apiKey = apiKey;
		this.resourceUuid = resourceUuid;
		this.action = action;
	}

	public BaseRequest getRequest() {
		return request;
	}

	public void setRequest(BaseRequest request) {
		this.request = request;
	}

	public long getReqId() {
		return reqId;
	}

	public void setReqId(long reqId) {
		this.reqId = reqId;
	}

	public IGenericTransport getTransport() {
		return transport;
	}

	public void setTransport(IGenericTransport transport) {
		this.transport = transport;
	}

	public IService getService() {
		return service;
	}

	public void setService(IService service) {
		this.service = service;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getResourceUuid() {
		return resourceUuid;
	}

	public void setResourceUuid(String resourceUuid) {
		this.resourceUuid = resourceUuid;
	}

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public BaseResponse getResponse() {
		return response;
	}

	public void setResponse(BaseResponse response) {
		this.response = response;
	}

	public CountDownLatch getSyncResponse() {
		return syncResponse;
	}

	public CountDownLatch setSyncResponse() {
		CountDownLatch sync = new CountDownLatch(1);
		this.syncResponse = sync;
		return sync;
	}
	
	
	
}
