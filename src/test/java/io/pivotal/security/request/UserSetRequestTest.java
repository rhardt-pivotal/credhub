package io.pivotal.security.request;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.credential.UserCredentialValue;
import io.pivotal.security.helper.JsonTestHelper;
import org.junit.Assert;
import org.junit.runner.RunWith;

import java.util.Set;
import javax.validation.ConstraintViolation;

import static com.greghaskins.spectrum.Spectrum.*;
import static io.pivotal.security.helper.JsonTestHelper.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.core.IsEqual.equalTo;

@RunWith(Spectrum.class)
public class UserSetRequestTest {

  private String validSetRequestJson;

  {
    beforeEach(() -> {
      // language=JSON
      validSetRequestJson = "{\n" +
          "  \"name\": \"some-name\",\n" +
          "  \"type\": \"user\",\n" +
          "  \"overwrite\": true,\n" +
          "  \"value\": {\n" +
          "    \"username\": \"dan\",\n" +
          "    \"password\": \"example-password\"\n" +
          "  }\n" +
          "}";
    });

    it("deserializes to UserSetRequest", () -> {
      UserSetRequest userSetRequest = JsonTestHelper.deserializeChecked(validSetRequestJson, UserSetRequest.class);

      Assert.assertThat(userSetRequest, instanceOf(UserSetRequest.class));
    });

    describe("when value is empty", () -> {
      it("should be invalid", () -> {
        // language=JSON
        String json = "{\n" +
            "  \"name\": \"some-name\",\n" +
            "  \"type\": \"user\",\n" +
            "  \"overwrite\": true\n" +
            "}";
        UserSetRequest userSetRequest = deserialize(json,
            UserSetRequest.class);
        Set<ConstraintViolation<UserSetRequest>> violations = validate(userSetRequest);

        assertThat(violations, contains(hasViolationWithMessage("error.missing_value")));
      });
    });

    describe("when type has unusual casing", () -> {
      it("should be valid", () -> {
        // language=JSON
        String json = "{\n" +
            "  \"name\": \"some-name\",\n" +
            "  \"type\": \"UseR\",\n" +
            "  \"overwrite\": true,\n" +
            "  \"value\": {\n" +
            "    \"username\": \"dan\",\n" +
            "    \"password\": \"example-password\"\n" +
            "  }\n" +
            "}";
        Set<ConstraintViolation<UserSetRequest>> violations = deserializeAndValidate(json, UserSetRequest.class);

        assertThat(violations.size(), equalTo(0));
      });
    });


    describe("when all fields are set", () -> {
      it("should be valid", () -> {
        Set<ConstraintViolation<UserSetRequest>> violations = deserializeAndValidate(validSetRequestJson,
            UserSetRequest.class);

        assertThat(violations.size(), equalTo(0));
      });

      it("should have valid 'value' field", () -> {
        UserSetRequest userSetRequest = JsonTestHelper.deserialize(validSetRequestJson, UserSetRequest.class);

        UserCredentialValue userValue = userSetRequest.getUserValue();
        assertThat(userValue.getUsername(), equalTo("dan"));
        assertThat(userValue.getPassword(), equalTo("example-password"));
      });
    });

    describe("when password is not sent in request", () -> {
      it("should be invalid", () -> {

        String invalidSetRequestJson = "{\n" +
            "  \"name\": \"some-name\",\n" +
            "  \"type\": \"user\",\n" +
            "  \"overwrite\": true,\n" +
            "  \"value\": {\n" +
            "    \"username\": \"dan\"\n" +
            "  }\n" +
            "}";

        Set<ConstraintViolation<UserSetRequest>> violations = JsonTestHelper.deserializeAndValidate(invalidSetRequestJson, UserSetRequest.class);

        assertThat(violations, contains(hasViolationWithMessage("error.missing_password")));
      });
    });
  }
}
