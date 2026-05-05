package com.legic.interview.protocol;

public class WiegandParser {
    public WiegandData parse26Bit(long rawData) {
        // Parity bits at start and end, 8 bits facility code, 16 bits card number
        int facilityCode = (int) ((rawData >> 17) & 0xFF);
        int cardNumber = (int) ((rawData >> 1) & 0xFFFF);
        return new WiegandData(facilityCode, cardNumber);
    }
}
