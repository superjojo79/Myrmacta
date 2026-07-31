package com.flowforge.infrastructure.tenant;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class TenantIdValidatorTest {

    @Test
    void rejectsNull_withNullSafeMessage() {
        assertThatThrownBy(() -> TenantIdValidator.validate(null))
                .isInstanceOf(InvalidTenantIdException.class)
                .hasMessageContaining("null");
    }

    @Test
    void rejectsInvalidId_withIdInMessage() {
        assertThatThrownBy(() -> TenantIdValidator.validate("tenant-a"))
                .isInstanceOf(InvalidTenantIdException.class)
                .hasMessageContaining("tenant-a");
    }

    @ParameterizedTest
    @ValueSource(strings = {"tenant_a", "tenant_dev", "abc"})
    void acceptsValidTenantIds(String tenantId) {
        assertThatCode(() -> TenantIdValidator.validate(tenantId)).doesNotThrowAnyException();
    }

    @Test
    void acceptsMaxLengthTenantId() {
        String id = "a" + "b".repeat(62); // 63 chars total
        assertThatCode(() -> TenantIdValidator.validate(id)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "tenant-a", "Tenant_a", "1tenant", "ab", "a"})
    void rejectsInvalidTenantIds(String tenantId) {
        assertThatThrownBy(() -> TenantIdValidator.validate(tenantId))
                .isInstanceOf(InvalidTenantIdException.class);
    }

    @Test
    void rejectsTooLongTenantId() {
        String id = "a" + "b".repeat(63); // 64 chars total
        assertThatThrownBy(() -> TenantIdValidator.validate(id))
                .isInstanceOf(InvalidTenantIdException.class);
    }
}
