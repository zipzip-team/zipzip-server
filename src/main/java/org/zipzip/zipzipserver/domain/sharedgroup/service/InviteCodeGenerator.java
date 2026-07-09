package org.zipzip.zipzipserver.domain.sharedgroup.service;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class InviteCodeGenerator {

    private static final char[] CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int INVITE_CODE_LENGTH = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        StringBuilder inviteCode = new StringBuilder(INVITE_CODE_LENGTH);
        for (int index = 0; index < INVITE_CODE_LENGTH; index++) {
            inviteCode.append(CHARACTERS[secureRandom.nextInt(CHARACTERS.length)]);
        }
        return inviteCode.toString();
    }
}
