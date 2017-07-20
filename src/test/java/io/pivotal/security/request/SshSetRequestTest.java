package io.pivotal.security.request;

import com.greghaskins.spectrum.Spectrum;
import org.junit.runner.RunWith;

import java.util.Set;
import javax.validation.ConstraintViolation;

import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.helper.JsonTestHelper.deserialize;
import static io.pivotal.security.helper.JsonTestHelper.deserializeAndValidate;
import static io.pivotal.security.helper.JsonTestHelper.hasViolationWithMessage;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertNull;

@RunWith(Spectrum.class)
public class SshSetRequestTest {

  {
    describe("when the value is valid", () -> {
      it("should not have violations", () -> {
        String json = "{"
            + "\"name\": \"/example/ssh\","
            + "\"type\": \"ssh\","
            + "\"value\": {"
            + "\"public_key\":\"fake-public-key\","
            + "\"private_key\":\"fake-private-key\""
            + "}"
            + "}";
        Set<ConstraintViolation<SshSetRequest>> violations = deserializeAndValidate(json,
            SshSetRequest.class);

        assertThat(violations.size(), equalTo(0));
      });

      it("should should deserialize to a SshSetRequest", () -> {
        String json = "{"
            + "\"name\": \"/example/ssh\","
            + "\"type\": \"ssh\","
            + "\"value\": {"
            + "\"public_key\":\"fake-public-key\","
            + "\"private_key\":\"fake-private-key\""
            + "}"
            + "}";
        SshSetRequest deserialize = deserialize(json, SshSetRequest.class);

        assertThat(deserialize, instanceOf(SshSetRequest.class));
      });

      it("should not require the public key SshSetRequest", () -> {
        String json = "{"
            + "\"name\": \"/example/ssh\","
            + "\"type\": \"ssh\","
            + "\"value\": {"
            + "\"private_key\":\"fake-private-key\""
            + "}"
            + "}";
        Set<ConstraintViolation<SshSetRequest>> violations = deserializeAndValidate(json,
            SshSetRequest.class);

        assertThat(violations.size(), equalTo(0));
      });

      it("should not require the private key SshSetRequest", () -> {
        String json = "{"
            + "\"name\": \"/example/ssh\","
            + "\"type\": \"ssh\","
            + "\"value\": {"
            + "\"public_key\":\"fake-public-key\""
            + "}"
            + "}";
        Set<ConstraintViolation<SshSetRequest>> violations = deserializeAndValidate(json,
            SshSetRequest.class);

        assertThat(violations.size(), equalTo(0));
      });
    });

    describe("when type has unusual casing", () -> {
      it("should be valid", () -> {
        String json = "{"
            + "\"name\": \"/example/ssh\","
            + "\"type\": \"sSh\","
            + "\"value\": {"
            + "\"public_key\":\"fake-public-key\","
            + "\"private_key\":\"fake-private-key\""
            + "}"
            + "}";
        Set<ConstraintViolation<SshSetRequest>> violations = deserializeAndValidate(json,
            SshSetRequest.class);

        assertThat(violations.size(), equalTo(0));
      });
    });

    describe("when no value is set", () -> {
      it("should be in invalid", () -> {
        String json = "{\n"
            + "  \"name\": \"/example/ssh\",\n"
            + "  \"type\": \"ssh\"\n"
            + "}";
        Set<ConstraintViolation<SshSetRequest>> violations = deserializeAndValidate(json,
            SshSetRequest.class);

        assertThat(violations, contains(hasViolationWithMessage("error.missing_value")));
      });
    });

    describe("when value is an empty object", () -> {
      it("should be invalid", () -> {
        String json = "{\n"
            + "  \"name\": \"/example/ssh\",\n"
            + "  \"type\": \"ssh\",\n"
            + "  \"value\": {}\n"
            + "}";
        Set<ConstraintViolation<SshSetRequest>> violations = deserializeAndValidate(json,
            SshSetRequest.class);

        assertThat(violations,
            contains(hasViolationWithMessage("error.missing_rsa_ssh_parameters")));
      });
    });

    describe("when ssh has all empty string sub-fields", () -> {
      it("should be invalid", () -> {
        String json = "{\n"
            + "  \"name\": \"/example/ssh\",\n"
            + "  \"type\": \"ssh\",\n"
            + "  \"value\": {"
            + "    \"public_key\":\"\","
            + "    \"private_key\":\"\""
            + "  }"
            + "}";
        Set<ConstraintViolation<SshSetRequest>> violations = deserializeAndValidate(json,
            SshSetRequest.class);

        assertThat(violations,
            contains(hasViolationWithMessage("error.missing_rsa_ssh_parameters")));
      });

      it("should coerce an empty public key into null", () -> {
        String json = "{"
            + "\"name\": \"/example/ssh\","
            + "\"type\": \"ssh\","
            + "\"value\": {"
            + "\"public_key\":\"\","
            + "\"private_key\":\"fake-private-key\""
            + "}"
            + "}";
        SshSetRequest deserialize = deserialize(json, SshSetRequest.class);

        assertNull(deserialize.getSshKeyValue().getPublicKey());
      });

      it("should coerce an empty private key into null", () -> {
        String json = "{"
            + "\"name\": \"/example/ssh\","
            + "\"type\": \"ssh\","
            + "\"value\": {"
            + "\"public_key\":\"fake-public-key\","
            + "\"private_key\":\"\""
            + "}"
            + "}";
        SshSetRequest deserialize = deserialize(json, SshSetRequest.class);

        assertNull(deserialize.getSshKeyValue().getPrivateKey());
      });
    });

    describe("when ssh has all null string sub-fields", () -> {
      it("should be invalid", () -> {
        String json = "{\n"
            + "  \"name\": \"/example/ssh\",\n"
            + "  \"type\": \"ssh\",\n"
            + "  \"value\": {"
            + "    \"public_key\":null,"
            + "    \"private_key\":null"
            + "  }"
            + "}";
        Set<ConstraintViolation<SshSetRequest>> violations = deserializeAndValidate(json,
            SshSetRequest.class);

        assertThat(violations,
            contains(hasViolationWithMessage("error.missing_rsa_ssh_parameters")));
      });
    });
  }
}
