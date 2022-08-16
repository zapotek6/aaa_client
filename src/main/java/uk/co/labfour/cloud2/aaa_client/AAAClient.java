package uk.co.labfour.cloud2.aaa_client;

import uk.co.labfour.bjson.BJsonDeSerializer;
import uk.co.labfour.bjson.BJsonDeSerializerFactory;
import uk.co.labfour.bjson.BJsonException;
import uk.co.labfour.bjson.BJsonObject;
import uk.co.labfour.cloud2.aaa.common.AAAConstants;
import uk.co.labfour.cloud2.aaa.common.IAAAClient;
import uk.co.labfour.cloud2.aaa.common.RequestInfo;
import uk.co.labfour.cloud2.aaa.common.Utility;
import uk.co.labfour.cloud2.aaa.common.model.AlexaOAuth2Token;
import uk.co.labfour.cloud2.aaa.common.model.Token;
import uk.co.labfour.cloud2.microservice.ServiceError;
import uk.co.labfour.cloud2.protocol.BaseRequest;
import uk.co.labfour.cloud2.protocol.BaseResponse;
import uk.co.labfour.error.BEarer;
import uk.co.labfour.error.BException;
import uk.co.labfour.logger.MyLogger;
import uk.co.labfour.logger.MyLoggerFactory;
import uk.co.labfour.net.proto.mqtt.client.MqttMessage;
import uk.co.labfour.net.transport.IGenericTransport2;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static uk.co.labfour.cloud2.aaa.common.AAAConstants.TOKEN_FLD;
import static uk.co.labfour.cloud2.aaa.common.AAAConstants.UUID_FLD;

public class AAAClient implements IAAAClient {
    MyLogger log = MyLoggerFactory.getInstance();
	private Long reqId = 0L;
	private final ConcurrentHashMap<String, RequestInfo> waitAuthz = new ConcurrentHashMap<>();
	private final String replyTo;
    private final IGenericTransport2 transport;
	private final long aaaTimeoutValue = AAAConstants.DEFAULT_AAA_TIMEOUT_VALUE;
	private final TimeUnit aaaTimeoutTimeUnit = AAAConstants.DEFAULT_AAA_TIMEOUT_TIMEUNIT;

    public AAAClient(IGenericTransport2 transport, String replyTo) {
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

    private BEarer<MqttMessage> send(BaseRequest request, IGenericTransport2 transport) {
        return transport.send(request);

    }

    private void sendRequest(BaseRequest request, RequestInfo requestInfo, IGenericTransport2 transport) throws BException {
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
            request
                    .getPayload()
                    .put(AAAConstants.OPERATIONS_ARRAY_CONTAINER_FLD,
                            requestInfo
                                    .getRequest()
                                    .getPayload()
                                    .getElementAsBJsonArray(AAAConstants.OPERATIONS_ARRAY_CONTAINER_FLD));

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

    public BEarer<Token> doReadToken(RequestInfo requestInfo, String apiKey, String token) {

        try {

            BaseRequest authReq = Utility.createRequest(apiKey, AAAConstants.AAA_CONSUMER, AAAConstants.AAA_READ_TOKEN_API, replyTo);

            prepareAndAddAuthzStuff(authReq, requestInfo);

            authReq.getPayload().put(TOKEN_FLD, token);

            sendRequest(authReq, requestInfo, transport);

            BaseResponse response = waitRequestCompletion(requestInfo, authReq);

            if (0 == response.getErrCode()) {
                BJsonDeSerializer bJsonDeSerializer = BJsonDeSerializerFactory.getInstanceForMongoDB(false);

                Token token1 = bJsonDeSerializer.fromJson(response.getPayload().getElementAsBJsonObject(TOKEN_FLD), Token.class);

                return new BEarer<Token>()
                        .setSuccess()
                        .set(token1);
            } else {
                return BEarer.createGenericError(this, response.getErrDescription())
                        .setCode(response.getErrCode());
            }

        } catch(BException e) {
            return BEarer.createGenericError(this, e.getMessage())
                    .setCode(e.getErrorCode());
        } catch (BJsonException e) {
            return BEarer.createGenericError(this, e.getMessage());
        }
    }



    public BEarer<String> doObtainAlexaOAuth2Token(RequestInfo requestInfo, String apiKey, String token, String authorizationCode) {

        try {

            BaseRequest authReq = Utility.createRequest(apiKey, AAAConstants.AAA_CONSUMER, AAAConstants.AAA_OBTAIN_ALEXA_OAUTH2_TOKEN_API, replyTo);

            prepareAndAddAuthzStuff(authReq, requestInfo);

            authReq.getPayload().put("userToken", token);
            authReq.getPayload().put("authorizationCode", authorizationCode);

            sendRequest(authReq, requestInfo, transport);

            BaseResponse response = waitRequestCompletion(requestInfo, authReq);

            if (0 == response.getErrCode()) {
                BJsonDeSerializer bJsonDeSerializer = BJsonDeSerializerFactory.getInstance(false);

                //Token token1 = bJsonDeSerializer.fromJson(response.getPayload().getElementAsString(TOKEN_FLD), Token.class);

                BEarer<String> alexaOAuth2TokenOp = response.getPayload().getElmAsString(TOKEN_FLD);

                if (alexaOAuth2TokenOp.isOk()) {
                    return new BEarer<String>()
                            .setSuccess()
                            .set(alexaOAuth2TokenOp.get());
                } else {
                    return BEarer.createGenericError(this, "invalid alexa oauth2 token")
                            .setCode(500);
                }

            } else {
                return BEarer.createGenericError(this, response.getErrDescription());
            }

        } catch(BException e) {
            return BEarer.createGenericError(this, e.getMessage())
                    .setCode(e.getErrorCode());
        } catch (BJsonException e) {
            return BEarer.createGenericError(this, e.getMessage());
        }
    }

    public BEarer<String> doRefreshAlexaOAuth2Token(RequestInfo requestInfo, String apiKey, String token) {

        try {

            BaseRequest authReq = Utility.createRequest(apiKey, AAAConstants.AAA_CONSUMER, AAAConstants.AAA_REFRESH_ALEXA_OAUTH2_TOKEN_API, replyTo);

            prepareAndAddAuthzStuff(authReq, requestInfo);

            authReq.getPayload().put(TOKEN_FLD, token);

            sendRequest(authReq, requestInfo, transport);

            BaseResponse response = waitRequestCompletion(requestInfo, authReq);

            if (0 == response.getErrCode()) {
                BJsonDeSerializer bJsonDeSerializer = BJsonDeSerializerFactory.getInstance(false);

                //Token token1 = bJsonDeSerializer.fromJson(response.getPayload().getElementAsString(TOKEN_FLD), Token.class);

                BEarer<String> alexaOAuth2TokenOp = response.getPayload().getElmAsString(TOKEN_FLD);

                if (alexaOAuth2TokenOp.isOk()) {
                    return new BEarer<String>()
                            .setSuccess()
                            .set(alexaOAuth2TokenOp.get());
                } else {
                    return BEarer.createGenericError(this, "invalid alexa oauth2 token")
                            .setCode(500);
                }

            } else {
                return BEarer.createGenericError(this, response.getErrDescription());
            }

        } catch(BException e) {
            return BEarer.createGenericError(this, e.getMessage())
                    .setCode(e.getErrorCode());
        } catch (BJsonException e) {
            return BEarer.createGenericError(this, e.getMessage());
        }
    }

    public BEarer<AlexaOAuth2Token> doReadAlexaOAuth2Token(RequestInfo requestInfo, String apiKey, String uuid) {

        try {

            BaseRequest authReq = Utility.createRequest(apiKey, AAAConstants.AAA_CONSUMER, AAAConstants.AAA_READ_ALEXA_OAUTH_TOKEN_API, replyTo);

            prepareAndAddAuthzStuff(authReq, requestInfo);

            authReq.getPayload().put(UUID_FLD, uuid);

            sendRequest(authReq, requestInfo, transport);

            BaseResponse response = waitRequestCompletion(requestInfo, authReq);

            if (0 == response.getErrCode()) {
                BJsonDeSerializer bJsonDeSerializer = BJsonDeSerializerFactory.getInstanceForMongoDB(false);

                AlexaOAuth2Token token1 = bJsonDeSerializer.fromJson(response.getPayload().getElementAsBJsonObject(TOKEN_FLD), AlexaOAuth2Token.class);

                return new BEarer<AlexaOAuth2Token>()
                        .setSuccess()
                        .set(token1);
            } else {
                return BEarer.createGenericError(this, response.getErrDescription())
                        .setCode(response.getErrCode());
            }

        } catch(BException e) {
            return BEarer.createGenericError(this, e.getMessage())
                    .setCode(e.getErrorCode());
        } catch (BJsonException e) {
            return BEarer.createGenericError(this, e.getMessage());
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
