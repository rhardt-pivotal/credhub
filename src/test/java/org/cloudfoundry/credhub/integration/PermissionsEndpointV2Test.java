package org.cloudfoundry.credhub.integration;

import org.cloudfoundry.credhub.CredentialManagerApp;
import org.cloudfoundry.credhub.helper.JsonTestHelper;
import org.cloudfoundry.credhub.request.PermissionOperation;
import org.cloudfoundry.credhub.util.DatabaseProfileResolver;
import org.cloudfoundry.credhub.view.PermissionsV2View;
import org.hamcrest.core.IsEqual;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.cloudfoundry.credhub.util.AuthConstants.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@ActiveProfiles(value = {"unit-test", "unit-test-permissions"}, resolver = DatabaseProfileResolver.class)
@Transactional
public class PermissionsEndpointV2Test {

  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @Before
  public void beforeEach() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  @Test
  public void POST_whenUserHasPermissionOnPath_theyCanAddAPermission() throws Exception {
    String credentialName = "/test";

    this.setPermissions(credentialName, PermissionOperation.WRITE);

    MockHttpServletRequestBuilder writeCredentialRequest = put("/api/v1/data")
        .header("Authorization", "Bearer " + USER_A_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"name\": \"" + credentialName + "\",\n"
            + "  \"value\": \"test-value\",\n"
            + "  \"type\": \"password\"\n"
            + "}");

    mockMvc.perform(writeCredentialRequest).andExpect(status().isOk());
  }

  @Test
  public void POST_whenUserDoesNotHavePermissionOnPath_theyCannotAddAPermission() throws Exception {
    String credentialName = "/test";

    MockHttpServletRequestBuilder addPermissionRequest = post("/api/v2/permissions")
        .header("Authorization", "Bearer " + NO_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + credentialName + "\",\n"
            + "  \"operations\": [\"write\"]\n"
            + "}");

    mockMvc.perform(addPermissionRequest).andExpect(status().isNotFound());
  }

  @Test
  public void POST_whenUserTriesToAddOperationThatDoesntExist_theyReceiveAnUnprocessibleEntry() throws Exception {
    String credentialName = "/test";

    MockHttpServletRequestBuilder addPermissionRequest = post("/api/v2/permissions")
        .header("Authorization", "Bearer " + NO_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + credentialName + "\",\n"
            + "  \"operations\": [\"bad_operation\"]\n"
            + "}");

    mockMvc.perform(addPermissionRequest).andExpect(status().isUnprocessableEntity());
  }

  @Test
  public void POST_whenUserTriesToAddAPermissionThatAlreadyExists_theyReceiveAConflict() throws Exception {
    String credentialName = "/user-a/*";

    MockHttpServletRequestBuilder addPermissionRequest = post("/api/v2/permissions")
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + credentialName + "\",\n"
            + "  \"operations\": [\"read\"]\n"
            + "}");

    mockMvc.perform(addPermissionRequest).andExpect(status().isConflict());
  }

  @Test
  public void POST_whenUserTriesToAddAnAdditionalOperationToAPermissionThatAlreadyExists_theyReceiveAConflict() throws Exception {
    String credentialName = "/test";

    UUID credentialUuid = this.setPermissions(credentialName, PermissionOperation.WRITE);

    MockHttpServletRequestBuilder addPermissionRequestWithRead = post("/api/v2/permissions")
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + credentialName + "\",\n"
            + "  \"operations\": [\"write\", \"read\"]\n"
            + "}");

    mockMvc.perform(addPermissionRequestWithRead).andExpect(status().isConflict());
  }

  @Test
  public void GET_whenUserGivesAPermissionGuid_theyReceiveThePermissions() throws Exception {
    String credentialName = "/test";
    UUID credentialUuid = this.setPermissions(credentialName, PermissionOperation.WRITE);

    MockHttpServletRequestBuilder getUuidRequest = get("/api/v2/permissions/" + credentialUuid)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON);

    String content = mockMvc.perform(getUuidRequest).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    PermissionsV2View returnValue = JsonTestHelper.deserialize(content, PermissionsV2View.class);
    assertThat(returnValue.getActor(), equalTo(USER_A_ACTOR_ID));
    assertThat(returnValue.getPath(), equalTo(credentialName));
    assertThat(returnValue.getOperations(), contains(PermissionOperation.WRITE));
    assertThat(returnValue.getUuid(), equalTo(credentialUuid));
  }

  @Test
  public void GET_whenUserGivesAPermissionGuid_whenTheyDontHaveREADACLPermission_theyReceiveA403() throws Exception {
    String credentialName = "/test";
    UUID credentialUuid = this.setPermissions(credentialName, PermissionOperation.WRITE);

    MockHttpServletRequestBuilder getUuidRequest = get("/api/v2/permissions/" + credentialUuid)
        .header("Authorization", "Bearer " + USER_A_TOKEN)
        .accept(APPLICATION_JSON);

    mockMvc.perform(getUuidRequest).andExpect(status().isForbidden()).andReturn().getResponse().getContentAsString();
  }

  @Test
  public void PATCH_whenUserGivesAPermission_forAPathAndActorThatDoesNotExist_theyReceiveA404() throws Exception{
    String invalidGuid = "invalid";

    MockHttpServletRequestBuilder patchPermissionRequest = patch("/api/v2/permissions/" + invalidGuid)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"operations\": [\"" + "write" + "\"]\n"
            + "}");

    String responseJson = mockMvc.perform(patchPermissionRequest).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
    String errorMessage = new JSONObject(responseJson).getString("error");

    assertThat(errorMessage, is(IsEqual.equalTo("The request includes a permission that does not exist.")));
  }

  @Test
  public void PATCH_whenWriteIsEnabledOnExistingPermissionForUserA_UserACanCreateCredentials() throws Exception{
    String credentialName = "/test";
    String passwordValue = "passwordValue";

    UUID permissionUUID = this.setPermissions(credentialName, PermissionOperation.READ);

    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isForbidden());

    MockHttpServletRequestBuilder patchPermissionRequest = patch("/api/v2/permissions/" + permissionUUID)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"operations\": [\"" + "write" + "\"]\n"
            + "}");

    String content = mockMvc.perform(patchPermissionRequest).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    PermissionsV2View returnValue = JsonTestHelper.deserialize(content, PermissionsV2View.class);
    assertThat(returnValue.getUuid(), equalTo(permissionUUID));
    assertThat(returnValue.getActor(), equalTo(USER_A_ACTOR_ID));
    assertThat(returnValue.getPath(), equalTo(credentialName));
    assertThat(returnValue.getOperations(), equalTo(Collections.singletonList(PermissionOperation.WRITE)));

    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isOk());
  }


  @Test
  public void PATCH_whenUserDoesNotHavePermissionOnPath_theyCannotAddAPermission() throws Exception {
    String credentialName = "/test";

    UUID permissionUUID = this.setPermissions(credentialName, PermissionOperation.READ);

    MockHttpServletRequestBuilder patchPermissionRequest = patch("/api/v2/permissions/" + permissionUUID)
        .header("Authorization", "Bearer " + NO_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"operations\": [\"write\"]\n"
            + "}");

    mockMvc.perform(patchPermissionRequest).andExpect(status().isNotFound());
  }

  @Test
  public void PUT_whenUserDoesNotHavePermissionOnPath_theyCannotAddAPermission() throws Exception {
    String credentialName = "/test";

    UUID permissionUUID = this.setPermissions(credentialName, PermissionOperation.READ);

    MockHttpServletRequestBuilder putPermissionRequest = put("/api/v2/permissions/" + permissionUUID)
        .header("Authorization", "Bearer " + NO_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + credentialName + "\",\n"
            + "  \"operations\": [\"" + "write" + "\"]\n"
            + "}");

    mockMvc.perform(putPermissionRequest).andExpect(status().isNotFound());
  }


  @Test
  public void PUT_whenWriteIsEnabledOnExistingPermissionForUserA_UserACanCreateCredentials() throws Exception{
    String credentialName = "/test";
    String passwordValue = "passwordValue";

    UUID credUUID = this.setPermissions(credentialName, PermissionOperation.READ);

    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isForbidden());

    MockHttpServletRequestBuilder putPermissionRequest = put("/api/v2/permissions/" + credUUID)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + credentialName + "\",\n"
            + "  \"operations\": [\"" + "write" + "\"]\n"
            + "}");

    String content = mockMvc.perform(putPermissionRequest).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    PermissionsV2View returnValue = JsonTestHelper.deserialize(content, PermissionsV2View.class);
    assertThat(returnValue.getUuid(), equalTo(credUUID));
    assertThat(returnValue.getActor(), equalTo(USER_A_ACTOR_ID));
    assertThat(returnValue.getPath(), equalTo(credentialName));
    assertThat(returnValue.getOperations(), equalTo(Collections.singletonList(PermissionOperation.WRITE)));

    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isOk());
  }

  @Test
  public void PUT_whenUUIDDoesNotMatchGivenActorAndPath_ReturnStatusBadRequest() throws Exception{
    String credentialName = "/test";
    String badCredentialName = "/wrongName";
    String passwordValue = "passwordValue";

    UUID credUUID = this.setPermissions(credentialName, PermissionOperation.READ);

    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isForbidden());

    MockHttpServletRequestBuilder putPermissionRequest = put("/api/v2/permissions/" + credUUID)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + badCredentialName + "\",\n"
            + "  \"operations\": [\"" + "write" + "\"]\n"
            + "}");

    String responseJson = mockMvc.perform(putPermissionRequest).andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();
    String errorMessage = new JSONObject(responseJson).getString("error");

    assertThat(errorMessage, is(IsEqual.equalTo("The permission guid does not match the provided actor and path.")));
  }

  @Test
  public void PUT_whenUUIDDoesNotExist_ReturnStatusNotFound() throws Exception{
    String credUUID = "1550919c-b7e1-4288-85fd-c73220e6ac5f";

    MockHttpServletRequestBuilder putPermissionRequest = put("/api/v2/permissions/" + credUUID)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + "badCredentialName" + "\",\n"
            + "  \"operations\": [\"" + "write" + "\"]\n"
            + "}");

    String responseJson = mockMvc.perform(putPermissionRequest).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
    String errorMessage = new JSONObject(responseJson).getString("error");

    assertThat(errorMessage, is(IsEqual.equalTo("The request includes a permission that does not exist.")));
  }

  @Test
  public void PUT_whenUUIDIsInvalid_ReturnStatusNotFound() throws Exception{
    String credUUID = "not-a-uuid";

    MockHttpServletRequestBuilder putPermissionRequest = put("/api/v2/permissions/" + credUUID)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + "badCredentialName" + "\",\n"
            + "  \"operations\": [\"" + "write" + "\"]\n"
            + "}");

    String responseJson = mockMvc.perform(putPermissionRequest).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
    String errorMessage = new JSONObject(responseJson).getString("error");

    assertThat(errorMessage, is(IsEqual.equalTo("The request includes a permission that does not exist.")));
  }

  @Test
  public void PUT_whenUserGivesAPermission_forAPathAndActorThatDoesNotExist_theyReceiveA404() throws Exception{
    String credentialName = "does_not_exist";
    String fakeUUID = "fake_guid";

    MockHttpServletRequestBuilder putPermissionRequest = put("/api/v2/permissions/"+ fakeUUID)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + credentialName + "\",\n"
            + "  \"operations\": [\"" + "write" + "\"]\n"
            + "}");

    String responseJson = mockMvc.perform(putPermissionRequest).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
    String errorMessage = new JSONObject(responseJson).getString("error");

    assertThat(errorMessage, is(IsEqual.equalTo("The request includes a permission that does not exist.")));
  }

  @Test
  public void DELETE_whenPermissionIsDeletedForUserA_UserACannotAccessCredentialInAnyWay() throws Exception{
    String credentialName = "/test";
    String passwordValue = "passwordValue";

    UUID credUUID = this.setPermissions(credentialName, PermissionOperation.WRITE);

    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isOk());

    MockHttpServletRequestBuilder deletePermissionRequest = delete("/api/v2/permissions/" + credUUID)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON);

    String content = mockMvc.perform(deletePermissionRequest).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    PermissionsV2View returnValue = JsonTestHelper.deserialize(content, PermissionsV2View.class);
    assertThat(returnValue.getUuid(), equalTo(credUUID));
    assertThat(returnValue.getActor(), equalTo(USER_A_ACTOR_ID));
    assertThat(returnValue.getPath(), equalTo(credentialName));
    assertThat(returnValue.getOperations(), equalTo(Collections.singletonList(PermissionOperation.WRITE)));

    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isForbidden());
  }

  @Test
  public void DELETE_whenUserDoesNotHavePermission_CannotDeleteThePermission() throws Exception{
    String credentialName = "/test";
    String passwordValue = "passwordValue";

    UUID credUUID = this.setPermissions(credentialName, PermissionOperation.WRITE);

    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isOk());

    MockHttpServletRequestBuilder deletePermissionRequest = delete("/api/v2/permissions/" + credUUID)
        .header("Authorization", "Bearer " + NO_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON);

    mockMvc.perform(deletePermissionRequest).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
    setPassword(mockMvc, credentialName, passwordValue, USER_A_TOKEN).andExpect(status().isOk());
  }

  @Test
  public void DELETE_whenUserDeletesAPermission_withAnInvalidGuid_theyReceiveA404() throws Exception{
    String invalidGuid = "invalid";

    MockHttpServletRequestBuilder patchPermissionRequest = delete("/api/v2/permissions/" + invalidGuid)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON);

    String responseJson = mockMvc.perform(patchPermissionRequest).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
    String errorMessage = new JSONObject(responseJson).getString("error");

    assertThat(errorMessage, is(IsEqual.equalTo("The request includes a permission that does not exist.")));
  }

  @Test
  public void DELETE_whenUserDeletesAPermission_withAGuidThatDoeNotExist_theyReceiveA404() throws Exception{
    String nonExistingGuid = UUID.randomUUID().toString();

    MockHttpServletRequestBuilder patchPermissionRequest = delete("/api/v2/permissions/" + nonExistingGuid)
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON);

    String responseJson = mockMvc.perform(patchPermissionRequest).andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
    String errorMessage = new JSONObject(responseJson).getString("error");

    assertThat(errorMessage, is(IsEqual.equalTo("The request includes a permission that does not exist.")));
  }

  private UUID setPermissions(String credentialName, PermissionOperation operation) throws Exception{
    MockHttpServletRequestBuilder addPermissionRequest = post("/api/v2/permissions")
        .header("Authorization", "Bearer " + ALL_PERMISSIONS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"actor\": \"" + USER_A_ACTOR_ID + "\",\n"
            + "  \"path\": \"" + credentialName + "\",\n"
            + "  \"operations\": [\"" + operation.getOperation() + "\"]\n"
            + "}");

    String content = mockMvc.perform(addPermissionRequest).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    PermissionsV2View returnValue = JsonTestHelper.deserialize(content, PermissionsV2View.class);
    assertThat(returnValue.getActor(), equalTo(USER_A_ACTOR_ID));
    assertThat(returnValue.getPath(), equalTo(credentialName));
    assertThat(returnValue.getOperations(), equalTo(Collections.singletonList(operation)));

    assertThat(returnValue.getUuid(), notNullValue());
    UUID credentialUuid = returnValue.getUuid();

    return credentialUuid;
  }

  private ResultActions setPassword(MockMvc mockMvc, String credentialName, String passwordValue, String token) throws Exception {
    Map<String, Object> passwordRequestBody = new HashMap() {
      {
        put("name", credentialName);
        put("type", "password");
        put("value", passwordValue);
      }
    };

    String content = JsonTestHelper.serializeToString(passwordRequestBody);

    MockHttpServletRequestBuilder put = put("/api/v1/data")
        .header("Authorization", "Bearer " + token)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content(content);

    return mockMvc.perform(put);
  }

}
