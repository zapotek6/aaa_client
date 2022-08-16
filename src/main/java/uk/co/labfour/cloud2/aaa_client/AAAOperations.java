package uk.co.labfour.cloud2.aaa_client;

import uk.co.labfour.bjson.BJsonException;
import uk.co.labfour.bjson.BJsonObject;
import uk.co.labfour.cloud2.aaa.common.AAAEntityOperationBuilder;
import uk.co.labfour.cloud2.aaa.common.AAAEntityOperationsBuilder;
import uk.co.labfour.cloud2.aaa.common.IAAAClient;
import uk.co.labfour.cloud2.aaa.common.RequestInfo;
import uk.co.labfour.cloud2.microservice.ServiceContext;
import uk.co.labfour.cloud2.protocol.BaseRequest;
import uk.co.labfour.cloud2.protocol.BaseResponse;
import uk.co.labfour.error.BException;

import static uk.co.labfour.cloud2.aaa.common.AAAConstants.*;

public class AAAOperations {

    public static BaseResponse createComplexEntityForUser(ServiceContext serviceContext, String name, String uid, String pwd) throws BException {

        AAAEntityOperationBuilder createuserOperation = new AAAEntityOperationBuilder()
                .setAction("1", ADD_OPE, "name", "description", "00000000-0000-0000-0000-00000000000c", "00000000-0000-0000-0000-000000000003")
                .setAuthWithCredentials(uid, pwd)
                .addPermission("00000000-0000-0000-0000-000000000001", PERMISSION_ACTION__CREATE_ENTITY, PERMISSION_ALLOW)
                .addPermission("00000000-0000-0000-0000-000000000001", PERMISSION_ACTION__DELETE_ENTITY, PERMISSION_ALLOW)
                .addPermission("00000000-0000-0000-0000-000000000001", PERMISSION_ACTION__READ_ENTITY, PERMISSION_ALLOW)
                .addRole("00000000-0000-0000-0000-00000000000c", "00000000-0000-0000-0000-000000000008");

        AAAEntityOperationsBuilder operationsBuilder = new AAAEntityOperationsBuilder();
        operationsBuilder.add(createuserOperation);

        BJsonObject payload = new BJsonObject();
        try {
            payload.put(operationsBuilder.getObjectName(), operationsBuilder.build());
        } catch (BJsonException e) {

        }

        return execute(serviceContext, payload);
    }

    public static BaseResponse createComplexEntityForDevice(ServiceContext serviceContext, String ownerUuid, String name, String description, String apiKey) throws BException {

        AAAEntityOperationBuilder createuserOperation = new AAAEntityOperationBuilder()
                .setAction("1", ADD_OPE, "name", "description", "00000000-0000-0000-0000-000000000015", ownerUuid)
                .setAuthWithApiKey(apiKey)
                .addPermission("00000000-0000-0000-0000-000000000001", PERMISSION_ACTION__CREATE_ENTITY, PERMISSION_ALLOW)
                .addPermission("00000000-0000-0000-0000-000000000001", PERMISSION_ACTION__DELETE_ENTITY, PERMISSION_ALLOW)
                .addPermission("00000000-0000-0000-0000-000000000001", PERMISSION_ACTION__READ_ENTITY, PERMISSION_ALLOW)
                .addRole("00000000-0000-0000-0000-000000000015", "00000000-0000-0000-0000-000000000007");

        AAAEntityOperationsBuilder operationsBuilder = new AAAEntityOperationsBuilder();
        operationsBuilder.add(createuserOperation);

        BJsonObject payload = new BJsonObject();
        try {
            payload.put(operationsBuilder.getObjectName(), operationsBuilder.build());
        } catch (BJsonException e) {

        }

        return execute(serviceContext, payload);
    }

    private static BaseResponse execute(ServiceContext serviceContext, BJsonObject payload) throws BException {
        IAAAClient aaaClient = serviceContext.getAaaClient();

        BaseRequest request = new BaseRequest();

        request.setPayload(payload);

        RequestInfo requestInfo = new RequestInfo(request,
                serviceContext.getServiceApiKey(),
                serviceContext.getServiceUuidAsString(),
                "",
                serviceContext.getAaaTransport(), null);
        requestInfo.setSyncResponse();

        return  aaaClient.doCreateComplexEntity(requestInfo, serviceContext.getServiceApiKey());
    }




}
