package com.example.backend.auth.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterRequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"matkhau1", "12345abc", "Mật khẩu 2024", "abc!@#12"})
    void acceptsPasswordsWithEightCharactersLettersAndDigits(String password) {
        assertThat(validator.validateProperty(request("tranthib", password), "password")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "matkhaumoi", "12345678", "        ", ""})
    void rejectsShortOrLetterOnlyOrDigitOnlyPasswords(String password) {
        assertThat(validator.validateProperty(request("tranthib", password), "password")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "tran thi b", "trần.b", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rejectsInvalidUsernames(String username) {
        assertThat(validator.validateProperty(request(username, "matkhau123"), "username")).isNotEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"tranthib", "tran.thi_b-01", " khach01 "})
    void acceptsValidUsernames(String username) {
        assertThat(validator.validateProperty(request(username, "matkhau123"), "username")).isEmpty();
    }

    private static RegisterRequest request(String username, String password) {
        RegisterRequest request = new RegisterRequest();
        request.setUsername(username);
        request.setFullName("Trần Thị B");
        request.setPassword(password);
        return request;
    }
}
