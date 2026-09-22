package com.hnieacm.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @Author: HaoRan_Lyu
 * @Date: 2026/09/21
 * @Description: 资料变更申请请求的 Bean Validation 契约：email 为可选字段，但填写时必须是合法邮箱且不超过 255。
 */
class ProfileChangeCreateRequestTest {

    private static ValidatorFactory validatorFactory;

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void validEmailPasses() {
        assertThat(validator.validate(request("alice@example.com"))).isEmpty();
    }

    @Test
    void optionalEmailMayBeNull() {
        assertThat(validator.validate(request(null))).isEmpty();
    }

    @Test
    void optionalEmailMayBeEmpty() {
        assertThat(validator.validate(request(""))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-an-email", "a@@example.com"})
    void invalidEmailIsRejectedWithUnifiedMessage(String email) {
        Set<ConstraintViolation<ProfileChangeCreateRequest>> violations = validator.validate(request(email));

        assertThat(violations).isNotEmpty();
        assertThat(violations).allSatisfy(violation -> {
            assertThat(violation.getPropertyPath().toString()).isEqualTo("email");
            assertThat(violation.getMessage()).isEqualTo("email 格式不正确");
        });
    }

    @Test
    void emailFieldKeepsEmailAndSizeConstraints() throws NoSuchFieldException {
        assertThat(ProfileChangeCreateRequest.class.getDeclaredField("email").getAnnotation(Email.class)).isNotNull();
        assertThat(ProfileChangeCreateRequest.class.getDeclaredField("email").getAnnotation(Size.class).max())
                .isEqualTo(255);
    }

    @Test
    void oversizedEmailIsRejectedBySizeConstraint() {
        String oversized = "a".repeat(251) + "@e.io";

        Set<ConstraintViolation<ProfileChangeCreateRequest>> violations = validator.validate(request(oversized));

        assertThat(violations).anySatisfy(violation -> {
            assertThat(violation.getConstraintDescriptor().getAnnotation().annotationType()).isEqualTo(Size.class);
            assertThat(violation.getMessage()).isEqualTo("email 长度不能超过 255");
        });
    }

    private ProfileChangeCreateRequest request(String email) {
        ProfileChangeCreateRequest request = new ProfileChangeCreateRequest();
        request.setEmail(email);
        request.setReason("修改邮箱");
        return request;
    }
}
