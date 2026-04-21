package tn.esprit.usermanagementservice.service;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import org.springframework.stereotype.Component;

@Component
public class PhoneNumberValidator {

    private final PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();

    public boolean isValid(String phoneNumber, String countryCode) {
        try {
            Phonenumber.PhoneNumber number = phoneUtil.parse(phoneNumber, countryCode);
            return phoneUtil.isValidNumber(number);
        } catch (NumberParseException e) {
            return false;
        }
    }

    public String format(String phoneNumber, String countryCode) {
        try {
            Phonenumber.PhoneNumber number = phoneUtil.parse(phoneNumber, countryCode);
            return phoneUtil.format(number, PhoneNumberUtil.PhoneNumberFormat.E164);
        } catch (NumberParseException e) {
            return phoneNumber; // return as-is if parsing fails
        }
    }
}