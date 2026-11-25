package kr.ac.cbnu.cafeteria.payment;

/**
 * 결제 모듈 인터페이스(설계서 3.3절, EIR-SW-07).
 * Payment는 구현체를 모르므로, 실제 PG로 교체해도 도메인 코드는 바뀌지 않는다.
 */
public interface PaymentGateway {

    /** 승인 요청 */
    ApprovalResult approve(int amount, String method);

    /** 승인 번호로 환불 요청 */
    boolean refund(String approvalNo, int amount);
}
