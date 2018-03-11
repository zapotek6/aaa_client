package uk.co.labfour.cloud2.aaa_client;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import uk.co.labfour.bjson.BJsonException;
import uk.co.labfour.bjson.BJsonObject;
import uk.co.labfour.cloud2.aaa.common.AAAConstants;
import uk.co.labfour.cloud2.aaa.common.IAAAClient;
import uk.co.labfour.cloud2.aaa.common.RequestInfo;
import uk.co.labfour.cloud2.aaa.common.Utility;
import uk.co.labfour.cloud2.microservice.ServiceError;
import uk.co.labfour.cloud2.protocol.BaseRequest;
import uk.co.labfour.cloud2.protocol.BaseResponse;
import uk.co.labfour.error.BException;
import uk.co.labfour.logger.MyLogger;
import uk.co.labfour.logger.MyLoggerFactory;
import uk.co.labfour.net.transport.IGenericTransport;

public class AAAClient implements IAAAClient {
    MyLogger log = MyLoggerFactory.getInstance();
	private Long reqId = 0L;
	private final ConcurrentHashMap<String, RequestInfo> waitAuthz = new ConcurrentHashMap<>();
	private final String replyTo;
	private final IGenericTransport transport;
	private final long aaaTimeoutValue = AAAConstants.DEFAULT_AAA_TIMEOUT_VALUE;
	private final TimeUnit aaaTimeoutTimeUnit = AAAConstants.DEFAULT_AAA_TIMEOUT_TIMEUNIT;

	public AAAClient(IGenericTransport transport, String replyTo) {
		this.transport = transport;
		this.replyTo = replyTo;
	}
	
	public BaseResponse doProcess(BaseResponse response) {

		RequestInfo requestInfo = deque(response.getReqid());
		//BaseRequest request = requestInfo.getRequest();
		requestInfo.setResponse(response);
		requestInfo.getSyncResponse().countDown();

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

    private long assignUniqueIdForCorrelation(BaseRequest request, RequestInfo requestInfo) {
        long reqId = getNextReqId();
        request.setReqid(Long.toString(reqId));
        requestInfo.setReqId(reqId);

        return reqId;
    }

    private void send(BaseRequest request, IGenericTransport transport) throws BException {
	    try {
	        transport.send(request);
        } catch (BException e) {
            throw new BException("Transport error", ServiceError.INTERNAL_ERR);
        }
    }

    private void sendRequest(BaseRequest request, RequestInfo requestInfo, IGenericTransport transport) throws BException {
        long reqId = assignUniqueIdForCorrelation(request, requestInfo);

        enque(Long.toString(reqId), requestInfo);

        send(request, transport);
    }

    private BJsonObject createAuthzObject(RequestInfo requestInfo) throws BException {
        BJsonObject authz = new BJsonObject();

        try {
            authz.put("targetResourceUuid", requestInfo.getTargetResourceUuid());
            authz.put("actionOnTargetResource", requestInfo.getActionOnTargetResource());
        } catch (BJsonException e) {
            throw new BException("Malformed input", ServiceError.CLIENT_ERR);
        }

        return authz;
    }

    private void prepareAndAddAuthzStuff(BaseRequest request, RequestInfo requestInfo) throws BException {

	    BJsonObject authz = createAuthzObject(requestInfo);

	    try {
            request.getPayload().put(AAAConstants.AUTH_CONTAINER_FLD, requestInfo.getRequest().getAuth());
            request.getPayload().put(AAAConstants.AUTHZ_CONTAINER_FLD, authz);
        } catch (BJsonException e) {
            throw new BException("Malformed input", ServiceError.CLIENT_ERR);
        }
    }

    private void addOperations(BaseRequest request, RequestInfo requestInfo) throws BException {
        try {
            request.getPayload().put(AAAConstants.OPERATIONS_ARRAY_CONTAINER_FLD, requestInfo.getRequest().getPayload().getElementAsBJsonArray(AAAConstants.OPERATIONS_ARRAY_CONTAINER_FLD));

        } catch (BJsonException e) {
            throw new BException("Malformed input", ServiceError.CLIENT_ERR);
        }
    }

	public BaseResponse doAuthz(RequestInfo requestInfo, String apiKey) {

        try {

            BaseRequest authReq = Utility.createRequest(apiKey, AAAConstants.AAA_CONSUMER, AAAConstants.AAA_AUTHORIZATOR_API, replyTo);

            prepareAndAddAuthzStuff(authReq, requestInfo);

            sendRequest(authReq, requestInfo, transport);

            return waitRequestCompletion(requestInfo, authReq);

        } catch (BException e) {
            return new BaseResponse(requestInfo.getRequest()).setError(e.getErrorCode(), e.getMessage());
        }

	}

	public BaseResponse doCreateComplexEntity(RequestInfo requestInfo, String apiKey) {

        try {

            BaseRequest authReq = Utility.createRequest(apiKey, AAAConstants.AAA_CONSUMER, AAAConstants.AAA_CREATE_COMPLEX_ENTITY_API, replyTo);

            prepareAndAddAuthzStuff(authReq, requestInfo);

            addOperations(authReq, requestInfo);

            sendRequest(authReq, requestInfo, transport);

            return waitRequestCompletion(requestInfo, authReq);
        } catch(BException e) {
            return new BaseResponse(requestInfo.getRequest()).setError(e.getErrorCode(), e.getMessage());
        }


	}

	private BaseResponse waitRequestCompletion(RequestInfo requestInfo, BaseRequest authReq) throws BException {
        requestInfo.waitCompletion(aaaTimeoutValue, aaaTimeoutTimeUnit);
        BaseResponse response = requestInfo.getResponse();

        if (null == response) {
            response = new BaseResponse(authReq);
            response.setError(ServiceError.INTERNAL_ERR, "AAA Service timeout");
        }

        return response;
    }

}
