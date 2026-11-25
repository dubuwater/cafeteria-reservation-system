package kr.ac.cbnu.cafeteria.payment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

/*=====================================================================
 파일명   : MockPaymentGateway.java
 기능     : 모의 결제 모듈. 실제 금전 거래 없이 승인·환불 결과만 돌려준다(설계서 4.9절).
            app.mock-payment.fail=true로 실행하면 승인을 거절해 실패 흐름을 시연할 수 있다.
=====================================================================*/
@Component
public class MockPaymentGateway implements PaymentGateway {

    static final String PREFIX = "MOCK-";

    private final boolean alwaysFail;

    public MockPaymentGateway(@Value("${app.mock-payment.fail:false}") boolean alwaysFail) {
        this.alwaysFail = alwaysFail;
    }

    /** 설계서 4.9.1 MockPaymentGateway::approve() */
    @Override
    public ApprovalResult approve(int amount, String method) {
        if (amount <= 0 || alwaysFail) {
            return ApprovalResult.rejected();
        }
        // 승인 번호는 approval_no VARCHAR(40)에 맞도록 하이픈 없는 UUID를 쓴다(MOCK- + 32자 = 37자).
        return new ApprovalResult(true, PREFIX + UUID.randomUUID().toString().replace("-", ""));
    }

    /** 설계서 4.9.2 MockPaymentGateway::refund() */
    @Override
    public boolean refund(String approvalNo, int amount) {
        return approvalNo != null && approvalNo.startsWith(PREFIX) && amount > 0;
    }
}
