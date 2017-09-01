package uk.co.labfour.cloud2.aaa_client;

import uk.co.labfour.cloud2.protocol.BaseResponse;
import uk.co.labfour.error.BException;

public interface IAAAClient {

	String AAA_CONSUMER = "aaa";
	String AAA_AUTHORIZATOR_API = "aaa.authorizator";
	String AAA_AUTHENTICATOR_API = "aaa.authenticator";

	BaseResponse doProcess(BaseResponse response) throws BException;

	boolean doAuthz(RequestInfo requestInfo, String apiKey) throws BException;

}