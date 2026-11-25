package kr.ac.cbnu.cafeteria.payment;

/** 결제 승인 결과(설계서 3.4절). approvalNo는 환불 시 같은 거래를 식별하는 데 쓴다. */
public record ApprovalResult(boolean approved, String approvalNo) {

    public static ApprovalResult rejected() {
        return new ApprovalResult(false, null);
    }
}
