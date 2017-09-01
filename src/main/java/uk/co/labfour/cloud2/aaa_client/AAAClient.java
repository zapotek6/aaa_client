package uk.co.labfour.cloud2.aaa_client;

import java.util.concurrent.ConcurrentHashMap;

import uk.co.labfour.bjson.BJsonException;
import uk.co.labfour.bjson.BJsonObject;
import uk.co.labfour.cloud2.protocol.BaseRequest;
import uk.co.labfour.cloud2.protocol.BaseResponse;
import uk.co.labfour.error.BException;
import uk.co.labfour.logger.MyLogger;
import uk.co.labfour.logger.MyLoggerFactory;
import uk.co.labfour.net.transport.IGenericTransport;

public class AAAClient implements IAAAClient {
	MyLogger log = MyLoggerFactory.getInstance();
	Long reqId = 0L;
	ConcurrentHashMap<String, RequestInfo> waitAuthz = new ConcurrentHashMap<String, RequestInfo>();
	String replyTo;
	IGenericTransport aaaTransport;
	
	public AAAClient(IGenericTransport aaaTransport, String replyTo) {
		this.aaaTransport = aaaTransport;
		this.replyTo = replyTo;
	}
	
	public BaseResponse doProcess(BaseResponse response) throws BException {
			
			if (0 == response.getApi().compareTo(AAA_AUTHENTICATOR_API)) {
				System.out.println("response from aaa.authenticator " + response.getPayload().toString());
			} else if (0 == response.getApi().compareTo(AAA_AUTHORIZATOR_API)) {
				RequestInfo requestInfo = deque(response.getReqid());
				BaseRequest request = requestInfo.getRequest();
				IGenericTransport transport = requestInfo.getTransport();
				BaseResponse svcResponse = new BaseResponse(request);
				try {
					if (!response.containsError()) {
						if (response.getPayload().has("authorized")) {
							if (response.getPayload().getElementAsBoolean("authorized")) {
								
								// run the service
								svcResponse = requestInfo.getService().doExec(request);		
								
							} else {
								svcResponse.setError(response.getErrCode(), response.getErrDescription());
							}
						} else {
							svcResponse.setError(response.getErrCode(), response.getErrDescription());
						}
					} else  {
						svcResponse.setError(response.getErrCode(), response.getErrDescription());
					}
				} catch (BJsonException e) {
					svcResponse.setError(response.getErrCode(), response.getErrDescription());
				}
				
				// if the request is async send the response using the transport
				// if the reuqest is sync set in the request info the response and the unblock (see CountDownLatch in RequestInfo) the wainting thread
				if (null == requestInfo.getSyncResponse()) {
					transport.reply(svcResponse);
				} else {
					requestInfo.setResponse(svcResponse);
					requestInfo.getSyncResponse().countDown();
				}
				
				System.out.println("response from aaa.authorizator " + response.getPayload().toString());
			} else {
				System.out.println("no API match");
			}
			
			return response;
		}
		
	private void enque(String reqId, RequestInfo requestInfo) {
		waitAuthz.put(reqId, requestInfo);
	}
	
	private RequestInfo deque(String reqId) {
		return waitAuthz.get(reqId);
	}
	
	private long getNextReqId() {

		synchronized (reqId) {
			return ++reqId;
		
		}

	}
	
	public boolean doAuthz(RequestInfo requestInfo, String apiKey) throws BException {
		BaseRequest authReq = new BaseRequest();
		
		BJsonObject serviceAuth = new BJsonObject();
		try {
			serviceAuth.put("apikey", apiKey);
			serviceAuth.put("mode", "apikey");
		} catch (BJsonException e) {
			throw new BException(e);
		}
		
		authReq.setAuth(serviceAuth);
		authReq.setApi(AAA_AUTHORIZATOR_API);
		authReq.setConsumer(AAA_CONSUMER);
		authReq.setReplyTo(replyTo);
		
		try {
			
			BJsonObject authz = new BJsonObject();
			authz.put("resourceUuid", requestInfo.getResourceUuid());
			authz.put("action", requestInfo.getAction());
			authReq.getPayload().put(BaseRequest.kAuth, requestInfo.getRequest().getAuth());
			authReq.getPayload().put("authz", authz);
			
		} catch (BJsonException e) {
			throw new BException("error creating authz request", e);
		}
		
		long reqId = getNextReqId();
		authReq.setReqid(Long.toString(reqId));
		requestInfo.setReqId(reqId);
		enque(Long.toString(reqId), requestInfo);
		aaaTransport.send(authReq);
		return false;
	}
	
	
	
}
