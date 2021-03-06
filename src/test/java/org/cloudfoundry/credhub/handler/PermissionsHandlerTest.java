package org.cloudfoundry.credhub.handler;

import com.google.common.collect.Lists;
import org.cloudfoundry.credhub.audit.CEFAuditRecord;
import org.cloudfoundry.credhub.data.CredentialDataService;
import org.cloudfoundry.credhub.domain.CredentialVersion;
import org.cloudfoundry.credhub.domain.PasswordCredentialVersion;
import org.cloudfoundry.credhub.entity.Credential;
import org.cloudfoundry.credhub.entity.PasswordCredentialVersionData;
import org.cloudfoundry.credhub.entity.PermissionData;
import org.cloudfoundry.credhub.exceptions.EntryNotFoundException;
import org.cloudfoundry.credhub.exceptions.InvalidPermissionOperationException;
import org.cloudfoundry.credhub.request.PermissionEntry;
import org.cloudfoundry.credhub.request.PermissionOperation;
import org.cloudfoundry.credhub.request.PermissionsRequest;
import org.cloudfoundry.credhub.request.PermissionsV2Request;
import org.cloudfoundry.credhub.service.PermissionCheckingService;
import org.cloudfoundry.credhub.service.PermissionService;
import org.cloudfoundry.credhub.service.PermissionedCredentialService;
import org.cloudfoundry.credhub.view.PermissionsView;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static org.cloudfoundry.credhub.handler.PermissionsHandler.INVALID_NUMBER_OF_PERMISSIONS;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.collection.IsCollectionWithSize.hasSize;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class PermissionsHandlerTest {
  private static final String CREDENTIAL_NAME = "/test-credential";
  private static final String ACTOR_NAME = "test-actor";
  private static final String ACTOR_NAME2 = "someone-else";
  private static final String USER = "test-user";

  private PermissionsHandler subject;

  private PermissionService permissionService;
  private PermissionCheckingService permissionCheckingService;
  private CredentialDataService credentialDataService;
  private PermissionedCredentialService permissionedCredentialService;
  private CEFAuditRecord auditRecord;

  private final Credential credential = new Credential(CREDENTIAL_NAME);
  private final CredentialVersion credentialVersion = new PasswordCredentialVersion(new PasswordCredentialVersionData(CREDENTIAL_NAME));
  private PermissionsRequest permissionsRequest;
  private PermissionsV2Request permissionsV2Request;

  @Before
  public void beforeEach() {
    permissionService = mock(PermissionService.class);
    permissionCheckingService = mock(PermissionCheckingService.class);
    credentialDataService = mock(CredentialDataService.class);
    permissionedCredentialService = mock(PermissionedCredentialService.class);
    auditRecord = mock(CEFAuditRecord.class);
    subject = new PermissionsHandler(
        permissionService,
        permissionedCredentialService, auditRecord);

    permissionsRequest = mock(PermissionsRequest.class);
    permissionsV2Request = new PermissionsV2Request();

    when(permissionedCredentialService.findMostRecent(CREDENTIAL_NAME)).thenReturn(credentialVersion);
    when(credentialDataService.find(any(String.class))).thenReturn(credential);
  }

  @Test
  public void getPermissions_whenTheNameDoesntStartWithASlash_fixesTheName() {
    List<PermissionEntry> accessControlList = newArrayList();
    when(permissionService.getPermissions(any(CredentialVersion.class)))
        .thenReturn(accessControlList);
    when(permissionCheckingService
        .hasPermission(any(String.class), eq(CREDENTIAL_NAME), eq(PermissionOperation.READ_ACL)))
        .thenReturn(true);

    PermissionsView response = subject.getPermissions(CREDENTIAL_NAME);
    assertThat(response.getCredentialName(), equalTo(CREDENTIAL_NAME));

  }

  @Test
  public void getPermissions_verifiesTheUserHasPermissionToReadTheAcl_andReturnsTheAclResponse() {
    ArrayList<PermissionOperation> operations = newArrayList(
        PermissionOperation.READ,
        PermissionOperation.WRITE
    );
    when(permissionCheckingService
        .hasPermission(any(String.class), eq(CREDENTIAL_NAME), eq(PermissionOperation.READ_ACL)))
        .thenReturn(true);
    PermissionEntry permissionEntry = new PermissionEntry(
        ACTOR_NAME,
        "test-path",
        operations
    );
    List<PermissionEntry> accessControlList = newArrayList(permissionEntry);
    when(permissionService.getPermissions(credentialVersion))
        .thenReturn(accessControlList);

    PermissionsView response = subject.getPermissions(
        CREDENTIAL_NAME
    );

    List<PermissionEntry> accessControlEntries = response.getPermissions();

    assertThat(response.getCredentialName(), equalTo(CREDENTIAL_NAME));
    assertThat(accessControlEntries, hasSize(1));

    PermissionEntry entry = accessControlEntries.get(0);
    assertThat(entry.getActor(), equalTo(ACTOR_NAME));

    List<PermissionOperation> allowedOperations = entry.getAllowedOperations();
    assertThat(allowedOperations, contains(
        equalTo(PermissionOperation.READ),
        equalTo(PermissionOperation.WRITE)
    ));
  }

  @Test
  public void setPermissions_setsAndReturnsThePermissions() {
    when(permissionCheckingService
        .hasPermission(any(String.class), eq(CREDENTIAL_NAME), eq(PermissionOperation.WRITE_ACL)))
        .thenReturn(true);
    when(permissionCheckingService
        .userAllowedToOperateOnActor(ACTOR_NAME))
        .thenReturn(true);

    ArrayList<PermissionOperation> operations = newArrayList(
        PermissionOperation.READ,
        PermissionOperation.WRITE
    );
    PermissionEntry permissionEntry = new PermissionEntry(ACTOR_NAME, "test-path", operations);
    List<PermissionEntry> accessControlList = newArrayList(permissionEntry);

    PermissionEntry preexistingPermissionEntry = new PermissionEntry(ACTOR_NAME2, "test-path", Lists.newArrayList(PermissionOperation.READ)
    );
    List<PermissionEntry> expectedControlList = newArrayList(permissionEntry,
        preexistingPermissionEntry);

    when(permissionService.getPermissions(credentialVersion))
        .thenReturn(expectedControlList);

    when(permissionsRequest.getCredentialName()).thenReturn(CREDENTIAL_NAME);
    when(permissionsRequest.getPermissions()).thenReturn(accessControlList);

    subject.setPermissions(permissionsRequest);

    ArgumentCaptor<List> permissionsListCaptor = ArgumentCaptor.forClass(List.class);
    verify(permissionService).savePermissionsForUser(permissionsListCaptor.capture());

    PermissionEntry entry = accessControlList.get(0);
    assertThat(entry.getActor(), equalTo(ACTOR_NAME));
    assertThat(entry.getPath(), equalTo(CREDENTIAL_NAME));
    assertThat(entry.getAllowedOperations(), contains(equalTo(PermissionOperation.READ), equalTo(PermissionOperation.WRITE)));
  }

  @Test(expected = IllegalArgumentException.class)
  public void setPermissionsCalledWithOnePermission_whenPermissionServiceReturnsMultiplePermissions_throwsException(){
    when(permissionCheckingService.hasPermission(any(String.class), eq(CREDENTIAL_NAME), eq(PermissionOperation.WRITE_ACL))).thenReturn(true);
    when(permissionCheckingService.userAllowedToOperateOnActor(ACTOR_NAME)).thenReturn(true);

    ArrayList<PermissionData> permissionList = new ArrayList<>();
    permissionList.add(new PermissionData());
    permissionList.add(new PermissionData());

    when(permissionService.savePermissionsForUser(any())).thenReturn(permissionList);

    ArrayList<PermissionOperation> operations = newArrayList(PermissionOperation.READ, PermissionOperation.WRITE);
    PermissionEntry permissionEntry = new PermissionEntry(ACTOR_NAME, "test-path", operations);

    PermissionEntry preexistingPermissionEntry = new PermissionEntry(ACTOR_NAME2, "test-path", Lists.newArrayList(PermissionOperation.READ));
    List<PermissionEntry> expectedControlList = newArrayList(permissionEntry, preexistingPermissionEntry);

    when(permissionService.getPermissions(credentialVersion)).thenReturn(expectedControlList);

    permissionsV2Request.setOperations(operations);
    permissionsV2Request.setPath(CREDENTIAL_NAME);

    try{
      subject.setPermissions(permissionsV2Request);
    } catch (Exception e) {
      assertThat(e.getMessage(), equalTo(INVALID_NUMBER_OF_PERMISSIONS));
      throw e;
    }

  }

  @Test
  public void setPermissions_whenUserUpdatesOwnPermission_throwsException() {
    when(permissionCheckingService
        .hasPermission(any(String.class), eq(CREDENTIAL_NAME), eq(PermissionOperation.WRITE_ACL)))
        .thenReturn(true);
    when(permissionCheckingService
        .userAllowedToOperateOnActor(ACTOR_NAME))
        .thenReturn(false);

    List<PermissionEntry> accessControlList = Arrays.asList(new PermissionEntry(ACTOR_NAME, "test-path", Arrays.asList(
        PermissionOperation.READ)));
    when(permissionsRequest.getCredentialName()).thenReturn(CREDENTIAL_NAME);
    when(permissionsRequest.getPermissions()).thenReturn(accessControlList);

    try {
      subject.setPermissions(permissionsRequest);
    } catch (InvalidPermissionOperationException e) {
      assertThat(e.getMessage(), equalTo("error.permission.invalid_update_operation"));
      verify(permissionService, times(0)).savePermissionsForUser(any());
    }
  }

  @Test
  public void deletePermissions_whenTheUserHasPermission_deletesTheAce() {
    when(permissionCheckingService
        .hasPermission(any(String.class), eq(CREDENTIAL_NAME), eq(PermissionOperation.WRITE_ACL)))
        .thenReturn(true);
    when(permissionService.deletePermissions(CREDENTIAL_NAME, ACTOR_NAME))
        .thenReturn(true);
    when(permissionCheckingService
        .userAllowedToOperateOnActor(ACTOR_NAME))
        .thenReturn(true);

    subject.deletePermissionEntry(CREDENTIAL_NAME, ACTOR_NAME
    );

    verify(permissionService, times(1)).deletePermissions(
        CREDENTIAL_NAME, ACTOR_NAME);

  }

  @Test
  public void deletePermissions_whenNothingIsDeleted_throwsAnException() {
    when(permissionService.deletePermissions(CREDENTIAL_NAME, ACTOR_NAME))
        .thenReturn(false);

    try {
      subject.deletePermissionEntry(CREDENTIAL_NAME, ACTOR_NAME
      );
      fail("should throw");
    } catch (EntryNotFoundException e) {
      assertThat(e.getMessage(), equalTo("error.credential.invalid_access"));
    }
  }
}
