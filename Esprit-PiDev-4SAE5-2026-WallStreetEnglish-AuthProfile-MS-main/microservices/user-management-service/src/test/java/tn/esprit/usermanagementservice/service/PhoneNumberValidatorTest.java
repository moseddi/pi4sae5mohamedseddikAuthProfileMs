package tn.esprit.usermanagementservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PhoneNumberValidator Tests")
class PhoneNumberValidatorTest {

    private PhoneNumberValidator phoneNumberValidator;

    @BeforeEach
    void setUp() {
        phoneNumberValidator = new PhoneNumberValidator();
    }

    @Nested
    @DisplayName("isValid Tests")
    class IsValidTests {

        @Test
        @DisplayName("Should return true for valid Tunisian phone number")
        void isValid_ValidTunisianNumber_ShouldReturnTrue() {
            boolean result = phoneNumberValidator.isValid("20123456", "TN");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return true for valid French phone number")
        void isValid_ValidFrenchNumber_ShouldReturnTrue() {
            boolean result = phoneNumberValidator.isValid("612345678", "FR");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("Should return false for invalid phone number")
        void isValid_InvalidNumber_ShouldReturnFalse() {
            boolean result = phoneNumberValidator.isValid("123", "TN");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for empty phone number")
        void isValid_EmptyNumber_ShouldReturnFalse() {
            boolean result = phoneNumberValidator.isValid("", "TN");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for null phone number")
        void isValid_NullNumber_ShouldReturnFalse() {
            boolean result = phoneNumberValidator.isValid(null, "TN");

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Should return false for invalid country code")
        void isValid_InvalidCountryCode_ShouldReturnFalse() {
            boolean result = phoneNumberValidator.isValid("20123456", "XX");

            assertThat(result).isFalse();
        }
    }

    @Nested
    @DisplayName("format Tests")
    class FormatTests {

        @Test
        @DisplayName("Should format valid Tunisian number to E164 format")
        void format_ValidTunisianNumber_ShouldFormatCorrectly() {
            String result = phoneNumberValidator.format("20123456", "TN");

            assertThat(result).isEqualTo("+21620123456");
        }

        @Test
        @DisplayName("Should format valid French number to E164 format")
        void format_ValidFrenchNumber_ShouldFormatCorrectly() {
            String result = phoneNumberValidator.format("612345678", "FR");

            assertThat(result).isEqualTo("+33612345678");
        }

        @Test
        @DisplayName("Should return original number when parsing fails")
        void format_InvalidNumber_ShouldReturnOriginal() {
            String result = phoneNumberValidator.format("invalid", "TN");

            assertThat(result).isEqualTo("invalid");
        }

        @Test
        @DisplayName("Should return original number for null country code")
        void format_NullCountryCode_ShouldReturnOriginal() {
            String result = phoneNumberValidator.format("20123456", null);

            assertThat(result).isEqualTo("20123456");
        }

        @Test
        @DisplayName("Should return empty string when number is empty")
        void format_EmptyNumber_ShouldReturnEmpty() {
            String result = phoneNumberValidator.format("", "TN");

            assertThat(result).isEmpty();
        }
    }
}